package space.ourmosaic.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.*
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.screens.BlockedEntitiesScreen
import space.ourmosaic.app.screens.CacheDetailScreen
import space.ourmosaic.app.screens.DrawScreen
import space.ourmosaic.app.screens.FriendSystemScreen
import space.ourmosaic.app.screens.FriendMemberDetailScreen
import space.ourmosaic.app.screens.FriendsScreen
import space.ourmosaic.app.screens.HomeScreen
import space.ourmosaic.app.screens.ChatScreen
import space.ourmosaic.app.screens.LoginScreen
import space.ourmosaic.app.screens.ProfileScreen
import space.ourmosaic.app.screens.SettingsScreen
import space.ourmosaic.app.screens.SystemScreen
import space.ourmosaic.app.screens.MembersManageScreen
import space.ourmosaic.app.screens.MemberEditScreen
import space.ourmosaic.app.screens.SetupSystemScreen
import space.ourmosaic.app.system.AppSettings
import space.ourmosaic.app.system.SseService
import space.ourmosaic.app.system.SystemService
import space.ourmosaic.app.system.SyncWorker
import space.ourmosaic.app.utils.ConnectivityObserver
import space.ourmosaic.app.utils.Logger
import space.ourmosaic.app.utils.rememberConnectivityObserver

@Composable
fun AppRouter(
    navState: NavState,
    i18n: I18nState,
    appSettings: AppSettings,
    currentSystemId: String?,
    onOpenDrawer: () -> Unit,
    onLogout: () -> Unit,
    systemService: SystemService,
    chatService: space.ourmosaic.app.system.ChatService,
    sseService: SseService,
    offlineManager: OfflineManager,
    authService: AuthService,
    syncWorker: SyncWorker,
    systemContextManager: space.ourmosaic.app.system.SystemContextManager,
    theme: space.ourmosaic.app.AppTheme,
    onThemeChange: (space.ourmosaic.app.AppTheme) -> Unit
) {
    val connectivityObserver = rememberConnectivityObserver()

    LaunchedEffect(connectivityObserver, currentSystemId) {
        connectivityObserver.observe().collect { status ->
            Logger.d("AppRouter", "[SYNC_DEBUG] Connectivity status: $status for system: $currentSystemId")
            if (status == ConnectivityObserver.Status.Available) {
                Logger.d("AppRouter", "[SYNC_DEBUG] Triggering sync and refresh")
                syncWorker.sync(currentSystemId)
                // Refresh data to ensure server truth after sync
                systemService.getMembers(currentSystemId)
                systemService.getActiveFrontSessions(forceRefresh = true, systemId = currentSystemId)
            }
        }
    }

    LaunchedEffect(currentSystemId) {
        // Initial sync and refresh on app start or system switch if logged in
        if (authService.getAccessToken() != null) {
            Logger.d("AppRouter", "[SYNC_DEBUG] System switch or app start, triggering sync and refresh for $currentSystemId")
            syncWorker.sync(currentSystemId)
            systemService.getMembers(currentSystemId)
            systemService.getActiveFrontSessions(forceRefresh = true, systemId = currentSystemId)
        }
    }

    when (navState.currentRoute) {
        Route.Login -> LoginScreen(
            i18n = i18n,
            authService = authService,
            appSettings = appSettings,
            onLoginSuccess = { navState.navigateTo(Route.Home) }
        )

        Route.Home -> HomeScreen(
            i18n = i18n,
            currentSystemId = currentSystemId,
            onOpenDrawer = onOpenDrawer,
            onNavigate = { route -> navState.navigateTo(route) },
            systemService = systemService,
            offlineManager = offlineManager,
            authService = authService
        )

        Route.Profile -> ProfileScreen(
            i18n = i18n,
            currentSystemId = currentSystemId,
            onOpenDrawer = onOpenDrawer,
            onBack = navState::back,
            onNavigate = { route -> navState.navigateTo(route) },
            systemService = systemService,
            offlineManager = offlineManager,
            authService = authService
        )

        is Route.System -> SystemScreen(
            systemId = (navState.currentRoute as Route.System).systemId,
            i18n = i18n,
            onOpenDrawer = onOpenDrawer,
            onBack = navState::back,
            onManageMembers = { id -> navState.navigateTo(Route.MembersManage(id)) },
            onNavigateToSubSystem = { id -> navState.navigateTo(Route.System(id)) },
            onNavigateToChat = { id -> navState.navigateTo(Route.Chat(id)) },
            offlineManager = offlineManager,
            authService = authService,
            systemService = systemService
        )

        is Route.MembersManage -> {
            val route = navState.currentRoute as Route.MembersManage
            MembersManageScreen(
                systemId = route.systemId,
                i18n = i18n,
                appSettings = appSettings,
                onBack = navState::back,
                onEditMember = { id -> navState.navigateTo(Route.MemberEdit(id, route.systemId)) },
                offlineManager = offlineManager,
                systemService = systemService,
                authService = authService
            )
        }

        is Route.MemberEdit -> {
            val route = navState.currentRoute as Route.MemberEdit
            MemberEditScreen(
                memberId = route.memberId,
                systemId = route.systemId,
                i18n = i18n,
                onBack = navState::back,
                systemService = systemService,
                offlineManager = offlineManager,
                authService = authService
            )
        }

        Route.Settings -> SettingsScreen(
            i18n = i18n,
            appSettings = appSettings,
            currentSystemId = currentSystemId,
            onOpenDrawer = onOpenDrawer,
            onLogout = onLogout,
            offlineManager = offlineManager,
            systemService = systemService,
            authService = authService,
            onNavigate = { route -> navState.navigateTo(route) },
            theme = theme,
            onThemeChange = onThemeChange
        )

        Route.BlockedEntities -> BlockedEntitiesScreen(
            i18n = i18n,
            currentSystemId = currentSystemId,
            onBack = navState::back,
            systemService = systemService
        )

        Route.CacheDetail -> CacheDetailScreen(
            i18n = i18n,
            onBack = navState::back,
            offlineManager = offlineManager,
            authService = authService,
            currentSystemId = currentSystemId
        )

        Route.Draw -> DrawScreen(
            i18n = i18n,
            appSettings = appSettings,
            onBack = navState::back
        )

        is Route.Chat -> {
            val route = navState.currentRoute as Route.Chat
            ChatScreen(
                channelId = null,
                systemId = route.systemId,
                i18n = i18n,
                onOpenDrawer = onOpenDrawer,
                onNavigate = { navState.navigateTo(it) },
                chatService = chatService,
                systemService = systemService,
                systemContextManager = systemContextManager,
                navState = navState,
                offlineManager = offlineManager,
                authService = authService
            )
        }

        is Route.ChatChannel -> {
            val route = navState.currentRoute as Route.ChatChannel
            ChatScreen(
                channelId = route.channelId,
                systemId = route.systemId,
                i18n = i18n,
                onOpenDrawer = onOpenDrawer,
                onNavigate = { navState.navigateTo(it) },
                chatService = chatService,
                systemService = systemService,
                systemContextManager = systemContextManager,
                navState = navState,
                offlineManager = offlineManager,
                authService = authService
            )
        }

        Route.SetupSystem -> SetupSystemScreen(
            i18n = i18n,
            currentSystemId = currentSystemId,
            onSetupComplete = { systemId -> 
                if (systemId != null) {
                    navState.replaceLast(Route.System(systemId))
                } else {
                    navState.navigateTo(Route.Home) 
                }
            },
            onSkip = { navState.back() },
            systemService = systemService,
            sseService = sseService,
            offlineManager = offlineManager,
            authService = authService
        )

        Route.Friends -> FriendsScreen(
            i18n = i18n,
            currentSystemId = currentSystemId,
            onBack = navState::back,
            systemService = systemService,
            offlineManager = offlineManager,
            authService = authService,
            onFriendClick = { id -> navState.navigateTo(Route.FriendSystem(id)) }
        )

        is Route.FriendSystem -> {
            val route = navState.currentRoute as Route.FriendSystem
            FriendSystemScreen(
                friendId = route.friendId,
                initialPage = route.initialPage,
                i18n = i18n,
                currentSystemId = currentSystemId,
                onBack = navState::back,
                onNavigate = { navState.navigateTo(it) },
                onTabChange = { page ->
                    navState.replaceLast(Route.FriendSystem(route.friendId, page))
                },
                systemService = systemService,
                authService = authService
            )
        }

        is Route.MemberDetail -> {
            val memberId = (navState.currentRoute as Route.MemberDetail).memberId
            var member by remember { mutableStateOf<space.ourmosaic.app.system.MemberResponse?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            
            LaunchedEffect(memberId, currentSystemId) {
                isLoading = true
                // Try cache first
                offlineManager.getCachedMembers()?.find { it.id == memberId }?.let {
                    member = it
                    isLoading = false
                } ?: run {
                    systemService.getMembers(currentSystemId).onSuccess { members ->
                        member = members.find { it.id == memberId }
                        isLoading = false
                    }.onFailure {
                        isLoading = false
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                member?.let {
                    FriendMemberDetailScreen(
                        member = it,
                        i18n = i18n,
                        currentSystemId = currentSystemId,
                        onBack = navState::back,
                        onEdit = { id -> navState.navigateTo(Route.MemberEdit(id)) },
                        authService = authService,
                        systemService = systemService
                    )
                }
            }
        }

        is Route.FriendMemberDetail -> {
            val route = navState.currentRoute as Route.FriendMemberDetail
            var member by remember { mutableStateOf<space.ourmosaic.app.system.MemberResponse?>(null) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(route.friendId, route.memberId, currentSystemId) {
                isLoading = true
                systemService.getFriendMembers(route.friendId, currentSystemId).onSuccess { members ->
                    member = members.find { it.id == route.memberId }
                    isLoading = false
                }.onFailure {
                    isLoading = false
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                member?.let {
                    FriendMemberDetailScreen(
                        member = it,
                        i18n = i18n,
                        currentSystemId = currentSystemId,
                        onBack = navState::back,
                        onEdit = { id -> navState.navigateTo(Route.MemberEdit(id)) },
                        authService = authService,
                        systemService = systemService
                    )
                }
            }
        }
    }
}
