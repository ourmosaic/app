package space.ourmosaic.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.httpUrlFetcher
import io.kamel.core.config.takeFrom
import io.kamel.image.config.Default
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.imageBitmapDecoder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.i18n.rememberI18nState
import space.ourmosaic.app.navigation.AppRouter
import space.ourmosaic.app.navigation.BackHandler
import space.ourmosaic.app.navigation.Route
import space.ourmosaic.app.navigation.rememberNavState
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.system.*
import space.ourmosaic.app.utils.Logger
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject

@Composable
fun App(initialTargetRoute: String? = null) {
    val authService = remember { AuthService() }
    val startRoute = if (authService.getAccessToken() != null) Route.Home else Route.Login
    
    val kamelConfig = remember {
        KamelConfig {
            takeFrom(KamelConfig.Default)
            imageBitmapDecoder()
            httpUrlFetcher {
                httpCache(100 * 1024 * 1024) // 100MB
            }
        }
    }

    MaterialTheme {
        CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
            val i18nState = rememberI18nState()
            val navState = rememberNavState(startRoute)
            val offlineManager = remember { OfflineManager() }
            val systemService = remember { SystemService(authService, offlineManager) }
            val syncWorker = remember { SyncWorker(systemService, offlineManager, authService) }
            val sseService = remember { SseService(authService) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val isLoggedOut = navState.currentRoute == Route.Login
            
            val userMe by offlineManager.cachedUserMe.collectAsState(authService.userMe.value)

            // Sync authService userMe to offlineManager for optimistic UI
            LaunchedEffect(authService.userMe.value) {
                authService.userMe.value?.let { offlineManager.cacheUserMe(it) }
            }

            // Sync loop
            LaunchedEffect(isLoggedOut) {
                if (!isLoggedOut && authService.getAccessToken() != null) {
                    syncWorker.start(this)
                } else {
                    syncWorker.stop()
                }
            }

            // Rafraîchir les infos utilisateur au démarrage ou au login
            LaunchedEffect(isLoggedOut) {
                if (!isLoggedOut && authService.getAccessToken() != null) {
                    authService.getUserMe()
                }
            }

            // Gérer le cycle de vie du streaming SSE
            val hasSystem = userMe?.system != null
            LaunchedEffect(isLoggedOut, hasSystem) {
                if (!isLoggedOut) {
                    val token = authService.getAccessToken()
                    if (token != null) {
                        // Background service is disabled to avoid annoying notification
                        // startSseBackgroundService()
                        sseService.startStreaming(this)
                    }
                } else {
                    stopSseBackgroundService()
                    sseService.stopStreaming()
                }
            }

            LaunchedEffect(sseService.events) {
                sseService.events.collect { event ->
                    Logger.d("App", "SSE Event received: topic=${event.topic}, payload=${event.payload}")
                    when (event.topic) {
                        SseTopics.FRONT_SESSIONS, SseTopics.FEDERATION_FRONT_SESSIONS, SseTopics.FRONT_CHANGES -> {
                            // Refresh fronting status
                            systemService.getActiveFrontSessions()
                            systemService.getFriends() // Friends' fronting might have changed
                        }
                        SseTopics.FRIEND_FRONT_SESSIONS -> {
                            try {
                                val jsonPayload = event.payload as? JsonObject
                                if (jsonPayload != null && jsonPayload.containsKey("event")) {
                                    val payload = systemService.json.decodeFromJsonElement<FriendFrontEventPayload>(event.payload)
                                    val friendName = payload.friend.customName ?: payload.friend.username ?: "Friend"
                                    val memberNames = payload.activeMembers.joinToString(", ") { it.name }
                                    
                                    if (memberNames.isNotEmpty()) {
                                        val body = "${i18nState.text(MessageKey.FrontActive)}: $memberNames"
                                        // Use a stable ID based on friend's system ID hash to update the same notification
                                        val notificationId = payload.friend.systemId.hashCode()
                                        showSimpleNotification(friendName, body, notificationId)
                                    }
                                    
                                    // Also refresh data
                                    systemService.getFriends()
                                }
                            } catch (e: Exception) {
                                Logger.e("App", "Error decoding friend front event", e)
                            }
                        }
                        SseTopics.FRIENDSHIP -> {
                            // Refresh friends list and requests
                            systemService.getFriends()
                            systemService.getReceivedFriendRequests()
                            systemService.getSentFriendRequests()
                        }
                        SseTopics.IMPORT -> {
                            try {
                                val payload = systemService.json.decodeFromJsonElement<ImportEventPayload>(event.payload)
                                Logger.d("App", "Import event: ${payload.event}")
                                
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
                                Logger.e("App", "Error decoding import event", e)
                            }
                        }
                    }
                }
            }

            LaunchedEffect(i18nState.showFrontNotification) {
                val activeSessionsFlow = offlineManager.cachedFrontSessions
                val membersFlow = offlineManager.cachedMembers
                
                combine(activeSessionsFlow, membersFlow) { sessions, members ->
                    if (i18nState.showFrontNotification && authService.getAccessToken() != null) {
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
                }.collect { }
            }

            LaunchedEffect(initialTargetRoute) {
                if (initialTargetRoute == "members_manage" && authService.getAccessToken() != null) {
                    navState.navigateTo(Route.MembersManage)
                }
            }

            BackHandler(enabled = (drawerState.isOpen || navState.canGoBack) && !isLoggedOut) {
                if (drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                } else if (navState.canGoBack) {
                    navState.back()
                }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !isLoggedOut,
                drawerContent = {
                    ModalDrawerSheet {
                        val isSystem = userMe?.isSystem == true
                        Route.all.filter { route ->
                            // Hide system-specific routes if user is not a system
                            if (!isSystem) {
                                route != Route.System && route != Route.MembersManage
                            } else true
                        }.forEach { route ->
                            NavigationDrawerItem(
                                label = { Text(i18nState.text(route.titleKey)) },
                                selected = navState.currentRoute == route,
                                onClick = {
                                    navState.navigateTo(route)
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(route.icon, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                }
            ) {
                Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                    AppRouter(
                        navState = navState,
                        i18n = i18nState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onLogout = { navState.navigateTo(Route.Login) },
                        systemService = systemService,
                        sseService = sseService,
                        offlineManager = offlineManager,
                        authService = authService,
                        syncWorker = syncWorker
                    )
                }
            }
        }
    }
}
