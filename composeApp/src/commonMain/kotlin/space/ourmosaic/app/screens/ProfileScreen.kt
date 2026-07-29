package space.ourmosaic.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.auth.UserMeResponse
import space.ourmosaic.app.system.MemberResponse
import space.ourmosaic.app.system.SystemService
import space.ourmosaic.app.system.FrontSession
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.navigation.Route
import space.ourmosaic.app.components.MosaicAvatar
import space.ourmosaic.app.offline.OfflineManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    i18n: I18nState,
    currentSystemId: String?,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    systemService: SystemService,
    offlineManager: OfflineManager,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()
    val user by offlineManager.cachedUserMe.collectAsState(null)
    val systems by offlineManager.cachedSystems.collectAsState(initial = emptyList())

    val currentSystem = remember(currentSystemId, user, systems) {
        if (currentSystemId == null || currentSystemId == "@me") user?.system
        else systems.find { it.id == currentSystemId } ?: user?.system
    }

    val cachedSessions by offlineManager.cachedFrontSessions.collectAsState(initial = emptyList())
    val activeSessions = remember(cachedSessions) { cachedSessions?.filter { it.endTime == null } ?: emptyList() }
    
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            val userResult = authService.getUserMe()
            if (userResult.isSuccess) {
                userResult.getOrNull()?.let { offlineManager.cacheUserMe(it) }
                systemService.getActiveFrontSessions(forceRefresh = true, systemId = currentSystemId)
                isLoading = false
            } else {
                error = userResult.exceptionOrNull()?.message
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentSystemId) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n.text(MessageKey.ProfileTitle)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Error: $error", color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                user?.let { userInfo ->
                    item {
                        ProfileHeader(userInfo, currentSystem, authService)
                    }

                    currentSystem?.let { system ->
                        item {
                            SystemSection(system, i18n)
                        }
                    }

                    item {
                        Text(
                            text = i18n.text(MessageKey.ProfileMembersTitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (activeSessions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Box(
                                    Modifier.padding(24.dp).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Personne n'est au front actuellement",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(activeSessions.size) { index ->
                            val session = activeSessions[index]
                            val cachedMembers by offlineManager.cachedMembers.collectAsState(initial = emptyList())
                            val member = cachedMembers?.find { it.id == session.memberId } ?: session.member
                            
                            member?.let {
                                MemberItem(it, authService) {
                                    onNavigate(Route.MemberDetail(it.id))
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(user: UserMeResponse, currentSystem: space.ourmosaic.app.system.SystemResponse?, authService: AuthService) {
    val displayName = currentSystem?.customName ?: user.username
    val avatarUrl = currentSystem?.avatarUrl ?: user.system?.avatarUrl

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        MosaicAvatar(
            avatarUrl = avatarUrl,
            size = 80.dp,
            cornerRadius = 40.dp, // Cercle pour le profil principal
            authService = authService
        )

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (user.isSystem) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "System",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun SystemSection(system: space.ourmosaic.app.system.SystemResponse, i18n: I18nState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = system.customName ?: i18n.text(MessageKey.ProfileSystemTitle),
                style = MaterialTheme.typography.titleLarge
            )
            system.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            
            if (system.domain != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Domain: ${system.domain}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MemberItem(member: MemberResponse, authService: AuthService, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MosaicAvatar(
                avatarUrl = member.avatarUrl,
                size = 48.dp,
                cornerRadius = 24.dp, // Cercle pour les membres ici
                authService = authService
            )

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                member.pronouns?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                member.role?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            if (member.inDormancy) {
                Badge(containerColor = MaterialTheme.colorScheme.outline) {
                    Text("Dormancy")
                }
            }
        }
    }
}

