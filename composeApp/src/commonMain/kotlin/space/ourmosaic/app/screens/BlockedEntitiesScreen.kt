package space.ourmosaic.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.system.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedEntitiesScreen(
    i18n: I18nState,
    currentSystemId: String?,
    onBack: () -> Unit,
    systemService: SystemService
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        i18n.text(MessageKey.SafetyTabUsers),
        i18n.text(MessageKey.SafetyTabSystems),
        i18n.text(MessageKey.SafetyTabMembers)
    )

    var blockedUsers by remember { mutableStateOf<List<BlockedUserResponse>>(emptyList()) }
    var blockedSystems by remember { mutableStateOf<List<BlockedSystemResponse>>(emptyList()) }
    var blockedMembers by remember { mutableStateOf<List<BlockedMemberResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val loadData = suspend {
        isLoading = true
        val users = systemService.getBlockedUsers(currentSystemId)
        val systems = systemService.getBlockedSystems(currentSystemId)
        val members = systemService.getBlockedMembers(currentSystemId)
        
        blockedUsers = users.getOrDefault(emptyList())
        blockedSystems = systems.getOrDefault(emptyList())
        blockedMembers = members.getOrDefault(emptyList())
        isLoading = false
    }

    LaunchedEffect(currentSystemId) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n.text(MessageKey.SafetyBlockedEntities)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> BlockedList(
                        items = blockedUsers,
                        i18n = i18n,
                        onUnblock = { id ->
                            scope.launch {
                                systemService.unblockEntity(UnblockRequest(BlockType.USER, id), currentSystemId)
                                loadData()
                            }
                        },
                        getName = { it.blocked.customName ?: it.blocked.username ?: "Unknown User" }
                    )
                    1 -> BlockedList(
                        items = blockedSystems,
                        i18n = i18n,
                        onUnblock = { id ->
                            scope.launch {
                                systemService.unblockEntity(UnblockRequest(BlockType.SYSTEM, id), currentSystemId)
                                loadData()
                            }
                        },
                        getName = { it.blocked.customName ?: it.blocked.username ?: "Unknown System" }
                    )
                    2 -> BlockedList(
                        items = blockedMembers,
                        i18n = i18n,
                        onUnblock = { id ->
                            scope.launch {
                                systemService.unblockEntity(UnblockRequest(BlockType.MEMBER, id), currentSystemId)
                                loadData()
                            }
                        },
                        getName = { it.blocked.name }
                    )
                }
            }
        }
    }
}

@Composable
fun <T> BlockedList(
    items: List<T>,
    i18n: I18nState,
    onUnblock: (String) -> Unit,
    getName: (T) -> String
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    i18n.text(MessageKey.SafetyNoBlockedEntities),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                val id = when (item) {
                    is BlockedUserResponse -> item.blockedId
                    is BlockedSystemResponse -> item.blockedId
                    is BlockedMemberResponse -> item.blockedId
                    else -> ""
                }
                
                val reason = when (item) {
                    is BlockedUserResponse -> item.reason
                    is BlockedSystemResponse -> item.reason
                    is BlockedMemberResponse -> item.reason
                    else -> null
                }

                ListItem(
                    headlineContent = { Text(getName(item)) },
                    supportingContent = reason?.let { { Text(it) } },
                    trailingContent = {
                        IconButton(onClick = { onUnblock(id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Unblock", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
