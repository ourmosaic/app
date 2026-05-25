package space.ourmosaic.app.system

import space.ourmosaic.app.utils.Logger
import com.russhwolf.settings.Settings
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.offline.PendingAction
import space.ourmosaic.app.offline.PendingActionType
import kotlin.random.Random

class SystemService(
    private val authService: AuthService,
    private val offlineManager: OfflineManager = OfflineManager()
) {

    fun generateId(type: PendingActionType): String {
        return "${type.idPrefix}_${space.ourmosaic.app.randomUUID()}"
    }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
    }

    private val settings = Settings()

    private fun getHeaders(builder: HttpRequestBuilder) {
        val token = authService.getAccessToken()
        if (token != null) {
            builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun getMembers(): Result<List<MemberResponse>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members?withCustomFields=true"
        
        return try {
            val response = client.get(url) {
                getHeaders(this)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getMembers()
            }
            if (response.status.isSuccess()) {
                val members = response.body<List<MemberResponse>>()
                offlineManager.cacheMembers(members)
                
                // Also update custom field definitions cache if we got them in the response
                val fieldsFromMembers = members.flatMap { it.customFieldValues }
                    .mapNotNull { value -> 
                        value.customField?.let { info ->
                            CustomField(
                                id = value.customFieldId,
                                name = info.name,
                                type = info.type,
                                order = 0, // Order isn't in the member response, but name/type are
                                privacy = info.privacy,
                                systemId = members.firstOrNull()?.systemId ?: ""
                            )
                        }
                    }.distinctBy { it.id }
                
                if (fieldsFromMembers.isNotEmpty()) {
                    val current = offlineManager.getCachedCustomFields() ?: emptyList()
                    // Merge, but keep existing ones if they have more info (like order)
                    val merged = (current + fieldsFromMembers).distinctBy { it.id }
                    offlineManager.cacheCustomFields(merged)
                }

                Result.success(members)
            } else {
                val cached = offlineManager.getCachedMembers()
                if (cached != null) Result.success(cached)
                else Result.failure(Exception("Failed to fetch members: ${response.status}"))
            }
        } catch (e: Exception) {
            val cached = offlineManager.getCachedMembers()
            if (cached != null) Result.success(cached)
            else Result.failure(e)
        }
    }

    suspend fun updateSystem(dto: UpdateSystemDto, fromSync: Boolean = false): Result<SystemResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me"
        
        val actionId = if (!fromSync) generateId(PendingActionType.UPDATE_SYSTEM) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.UPDATE_SYSTEM,
                    jsonPayload = json.encodeToString(UpdateSystemDto.serializer(), dto),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.patch(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return updateSystem(dto, fromSync)
            }
            if (response.status.isSuccess()) {
                val system = response.body<SystemResponse>()
                if (actionId != null) offlineManager.removeAction(actionId)
                updateCachedSystem(system)
                Result.success(system)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to update system: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                val currentSystem = authService.userMe.value?.system
                val dummy = SystemResponse(
                    id = currentSystem?.id ?: settings.getStringOrNull("system_id") ?: "",
                    customName = dto.customName ?: currentSystem?.customName,
                    description = dto.description ?: currentSystem?.description,
                    color = dto.color ?: currentSystem?.color,
                    userId = currentSystem?.userId ?: ""
                )
                updateCachedSystem(dummy)
                Result.success(dummy)
            } else Result.failure(e)
        }
    }

    suspend fun createMember(dto: CreateMemberDto, fromSync: Boolean = false): Result<MemberResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members?withCustomFields=true"
        
        val actionId = if (!fromSync) generateId(PendingActionType.CREATE_MEMBER) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.CREATE_MEMBER,
                    jsonPayload = json.encodeToString(CreateMemberDto.serializer(), dto),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.post(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return createMember(dto, fromSync)
            }
            if (response.status.isSuccess()) {
                val member = response.body<MemberResponse>()
                // If the member response doesn't have custom fields (it should, but just in case)
                // we might want to fetch them or assume it's fine for a new member
                if (actionId != null) {
                    offlineManager.saveIdMapping(member.id, actionId)
                    offlineManager.removeAction(actionId)
                }
                updateMemberCacheOptimistically(member)
                Result.success(member)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to create member: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                // Return a dummy member with the actionId as temporary ID
                Result.success(MemberResponse(
                    id = actionId,
                    name = dto.name,
                    pronouns = dto.pronouns,
                    color = dto.color,
                    privacy = dto.privacy ?: PrivacyLevel.PRIVATE,
                    inDormancy = false,
                    systemId = settings.getStringOrNull("system_id") ?: "",
                    createdAt = Clock.System.now().toString(),
                    updatedAt = Clock.System.now().toString()
                ))
            } else Result.failure(e)
        }
    }

    private fun updateMemberCacheOptimistically(member: MemberResponse) {
        val members = offlineManager.getCachedMembers()?.toMutableList() ?: mutableListOf()
        val index = members.indexOfFirst { it.id == member.id }
        if (index != -1) {
            members[index] = member
        } else {
            members.add(member)
        }
        offlineManager.cacheMembers(members)
    }

    suspend fun updateMember(memberId: String, dto: UpdateMemberDto, fromSync: Boolean = false): Result<MemberResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/$memberId?withCustomFields=true"
        
        val actionId = if (!fromSync) generateId(PendingActionType.UPDATE_MEMBER) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.UPDATE_MEMBER,
                    memberId = memberId,
                    jsonPayload = json.encodeToString(UpdateMemberDto.serializer(), dto),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.patch(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return updateMember(memberId, dto, fromSync)
            }
            if (response.status.isSuccess()) {
                val member = response.body<MemberResponse>()
                if (actionId != null) offlineManager.removeAction(actionId)
                updateMemberCacheOptimistically(member)
                Result.success(member)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to update member: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                // Return dummy response
                val current = offlineManager.getCachedMembers()?.find { it.id == memberId }
                val dummy = current?.copy(
                    name = dto.name ?: current.name,
                    pronouns = dto.pronouns ?: current.pronouns,
                    description = dto.description ?: current.description,
                    color = dto.color ?: current.color,
                    privacy = dto.privacy ?: current.privacy
                ) ?: MemberResponse(
                    id = memberId, 
                    name = dto.name ?: "Unknown", 
                    systemId = "", 
                    createdAt = "", 
                    updatedAt = "",
                    inDormancy = false,
                    privacy = PrivacyLevel.PRIVATE
                )
                updateMemberCacheOptimistically(dummy)
                Result.success(dummy)
            } else Result.failure(e)
        }
    }

    suspend fun updateMemberField(memberId: String, fieldId: String, value: String, fromSync: Boolean = false): Result<MemberResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/$memberId/fields/$fieldId?withCustomFields=true"
        
        val actionId = if (!fromSync) generateId(PendingActionType.UPDATE_MEMBER_FIELD) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.UPDATE_MEMBER_FIELD,
                    memberId = memberId,
                    fieldId = fieldId,
                    jsonPayload = value,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.patch(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(UpdateFieldContentDto(value = value))
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return updateMemberField(memberId, fieldId, value, fromSync)
            }
            if (response.status.isSuccess()) {
                val member = response.body<MemberResponse>()
                if (actionId != null) offlineManager.removeAction(actionId)
                updateMemberCacheOptimistically(member)
                Result.success(member)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to update member field: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                val current = offlineManager.getCachedMembers()?.find { it.id == memberId }
                if (current != null) {
                    val updatedFields = current.customFieldValues.toMutableList()
                    val fIndex = updatedFields.indexOfFirst { it.customFieldId == fieldId }
                    if (fIndex != -1) {
                        updatedFields[fIndex] = updatedFields[fIndex].copy(value = value)
                    } else {
                        updatedFields.add(CustomFieldValueResponse(value = value, customFieldId = fieldId))
                    }
                    val updatedMember = current.copy(customFieldValues = updatedFields)
                    updateMemberCacheOptimistically(updatedMember)
                    Result.success(updatedMember)
                } else {
                    val dummy = MemberResponse(
                        id = memberId, 
                        name = "Unknown", 
                        systemId = "", 
                        createdAt = "", 
                        updatedAt = "",
                        inDormancy = false,
                        privacy = PrivacyLevel.PRIVATE,
                        customFieldValues = listOf(CustomFieldValueResponse(value = value, customFieldId = fieldId))
                    )
                    updateMemberCacheOptimistically(dummy)
                    Result.success(dummy)
                }
            } else Result.failure(e)
        }
    }

    suspend fun uploadMemberAvatar(memberId: String, avatarBytes: ByteArray, fromSync: Boolean = false): Result<MemberResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/$memberId/avatar"
        
        if (!fromSync) {
            val fileName = "avatar_${memberId}_${Clock.System.now().toEpochMilliseconds()}.jpg"
            space.ourmosaic.app.utils.writeToCache(fileName, avatarBytes)
            
            offlineManager.queueAction(
                PendingAction(
                    id = generateId(PendingActionType.UPLOAD_AVATAR),
                    type = PendingActionType.UPLOAD_AVATAR,
                    memberId = memberId,
                    jsonPayload = fileName,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.post(url) {
                getHeaders(this)
                setBody(MultiPartFormDataContent(
                    formData {
                        append("file", avatarBytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                    }
                ))
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return uploadMemberAvatar(memberId, avatarBytes, fromSync)
            }
            if (response.status.isSuccess()) {
                val member = response.body<MemberResponse>()
                updateMemberCacheOptimistically(member)
                Result.success(member)
            } else {
                Result.failure(Exception("Failed to upload member avatar: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroup(groupId: String, dto: CreateGroupDto, fromSync: Boolean = false): Result<MemberGroup> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/groups/$groupId"
        
        val actionId = if (!fromSync) generateId(PendingActionType.UPDATE_GROUP) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.UPDATE_GROUP,
                    memberId = groupId,
                    jsonPayload = json.encodeToString(CreateGroupDto.serializer(), dto),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.patch(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return updateGroup(groupId, dto, fromSync)
            }
            if (response.status.isSuccess()) {
                val group = response.body<MemberGroup>()
                if (actionId != null) offlineManager.removeAction(actionId)
                updateGroupCacheOptimistically(group)
                Result.success(group)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to update group: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                val current = offlineManager.getCachedGroups()?.find { it.id == groupId }
                val updated = current?.copy(
                    name = dto.name,
                    color = dto.color,
                    icon = dto.icon,
                    parentId = dto.parentId ?: current.parentId
                ) ?: MemberGroup(
                    id = groupId,
                    name = dto.name,
                    color = dto.color,
                    icon = dto.icon,
                    parentId = dto.parentId,
                    systemId = ""
                )
                updateGroupCacheOptimistically(updated)
                Result.success(updated)
            } else Result.failure(e)
        }
    }

    suspend fun uploadSystemAvatar(avatarBytes: ByteArray, fromSync: Boolean = false): Result<SystemResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/avatar"
        
        if (!fromSync) {
            val fileName = "avatar_me_${Clock.System.now().toEpochMilliseconds()}.jpg"
            space.ourmosaic.app.utils.writeToCache(fileName, avatarBytes)
            
            offlineManager.queueAction(
                PendingAction(
                    id = generateId(PendingActionType.UPLOAD_AVATAR),
                    type = PendingActionType.UPLOAD_AVATAR,
                    memberId = "@me",
                    jsonPayload = fileName,
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.post(url) {
                getHeaders(this)
                setBody(MultiPartFormDataContent(
                    formData {
                        append("file", avatarBytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        })
                    }
                ))
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return uploadSystemAvatar(avatarBytes, fromSync)
            }
            if (response.status.isSuccess()) {
                val system = response.body<SystemResponse>()
                updateCachedSystem(system)
                Result.success(system)
            } else {
                Result.failure(Exception("Failed to upload system avatar: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCustomFields(): Result<List<CustomField>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/customFields"
        
        return try {
            val response = client.get(url) {
                getHeaders(this)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getCustomFields()
            }
            if (response.status.isSuccess()) {
                val fields = response.body<List<CustomField>>()
                offlineManager.cacheCustomFields(fields)
                Result.success(fields)
            } else {
                val cached = offlineManager.getCachedCustomFields()
                if (cached != null) Result.success(cached)
                else Result.failure(Exception("Failed to fetch custom fields: ${response.status}"))
            }
        } catch (e: Exception) {
            val cached = offlineManager.getCachedCustomFields()
            if (cached != null) Result.success(cached)
            else Result.failure(e)
        }
    }

    suspend fun createCustomField(fromSync: Boolean = false): Result<CustomField> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/customFields"
        
        val actionId = if (!fromSync) generateId(PendingActionType.CREATE_CUSTOM_FIELD) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.CREATE_CUSTOM_FIELD,
                    jsonPayload = "{}",
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.put(url) {
                getHeaders(this)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return createCustomField(fromSync)
            }
            if (response.status.isSuccess()) {
                val field = response.body<CustomField>()
                if (actionId != null) {
                    offlineManager.saveIdMapping(field.id, actionId)
                    offlineManager.removeAction(actionId)
                }
                val current = offlineManager.getCachedCustomFields() ?: emptyList()
                offlineManager.cacheCustomFields(current.filter { it.id != actionId } + field)
                Result.success(field)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to create custom field: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                Result.success(CustomField(
                    id = actionId,
                    name = "New Field",
                    type = FieldType.STRING,
                    order = 0,
                    privacy = PrivacyLevel.PRIVATE,
                    systemId = settings.getStringOrNull("system_id") ?: ""
                ))
            } else Result.failure(e)
        }
    }

    suspend fun updateCustomField(fieldId: String, dto: UpdateCustomFieldDefinitionDto, fromSync: Boolean = false): Result<CustomField> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/customFields/$fieldId"
        
        val actionId = if (!fromSync) generateId(PendingActionType.UPDATE_CUSTOM_FIELD) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.UPDATE_CUSTOM_FIELD,
                    fieldId = fieldId,
                    jsonPayload = json.encodeToString(UpdateCustomFieldDefinitionDto.serializer(), dto),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.patch(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return updateCustomField(fieldId, dto, fromSync)
            }
            if (response.status.isSuccess()) {
                val field = response.body<CustomField>()
                if (actionId != null) offlineManager.removeAction(actionId)
                val current = offlineManager.getCachedCustomFields() ?: emptyList()
                offlineManager.cacheCustomFields(current.map { if (it.id == fieldId) field else it })
                Result.success(field)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to update custom field: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                val current = offlineManager.getCachedCustomFields()?.find { it.id == fieldId }
                Result.success(current?.copy(
                    name = dto.name ?: current.name,
                    type = dto.type ?: current.type,
                    order = dto.order ?: current.order,
                    privacy = dto.privacy ?: current.privacy
                ) ?: CustomField(id = fieldId, name = dto.name ?: "", type = dto.type ?: FieldType.STRING, order = 0, privacy = PrivacyLevel.PRIVATE, systemId = ""))
            } else Result.failure(e)
        }
    }

    suspend fun deleteCustomField(fieldId: String, fromSync: Boolean = false): Result<Unit> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/customFields/$fieldId"
        
        val actionId = if (!fromSync) generateId(PendingActionType.DELETE_CUSTOM_FIELD) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.DELETE_CUSTOM_FIELD,
                    fieldId = fieldId,
                    jsonPayload = "{}",
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.delete(url) {
                getHeaders(this)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return deleteCustomField(fieldId, fromSync)
            }
            if (response.status.isSuccess()) {
                if (actionId != null) offlineManager.removeAction(actionId)
                val current = offlineManager.getCachedCustomFields() ?: emptyList()
                offlineManager.cacheCustomFields(current.filter { it.id != fieldId })
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to delete custom field: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) Result.success(Unit)
            else Result.failure(e)
        }
    }

    suspend fun startFrontSession(memberId: String): Result<FrontSession> {
        val now = Clock.System.now()
        val timestamp = now.toEpochMilliseconds()
        val actionId = generateId(PendingActionType.START_FRONT)

        val newSession = FrontSession(
            id = actionId,
            memberId = memberId,
            systemId = settings.getStringOrNull("system_id") ?: "",
            startTime = now.toString(),
            endTime = null
        )

        // Optimistic UI update: on affiche immédiatement le changement localement
        offlineManager.cacheFrontSessions(listOf(newSession))

        // 1. Si on est en ligne, on tente un appel direct
        val federation = authService.getAccessToken()?.let { authService.getFederation() }
        if (federation != null) {
            val resolvedMemberId = offlineManager.getServerId(memberId)
            if (resolvedMemberId != null && !resolvedMemberId.contains("_")) {
                val url = "https://$federation/v1/system/@me/members/$resolvedMemberId/front-session"
                space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Direct start front attempt: $url")
                try {
                    val response = client.post(url) {
                        getHeaders(this)
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }

                    if (response.status == HttpStatusCode.Unauthorized) {
                        if (authService.refreshToken().isSuccess) return startFrontSession(memberId)
                    }

                    if (response.status.isSuccess()) {
                        val responseText = response.bodyAsText()
                        space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Direct start success: $responseText")
                        val session = json.decodeFromString<FrontSession>(responseText)
                        // On lie l'ID temporaire à l'ID réel du serveur
                        offlineManager.saveIdMapping(session.id, actionId)
                        offlineManager.cacheFrontSessions(listOf(session))
                        return Result.success(session)
                    } else {
                        val errorBody = response.bodyAsText()
                        space.ourmosaic.app.utils.Logger.e("SystemService", "[SYNC_DEBUG] Direct start failed: ${response.status} - $errorBody")
                    }
                } catch (e: Exception) {
                    space.ourmosaic.app.utils.Logger.e("SystemService", "[SYNC_DEBUG] Direct start front exception", e)
                }
            } else {
                space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Skipping direct start: memberId $memberId (resolved: $resolvedMemberId) is local")
            }
        }

        // 2. Fallback offline (ou si l'appel direct a échoué)
        val action = PendingAction(
            id = actionId,
            type = PendingActionType.START_FRONT,
            memberId = memberId,
            jsonPayload = "{}",
            timestamp = timestamp
        )

        offlineManager.queueAction(action)
        triggerSync()

        return Result.success(newSession)
    }

    suspend fun endFrontSession(memberId: String, sessionId: String? = null): Result<FrontSession> {
        val now = Clock.System.now()
        val timestamp = now.toEpochMilliseconds()
        val actionId = generateId(PendingActionType.END_FRONT)

        // On prépare l'ID de session pour le cache
        val localSessionId = sessionId?.let { if (!it.startsWith("front_start")) offlineManager.getLocalId(it) ?: it else it }
        val targetSessionId = localSessionId ?: sessionId

        // Recherche dans le cache pour l'UI et le startTime
        val cachedSession = if (targetSessionId != null) {
            offlineManager.getCachedFrontSessions()?.find { it.id == targetSessionId }
        } else {
            offlineManager.getCachedFrontSessions()
                ?.filter { it.memberId == memberId && it.endTime == null }
                ?.minByOrNull { it.startTime }
        }

        val endedSession = FrontSession(
            id = cachedSession?.id ?: targetSessionId ?: actionId,
            memberId = memberId,
            systemId = settings.getStringOrNull("system_id") ?: "",
            startTime = cachedSession?.startTime ?: now.toString(),
            endTime = now.toString()
        )

        // Optimistic UI update: on marque immédiatement la session comme terminée
        offlineManager.cacheFrontSessions(listOf(endedSession))

        // 1. Si on est en ligne, on tente un appel direct
        val federation = authService.getAccessToken()?.let { authService.getFederation() }
        if (federation != null) {
            val resolvedMemberId = offlineManager.getServerId(memberId)
            val resolvedSessionId = sessionId?.let { offlineManager.getServerId(it) }

            if (resolvedMemberId != null && !resolvedMemberId.contains("_")) {
                val url = if (resolvedSessionId != null && !resolvedSessionId.contains("_")) {
                    "https://$federation/v1/system/@me/members/$resolvedMemberId/front-session/$resolvedSessionId/end"
                } else {
                    "https://$federation/v1/system/@me/members/$resolvedMemberId/front-session/end"
                }

                space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Direct end front attempt: $url")
                try {
                    val response = client.post(url) {
                        getHeaders(this)
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }

                    if (response.status == HttpStatusCode.Unauthorized) {
                        if (authService.refreshToken().isSuccess) return endFrontSession(memberId, sessionId)
                    }

                    if (response.status.isSuccess()) {
                        val responseText = response.bodyAsText()
                        space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Direct end success: $responseText")
                        val session = json.decodeFromString<FrontSession>(responseText)
                        offlineManager.cacheFrontSessions(listOf(session))
                        return Result.success(session)
                    } else {
                        val errorBody = response.bodyAsText()
                        space.ourmosaic.app.utils.Logger.e("SystemService", "[SYNC_DEBUG] Direct end failed: ${response.status} - $errorBody")
                    }
                } catch (e: Exception) {
                    space.ourmosaic.app.utils.Logger.e("SystemService", "[SYNC_DEBUG] Direct end front exception", e)
                }
            } else {
                space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Skipping direct end: memberId $memberId (resolved: $resolvedMemberId) or sessionId $sessionId is local")
            }
        }

        // 2. Fallback offline
        val originalStartTime = if (cachedSession != null) {
            try { Instant.parse(cachedSession.startTime).toEpochMilliseconds() } catch (e: Exception) { timestamp }
        } else {
            timestamp
        }

        val action = PendingAction(
            id = actionId,
            type = PendingActionType.END_FRONT,
            memberId = memberId,
            sessionId = targetSessionId,
            jsonPayload = json.encodeToString(mapOf("startTime" to originalStartTime.toString())),
            timestamp = timestamp
        )

        offlineManager.queueAction(action)
        triggerSync()

        return Result.success(endedSession)
    }

    suspend fun syncFrontSessions(): Result<List<FrontSession>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/front-session/sync"
        
        val allActions = offlineManager.getPendingActions()
        val frontActions = allActions.filter { it.type == PendingActionType.START_FRONT || it.type == PendingActionType.END_FRONT }
        
        if (frontActions.isEmpty()) return Result.success(emptyList())

        val sessionMap = mutableMapOf<String, SyncFrontSessionDto>()
        
        for (action in frontActions) {
            // ID Resolution: ensure memberId and sessionId are server UUIDs
            val resolvedMemberId = offlineManager.getServerId(action.memberId) ?: ""
            val resolvedSessionId = offlineManager.getServerId(action.sessionId)
            
            if (resolvedMemberId.isEmpty() || resolvedMemberId.contains("_")) {
                space.ourmosaic.app.utils.Logger.w("SystemService", "[SYNC_DEBUG] Sync Warning: memberId $resolvedMemberId for action ${action.id} is not a valid server UUID")
            }
            
            // Pour le START, on utilise son propre ID comme sessionId temporaire s'il n'est pas déjà résolu.
            // Pour le END, on utilise le sessionId s'il existe (mapping local ou ID serveur).
            val effectiveSessionId = if (action.type == PendingActionType.START_FRONT) {
                 resolvedSessionId ?: action.id 
            } else {
                 resolvedSessionId
            }
            
            // Clé de regroupement : on essaie de coupler START et END s'ils partagent le même ID.
            // Si pas de sessionId (END par membre), on utilise l'ID de l'action pour éviter les collisions.
            val groupKey = effectiveSessionId ?: action.id
            val existing = sessionMap[groupKey]
            
            if (action.type == PendingActionType.START_FRONT) {
                sessionMap[groupKey] = SyncFrontSessionDto(
                    memberId = resolvedMemberId,
                    sessionId = if (effectiveSessionId?.contains("_") == true) null else effectiveSessionId,
                    startTime = action.timestamp,
                    endTime = existing?.endTime
                )
            } else {
                // Pour une fin de session, on essaie de récupérer le startTime stocké dans le payload
                val payload = try { json.decodeFromString<Map<String, String>>(action.jsonPayload) } catch (e: Exception) { emptyMap() }
                val recoveredStartTime = payload["startTime"]?.toLongOrNull() ?: action.timestamp

                sessionMap[groupKey] = SyncFrontSessionDto(
                    memberId = resolvedMemberId.ifEmpty { existing?.memberId ?: "" },
                    sessionId = if (effectiveSessionId?.contains("_") == true) effectiveSessionId else null,
                    startTime = existing?.startTime ?: recoveredStartTime,
                    endTime = action.timestamp
                )
            }
        }

        val sessionsToSync = sessionMap.values.toList()
        val request = SyncFrontSessionsRequest(sessions = sessionsToSync)
        val payload = json.encodeToString(SyncFrontSessionsRequest.serializer(), request)

        space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] >>> SYNC FRONT SESSIONS REQUEST >>>")
        space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] URL: $url")
        space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Payload: $payload")

        return try {
            val response = client.post(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] <<< SYNC FRONT SESSIONS RESPONSE <<<")
            space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Status: ${response.status}")

            if (response.status == HttpStatusCode.Unauthorized) {
                space.ourmosaic.app.utils.Logger.w("SystemService", "[SYNC_DEBUG] Unauthorized, attempting token refresh...")
                if (authService.refreshToken().isSuccess) return syncFrontSessions()
            }
            
            val responseText = response.bodyAsText()
            if (response.status.isSuccess()) {
                space.ourmosaic.app.utils.Logger.d("SystemService", "[SYNC_DEBUG] Sync Success Body: $responseText")
                val syncedSessions = json.decodeFromString<List<FrontSession>>(responseText)
                
                // On enregistre les mappings d'ID pour les sessions créées par le serveur
                for (session in syncedSessions) {
                    val sessionStartTimeMs = try { Instant.parse(session.startTime).toEpochMilliseconds() } catch (e: Exception) { 0L }
                    
                    // Match by memberId and a small time window for startTime (5 seconds tolerance)
                    val match = sessionMap.entries.find { (_, dto) -> 
                        dto.memberId == session.memberId && 
                        kotlin.math.abs(dto.startTime - sessionStartTimeMs) < 5000 
                    }

                    if (match != null) {
                        val localId = match.key
                        if (localId.contains("_") && localId != session.id) {
                            offlineManager.saveIdMapping(session.id, localId)
                        }
                    }
                }
                
                offlineManager.cacheFrontSessions(syncedSessions)
                offlineManager.removeActions(frontActions.map { it.id })
                Result.success(syncedSessions)
            } else {
                space.ourmosaic.app.utils.Logger.e("SystemService", "[SYNC_DEBUG] Sync Failed Body: $responseText")
                Result.failure(Exception("Bulk sync failed: ${response.status} - $responseText"))
            }
        } catch (e: Exception) {
            space.ourmosaic.app.utils.Logger.e("SystemService", "[SYNC_DEBUG] Exception during bulk sync", e)
            Result.failure(e)
        }
    }

    suspend fun getFrontSessions(): Result<List<FrontSession>> {
        val federation =
            authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/front-sessions"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getFrontSessions()
            }
            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val sessions = json.decodeFromString<List<FrontSession>>(responseText)
                offlineManager.cacheFrontSessions(sessions)
                Result.success(sessions)
            } else {
                val cached = offlineManager.getCachedFrontSessions()
                if (cached != null) Result.success(cached)
                else Result.failure(Exception("Failed to fetch front sessions: ${response.status}"))
            }
        } catch (e: Exception) {
            val cached = offlineManager.getCachedFrontSessions()
            if (cached != null) Result.success(cached)
            else Result.failure(e)
        }
    }

    suspend fun getActiveFrontSessions(forceRefresh: Boolean = false): Result<List<FrontSession>> {
        // En mode offline ou rafraîchissement normal, on se base sur le cache global (qui contient les merged sessions)
        val cached = offlineManager.getCachedFrontSessions()
        if (!forceRefresh && cached != null) {
            return Result.success(cached.filter { it.endTime == null })
        }
        
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/front-sessions/active"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getActiveFrontSessions(forceRefresh)
            }
            if (response.status.isSuccess()) {
                val sessions = response.body<List<FrontSession>>()
                
                // CRUCIAL: On ne remplace pas tout le cache par les sessions actives du serveur.
                // On utilise cacheFrontSessions avec markOthersAsEnded = true pour invalider les sessions locales qui ne sont plus actives sur le serveur.
                offlineManager.cacheFrontSessions(sessions, markOthersAsEnded = true)
                
                // On retourne ce qui est réellement actif après merge
                val updatedCache = offlineManager.getCachedFrontSessions() ?: sessions
                Result.success(updatedCache.filter { it.endTime == null })
            } else {
                // En cas d'erreur serveur, on replie sur le cache
                if (cached != null) Result.success(cached.filter { it.endTime == null })
                else Result.failure(Exception("Failed to fetch active sessions: ${response.status}"))
            }
        } catch (e: Exception) {
            if (cached != null) Result.success(cached.filter { it.endTime == null })
            else Result.failure(e)
        }
    }

    suspend fun createGroup(dto: CreateGroupDto, fromSync: Boolean = false, parentId: String? = null): Result<MemberGroup> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/groups"
        val body = dto.copy(parentId = parentId ?: dto.parentId)
        
        val actionId = if (!fromSync) generateId(PendingActionType.CREATE_GROUP) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.CREATE_GROUP,
                    jsonPayload = json.encodeToString(CreateGroupDto.serializer(), body),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.post(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return createGroup(dto, fromSync, parentId)
            }
            if (response.status.isSuccess()) {
                val group = response.body<MemberGroup>()
                if (actionId != null) {
                    offlineManager.saveIdMapping(group.id, actionId)
                    offlineManager.removeAction(actionId)
                }
                updateGroupCacheOptimistically(group)
                Result.success(group)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to create group: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                val group = MemberGroup(
                    id = actionId,
                    name = body.name,
                    color = body.color,
                    icon = body.icon,
                    parentId = body.parentId,
                    systemId = settings.getStringOrNull("system_id") ?: ""
                )
                updateGroupCacheOptimistically(group)
                Result.success(group)
            } else Result.failure(e)
        }
    }

    private fun updateGroupCacheOptimistically(group: MemberGroup) {
        val current = offlineManager.getCachedGroups()?.toMutableList() ?: mutableListOf()
        val index = current.indexOfFirst { it.id == group.id }
        if (index != -1) {
            current[index] = group
        } else {
            current.add(group)
        }
        offlineManager.cacheGroups(current)
    }

    suspend fun deleteGroup(groupId: String, fromSync: Boolean = false): Result<Unit> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/groups/$groupId"
        
        val actionId = if (!fromSync) generateId(PendingActionType.DELETE_GROUP) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.DELETE_GROUP,
                    memberId = groupId,
                    jsonPayload = "{}",
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.delete(url) {
                getHeaders(this)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return deleteGroup(groupId, fromSync)
            }
            if (response.status.isSuccess()) {
                if (actionId != null) offlineManager.removeAction(actionId)
                removeGroupFromCache(groupId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete group: ${response.status}"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                removeGroupFromCache(groupId)
                Result.success(Unit)
            } else Result.failure(e)
        }
    }

    private fun removeGroupFromCache(groupId: String) {
        val current = offlineManager.getCachedGroups() ?: return
        offlineManager.cacheGroups(current.filter { it.id != groupId })
    }

    suspend fun updateMemberGroups(memberId: String, groupIds: List<String>, fromSync: Boolean = false): Result<MemberResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/$memberId/groups"
        val dto = UpdateMemberGroupsDto(groupIds)

        val actionId = if (!fromSync) generateId(PendingActionType.UPDATE_MEMBER_GROUPS) else null
        if (actionId != null) {
            offlineManager.queueAction(
                PendingAction(
                    id = actionId,
                    type = PendingActionType.UPDATE_MEMBER_GROUPS,
                    memberId = memberId,
                    jsonPayload = json.encodeToString(UpdateMemberGroupsDto.serializer(), dto),
                    timestamp = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        return try {
            val response = client.put(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return updateMemberGroups(memberId, groupIds, fromSync)
            }
            if (response.status.isSuccess()) {
                val member = response.body<MemberResponse>()
                if (actionId != null) offlineManager.removeAction(actionId)
                updateMemberCacheOptimistically(member)
                Result.success(member)
            } else {
                val error = response.bodyAsText()
                Result.failure(Exception("Failed to update member groups: ${response.status} - $error"))
            }
        } catch (e: Exception) {
            if (actionId != null) {
                val current = offlineManager.getCachedMembers()?.find { it.id == memberId }
                if (current != null) {
                    val updated = current.copy(groups = groupIds.map { MemberGroupLink(it) })
                    updateMemberCacheOptimistically(updated)
                    Result.success(updated)
                } else {
                    Result.success(MemberResponse(
                        id = memberId,
                        name = "Unknown",
                        systemId = "",
                        createdAt = "",
                        updatedAt = "",
                        inDormancy = false,
                        privacy = PrivacyLevel.PRIVATE,
                        groups = groupIds.map { MemberGroupLink(it) }
                    ))
                }
            } else Result.failure(e)
        }
    }

    suspend fun deleteMemberGroups(memberId: String, groupIds: List<String>): Result<MemberResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/members/$memberId/groups"
        val dto = UpdateMemberGroupsDto(groupIds)
        return try {
            val response = client.delete(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return deleteMemberGroups(memberId, groupIds)
            }
            if (response.status.isSuccess()) {
                val member = response.body<MemberResponse>()
                updateMemberCacheOptimistically(member)
                Result.success(member)
            } else {
                val error = response.bodyAsText()
                Result.failure(Exception("Failed to delete member groups: ${response.status} - $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroups(): Result<List<MemberGroup>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/groups"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getGroups()
            }
            if (response.status.isSuccess()) {
                val groups = response.body<List<MemberGroup>>()
                offlineManager.cacheGroups(groups)
                Result.success(groups)
            } else {
                val cached = offlineManager.getCachedGroups()
                if (cached != null) Result.success(cached)
                else Result.failure(Exception("Failed to fetch groups: ${response.status}"))
            }
        } catch (e: Exception) {
            val cached = offlineManager.getCachedGroups()
            if (cached != null) Result.success(cached)
            else Result.failure(e)
        }
    }

    suspend fun getChildGroups(groupId: String): Result<List<MemberGroup>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/groups/$groupId/children"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getChildGroups(groupId)
            }
            if (response.status.isSuccess()) Result.success(response.body())
            else Result.failure(Exception("Failed to fetch child groups: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMembersInGroup(groupId: String): Result<List<MemberResponse>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/system/@me/groups/$groupId/members?withCustomFields=true"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getMembersInGroup(groupId)
            }
            if (response.status.isSuccess()) {
                val members = response.body<List<MemberResponse>>()
                
                // Also update custom field definitions cache if we got them in the response
                val fieldsFromMembers = members.flatMap { it.customFieldValues }
                    .mapNotNull { value ->
                        value.customField?.let { info ->
                            CustomField(
                                id = value.customFieldId,
                                name = info.name,
                                type = info.type,
                                order = 0,
                                privacy = info.privacy,
                                systemId = members.firstOrNull()?.systemId ?: ""
                            )
                        }
                    }.distinctBy { it.id }

                if (fieldsFromMembers.isNotEmpty()) {
                    val current = offlineManager.getCachedCustomFields() ?: emptyList()
                    val merged = (current + fieldsFromMembers).distinctBy { it.id }
                    offlineManager.cacheCustomFields(merged)
                }

                Result.success(members)
            } else Result.failure(Exception("Failed to fetch group members: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequest(dto: SendFriendRequestDto): Result<FriendRequestResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/request"
        space.ourmosaic.app.utils.Logger.d("SystemService", "Sending friend request to $url with body: $dto")
        return try {
            val response = client.post(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return sendFriendRequest(dto)
            }
            if (response.status.isSuccess()) {
                val body = response.body<FriendRequestResponse>()
                space.ourmosaic.app.utils.Logger.d("SystemService", "Friend request sent successfully: $body")
                getSentFriendRequests() // Refresh the sent requests list
                Result.success(body)
            } else {
                val errorBody = response.bodyAsText()
                space.ourmosaic.app.utils.Logger.e("SystemService", "Failed to send friend request: ${response.status} - $errorBody")
                Result.failure(Exception("Failed to send friend request: ${response.status}"))
            }
        } catch (e: Exception) {
            space.ourmosaic.app.utils.Logger.e("SystemService", "Exception sending friend request", e)
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(requestId: String, accept: Boolean): Result<Unit> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/respond"
        val dto = RespondToFriendRequestDto(requestId, accept)
        return try {
            val response = client.post(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return respondToFriendRequest(requestId, accept)
            }
            if (response.status.isSuccess()) {
                // Update cache
                val current = offlineManager.getCachedReceivedRequests() ?: emptyList()
                offlineManager.cacheReceivedRequests(current.filter { it.id != requestId })
                
                if (accept) {
                    getFriends() // Refresh friends list if accepted
                }

                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to respond to friend request: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelFriendRequest(requestId: String): Result<Unit> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/request/$requestId"
        return try {
            val response = client.delete(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return cancelFriendRequest(requestId)
            }
            if (response.status.isSuccess()) {
                val current = offlineManager.getCachedSentRequests() ?: emptyList()
                offlineManager.cacheSentRequests(current.filter { it.id != requestId })
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to cancel friend request: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSentFriendRequests(): Result<List<FriendRequestResponse>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/requests/sent"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getSentFriendRequests()
            }
            if (response.status.isSuccess()) {
                val requests = response.body<List<FriendRequestResponse>>()
                offlineManager.cacheSentRequests(requests)
                Result.success(requests)
            } else {
                offlineManager.getCachedSentRequests()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Failed to get sent requests: ${response.status}"))
            }
        } catch (e: Exception) {
            offlineManager.getCachedSentRequests()?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    suspend fun getReceivedFriendRequests(): Result<List<FriendRequestResponse>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/requests/received"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getReceivedFriendRequests()
            }
            if (response.status.isSuccess()) {
                val requests = response.body<List<FriendRequestResponse>>()
                offlineManager.cacheReceivedRequests(requests)
                Result.success(requests)
            } else {
                offlineManager.getCachedReceivedRequests()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Failed to get received requests: ${response.status}"))
            }
        } catch (e: Exception) {
            offlineManager.getCachedReceivedRequests()?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    suspend fun getFriends(): Result<List<SystemResponse>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/list"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getFriends()
            }
            if (response.status.isSuccess()) {
                val friends = response.body<List<SystemResponse>>()
                offlineManager.cacheFriends(friends)
                Result.success(friends)
            } else {
                offlineManager.getCachedFriends()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Failed to get friends list: ${response.status}"))
            }
        } catch (e: Exception) {
            offlineManager.getCachedFriends()?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    suspend fun getFriendSystem(friendId: String): Result<FriendSystemView> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/$friendId/system"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getFriendSystem(friendId)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to get friend system: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriendMembers(friendId: String): Result<List<MemberResponse>> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/$friendId/members"
        return try {
            val response = client.get(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return getFriendMembers(friendId)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to get friend members: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFriendshipPermissions(friendId: String, dto: UpdateFriendshipPermissionsDto): Result<FriendshipResponse> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/$friendId/permissions"
        return try {
            val response = client.patch(url) {
                getHeaders(this)
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return updateFriendshipPermissions(friendId, dto)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to update friendship permissions: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun removeFriend(friendId: String): Result<Unit> {
        val federation = authService.getFederation() ?: return Result.failure(Exception("No federation"))
        val url = "https://$federation/v1/friendship/$friendId"
        return try {
            val response = client.delete(url) { getHeaders(this) }
            if (response.status == HttpStatusCode.Unauthorized) {
                if (authService.refreshToken().isSuccess) return removeFriend(friendId)
            }
            if (response.status.isSuccess()) {
                // Update cache
                val current = offlineManager.getCachedFriends() ?: emptyList()
                offlineManager.cacheFriends(current.filter { it.id != friendId })
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove friend: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateCachedSystem(system: SystemResponse) {
        val currentUser = authService.userMe.value
        if (currentUser != null) {
            val updatedUser = currentUser.copy(system = system)
            authService.updateUserMe(updatedUser)
            offlineManager.cacheUserMe(updatedUser)
        }
    }

    fun triggerSync() {
        offlineManager.triggerSync()
    }
}
