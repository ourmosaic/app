package space.ourmosaic.app.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.utils.Logger

class AppController(
    val authService: AuthService,
    val offlineManager: OfflineManager,
    val systemService: SystemService,
    val syncWorker: SyncWorker,
    val sseService: SseService
) {
    private var controllerScope: CoroutineScope? = null
    private var syncJob: Job? = null
    private var sseJob: Job? = null
    private var notificationJob: Job? = null

    fun start(scope: CoroutineScope, i18nState: I18nState, appSettings: AppSettings) {
        controllerScope = scope

        scope.launch {
            authService.userMe.collect { user ->
                if (user != null) {
                    offlineManager.cacheUserMe(user)
                    syncWorker.start(this)
                    authService.getUserMe()
                } else {
                    syncWorker.stop()
                }
            }
        }

        scope.launch {
            authService.userMe.collect { user ->
                if (user != null) {
                    sseService.startStreaming(this)
                } else {
                    sseService.stopStreaming()
                }
            }
        }

        scope.launch {
            sseService.events.collect { event ->
                handleSseEvent(event, i18nState)
            }
        }

        scope.launch {
            combine(offlineManager.cachedFrontSessions, offlineManager.cachedMembers) { sessions, members ->
                if (appSettings.showFrontNotification && authService.getAccessToken() != null) {
                    val active = sessions?.filter { it.endTime == null } ?: emptyList()
                    val m = members ?: emptyList()
                    val fronters = active.map { session ->
                        session.member?.name 
                            ?: m.find { it.id == session.memberId }?.name 
                            ?: session.memberId 
                    }
                    updateFrontNotification(fronters)
                } else {
                    updateFrontNotification(emptyList())
                }
            }.collect {}
        }
    }

    private suspend fun handleSseEvent(event: SseEvent, i18nState: I18nState) {
        Logger.d("AppController", "SSE Event: ${event.topic}")
        when (event.topic) {
            SseTopics.FRONT_SESSIONS, SseTopics.FEDERATION_FRONT_SESSIONS, SseTopics.FRONT_CHANGES -> {
                systemService.getActiveFrontSessions()
                systemService.getFriends()
            }
            SseTopics.FRIEND_FRONT_SESSIONS -> {
                handleFriendFrontEvent(event, i18nState)
            }
            SseTopics.FRIENDSHIP -> {
                systemService.getFriends()
                systemService.getReceivedFriendRequests()
                systemService.getSentFriendRequests()
            }
            SseTopics.IMPORT -> {
                handleImportEvent(event, i18nState)
            }
            SseTopics.BLOCKS -> {
                systemService.getBlockedUsers()
                systemService.getBlockedMembers()
                systemService.getBlockedSystems()
            }
            SseTopics.REPORTS -> {
                // Reports are usually handled by moderators, but we might want to refresh something if needed
            }
        }
    }

    private suspend fun handleFriendFrontEvent(event: SseEvent, i18nState: I18nState) {
        try {
            val jsonPayload = event.payload as? JsonObject
            if (jsonPayload != null && jsonPayload.containsKey("event")) {
                val payload = systemService.json.decodeFromJsonElement<FriendFrontEventPayload>(event.payload)
                val friendName = payload.friend.customName ?: payload.friend.username ?: "Friend"
                val memberNames = payload.activeMembers.joinToString(", ") { it.name }
                
                if (memberNames.isNotEmpty()) {
                    val body = "${i18nState.text(MessageKey.FrontActive)}: $memberNames"
                    val notificationId = payload.friend.systemId.hashCode()
                    showSimpleNotification(friendName, body, notificationId)
                }
                systemService.getFriends()
            }
        } catch (e: Exception) {
            Logger.e("AppController", "Error decoding friend front event", e)
        }
    }

    private suspend fun handleImportEvent(event: SseEvent, i18nState: I18nState) {
        try {
            val payload = systemService.json.decodeFromJsonElement<ImportEventPayload>(event.payload)
            if (payload.event == ImportEvents.COMPLETED) {
                offlineManager.setImporting(false)
                authService.getUserMe()
                systemService.getMembers()
                showSimpleNotification(
                    i18nState.text(MessageKey.NotificationImportSuccessTitle),
                    i18nState.text(MessageKey.NotificationImportSuccessMessage)
                )
            } else if (payload.event == ImportEvents.FAILED) {
                offlineManager.setImporting(false)
                showSimpleNotification(
                    i18nState.text(MessageKey.NotificationImportFailedTitle),
                    i18nState.text(MessageKey.NotificationImportFailedMessage, payload.error ?: "")
                )
            }
        } catch (e: Exception) {
            Logger.e("AppController", "Error decoding import event", e)
        }
    }
}
