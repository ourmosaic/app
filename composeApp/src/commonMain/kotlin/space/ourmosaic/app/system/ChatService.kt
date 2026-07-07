package space.ourmosaic.app.system

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.utils.Logger

@kotlinx.serialization.Serializable
data class SseChatMessageEvent(
    val type: String,
    val message: ChatMessageResponse
)

@kotlinx.serialization.Serializable
data class SseChatChannelEvent(
    val type: String,
    val channel: ChatChannelResponse
)


class ChatService(private val authService: AuthService) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    Logger.d("ChatServiceNetwork", message)
                }
            }
            level = LogLevel.ALL
        }
    }

    private suspend fun getBaseUrl(): String? = authService.getFederation()
    private suspend fun getToken(): String? = authService.getAccessToken()

    suspend fun getChatChannels(): Result<List<ChatChannelResponse>> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.get("https://$federation/v1/chat/channels") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to get channels: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(channelId: String, limit: Int = 50, offset: Int = 0): Result<List<ChatMessageResponse>> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.get("https://$federation/v1/chat/channels/$channelId/messages") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("limit", limit)
                parameter("offset", offset)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to get messages: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLastKnownSenders(channelId: String): Result<List<MemberResponse>> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.get("https://$federation/v1/chat/channels/$channelId/lastKnownSenders") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to get last senders: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createChannel(name: String): Result<ChatChannelResponse> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.post("https://$federation/v1/chat/channels") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(CreateChatChannelDto(name))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to create channel: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChannel(channelId: String): Result<Unit> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.delete("https://$federation/v1/chat/channels/$channelId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete channel: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(channelId: String, senderId: String, content: String): Result<ChatMessageResponse> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.post("https://$federation/v1/chat/channels/$channelId/messages") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(SendChatMessageDto(senderId, content))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to send message: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editMessage(channelId: String, messageId: String, content: String): Result<ChatMessageResponse> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.patch("https://$federation/v1/chat/channels/$channelId/messages/$messageId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(EditChatMessageDto(content))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to edit message: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(channelId: String, messageId: String): Result<Unit> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.delete("https://$federation/v1/chat/channels/$channelId/messages/$messageId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete message: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTyping(channelId: String, memberId: String, isTyping: Boolean): Result<Unit> {
        val federation = getBaseUrl() ?: return Result.failure(Exception("No federation"))
        val token = getToken() ?: return Result.failure(Exception("No token"))

        return try {
            val response = client.post("https://$federation/v1/chat/channels/$channelId/typing") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("memberId" to memberId, "isTyping" to isTyping))
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send typing: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
