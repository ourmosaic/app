package space.ourmosaic.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheDetailScreen(
    i18n: I18nState,
    onBack: () -> Unit,
    offlineManager: OfflineManager
) {
    var selectedJson by remember { mutableStateOf<Pair<String, String>?>(null) }

    val cacheItems = remember {
        val messageCount = offlineManager.getTotalMessagesCount()
        listOf(
            "Members" to ("members" to (offlineManager.getCachedMembers()?.size ?: 0)),
            "Groups" to ("groups" to (offlineManager.getCachedGroups()?.size ?: 0)),
            "Custom Fields" to ("fields" to (offlineManager.getCachedCustomFields()?.size ?: 0)),
            "Front Sessions" to ("sessions" to (offlineManager.getCachedFrontSessions()?.size ?: 0)),
            "Friends" to ("friends" to (offlineManager.getCachedFriends()?.size ?: 0)),
            "Sent Requests" to ("sent_requests" to (offlineManager.getCachedSentRequests()?.size ?: 0)),
            "Received Requests" to ("received_requests" to (offlineManager.getCachedReceivedRequests()?.size ?: 0)),
            "Blocked Users" to ("blocked_users" to (offlineManager.getCachedBlockedUsers()?.size ?: 0)),
            "Blocked Members" to ("blocked_members" to (offlineManager.getCachedBlockedMembers()?.size ?: 0)),
            "Blocked Systems" to ("blocked_systems" to (offlineManager.getCachedBlockedSystems()?.size ?: 0)),
            "Chat Channels" to ("channels" to (offlineManager.getCachedChatChannels()?.size ?: 0)),
            "Chat Messages" to ("messages_all" to messageCount),
            "Pending Actions" to ("actions" to (offlineManager.getPendingActions().size)),
            "ID Mappings" to ("mappings" to (offlineManager.getIdMappings().size))
        )
    }

    val estimatedSize = remember { offlineManager.getEstimatedCacheSize() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(i18n.text(MessageKey.SettingsViewCache))
                        Text(
                            "Est. Size: ${formatSize(estimatedSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = i18n.text(MessageKey.CommonBack))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cacheItems) { (label, data) ->
                val (key, count) = data
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val json = if (key == "messages_all") {
                            val channels = offlineManager.getCachedChatChannels() ?: emptyList()
                            val allMessages = channels.associate { channel ->
                                channel.name to (offlineManager.getCachedChatMessages(channel.id) ?: emptyList())
                            }
                            // Simplified JSON representation
                            allMessages.toString()
                        } else {
                            offlineManager.getRawJson(key) ?: "{}"
                        }
                        selectedJson = label to json
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
        }
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

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "${((mb * 100).toInt() / 100.0)} MB"
        kb >= 1.0 -> "${((kb * 100).toInt() / 100.0)} KB"
        else -> "$bytes B"
    }
}
