package space.ourmosaic.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.auth.AuthService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheDetailScreen(
    i18n: I18nState,
    onBack: () -> Unit,
    offlineManager: OfflineManager,
    authService: AuthService,
    currentSystemId: String? = null
) {
    var selectedJson by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSensitiveData by remember { mutableStateOf(false) }

    val cachedSystems = remember { offlineManager.getCachedSystems() ?: emptyList() }
    var totalEstimatedSize by remember { mutableStateOf(offlineManager.getTotalCacheSize()) }

    var showClearConfirmation by remember { mutableStateOf<String?>(null) } // null, "all", "global", or systemId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(i18n.text(MessageKey.SettingsViewCache))
                        Text(
                            "Total Est. Size: ${formatSize(totalEstimatedSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = i18n.text(MessageKey.CommonBack))
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirmation = "all" }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All Cache",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- GLOBAL CACHE ---
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "Global Cache",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showClearConfirmation = "global" }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Global Cache",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            val globalItems = listOf(
                "User Me" to "user_me",
                "Systems List" to "systems",
                "Friends" to "friends",
                "Sent Requests" to "sent_requests",
                "Received Requests" to "received_requests",
                "Blocked Users" to "blocked_users",
                "Blocked Members" to "blocked_members",
                "Blocked Systems" to "blocked_systems",
                "Pending Actions" to "actions",
                "ID Mappings" to "mappings"
            )

            items(globalItems) { (label, key) ->
                CacheCard(label, key, null, offlineManager) { result ->
                    selectedJson = result
                }
            }

            // --- PER SYSTEM CACHE ---
            cachedSystems.forEach { system ->
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "System: ${system.customName ?: system.username ?: system.id}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showClearConfirmation = system.id }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear System Cache",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                val systemItems = listOf(
                    "Members" to "members",
                    "Groups" to "groups",
                    "Custom Fields" to "fields",
                    "Front Sessions" to "sessions",
                    "Chat Channels" to "channels",
                    "Chat Messages" to "messages_all"
                )

                items(systemItems) { (label, key) ->
                    CacheCard(label, key, system.id, offlineManager) { result ->
                        selectedJson = result
                    }
                }
            }

            // --- SENSITIVE DATA ---
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "Sensitive Data",
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showSensitiveData,
                        onCheckedChange = { showSensitiveData = it }
                    )
                }
                if (showSensitiveData) {
                    Text(
                        "WARNING: This data is highly sensitive. Do not share screenshots of this section.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            if (showSensitiveData) {
                val sensitiveItems = listOf(
                    "Access Token" to "access_token",
                    "Refresh Token" to "refresh_token",
                    "Federation" to "federation"
                )
                items(sensitiveItems) { (label, key) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val value = when(key) {
                                "access_token" -> authService.getAccessToken()
                                "refresh_token" -> authService.getRefreshToken()
                                "federation" -> authService.getFederation()
                                else -> "Unknown"
                            }
                            selectedJson = label to (value ?: "null")
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("Click to view", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation != null) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = null },
            title = { Text("Confirm Cache Purge") },
            text = {
                val target = when(showClearConfirmation) {
                    "all" -> "ALL cached data"
                    "global" -> "Global cache (user, systems, actions)"
                    else -> "Cache for system ${cachedSystems.find { it.id == showClearConfirmation }?.customName ?: showClearConfirmation}"
                }
                Text("Are you sure you want to clear $target? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when(showClearConfirmation) {
                            "all" -> offlineManager.clearEverything()
                            "global" -> offlineManager.clearGlobalData()
                            else -> showClearConfirmation?.let { offlineManager.clearSystemData(it) }
                        }
                        totalEstimatedSize = offlineManager.getTotalCacheSize()
                        showClearConfirmation = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = null }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (selectedJson != null) {
        Dialog(onDismissRequest = { selectedJson = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selectedJson?.first ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(
                            text = selectedJson?.second ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    TextButton(
                        onClick = { selectedJson = null },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.End).padding(top = 8.dp)
                    ) {
                        Text(i18n.text(MessageKey.CommonBack))
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheCard(
    label: String,
    key: String,
    systemId: String?,
    offlineManager: OfflineManager,
    onClick: (Pair<String, String>) -> Unit
) {
    val count = remember(key, systemId) {
        when (key) {
            "members" -> offlineManager.getCachedMembers(systemId)?.size ?: 0
            "groups" -> offlineManager.getCachedGroups(systemId)?.size ?: 0
            "fields" -> offlineManager.getCachedCustomFields(systemId)?.size ?: 0
            "sessions" -> offlineManager.getCachedFrontSessions(systemId)?.size ?: 0
            "channels" -> offlineManager.getCachedChatChannels(systemId)?.size ?: 0
            "messages_all" -> offlineManager.getTotalMessagesCount(systemId)
            "friends" -> offlineManager.getCachedFriends()?.size ?: 0
            "sent_requests" -> offlineManager.getCachedSentRequests()?.size ?: 0
            "received_requests" -> offlineManager.getCachedReceivedRequests()?.size ?: 0
            "blocked_users" -> offlineManager.getCachedBlockedUsers()?.size ?: 0
            "blocked_members" -> offlineManager.getCachedBlockedMembers()?.size ?: 0
            "blocked_systems" -> offlineManager.getCachedBlockedSystems()?.size ?: 0
            "actions" -> offlineManager.getPendingActions().size
            "mappings" -> offlineManager.getIdMappings().size
            "user_me" -> if (offlineManager.getRawJson("user_me", null) != null) 1 else 0
            "systems" -> if (offlineManager.getRawJson("systems", null) != null) 1 else 0
            else -> 0
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val json = if (key == "messages_all") {
                val channels = offlineManager.getCachedChatChannels(systemId) ?: emptyList()
                val allMessages = channels.associate { channel ->
                    channel.name to (offlineManager.getCachedChatMessages(channel.id, systemId) ?: emptyList())
                }
                allMessages.toString()
            } else {
                offlineManager.getRawJson(key, systemId) ?: "{}"
            }
            onClick(label to json)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(count.toString(), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "${((mb * 100).toInt() / 100.0)} MB"
        kb >= 1.0 -> "${((kb * 100).toInt() / 100.0)} KB"
        else -> "$bytes B"
    }
}
