package space.ourmosaic.app.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.ourmosaic.app.offline.ChatMessageQueueType
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.utils.Logger

class ChatSyncWorker(
    private val chatService: ChatService,
    private val offlineManager: OfflineManager
) {
    private val tag = "ChatSyncWorker"

    fun startSync(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                try {
                    val pendingMessages = offlineManager.getPendingMessages()
                    if (pendingMessages.isNotEmpty()) {
                        Logger.d(tag, "Found ${pendingMessages.size} pending messages to sync")
                        
                        for (message in pendingMessages) {
                            if (!isActive) break
                            
                            try {
                                when (message.type) {
                                    ChatMessageQueueType.SEND -> {
                                        val result = chatService.sendMessage(
                                            message.channelId,
                                            message.senderId,
                                            message.content,
                                            message.systemId
                                        )
                                        
                                        result.onSuccess {
                                            Logger.d(tag, "Successfully synced send: ${message.id}")
                                            offlineManager.removePendingMessage(message.id)
                                        }.onFailure { error ->
                                            val isNetworkError = isNetworkError(error)
                                            if (isNetworkError) {
                                                Logger.d(tag, "Network error for send, will retry: ${message.id}")
                                                offlineManager.updatePendingMessageRetry(message.id)
                                                break // Stop processing if network error
                                            } else {
                                                Logger.e(tag, "Permanent error for send: ${message.id} - ${error.message}")
                                                offlineManager.removePendingMessage(message.id)
                                            }
                                        }
                                    }
                                    
                                    ChatMessageQueueType.EDIT -> {
                                        if (message.messageId != null) {
                                            val result = chatService.editMessage(
                                                message.channelId,
                                                message.messageId,
                                                message.content,
                                                message.systemId
                                            )
                                            
                                            result.onSuccess {
                                                Logger.d(tag, "Successfully synced edit: ${message.id}")
                                                offlineManager.removePendingMessage(message.id)
                                            }.onFailure { error ->
                                                val isNetworkError = isNetworkError(error)
                                                if (isNetworkError) {
                                                    Logger.d(tag, "Network error for edit, will retry: ${message.id}")
                                                    offlineManager.updatePendingMessageRetry(message.id)
                                                    break // Stop processing if network error
                                                } else {
                                                    Logger.e(tag, "Permanent error for edit: ${message.id} - ${error.message}")
                                                    offlineManager.removePendingMessage(message.id)
                                                }
                                            }
                                        } else {
                                            Logger.e(tag, "Edit message missing messageId: ${message.id}")
                                            offlineManager.removePendingMessage(message.id)
                                        }
                                    }
                                }
                                
                                delay(100) // Small delay between messages
                            } catch (e: Exception) {
                                Logger.e(tag, "Error syncing message ${message.id}: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(tag, "Error in sync loop: ${e.message}")
                }
                
                // Check every 30 seconds for new pending messages
                delay(30000)
            }
        }
    }

    private fun isNetworkError(error: Throwable): Boolean {
        return error.message?.let { msg ->
            msg.contains("Failed to connect") ||
            msg.contains("Connection refused") ||
            msg.contains("No route") ||
            msg.contains("Network") ||
            msg.contains("timeout") ||
            msg.contains("SocketException") ||
            msg.contains("IOException") ||
            error.cause?.toString()?.contains("IOException") == true
        } ?: false
    }
}
