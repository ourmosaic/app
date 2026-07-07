package space.ourmosaic.app.system

import kotlinx.serialization.Serializable

@Serializable
data class ChatChannelResponse(
    val id: String,
    val name: String,
    val systemId: String,
    val description: String? = null,
    val categoryId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class SseChatTypingEvent(
    val channelId: String,
    val memberId: String,
    val isTyping: Boolean
)

@Serializable
data class ChatMessageResponse(
    val id: String,
    val content: String,
    val senderId: String,
    val channelId: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val timestamp: String? = null,
    val sender: MemberResponse? = null,
    val isPending: Boolean = false,
    val isFailed: Boolean = false
)

@Serializable
data class CreateChatChannelDto(
    val name: String
)

@Serializable
data class SendChatMessageDto(
    val senderId: String,
    val content: String
)

@Serializable
data class EditChatMessageDto(
    val content: String
)
