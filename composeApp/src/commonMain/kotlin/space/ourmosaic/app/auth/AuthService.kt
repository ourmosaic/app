package space.ourmosaic.app.auth

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import space.ourmosaic.app.createSettings
import space.ourmosaic.app.createEncryptedSettings
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import space.ourmosaic.app.utils.Logger

class AuthService private constructor() {
    companion object {
        private val internalInstance by lazy { AuthService() }
        
        fun getInstance(): AuthService = internalInstance

        // Pour compatibilité avec l'appel remember { AuthService() }
        operator fun invoke(): AuthService = internalInstance
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    space.ourmosaic.app.utils.Logger.d("AuthServiceHTTP", message)
                }
            }
            level = LogLevel.ALL
        }
    }

    private val settings = createSettings()
    private val secureSettings = createEncryptedSettings()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val refreshMutex = Mutex()

    private val _userMe = MutableStateFlow<UserMeResponse?>(null)
    val userMe: StateFlow<UserMeResponse?> = _userMe.asStateFlow()

    private var memoryAccessToken: String? = null
    private var memoryRefreshToken: String? = null
    private var memoryFederation: String? = null

    init {
        // Hydrater le cache mémoire au démarrage
        memoryFederation = settings.getStringOrNull("federation")
        memoryAccessToken = secureSettings.getStringOrNull("access_token")
        memoryRefreshToken = secureSettings.getStringOrNull("refresh_token")

        // Hydrater le StateFlow depuis le cache au démarrage
        val cached = settings.getStringOrNull("cached_user_me")
        if (cached != null) {
            try {
                _userMe.value = json.decodeFromString(UserMeResponse.serializer(), cached)
            } catch (_: Exception) {
                // Cache invalide, ignorer
            }
        }
    }

    suspend fun login(federation: String, identifier: String, password: String): Result<AuthenticationResponse> {
        val url = "https://$federation/auth/login"
        
        val requestBody = LoginRequest(
            username = identifier,
            email = identifier,
            password = password
        )

        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (response.status.isSuccess()) {
                val authResponse = response.body<AuthenticationResponse>()
                saveAuthData(federation, authResponse)
                getUserMe()
                Result.success(authResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Login failed (${response.status.value}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(federation: String, username: String, email: String, password: String): Result<AuthenticationResponse> {
        val url = "https://$federation/auth/register"
        val requestBody = RegisterRequest(username = username, email = email, password = password)

        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (response.status.isSuccess()) {
                val authResponse = response.body<AuthenticationResponse>()
                saveAuthData(federation, authResponse)
                getUserMe()
                Result.success(authResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Registration failed (${response.status.value}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vérifie si une erreur 401 est réellement due à un token expiré
     * et non à une erreur métier (comme SYSTEM_NOT_FOUND).
     */
    fun isTokenExpiredError(response: HttpResponse, body: String): Boolean {
        if (response.status != HttpStatusCode.Unauthorized) return false
        // Si le message contient SYSTEM_NOT_FOUND ou USER_NOT_FOUND, ce n'est pas un problème de token
        // Dans ces cas, le 401 indique que la ressource n'est pas accessible, pas que le token est expiré.
        return !body.contains("SYSTEM_NOT_FOUND") && 
               !body.contains("USER_NOT_FOUND") && 
               !body.contains("NO_SYSTEM_FOUND")
    }

    fun hasSystem(): Boolean {
        val user = _userMe.value ?: return false
        return user.isSystem || user.system != null || user.systems.isNotEmpty()
    }

    suspend fun refreshToken(): Result<AuthenticationResponse> {
        val currentToken = getAccessToken()
        
        return refreshMutex.withLock {
            // Vérifier si un autre thread a déjà rafraîchi le token pendant qu'on attendait le mutex
            val latestToken = getAccessToken()
            if (latestToken != null && latestToken != currentToken) {
                Logger.d("AuthService", "Token was already refreshed by another request")
                // On renvoie un succès minimal, les champs d'expiration ne sont pas critiques ici car
                // l'appelant veut surtout savoir si le rafraîchissement a réussi.
                return Result.success(AuthenticationResponse(latestToken, getRefreshToken() ?: "", 0, 0))
            }

            val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
            val refreshToken = getRefreshToken() ?: return Result.failure(Exception("No refresh token stored"))
            
            val url = "https://$federation/auth/token/refresh"
            Logger.d("AuthService", "Attempting token refresh...")

            try {
                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(refreshToken))
                }
                if (response.status.isSuccess()) {
                    val authResponse = response.body<AuthenticationResponse>()
                    saveAuthData(federation, authResponse)
                    Logger.d("AuthService", "Token refresh successful")
                    Result.success(authResponse)
                } else {
                    val error = response.bodyAsText()
                    Logger.e("AuthService", "Token refresh failed: ${response.status} - $error")
                    
                    // Si le refresh token est invalide, la session est morte
                    if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.BadRequest) {
                        Logger.e("AuthService", "Refresh token invalid, logging out")
                        logout()
                    }
                    Result.failure(Exception("Refresh failed: ${response.status}"))
                }
            } catch (e: Exception) {
                Logger.e("AuthService", "Token refresh exception: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getUserMe(allowRetry: Boolean = true): Result<UserMeResponse> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val token = getAccessToken() ?: return Result.failure(Exception("No access token stored"))
        
        val url = "https://$federation/auth/me"
        
        return try {
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            
            val responseBody = response.bodyAsText()

            if (response.status == HttpStatusCode.Unauthorized && allowRetry) {
                if (isTokenExpiredError(response, responseBody)) {
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        return getUserMe(allowRetry = false)
                    }
                }
            }
            if (response.status.isSuccess()) {
                val userMeResponse = json.decodeFromString<UserMeResponse>(responseBody)
                updateUserMe(userMeResponse)
                Result.success(userMeResponse)
            } else {
                Logger.e("AuthService", "getUserMe failed with status ${response.status}: $responseBody")
                // Si on a une réponse d'erreur du serveur (4xx, 5xx), on ne fallback pas forcément sur le cache
                // car cela masquerait des changements importants (ex: compte désactivé, plus de système, etc.)
                if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                    _userMe.value = null
                    settings.remove("cached_user_me")
                    Result.failure(Exception("Session expired or access denied"))
                } else {
                    val cached = settings.getStringOrNull("cached_user_me")
                    if (cached != null) {
                        Result.success(json.decodeFromString(UserMeResponse.serializer(), cached))
                    } else {
                        Result.failure(Exception("Failed to get user info: ${response.status}"))
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("AuthService", "getUserMe exception: ${e.message}")
            val cached = settings.getStringOrNull("cached_user_me")
            if (cached != null) {
                try {
                    val user = json.decodeFromString(UserMeResponse.serializer(), cached)
                    _userMe.value = user
                    Result.success(user)
                } catch (_: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }

    fun updateUserMe(user: UserMeResponse) {
        settings["cached_user_me"] = json.encodeToString(UserMeResponse.serializer(), user)
        
        // Synchroniser le system_id pour les autres services
        // On sélectionne par défaut le système racine (parentSystemId == null)
        val systemId = user.systems.find { it.parentSystemId == null }?.id ?: user.system?.id

        if (systemId != null) {
            settings["system_id"] = systemId
        } else {
            settings.remove("system_id")
        }

        _userMe.value = user
    }

    suspend fun logout(offlineManager: space.ourmosaic.app.offline.OfflineManager? = null): Boolean {
        val federation = getFederation()
        val token = getAccessToken()
        
        if (federation != null && token != null) {
            try {
                client.delete("https://$federation/auth/session") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            } catch (_: Exception) {
                // Ignore failure on session delete
            }
        }
        
        memoryAccessToken = null
        memoryRefreshToken = null
        memoryFederation = null

        secureSettings.remove("access_token")
        secureSettings.remove("refresh_token")
        settings.remove("federation")
        settings.remove("cached_user_me")
        settings.remove("cached_members")
        settings.remove("system_id") // clear system_id too
        _userMe.value = null
        
        offlineManager?.clearAllData()
        return true
    }

    private fun saveAuthData(federation: String, response: AuthenticationResponse) {
        memoryFederation = federation
        memoryAccessToken = response.accessToken
        memoryRefreshToken = response.refreshToken

        settings.putString("federation", federation)
        secureSettings.putString("access_token", response.accessToken)
        secureSettings.putString("refresh_token", response.refreshToken)
        Logger.d("AuthService", "Auth data saved and cached in memory for federation: $federation")
    }

    fun getFederation(): String? = memoryFederation ?: settings.getStringOrNull("federation").also { memoryFederation = it }
    fun getAccessToken(): String? = memoryAccessToken ?: secureSettings.getStringOrNull("access_token").also { memoryAccessToken = it }
    fun getRefreshToken(): String? = memoryRefreshToken ?: secureSettings.getStringOrNull("refresh_token").also { memoryRefreshToken = it }

    suspend fun createSystem(allowRetry: Boolean = true): Result<Unit> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val token = getAccessToken()
        
        if (token.isNullOrBlank()) {
            Logger.e("AuthService", "createSystem: Access token is null or empty!")
            return Result.failure(Exception("No access token stored"))
        }

        return try {
            val response = client.post("https://$federation/v2/system/@me") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("{}") // Envoie un corps vide pour éviter le 400 si le backend l'exige
            }
            
            val responseBody = response.bodyAsText()

            if (response.status == HttpStatusCode.Unauthorized && allowRetry) {
                if (isTokenExpiredError(response, responseBody)) {
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        return createSystem(allowRetry = false)
                    }
                }
            }
            if (response.status.isSuccess()) {
                getUserMe() // Refresh cache and flow
                Result.success(Unit)
            } else {
                Logger.e("AuthService", "Failed to create system: ${response.status}, body: $responseBody")
                Result.failure(Exception("Failed to create system: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromSimplyPlural(apiKey: String, allowRetry: Boolean = true): Result<String> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val token = getAccessToken() ?: return Result.failure(Exception("No access token stored"))

        return try {
            val response = client.post("https://$federation/v1/import/simplyplural/api") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("apiKey" to apiKey))
            }
            
            val responseBody = response.bodyAsText()

            if (response.status == HttpStatusCode.Unauthorized && allowRetry) {
                if (isTokenExpiredError(response, responseBody)) {
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        return importFromSimplyPlural(apiKey, allowRetry = false)
                    }
                }
            }
            if (response.status.isSuccess()) {
                val body = json.decodeFromString<Map<String, String>>(responseBody)
                val importId = body["importId"] ?: ""
                Result.success(importId)
            } else {
                Result.failure(Exception("Import failed: ${response.status} - $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromSimplyPluralJson(jsonData: String, allowRetry: Boolean = true): Result<String> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val token = getAccessToken() ?: return Result.failure(Exception("No access token stored"))

        return try {
            val response = client.post("https://$federation/v1/import/simplyplural") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(jsonData)
            }
            
            val responseBody = response.bodyAsText()

            if (response.status == HttpStatusCode.Unauthorized && allowRetry) {
                if (isTokenExpiredError(response, responseBody)) {
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        return importFromSimplyPluralJson(jsonData, allowRetry = false)
                    }
                }
            }
            if (response.status.isSuccess()) {
                val body = json.decodeFromString<Map<String, String>>(responseBody)
                val importId = body["importId"] ?: ""
                Result.success(importId)
            } else {
                Result.failure(Exception("Import failed: ${response.status} - $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromAmpersand(jsonData: String, allowRetry: Boolean = true): Result<String> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val token = getAccessToken() ?: return Result.failure(Exception("No access token stored"))

        return try {
            val response = client.post("https://$federation/v1/import/ampersand") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(jsonData)
            }
            
            val responseBody = response.bodyAsText()

            if (response.status == HttpStatusCode.Unauthorized && allowRetry) {
                if (isTokenExpiredError(response, responseBody)) {
                    val refreshResult = refreshToken()
                    if (refreshResult.isSuccess) {
                        return importFromAmpersand(jsonData, allowRetry = false)
                    }
                }
            }
            if (response.status.isSuccess()) {
                val body = json.decodeFromString<Map<String, String>>(responseBody)
                val importId = body["importId"] ?: ""
                Result.success(importId)
            } else {
                Result.failure(Exception("Import failed: ${response.status} - $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
