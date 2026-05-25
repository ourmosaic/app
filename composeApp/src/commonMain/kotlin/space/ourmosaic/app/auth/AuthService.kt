package space.ourmosaic.app.auth

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import space.ourmosaic.app.createSettings
import space.ourmosaic.app.createEncryptedSettings
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import space.ourmosaic.app.utils.Logger

class AuthService {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val settings = createSettings()
    private val secureSettings = createEncryptedSettings()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _userMe = MutableStateFlow<UserMeResponse?>(null)
    val userMe: StateFlow<UserMeResponse?> = _userMe.asStateFlow()

    init {
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

    suspend fun refreshToken(): Result<AuthenticationResponse> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val refreshToken = getRefreshToken() ?: return Result.failure(Exception("No refresh token stored"))
        
        val url = "https://$federation/auth/token/refresh"
        
        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequest(refreshToken))
            }
            if (response.status.isSuccess()) {
                val authResponse = response.body<AuthenticationResponse>()
                saveAuthData(federation, authResponse)
                getUserMe()
                Result.success(authResponse)
            } else {
                Result.failure(Exception("Refresh failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
            if (response.status == HttpStatusCode.Unauthorized && allowRetry) {
                val refreshResult = refreshToken()
                if (refreshResult.isSuccess) {
                    return getUserMe(allowRetry = false)
                }
            }
            if (response.status.isSuccess()) {
                val userMeResponse = response.body<UserMeResponse>()
                updateUserMe(userMeResponse)
                Result.success(userMeResponse)
            } else {
                Logger.e("AuthService", "getUserMe failed with status ${response.status}")
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
        val systemId = user.system?.id
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
        settings["federation"] = federation
        secureSettings["access_token"] = response.accessToken
        secureSettings["refresh_token"] = response.refreshToken
    }

    fun getFederation(): String? = settings.getStringOrNull("federation")
    fun getAccessToken(): String? = secureSettings.getStringOrNull("access_token")
    fun getRefreshToken(): String? = secureSettings.getStringOrNull("refresh_token")

    suspend fun createSystem(): Result<Unit> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val token = getAccessToken() ?: return Result.failure(Exception("No access token stored"))

        return try {
            val response = client.post("https://$federation/v1/system/@me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess()) {
                getUserMe() // Refresh cache and flow
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to create system: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromSimplyPlural(apiKey: String): Result<String> {
        val federation = getFederation() ?: return Result.failure(Exception("No federation stored"))
        val token = getAccessToken() ?: return Result.failure(Exception("No access token stored"))

        return try {
            val response = client.post("https://$federation/v1/import/simplyplural/api") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("apiKey" to apiKey))
            }
            if (response.status.isSuccess()) {
                val body = response.body<Map<String, String>>()
                val importId = body["importId"] ?: ""
                Result.success(importId)
            } else {
                Result.failure(Exception("Import failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
