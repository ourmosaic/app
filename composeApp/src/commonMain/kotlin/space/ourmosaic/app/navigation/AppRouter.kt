package space.ourmosaic.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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
import space.ourmosaic.app.screens.FriendSystemScreen
import space.ourmosaic.app.screens.FriendMemberDetailScreen
import space.ourmosaic.app.screens.FriendsScreen
import space.ourmosaic.app.screens.HomeScreen
import space.ourmosaic.app.screens.LoginScreen
import space.ourmosaic.app.screens.ProfileScreen
import space.ourmosaic.app.screens.SettingsScreen
import space.ourmosaic.app.screens.SystemScreen
import space.ourmosaic.app.screens.MembersManageScreen
import space.ourmosaic.app.screens.MemberEditScreen
import space.ourmosaic.app.screens.SetupSystemScreen
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
    onOpenDrawer: () -> Unit,
    onLogout: () -> Unit,
    systemService: SystemService,
    sseService: SseService,
    offlineManager: OfflineManager,
    authService: AuthService,
    syncWorker: SyncWorker,
    theme: space.ourmosaic.app.AppTheme,
    onThemeChange: (space.ourmosaic.app.AppTheme) -> Unit
) {
    val connectivityObserver = rememberConnectivityObserver()

    LaunchedEffect(connectivityObserver) {
        connectivityObserver.observe().collect { status ->
            Logger.d("AppRouter", "[SYNC_DEBUG] Connectivity status: $status")
            if (status == ConnectivityObserver.Status.Available) {
                Logger.d("AppRouter", "[SYNC_DEBUG] Triggering sync and refresh")
                syncWorker.sync()
                // Refresh data to ensure server truth after sync
                systemService.getMembers()
                systemService.getActiveFrontSessions(forceRefresh = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        // Initial sync and refresh on app start if logged in
        if (authService.getAccessToken() != null) {
            Logger.d("AppRouter", "[SYNC_DEBUG] App start, triggering sync and refresh")
            syncWorker.sync()
            systemService.getMembers()
            systemService.getActiveFrontSessions(forceRefresh = true)
        }
    }

    when (navState.currentRoute) {
        Route.Login -> LoginScreen(
            i18n = i18n,
            authService = authService,
            onLoginSuccess = { navState.navigateTo(Route.Home) }
        )

        Route.Home -> HomeScreen(
            i18n = i18n,
            onOpenDrawer = onOpenDrawer,
            onNavigate = { route -> navState.navigateTo(route) },
            systemService = systemService,
            offlineManager = offlineManager,
            authService = authService
        )

        Route.Profile -> ProfileScreen(
            i18n = i18n,
            onOpenDrawer = onOpenDrawer,
            onBack = navState::back,
            onNavigate = { route -> navState.navigateTo(route) },
            systemService = systemService,
            offlineManager = offlineManager,
            authService = authService
        )

        Route.System -> SystemScreen(
            i18n = i18n,
            onOpenDrawer = onOpenDrawer,
            onBack = navState::back,
            onManageMembers = { navState.navigateTo(Route.MembersManage) },
            offlineManager = offlineManager
        )

        Route.MembersManage -> MembersManageScreen(
            i18n = i18n,
            onBack = navState::back,
            onEditMember = { id -> navState.navigateTo(Route.MemberEdit(id)) },
            offlineManager = offlineManager,
            systemService = systemService,
            authService = authService
        )

        is Route.MemberEdit -> MemberEditScreen(
            memberId = (navState.currentRoute as Route.MemberEdit).memberId,
            i18n = i18n,
            onBack = navState::back,
            systemService = systemService,
            offlineManager = offlineManager,
            authService = authService
        )

        Route.Settings -> SettingsScreen(
            i18n = i18n,
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
            onBack = navState::back,
            systemService = systemService
        )

        Route.SetupSystem -> SetupSystemScreen(
            i18n = i18n,
            onSetupComplete = { navState.navigateTo(Route.Home) },
            onSkip = { navState.back() },
            systemService = systemService,
            sseService = sseService,
            offlineManager = offlineManager,
            authService = authService
        )

        Route.Friends -> FriendsScreen(
            i18n = i18n,
            onBack = navState::back,
            systemService = systemService,
            offlineManager = offlineManager,
            authService = authService,
            onFriendClick = { id -> navState.navigateTo(Route.FriendSystem(id)) }
        )

        is Route.FriendSystem -> FriendSystemScreen(
            friendId = (navState.currentRoute as Route.FriendSystem).friendId,
            i18n = i18n,
            onBack = navState::back,
            onNavigate = { route -> navState.navigateTo(route) },
            systemService = systemService,
            authService = authService
        )

        is Route.MemberDetail -> {
            val memberId = (navState.currentRoute as Route.MemberDetail).memberId
            var member by remember { mutableStateOf<space.ourmosaic.app.system.MemberResponse?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            
            LaunchedEffect(memberId) {
                isLoading = true
                // Try cache first
                offlineManager.getCachedMembers()?.find { it.id == memberId }?.let {
                    member = it
                    isLoading = false
                } ?: run {
                    systemService.getMembers().onSuccess { members ->
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

            LaunchedEffect(route.friendId, route.memberId) {
                isLoading = true
                systemService.getFriendMembers(route.friendId).onSuccess { members ->
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
