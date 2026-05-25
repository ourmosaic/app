package space.ourmosaic.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import space.ourmosaic.app.MainActivity
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.utils.Logger

class SseForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sseService: SseService? = null
    private var systemService: SystemService? = null

    override fun onCreate() {
        super.onCreate()
        val authService = AuthService()
        val offlineManager = OfflineManager()
        sseService = SseService(authService)
        systemService = SystemService(authService, offlineManager)
        
        // Listen to events and show notifications/refresh data
        serviceScope.launch {
            sseService?.events?.collect { event ->
                Logger.d("SseForegroundService", "Received event in background: ${event.topic}")
                when (event.topic) {
                    SseTopics.FRONT_SESSIONS, SseTopics.FEDERATION_FRONT_SESSIONS, SseTopics.FRONT_CHANGES -> {
                        systemService?.getActiveFrontSessions()
                        systemService?.getFriends()
                    }
                    SseTopics.FRIEND_FRONT_SESSIONS -> {
                        try {
                            val jsonPayload = event.payload as? JsonObject
                            if (jsonPayload != null && jsonPayload.containsKey("event")) {
                                val payload = json.decodeFromJsonElement(FriendFrontEventPayload.serializer(), event.payload)
                                val friendName = payload.friend.customName ?: payload.friend.username ?: "A friend"
                                val memberNames = payload.activeMembers.joinToString(", ") { it.name }

                                if (memberNames.isNotEmpty()) {
                                    showSimpleNotification(
                                        friendName,
                                        "Front active: $memberNames",
                                        payload.friend.systemId.hashCode()
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e("SseForegroundService", "Error decoding background friend event", e)
                        }
                    }
                }
            }
        }
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // We no longer call startForeground to avoid the persistent notification.
        // This service will run as a background service.
        sseService?.startStreaming(serviceScope)
        return START_STICKY
    }

    // Notification removed to avoid annoyance. 
    // Service will run in background without being a "Foreground Service".
    /*
    private fun createNotification(): Notification {
        ...
    }
    */

    override fun onDestroy() {
        super.onDestroy()
        sseService?.stopStreaming()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
