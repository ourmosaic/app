package space.ourmosaic.app.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    val chatSyncWorker: ChatSyncWorker,
    val sseService: SseService,
    val chatService: ChatService
) {
    private var controllerScope: CoroutineScope? = null
    private var syncJob: Job? = null
    private var sseJob: Job? = null
    private var notificationJob: Job? = null

    companion object {
        private val _sseEvents = kotlinx.coroutines.flow.MutableSharedFlow<SseEvent>()
        val sseEvents = _sseEvents.asSharedFlow()
    }

    fun start(scope: CoroutineScope, i18nState: I18nState, appSettings: AppSettings, currentSystemIdFlow: Flow<String?>) {
        controllerScope = scope

        scope.launch {
            combine(authService.userMe, currentSystemIdFlow) { user, systemId ->
                user to systemId
            }.collect { (user, _) ->
                if (user != null) {
                    offlineManager.cacheUserMe(user)
                    syncWorker.start(this)
                    chatSyncWorker.startSync(this)
                    authService.getUserMe()
                    systemService.getSystems()
                } else {
                    syncWorker.stop()
                }
            }
        }

        scope.launch {
            combine(authService.userMe, currentSystemIdFlow) { user, systemId ->
                user to systemId
            }.collect { (user, systemId) ->
                if (user != null) {
                    sseService.startStreaming(this, systemId)
                } else {
                    sseService.stopStreaming()
                }
            }
        }

        scope.launch {
            sseService.events.collect { event ->
                _sseEvents.emit(event)
                handleSseEvent(event, i18nState)
            }
        }

        scope.launch {
            combine(offlineManager.cachedFrontSessions, offlineManager.cachedMembers) { sessions, members ->
                val active = sessions?.filter { it.endTime == null } ?: emptyList()
                val m = members ?: emptyList()
                
                // On détecte s'il nous manque des noms de membres pour les sessions actives
                val hasMissingNames = active.any { session ->
                    session.member?.name == null && m.none { it.id == session.memberId }
                }
                val needsMembers = active.isNotEmpty() && hasMissingNames

                val fronterNames = if (appSettings.showFrontNotification && authService.getAccessToken() != null) {
                    active.map { session ->
                        session.member?.name 
                            ?: m.find { it.id == session.memberId }?.name 
                            ?: session.memberId 
                    }
                } else {
                    emptyList()
                }

                Pair(fronterNames, needsMembers)
            }
            .distinctUntilChanged()
            .collect { (fronterNames, needsMembers) ->
                if (needsMembers && authService.getAccessToken() != null) {
                    scope.launch {
                        systemService.getMembers()
                        systemService.getSystems()
                    }
                }
                updateFrontNotification(fronterNames)
            }
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
                systemService.getSystems()
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
