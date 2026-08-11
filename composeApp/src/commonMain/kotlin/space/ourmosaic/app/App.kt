package space.ourmosaic.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.set
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.httpUrlFetcher
import io.kamel.core.config.takeFrom
import io.kamel.image.config.Default
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.imageBitmapDecoder
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.rememberI18nState
import space.ourmosaic.app.navigation.AppRouter
import space.ourmosaic.app.navigation.BackHandler
import space.ourmosaic.app.navigation.Route
import space.ourmosaic.app.navigation.rememberNavState
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.system.*
import space.ourmosaic.app.components.SystemSwitcher

enum class AppTheme {
    System, Light, Dark
}

@Composable
fun App(initialTargetRoute: String? = null, initialDeepLink: String? = null) {
    var initializationError by remember { mutableStateOf<String?>(null) }

    // On tente d'initialiser les services. Si l'un d'eux crash (ex: Keychain), on l'attrape.
    val authService = remember {
        try {
            AuthService()
        } catch (e: Exception) {
            initializationError = "AuthService Error: ${e.message}\n${e.stackTraceToString()}"
            null
        }
    }

    val offlineManager = remember {
        try {
            if (authService != null) OfflineManager() else null
        } catch (e: Exception) {
            initializationError = "OfflineManager Error: ${e.message}\n${e.stackTraceToString()}"
            null
        }
    }

    if (initializationError != null) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Mosaic Crash Report", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(initializationError!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        return
    }

    // Si on arrive ici, les services critiques sont chargés
    if (authService == null || offlineManager == null) return

    val systemService = remember { SystemService(authService, offlineManager) }
    val chatService = remember { ChatService(authService) }
    val syncWorker = remember { SyncWorker(systemService, offlineManager, authService) }
    val chatSyncWorker = remember { ChatSyncWorker(chatService, offlineManager) }
    val sseService = remember { SseService(authService) }

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

    val settings = remember { com.russhwolf.settings.Settings() }
    var theme by remember {
        mutableStateOf(
            try {
                AppTheme.valueOf(settings.getString("app_theme", AppTheme.System.name))
            } catch (e: Exception) {
                AppTheme.System
            }
        )
    }

    val darkTheme = when (theme) {
        AppTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    ) {
        CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
            val i18nState = rememberI18nState()
            val appSettings = remember { AppSettings() }
            val systemContextManager = remember { SystemContextManager(settings, authService) }
            val navState = rememberNavState(startRoute)
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val isLoggedOut = navState.currentRoute == Route.Login

            val appController = remember {
                AppController(authService, offlineManager, systemService, syncWorker, chatSyncWorker, sseService, chatService)
            }

            val userMe by offlineManager.cachedUserMe.collectAsState(authService.userMe.value)
            val systems by offlineManager.cachedSystems.collectAsState(emptyList())

            val currentSystemId by systemContextManager.currentSystemId.collectAsState()

            LaunchedEffect(userMe) {
                if (systemContextManager.currentSystemId.value == null) {
                    val rootId = userMe?.systems?.find { it.parentSystemId == null }?.id ?: userMe?.system?.id
                    if (rootId != null) {
                        systemContextManager.setSystem(rootId)
                    }
                }
            }

            LaunchedEffect(navState.currentRoute) {
                when (val r = navState.currentRoute) {
                    is Route.System -> r.systemId?.let { systemContextManager.setSystem(it) }
                    is Route.MembersManage -> r.systemId?.let { systemContextManager.setSystem(it) }
                    is Route.MemberEdit -> r.systemId?.let { systemContextManager.setSystem(it) }
                    is Route.Chat -> r.systemId?.let { systemContextManager.setSystem(it) }
                    is Route.ChatChannel -> r.systemId?.let { systemContextManager.setSystem(it) }
                    else -> {}
                }
            }

            LaunchedEffect(Unit) {
                appController.start(
                    this,
                    i18nState,
                    appSettings,
                    systemContextManager.currentSystemId
                )
            }

            LaunchedEffect(initialTargetRoute) {
                if (initialTargetRoute == "members_manage" && authService.getAccessToken() != null) {
                    navState.navigateTo(Route.MembersManage())
                }
            }

            LaunchedEffect(initialDeepLink) {
                initialDeepLink?.let { url ->
                    if (url.startsWith("mosaic://")) {
                        val path = url.substringAfter("mosaic://").substringAfter("/", "")
                        val segments = path.split("/").filter { it.isNotEmpty() }
                        
                        if (segments.isEmpty()) return@let

                        when (segments[0]) {
                            "friend" -> {
                                segments.getOrNull(1)?.let { friendId ->
                                    if (authService.getAccessToken() != null) {
                                        navState.navigateTo(Route.FriendSystem(friendId))
                                    }
                                }
                            }
                            "email-confirmed" -> {
                                navState.navigateTo(Route.Login)
                            }
                        }
                    }
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
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            SystemSwitcher(
                                userMe = userMe,
                                systems = systems,
                                currentSystemId = currentSystemId,
                                systemContextManager = systemContextManager,
                                navState = navState,
                                i18n = i18nState,
                                authService = authService,
                                onCloseDrawer = { scope.launch { drawerState.close() } }
                            )

                            val isSystem = userMe?.isSystem == true

                            Route.all.filter { route ->
                                if (!isSystem) {
                                    route !is Route.System && route !is Route.MembersManage
                                } else true
                            }.forEach { route ->
                                NavigationDrawerItem(
                                    label = { Text(i18nState.text(route.titleKey)) },
                                    selected = when (route) {
                                        is Route.System -> navState.currentRoute is Route.System
                                        is Route.Chat -> navState.currentRoute is Route.Chat || navState.currentRoute is Route.ChatChannel
                                        is Route.MembersManage -> navState.currentRoute is Route.MembersManage || navState.currentRoute is Route.MemberEdit
                                        else -> navState.currentRoute == route
                                    },
                                    onClick = {
                                        val targetRoute = when (route) {
                                            is Route.System -> Route.System(currentSystemId)
                                            is Route.Chat -> Route.Chat(currentSystemId)
                                            is Route.MembersManage -> Route.MembersManage(currentSystemId)
                                            else -> route
                                        }
                                        navState.navigateTo(targetRoute)
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(route.icon, contentDescription = null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                }
            ) {
                Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                    AppRouter(
                        navState = navState,
                        i18n = i18nState,
                        appSettings = appSettings,
                        currentSystemId = currentSystemId,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onLogout = { navState.navigateTo(Route.Login) },
                        systemService = systemService,
                        chatService = chatService,
                        sseService = sseService,
                        offlineManager = offlineManager,
                        authService = authService,
                        syncWorker = syncWorker,
                        systemContextManager = systemContextManager,
                        theme = theme,
                        onThemeChange = {
                            theme = it
                            settings.set("app_theme", it.name)
                        }
                    )
                }
            }
        }
    }
}
