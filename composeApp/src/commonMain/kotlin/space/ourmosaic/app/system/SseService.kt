package space.ourmosaic.app.system

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.utils.Logger

class SseService(private val authService: AuthService) {
    private val tag = "SseService"
    
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(SSE) {

        }
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    space.ourmosaic.app.utils.Logger.d("SseServiceHTTP", message)
                }
            }
            level = LogLevel.ALL
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15000
            socketTimeoutMillis = null
            requestTimeoutMillis = null
        }
    }

    private val _events = MutableSharedFlow<SseEvent>()
    val events: SharedFlow<SseEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var sseJob: Job? = null
    private var currentStreamingUrl: String? = null
    private var currentToken: String? = null

    private var currentSystemId: String? = null

    fun getCurrentSystemId(): String? = currentSystemId

    fun startStreaming(scope: CoroutineScope, systemId: String? = null) {
        val token = authService.getAccessToken() ?: return
        val federation = authService.getFederation() ?: return
        
        val systemScope = if (systemId != null) "system/$systemId/" else ""
        val url = "https://$federation/v1/${systemScope}notifications"

        if (sseJob?.isActive == true && url == currentStreamingUrl && token == currentToken && systemId == currentSystemId) {
            return
        }

        stopStreaming()
        currentStreamingUrl = url
        currentToken = token
        this.currentSystemId = systemId

        sseJob = scope.launch {
            var attempt = 0
            while (isActive) {
                val activeToken = authService.getAccessToken() ?: break
                try {
                    Logger.d(tag, "Connecting to SSE: $url (attempt ${attempt + 1})")
                    try {
                        client.sse(url, {
                            header(HttpHeaders.Authorization, "Bearer $activeToken")
                            header(HttpHeaders.Accept, "text/event-stream")
                            header(HttpHeaders.CacheControl, "no-cache")
                            header(HttpHeaders.Connection, "keep-alive")
                            parameter("heartbeat", "true")
                        }) {
                            attempt = 0 // Reset attempt counter on success
                            _isConnected.value = true
                            Logger.d(tag, "SSE Connection established")
                            incoming.collect { event ->
                                try {
                                    val sseEvent = try {
                                        val dataJson = event.data ?: "{}"
                                        val dataElement = json.parseToJsonElement(dataJson)
                                        
                                        if (dataElement is JsonObject && dataElement.containsKey("topic")) {
                                            json.decodeFromJsonElement<SseEvent>(dataElement)
                                        } else {
                                            SseEvent(event.event ?: "message", dataElement)
                                        }
                                    } catch (e: Exception) {
                                        Logger.e(tag, "Error parsing SSE data: ${e.message}")
                                        SseEvent("error", JsonObject(mapOf("error" to JsonPrimitive(e.message))))
                                    }

                                    val topic = sseEvent.topic
                                    val isHeartbeat = topic.equals(SseTopics.PING, ignoreCase = true) ||
                                                      topic.equals(SseTopics.KEEPALIVE, ignoreCase = true) ||
                                                      topic.equals(SseTopics.READY, ignoreCase = true)

                                    if (!isHeartbeat) {
                                        Logger.d(tag, "Received SSE: $topic -> ${event.data}")
                                        _events.emit(sseEvent)
                                    } else {
                                        Logger.d(tag, "Received SSE Heartbeat: $topic")
                                    }
                                } catch (e: Exception) {
                                    Logger.e(tag, "Error handling SSE event: ${e.message}")
                                }
                            }
                        }
                    } catch (e: NullPointerException) {
                        Logger.e(tag, "SSE NullPointerException (Ktor bug), will reconnect: ${e.message}")
                        _isConnected.value = false
                        attempt++
                        val delayMs = (2000L * attempt).coerceAtMost(30000L)
                        delay(delayMs)
                        continue
                    }
                    _isConnected.value = false
                    Logger.d(tag, "SSE Connection closed normally, retrying in 2s...")
                } catch (e: Exception) {
                    _isConnected.value = false
                    if (!isActive) break
                    
                    val isAuthError = e.message?.contains("401") == true || 
                                     (e as? ClientRequestException)?.response?.status == HttpStatusCode.Unauthorized

                    attempt++
                    val delayMs = (2000L * attempt).coerceAtMost(30000L)
                    Logger.e(tag, "SSE Connection error (attempt $attempt), retrying in ${delayMs}ms: ${e.message}")
                    
                    if (isAuthError) {
                        val refreshed = authService.refreshToken()
                        if (refreshed.isFailure) {
                            Logger.e(tag, "Failed to refresh token for SSE, stopping.")
                            break
                        }
                        currentToken = authService.getAccessToken()
                    }
                    
                    delay(delayMs)
                    continue
                }
                delay(2000)
            }
        }
    }

    fun stopStreaming() {
        sseJob?.cancel()
        sseJob = null
        currentStreamingUrl = null
        currentToken = null
        currentSystemId = null
        _isConnected.value = false
    }
}
