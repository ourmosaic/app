package space.ourmosaic.app.system

import kotlinx.serialization.Serializable

@Serializable
enum class PrivacyLevel {
    PRIVATE, FRIENDS, PUBLIC
}

@Serializable
enum class FieldType {
    STRING, LONG_TEXT, COLOR, DATE, NUMBER
}

@Serializable
data class SystemResponse(
    val id: String,
    val customName: String? = null,
    val username: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val color: String? = null,
    val domain: String? = null,
    val userId: String? = null,
    val type: FriendshipType? = null
)

@Serializable
data class CustomFieldInfo(
    val name: String,
    val type: FieldType,
    val privacy: PrivacyLevel
)

@Serializable
data class CustomFieldValueResponse(
    val value: String,
    val customFieldId: String,
    val customField: CustomFieldInfo? = null
)

@Serializable
data class MemberResponse(
    val id: String,
    val name: String,
    val pronouns: String? = null,
    val avatarUrl: String? = null,
    val role: String? = null,
    val description: String? = null,
    val domain: String? = null,
    val inDormancy: Boolean,
    val privacy: PrivacyLevel,
    val createdAt: String,
    val updatedAt: String,
    val systemId: String,
    val color: String? = null,
    val groups: List<MemberGroupLink> = emptyList(),
    val customFieldValues: List<CustomFieldValueResponse> = emptyList(),
    val currentFrontSessions: List<FrontSession> = emptyList()
)

@Serializable
data class MemberGroupLink(
    val groupId: String
)

@Serializable
data class FrontSession(
    val id: String,
    val memberId: String,
    val systemId: String,
    val startTime: String,
    val endTime: String? = null,
    val member: MemberResponse? = null
)

@Serializable
data class UpdateCustomFieldDefinitionDto(
    val name: String? = null,
    val type: FieldType? = null,
    val order: Int? = null,
    val privacy: PrivacyLevel? = null
)

@Serializable
data class CustomField(
    val id: String,
    val name: String,
    val type: FieldType,
    val order: Int,
    val privacy: PrivacyLevel,
    val systemId: String
)

@Serializable
data class MemberGroup(
    val id: String,
    val name: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val parentId: String? = null,
    val systemId: String,
    val domain: String? = null,
    val createdAt: String? = null
)

@Serializable
data class CreateGroupDto(
    val name: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val parentId: String? = null
)

@Serializable
data class SyncFrontSessionDto(
    val memberId: String,
    val sessionId: String? = null,
    val startTime: Long,
    val endTime: Long? = null
)

@Serializable
data class SyncFrontSessionsRequest(
    val sessions: List<SyncFrontSessionDto>
)

@Serializable
data class UpdateMemberGroupsDto(
    val groupIds: List<String>
)

@Serializable
data class CreateSystemDto(
    val customName: String? = null,
    val description: String? = null
)

@Serializable
data class UpdateSystemDto(
    val customName: String? = null,
    val description: String? = null,
    val color: String? = null
)

@Serializable
data class CreateMemberDto(
    val name: String,
    val description: String? = null,
    val pronouns: String? = null,
    val role: String? = null,
    val privacy: PrivacyLevel = PrivacyLevel.PRIVATE,
    val color: String? = null
)

@Serializable
data class UpdateFieldContentDto(
    val value: String
)

@Serializable
data class UpdateMemberDto(
    val name: String? = null,
    val description: String? = null,
    val pronouns: String? = null,
    val role: String? = null,
    val privacy: PrivacyLevel? = null,
    val color: String? = null
)

object SseTopics {
    const val FRIENDSHIP = "friendship"
    const val FEDERATION_FRONT_SESSIONS = "federation:frontSessions"
    const val IMPORT = "import"
    const val FRONT_SESSIONS = "front-sessions"
    const val FRONT_CHANGES = "front-changes"
    const val FRIEND_FRONT_SESSIONS = "friend-front-sessions"
    const val PING = "ping"
    const val KEEPALIVE = "keepalive"
    const val READY = "ready"
}

@Serializable
data class SseEvent(
    val topic: String,
    val payload: kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonNull
)

@Serializable
data class ImportEventPayload(
    val event: String = "",
    val importId: String = "",
    val systemId: String? = null,
    val error: String? = null
)

@Serializable
enum class FriendshipType {
    FRIENDS
}

@Serializable
data class FriendRequestResponse(
    val id: String,
    val senderId: String? = null,
    val recipientId: String? = null,
    val sender: SystemResponse? = null,
    val recipient: SystemResponse? = null,
    val status: String? = null,
    val createdAt: String
)

@Serializable
data class FriendshipResponse(
    val id: String,
    val system: SystemResponse,
    val type: FriendshipType,
    val canViewFront: Boolean = false,
    val canReceiveFrontNotifications: Boolean = false,
    val canViewSharedMembers: Boolean = false,
    val notifyMeOnFriendFrontChange: Boolean = false,
    val createdAt: String
)

@Serializable
data class FriendshipPermissions(
    val canViewFront: Boolean = false,
    val canReceiveFrontNotifications: Boolean = false,
    val canViewSharedMembers: Boolean = false,
    val notifyMeOnFriendFrontChange: Boolean = false
)

@Serializable
data class ActiveFrontSession(
    val sessionId: String,
    val member: MemberResponse,
    val startTime: String
)

@Serializable
data class FriendSystemView(
    val id: String,
    val customName: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val color: String? = null,
    val frontPrivacy: PrivacyLevel,
    val permissions: FriendshipPermissions,
    val activeFrontSessions: List<ActiveFrontSession>,
    val members: List<MemberResponse>
)

@Serializable
data class UpdateFriendshipPermissionsDto(
    val canViewFront: Boolean? = null,
    val canReceiveFrontNotifications: Boolean? = null,
    val canViewSharedMembers: Boolean? = null,
    val notifyMeOnFriendFrontChange: Boolean? = null
)

@Serializable
data class SendFriendRequestDto(
    val recipientId: String? = null,
    val username: String? = null,
    val federationUrl: String? = null
)

@Serializable
data class RespondToFriendRequestDto(
    val requestId: String,
    val accept: Boolean
)

@Serializable
data class FriendFrontMember(
    val memberId: String,
    val name: String
)

@Serializable
data class FriendFrontEventPayload(
    val event: String,
    val timestamp: Long,
    val friend: FriendFrontSystemInfo,
    val activeMembers: List<FriendFrontMember>
)

@Serializable
data class FriendFrontSystemInfo(
    val systemId: String,
    val customName: String? = null,
    val username: String? = null,
    val userId: String? = null,
    val avatarUrl: String? = null
)

object ImportEvents {
    const val STARTED = "IMPORT_STARTED"
    const val COMPLETED = "IMPORT_COMPLETED"
    const val FAILED = "IMPORT_FAILED"
}
