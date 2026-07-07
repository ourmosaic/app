package space.ourmosaic.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.auth.UserMeResponse
import space.ourmosaic.app.components.MosaicAvatar
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.navigation.Route
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.system.*
import kotlinx.serialization.json.decodeFromJsonElement

data class DashboardItem(
    val titleKey: MessageKey,
    val icon: ImageVector,
    val route: Route
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    i18n: I18nState,
    onOpenDrawer: () -> Unit,
    onNavigate: (Route) -> Unit,
    systemService: SystemService,
    offlineManager: OfflineManager,
    authService: AuthService
) {
    val userMe by offlineManager.cachedUserMe.collectAsState(initial = null)
    val isSystem = userMe?.isSystem == true
    val pendingCount by offlineManager.pendingActionsCount.collectAsState()
    val isImporting by offlineManager.isImporting.collectAsState()
    val syncErrors by offlineManager.syncErrors.collectAsState()

    val dashboardItems = remember(userMe, isSystem) {
        val items = mutableListOf<DashboardItem>()
        
        if (isSystem) {
            items.add(DashboardItem(Route.MembersManage.titleKey, Route.MembersManage.icon, Route.MembersManage))
            items.add(DashboardItem(Route.Chat.titleKey, Route.Chat.icon, Route.Chat))
            items.add(DashboardItem(Route.Profile.titleKey, Route.Profile.icon, Route.Profile))
            items.add(DashboardItem(Route.Friends.titleKey, Route.Friends.icon, Route.Friends))
            items.add(DashboardItem(Route.System.titleKey, Route.System.icon, Route.System))
        } else {
            items.add(DashboardItem(Route.SetupSystem.titleKey, Route.SetupSystem.icon, Route.SetupSystem))
            items.add(DashboardItem(Route.Chat.titleKey, Route.Chat.icon, Route.Chat))
            items.add(DashboardItem(Route.Profile.titleKey, Route.Profile.icon, Route.Profile))
            items.add(DashboardItem(Route.Friends.titleKey, Route.Friends.icon, Route.Friends))
        }
        
        items.add(DashboardItem(Route.Settings.titleKey, Route.Settings.icon, Route.Settings))
        items
    }

    LaunchedEffect(Unit) {
        if (userMe == null) {
            authService.getUserMe()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    if (pendingCount > 0) {
                        IconButton(onClick = { offlineManager.triggerSync() }) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(Icons.Default.Sync, contentDescription = "Retry Sync")
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                                ) {
                                    Text("$pendingCount")
                                }
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (userMe == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                // Header with System Avatar and Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    MosaicAvatar(
                        avatarUrl = userMe?.system?.avatarUrl,
                        authService = authService,
                        size = 48.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = userMe?.system?.customName ?: userMe?.username ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val activeSessions by offlineManager.cachedFrontSessions.collectAsState(initial = emptyList())
                        val activeMembers = remember(activeSessions) {
                            (activeSessions ?: emptyList()).filter { it.endTime == null }
                                .mapNotNull { it.member?.name }
                                .distinct()
                        }
                            
                        if (activeMembers.isNotEmpty()) {
                            Text(
                                text = "${i18n.text(MessageKey.FrontActive)}: ${activeMembers.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isImporting) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = i18n.text(MessageKey.SetupImportingMessage),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    if (syncErrors.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sync Errors Detected",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    syncErrors.forEach { error ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${error.actionType}: ${error.message}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            IconButton(onClick = { offlineManager.dismissSyncError(error.id) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(dashboardItems) { item ->
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigate(item.route) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = i18n.text(item.titleKey),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
