package space.ourmosaic.app.offline

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.ourmosaic.app.system.CustomField
import space.ourmosaic.app.system.MemberResponse
import space.ourmosaic.app.system.FrontSession
import space.ourmosaic.app.system.UpdateCustomFieldDefinitionDto

@Serializable
enum class PendingActionType(val idPrefix: String) {
    CREATE_MEMBER("crt_mem"),
    UPDATE_MEMBER("upd_mem"),
    DELETE_MEMBER("del_mem"),
    UPDATE_MEMBER_FIELD("fld"),
    UPDATE_SYSTEM("upd_sys"),
    START_FRONT("front_start"),
    END_FRONT("front_end"),
    CREATE_GROUP("crt_grp"),
    UPDATE_GROUP("upd_grp"),
    DELETE_GROUP("del_grp"),
    UPDATE_MEMBER_GROUPS("upd_mem_grps"),
    UPLOAD_AVATAR("upd_avt"),
    SYNC_FRONT_SESSIONS("sync_front"),
    CREATE_CUSTOM_FIELD("crt_cf"),
    UPDATE_CUSTOM_FIELD("upd_cf"),
    DELETE_CUSTOM_FIELD("del_cf")
}

@Serializable
data class PendingAction(
    val id: String,
    val type: PendingActionType,
    val memberId: String? = null,
    val fieldId: String? = null,
    val sessionId: String? = null,
    val jsonPayload: String,
    val timestamp: Long = 0L
)

@Serializable
data class SyncError(
    val id: String,
    val actionType: PendingActionType,
    val message: String,
    val timestamp: Long
)

class OfflineManager(private val settings: Settings = Settings()) {
    private val json = Json { ignoreUnknownKeys = true }
    
    private val ACTIONS_KEY = "offline_pending_actions"
    private val ERRORS_KEY = "offline_sync_errors"
    private val FIELDS_CACHE_KEY = "cached_custom_fields"
    private val MEMBERS_CACHE_KEY = "cached_members"
    private val GROUPS_CACHE_KEY = "cached_groups"
    private val FRONT_SESSIONS_CACHE_KEY = "cached_front_sessions"
    private val ID_MAPPING_KEY = "id_mapping"
    private val USER_ME_CACHE_KEY = "cached_user_me"
    private val FRIENDS_CACHE_KEY = "cached_friends"
    private val SENT_REQUESTS_CACHE_KEY = "cached_sent_requests"
    private val RECEIVED_REQUESTS_CACHE_KEY = "cached_received_requests"
    private val BLOCKED_USERS_CACHE_KEY = "cached_blocked_users"
    private val BLOCKED_MEMBERS_CACHE_KEY = "cached_blocked_members"
    private val BLOCKED_SYSTEMS_CACHE_KEY = "cached_blocked_systems"
    private val CHAT_CHANNELS_CACHE_KEY = "cached_chat_channels"
    private val CHAT_MESSAGES_CACHE_PREFIX = "cached_chat_messages_"

    private val _pendingActions = MutableStateFlow(getPendingActions())
    private val _pendingActionsCount = MutableStateFlow(_pendingActions.value.size)
    val pendingActionsCount: StateFlow<Int> = _pendingActionsCount.asStateFlow()

    private val _syncErrors = MutableStateFlow(getSyncErrors())
    val syncErrors: StateFlow<List<SyncError>> = _syncErrors.asStateFlow()

    private val _syncTrigger = MutableStateFlow(0L)
    val syncTrigger: StateFlow<Long> = _syncTrigger.asStateFlow()

    private val _idMappings = MutableStateFlow(getIdMappings())
    val idMappings: StateFlow<Map<String, String>> = _idMappings.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<space.ourmosaic.app.system.BlockedUserResponse>?>(null)
    val cachedBlockedUsers: Flow<List<space.ourmosaic.app.system.BlockedUserResponse>> = _blockedUsers
        .map { it ?: getCachedBlockedUsers() ?: emptyList() }
        .distinctUntilChanged()

    private val _blockedMembers = MutableStateFlow<List<space.ourmosaic.app.system.BlockedMemberResponse>?>(null)
    val cachedBlockedMembers: Flow<List<space.ourmosaic.app.system.BlockedMemberResponse>> = _blockedMembers
        .map { it ?: getCachedBlockedMembers() ?: emptyList() }
        .distinctUntilChanged()

    private val _blockedSystems = MutableStateFlow<List<space.ourmosaic.app.system.BlockedSystemResponse>?>(null)
    val cachedBlockedSystems: Flow<List<space.ourmosaic.app.system.BlockedSystemResponse>> = _blockedSystems
        .map { it ?: getCachedBlockedSystems() ?: emptyList() }
        .distinctUntilChanged()

    private val _serverFriends = MutableStateFlow<List<space.ourmosaic.app.system.SystemResponse>?>(null)
    val cachedFriends: Flow<List<space.ourmosaic.app.system.SystemResponse>> = _serverFriends
        .map { it ?: getCachedFriends() ?: emptyList() }
        .distinctUntilChanged()

    private val _serverSentRequests = MutableStateFlow<List<space.ourmosaic.app.system.FriendRequestResponse>?>(null)
    val cachedSentRequests: Flow<List<space.ourmosaic.app.system.FriendRequestResponse>> = _serverSentRequests
        .map { it ?: getCachedSentRequests() ?: emptyList() }
        .distinctUntilChanged()

    private val _serverReceivedRequests = MutableStateFlow<List<space.ourmosaic.app.system.FriendRequestResponse>?>(null)
    val cachedReceivedRequests: Flow<List<space.ourmosaic.app.system.FriendRequestResponse>> = _serverReceivedRequests
        .map { it ?: getCachedReceivedRequests() ?: emptyList() }
        .distinctUntilChanged()

    private val _serverFrontSessions = MutableStateFlow<List<FrontSession>?>(null)
    val cachedFrontSessions: Flow<List<FrontSession>?> = combine(_serverFrontSessions, _pendingActions, _idMappings) { serverSessions, actions, mappings ->
        val sessions = serverSessions ?: getCachedFrontSessions() ?: emptyList()
        
        // Use a set for faster lookup of local IDs that already have a server representation
        val localIdsRepresentedByServer = sessions.mapNotNull { mappings[it.id] }.toSet()

        // Filter out local "seed" sessions if their server-side UUID version is already in the cache
        val result = sessions.filter { s ->
            !s.id.startsWith("front_start") || !localIdsRepresentedByServer.contains(s.id)
        }.toMutableList()
        
        val frontActions = actions.filter { it.type == PendingActionType.START_FRONT || it.type == PendingActionType.END_FRONT }
            .sortedBy { it.timestamp }
        
        val systemId = settings.getStringOrNull("system_id") ?: ""
        
        frontActions.forEach { action ->
            val memberId = resolveId(action.memberId, mappings) ?: return@forEach
            if (action.type == PendingActionType.START_FRONT) {
                // Check if this session is already present (by local ID or its server mapping)
                val isAlreadyPresent = result.any { s -> 
                    s.id == action.id || mappings[s.id] == action.id
                }
                
                if (!isAlreadyPresent) {
                    result.add(FrontSession(
                        id = action.id,
                        memberId = memberId,
                        systemId = systemId,
                        startTime = Instant.fromEpochMilliseconds(action.timestamp).toString(),
                        endTime = null
                    ))
                }
            } else if (action.type == PendingActionType.END_FRONT) {
                val targetSessionId = resolveId(action.sessionId, mappings)
                
                // 1. Mark existing sessions as ended
                var found = false
                if (targetSessionId != null) {
                    // Match by exact ID (local or server) or via mapping in both directions
                    val indices = result.indices.filter { i ->
                        val s = result[i]
                        s.id == targetSessionId || 
                        mappings[s.id] == targetSessionId ||
                        (s.id.startsWith("front_start") && mappings[targetSessionId] == s.id)
                    }
                    
                    if (indices.isNotEmpty()) {
                        found = true
                        indices.forEach { idx ->
                            result[idx] = result[idx].copy(endTime = Instant.fromEpochMilliseconds(action.timestamp).toString())
                        }
                    }
                }
                
                // 2. Fallback: If not found by ID or no ID provided, mark all active sessions for this member as ended
                if (!found) {
                    result.indices.filter { result[it].memberId == memberId && result[it].endTime == null }
                        .forEach { idx ->
                            val session = result[idx]
                            val sessionStartTime = try { 
                                Instant.parse(session.startTime).toEpochMilliseconds() 
                            } catch (e: Exception) { 0L }
                            
                            // Only end sessions that started before or at the time of this end action
                            if (sessionStartTime <= action.timestamp) {
                                result[idx] = session.copy(endTime = Instant.fromEpochMilliseconds(action.timestamp).toString())
                            }
                        }
                }
            }
        }
        
        // Multi-front session support:
        // We no longer enforce a "one active session per member" sanity check here because 
        // the server now supports multiple active sessions if explicitly started.
        // However, we still want to keep the logic that prevents redundant local seeds.
        result.toList()
    }.distinctUntilChanged()

    private val _serverMembers = MutableStateFlow<List<MemberResponse>?>(null)
    val cachedMembers: Flow<List<MemberResponse>?> = combine(_serverMembers, cachedFrontSessions, _pendingActions, _idMappings) { members, frontSessions, actions, mappings ->
        val baseMembers = members ?: getCachedMembers() ?: emptyList()
        val sessions = frontSessions ?: emptyList()
        
        // Identify local IDs that are already represented by a server response
        val localIdsRepresentedByServer = baseMembers.mapNotNull { mappings[it.id] }.toSet()

        val result = baseMembers.map { member ->
            // Use the already-calculated sessions from cachedFrontSessions
            val memberSessions = sessions.filter { it.memberId == member.id }
            member.copy(currentFrontSessions = memberSessions)
        }.toMutableList()
        
        // Optimistic creation
        actions.filter { it.type == PendingActionType.CREATE_MEMBER }.forEach { action ->
            try {
                val dto = json.decodeFromString(space.ourmosaic.app.system.CreateMemberDto.serializer(), action.jsonPayload)
                
                // Content-based de-duplication: check if a server member with same name/pronouns already exists
                val isDuplicateByContent = result.any { 
                    !it.id.contains("_") && it.name == dto.name && it.pronouns == dto.pronouns
                }

                if (result.none { it.id == action.id } && !localIdsRepresentedByServer.contains(action.id) && !isDuplicateByContent) {
                    result.add(MemberResponse(
                        id = action.id,
                        name = dto.name,
                        pronouns = dto.pronouns,
                        inDormancy = dto.inDormancy,
                        privacy = dto.privacy ?: space.ourmosaic.app.system.PrivacyLevel.PRIVATE,
                        description = dto.description,
                        role = dto.role,
                        createdAt = Instant.fromEpochMilliseconds(action.timestamp).toString(),
                        updatedAt = Instant.fromEpochMilliseconds(action.timestamp).toString(),
                        systemId = settings.getStringOrNull("system_id") ?: "",
                        color = dto.color
                    ))
                }
            } catch (e: Exception) {}
        }

        // Optimistic update
        actions.filter { it.type == PendingActionType.UPDATE_MEMBER }.forEach { action ->
            try {
                val dto = json.decodeFromString(space.ourmosaic.app.system.UpdateMemberDto.serializer(), action.jsonPayload)
                val memberId = resolveId(action.memberId, mappings)
                val index = result.indexOfFirst { it.id == memberId }
                if (index != -1) {
                    val current = result[index]
                    result[index] = current.copy(
                        name = dto.name ?: current.name,
                        pronouns = dto.pronouns ?: current.pronouns,
                        description = dto.description ?: current.description,
                        role = dto.role ?: current.role,
                        inDormancy = dto.inDormancy ?: current.inDormancy,
                        privacy = dto.privacy ?: current.privacy,
                        color = dto.color ?: current.color
                    )
                }
            } catch (e: Exception) {}
        }

        // Optimistic deletion
        actions.filter { it.type == PendingActionType.DELETE_MEMBER }.forEach { action ->
            val memberId = resolveId(action.memberId, mappings)
            result.removeAll { it.id == memberId }
        }

        // Optimistic member field update
        actions.filter { it.type == PendingActionType.UPDATE_MEMBER_FIELD }.forEach { action ->
            val memberId = resolveId(action.memberId, mappings)
            val index = result.indexOfFirst { it.id == memberId }
            val fieldId = resolveId(action.fieldId, mappings)
            if (index != -1 && fieldId != null) {
                val current = result[index]
                val currentFields = current.customFieldValues.toMutableList()
                val fieldIndex = currentFields.indexOfFirst { it.customFieldId == fieldId }
                
                // action.jsonPayload is a raw string for UPDATE_MEMBER_FIELD, not a JSON object
                val newValue = action.jsonPayload

                if (fieldIndex != -1) {
                    currentFields[fieldIndex] = currentFields[fieldIndex].copy(value = newValue)
                } else {
                    currentFields.add(space.ourmosaic.app.system.CustomFieldValueResponse(
                        value = newValue,
                        customFieldId = fieldId
                    ))
                }
                result[index] = current.copy(customFieldValues = currentFields)
            }
        }

        // Optimistic member groups update
        actions.filter { it.type == PendingActionType.UPDATE_MEMBER_GROUPS }.forEach { action ->
            try {
                val dto = json.decodeFromString(space.ourmosaic.app.system.UpdateMemberGroupsDto.serializer(), action.jsonPayload)
                val memberId = resolveId(action.memberId, mappings)
                val index = result.indexOfFirst { it.id == memberId }
                if (index != -1) {
                    val current = result[index]
                    result[index] = current.copy(
                        groups = dto.groupIds.map { space.ourmosaic.app.system.MemberGroupLink(resolveId(it, mappings)!!) }
                    )
                }
            } catch (e: Exception) {}
        }

        // Optimistic avatar update
        actions.filter { it.type == PendingActionType.UPLOAD_AVATAR && it.memberId != null && it.memberId != "@me" }.forEach { action ->
            val memberId = resolveId(action.memberId, mappings)
            val index = result.indexOfFirst { it.id == memberId }
            if (index != -1) {
                val current = result[index]
                // We use a special prefix to signal the UI/Image Loader to look in local cache
                result[index] = current.copy(avatarUrl = "cache://${action.jsonPayload}")
            }
        }

        result
    }.distinctUntilChanged()

    private val _serverUserMe = MutableStateFlow<space.ourmosaic.app.auth.UserMeResponse?>(null)
    val cachedUserMe: Flow<space.ourmosaic.app.auth.UserMeResponse?> = combine(_serverUserMe, _pendingActions) { user, actions ->
        var result = user ?: return@combine null

        // Optimistic system update
        actions.filter { it.type == PendingActionType.UPDATE_SYSTEM }.forEach { action ->
            try {
                val dto = json.decodeFromString(space.ourmosaic.app.system.UpdateSystemDto.serializer(), action.jsonPayload)
                val currentSystem = result.system
                if (currentSystem != null) {
                    result = result.copy(
                        system = currentSystem.copy(
                            customName = dto.customName ?: currentSystem.customName,
                            description = dto.description ?: currentSystem.description,
                            color = dto.color ?: currentSystem.color
                        )
                    )
                }
            } catch (e: Exception) {}
        }

        // Optimistic system avatar update
        actions.filter { it.type == PendingActionType.UPLOAD_AVATAR && it.memberId == "@me" }.forEach { action ->
            val currentSystem = result.system
            if (currentSystem != null) {
                result = result.copy(
                    system = currentSystem.copy(avatarUrl = "cache://${action.jsonPayload}")
                )
            }
        }

        result
    }.distinctUntilChanged()

    private val _serverGroups = MutableStateFlow<List<space.ourmosaic.app.system.MemberGroup>?>(null)
    val cachedGroups: Flow<List<space.ourmosaic.app.system.MemberGroup>?> = combine(_serverGroups, _pendingActions, _idMappings) { groups, actions, mappings ->
        val baseGroups = groups ?: getCachedGroups() ?: emptyList()
        val result = baseGroups.toMutableList()
        
        val localIdsRepresentedByServer = baseGroups.mapNotNull { mappings[it.id] }.toSet()

        // Optimistic creation
        actions.filter { it.type == PendingActionType.CREATE_GROUP }.forEach { action ->
            try {
                val dto = json.decodeFromString(space.ourmosaic.app.system.CreateGroupDto.serializer(), action.jsonPayload)
                
                // Content-based de-duplication: check if a server group with same name and parent exists
                val resolvedParentId = resolveId(dto.parentId, mappings)
                val isDuplicateByContent = result.any {
                    !it.id.contains("_") && it.name == dto.name && it.parentId == resolvedParentId
                }

                if (result.none { it.id == action.id } && !localIdsRepresentedByServer.contains(action.id) && !isDuplicateByContent) {
                    result.add(space.ourmosaic.app.system.MemberGroup(
                        id = action.id,
                        name = dto.name,
                        color = dto.color,
                        icon = dto.icon,
                        parentId = resolveId(dto.parentId, mappings),
                        systemId = settings.getStringOrNull("system_id") ?: "",
                        createdAt = Instant.fromEpochMilliseconds(action.timestamp).toString()
                    ))
                }
            } catch (e: Exception) {}
        }

        // Optimistic update
        actions.filter { it.type == PendingActionType.UPDATE_GROUP }.forEach { action ->
            try {
                val dto = json.decodeFromString(space.ourmosaic.app.system.CreateGroupDto.serializer(), action.jsonPayload)
                val groupId = resolveId(action.memberId, mappings)
                val index = result.indexOfFirst { it.id == groupId }
                if (index != -1) {
                    val current = result[index]
                    result[index] = current.copy(
                        name = dto.name ?: current.name,
                        color = dto.color ?: current.color,
                        icon = dto.icon ?: current.icon,
                        parentId = resolveId(dto.parentId, mappings) ?: current.parentId
                    )
                }
            } catch (e: Exception) {}
        }

        // Optimistic deletion
        actions.filter { it.type == PendingActionType.DELETE_GROUP }.forEach { action ->
            val groupId = resolveId(action.memberId, mappings)
            result.removeAll { it.id == groupId }
        }

        result
    }.distinctUntilChanged()

    private val _serverCustomFields = MutableStateFlow<List<CustomField>?>(null)
    val cachedCustomFields: Flow<List<CustomField>?> = combine(_serverCustomFields, _pendingActions, _idMappings) { fields, actions, mappings ->
        val baseFields = fields ?: getCachedCustomFields() ?: emptyList()
        val result = baseFields.toMutableList()
        
        val localIdsRepresentedByServer = baseFields.mapNotNull { mappings[it.id] }.toSet()

        // Optimistic creation
        actions.filter { it.type == PendingActionType.CREATE_CUSTOM_FIELD }.forEach { action ->
            if (result.none { it.id == action.id } && !localIdsRepresentedByServer.contains(action.id)) {
                try {
                    val dto = if (action.jsonPayload != "{}") {
                        json.decodeFromString(UpdateCustomFieldDefinitionDto.serializer(), action.jsonPayload)
                    } else null
                    
                    // Content-based de-duplication: avoid duplicates if a server field with same name/type already exists
                    val isDuplicateByContent = dto != null && result.any {
                        !it.id.contains("_") && it.name == dto.name && it.type == dto.type
                    }

                    if (isDuplicateByContent) return@forEach

                    val maxOrder = result.maxOfOrNull { it.order } ?: -1
                    result.add(CustomField(
                        id = action.id,
                        name = dto?.name ?: "New Field",
                        type = dto?.type ?: space.ourmosaic.app.system.FieldType.STRING,
                        order = dto?.order ?: (maxOrder + 1),
                        privacy = dto?.privacy ?: space.ourmosaic.app.system.PrivacyLevel.PRIVATE,
                        systemId = settings.getStringOrNull("system_id") ?: ""
                    ))
                } catch (e: Exception) {
                    // Fallback if decoding fails
                    val maxOrder = result.maxOfOrNull { it.order } ?: -1
                    result.add(CustomField(
                        id = action.id,
                        name = "New Field",
                        type = space.ourmosaic.app.system.FieldType.STRING,
                        order = maxOrder + 1,
                        privacy = space.ourmosaic.app.system.PrivacyLevel.PRIVATE,
                        systemId = settings.getStringOrNull("system_id") ?: ""
                    ))
                }
            }
        }

        // Optimistic update
        actions.filter { it.type == PendingActionType.UPDATE_CUSTOM_FIELD }.forEach { action ->
            try {
                val dto = json.decodeFromString(space.ourmosaic.app.system.UpdateCustomFieldDefinitionDto.serializer(), action.jsonPayload)
                val fieldId = resolveId(action.fieldId, mappings)
                val index = result.indexOfFirst { it.id == fieldId }
                if (index != -1) {
                    val current = result[index]
                    result[index] = current.copy(
                        name = dto.name ?: current.name,
                        type = dto.type ?: current.type,
                        order = dto.order ?: current.order,
                        privacy = dto.privacy ?: current.privacy
                    )
                }
            } catch (e: Exception) {
                space.ourmosaic.app.utils.Logger.e("OfflineManager", "Error applying optimistic update to custom field: ${e.message}")
            }
        }

        // Optimistic delete
        actions.filter { it.type == PendingActionType.DELETE_CUSTOM_FIELD }.forEach { action ->
            val fieldId = resolveId(action.fieldId, mappings)
            result.removeAll { it.id == fieldId }
        }

        result
    }.distinctUntilChanged()

    private val _isImporting = MutableStateFlow(settings.getBoolean("is_importing", false))
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun setImporting(importing: Boolean) {
        settings["is_importing"] = importing
        _isImporting.value = importing
    }

    init {
        _serverMembers.value = getCachedMembers()
        _serverUserMe.value = getCachedUserMe()
        
        // Ensure system_id is set if we have a cached user
        _serverUserMe.value?.system?.id?.let { 
            if (settings.getStringOrNull("system_id") == null) {
                settings["system_id"] = it
            }
        }
        
        _serverFrontSessions.value = getCachedFrontSessions()
        _serverGroups.value = getCachedGroups()
        _serverCustomFields.value = getCachedCustomFields()
        _serverFriends.value = getCachedFriends()
        _serverSentRequests.value = getCachedSentRequests()
        _serverReceivedRequests.value = getCachedReceivedRequests()
    }

    fun triggerSync() {
        _syncTrigger.value = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        _pendingActionsCount.value = getPendingActions().size
    }

    fun queueAction(action: PendingAction, trigger: Boolean = true) {
        space.ourmosaic.app.utils.Logger.d("OfflineManager", "Queueing action: Type=${action.type}, ID=${action.id}")
        val current = getPendingActions().toMutableList()
        current.add(action)
        saveActions(current)
        _pendingActions.value = current
        _pendingActionsCount.value = current.size
        if (trigger) triggerSync()
    }

    fun getPendingActions(): List<PendingAction> {
        val raw = settings.getStringOrNull(ACTIONS_KEY) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(PendingAction.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeAction(actionId: String) {
        val current = getPendingActions().filter { it.id != actionId }
        saveActions(current)
        _pendingActions.value = current
        _pendingActionsCount.value = current.size
    }

    fun reportSyncError(error: SyncError) {
        val current = getSyncErrors().toMutableList()
        current.add(error)
        saveSyncErrors(current)
        _syncErrors.value = current
    }

    fun dismissSyncError(errorId: String) {
        val current = getSyncErrors().filter { it.id != errorId }
        saveSyncErrors(current)
        _syncErrors.value = current
    }

    fun getSyncErrors(): List<SyncError> {
        val raw = settings.getStringOrNull(ERRORS_KEY) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(SyncError.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSyncErrors(errors: List<SyncError>) {
        settings[ERRORS_KEY] = json.encodeToString(ListSerializer(SyncError.serializer()), errors)
    }

    fun removeActions(actionIds: List<String>) {
        if (actionIds.isEmpty()) return
        val current = getPendingActions().filter { it.id !in actionIds }
        saveActions(current)
        _pendingActions.value = current
        _pendingActionsCount.value = current.size
    }

    private fun saveActions(actions: List<PendingAction>) {
        settings[ACTIONS_KEY] = json.encodeToString(ListSerializer(PendingAction.serializer()), actions)
    }

    fun cacheCustomFields(fields: List<CustomField>) {
        settings[FIELDS_CACHE_KEY] = json.encodeToString(ListSerializer(CustomField.serializer()), fields)
        _serverCustomFields.value = fields
    }

    fun getCachedCustomFields(): List<CustomField>? {
        val raw = settings.getStringOrNull(FIELDS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(CustomField.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheMembers(members: List<MemberResponse>) {
        settings[MEMBERS_CACHE_KEY] = json.encodeToString(ListSerializer(MemberResponse.serializer()), members)
        _serverMembers.value = members
    }

    fun cacheUserMe(user: space.ourmosaic.app.auth.UserMeResponse) {
        settings[USER_ME_CACHE_KEY] = json.encodeToString(space.ourmosaic.app.auth.UserMeResponse.serializer(), user)
        
        // Sync system_id for other services
        user.system?.id?.let { settings["system_id"] = it }

        _serverUserMe.value = user
    }

    fun getCachedUserMe(): space.ourmosaic.app.auth.UserMeResponse? {
        val raw = settings.getStringOrNull(USER_ME_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(space.ourmosaic.app.auth.UserMeResponse.serializer(), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun getCachedMembers(): List<MemberResponse>? {
        val raw = settings.getStringOrNull(MEMBERS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(MemberResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheGroups(groups: List<space.ourmosaic.app.system.MemberGroup>) {
        settings[GROUPS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.MemberGroup.serializer()), groups)
        _serverGroups.value = groups
    }

    fun getCachedGroups(): List<space.ourmosaic.app.system.MemberGroup>? {
        val raw = settings.getStringOrNull(GROUPS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.MemberGroup.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheFriends(friends: List<space.ourmosaic.app.system.SystemResponse>) {
        settings[FRIENDS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.SystemResponse.serializer()), friends)
        _serverFriends.value = friends
    }

    fun getCachedFriends(): List<space.ourmosaic.app.system.SystemResponse>? {
        val raw = settings.getStringOrNull(FRIENDS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.SystemResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheSentRequests(requests: List<space.ourmosaic.app.system.FriendRequestResponse>) {
        settings[SENT_REQUESTS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.FriendRequestResponse.serializer()), requests)
        _serverSentRequests.value = requests
    }

    fun getCachedSentRequests(): List<space.ourmosaic.app.system.FriendRequestResponse>? {
        val raw = settings.getStringOrNull(SENT_REQUESTS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.FriendRequestResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheReceivedRequests(requests: List<space.ourmosaic.app.system.FriendRequestResponse>) {
        settings[RECEIVED_REQUESTS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.FriendRequestResponse.serializer()), requests)
        _serverReceivedRequests.value = requests
    }

    fun getCachedReceivedRequests(): List<space.ourmosaic.app.system.FriendRequestResponse>? {
        val raw = settings.getStringOrNull(RECEIVED_REQUESTS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.FriendRequestResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheBlockedUsers(users: List<space.ourmosaic.app.system.BlockedUserResponse>) {
        settings[BLOCKED_USERS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.BlockedUserResponse.serializer()), users)
        _blockedUsers.value = users
    }

    fun getCachedBlockedUsers(): List<space.ourmosaic.app.system.BlockedUserResponse>? {
        val raw = settings.getStringOrNull(BLOCKED_USERS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.BlockedUserResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheBlockedMembers(members: List<space.ourmosaic.app.system.BlockedMemberResponse>) {
        settings[BLOCKED_MEMBERS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.BlockedMemberResponse.serializer()), members)
        _blockedMembers.value = members
    }

    fun getCachedBlockedMembers(): List<space.ourmosaic.app.system.BlockedMemberResponse>? {
        val raw = settings.getStringOrNull(BLOCKED_MEMBERS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.BlockedMemberResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheBlockedSystems(systems: List<space.ourmosaic.app.system.BlockedSystemResponse>) {
        settings[BLOCKED_SYSTEMS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.BlockedSystemResponse.serializer()), systems)
        _blockedSystems.value = systems
    }

    fun getCachedBlockedSystems(): List<space.ourmosaic.app.system.BlockedSystemResponse>? {
        val raw = settings.getStringOrNull(BLOCKED_SYSTEMS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.BlockedSystemResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheFrontSessions(sessions: List<FrontSession>, markOthersAsEnded: Boolean = false) {
        val current = getCachedFrontSessions() ?: emptyList()
        val mappings = getIdMappings()
        
        // Merge strategy: prioritize ended sessions and server IDs.
        // This prevents stale server responses (still "active") from overwriting local "ended" state.
        val all = current + sessions
        
        // Map everything to their canonical ID first to ensure grouping works
        val grouped = all.groupBy { s ->
            // Canonical ID is the server UUID.
            mappings.entries.find { it.value == s.id }?.key ?: s.id
        }

        var merged = grouped.map { (canonicalId, group) ->
            // In each group (representing the same session):
            // 1. Prefer an ended version (once a session is ended, it stays ended)
            // 2. Prefer a version with a server UUID (doesn't start with front_start)
            val ended = group.find { it.endTime != null }
            val serverVersion = group.find { !it.id.startsWith("front_start") && !it.id.startsWith("front_end") }
            
            (ended ?: serverVersion ?: group.first()).copy(id = serverVersion?.id ?: canonicalId)
        }

        if (markOthersAsEnded) {
            val serverActiveIds = sessions.filter { it.endTime == null }.map { it.id }.toSet()
            
            merged = merged.map { s ->
                if (s.endTime == null) {
                    val cid = if (!s.id.startsWith("front_start")) s.id else mappings.entries.find { it.value == s.id }?.key
                    
                    // If we have a server ID and it's NOT in the list of active sessions from the server,
                    // it means the session has ended on the server side.
                    if (cid != null && !serverActiveIds.contains(cid)) {
                        s.copy(endTime = Clock.System.now().toString())
                    } else s
                } else s
            }
        }

        settings[FRONT_SESSIONS_CACHE_KEY] = json.encodeToString(ListSerializer(FrontSession.serializer()), merged)
        _serverFrontSessions.value = merged
    }

    fun cacheChatChannels(channels: List<space.ourmosaic.app.system.ChatChannelResponse>) {
        settings[CHAT_CHANNELS_CACHE_KEY] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.ChatChannelResponse.serializer()), channels)
    }

    fun getCachedChatChannels(): List<space.ourmosaic.app.system.ChatChannelResponse>? {
        val raw = settings.getStringOrNull(CHAT_CHANNELS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.ChatChannelResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheChatMessages(channelId: String, messages: List<space.ourmosaic.app.system.ChatMessageResponse>) {
        settings[CHAT_MESSAGES_CACHE_PREFIX + channelId] = json.encodeToString(ListSerializer(space.ourmosaic.app.system.ChatMessageResponse.serializer()), messages)
    }

    fun getCachedChatMessages(channelId: String): List<space.ourmosaic.app.system.ChatMessageResponse>? {
        val raw = settings.getStringOrNull(CHAT_MESSAGES_CACHE_PREFIX + channelId) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.ChatMessageResponse.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun getTotalMessagesCount(): Int {
        val channels = getCachedChatChannels() ?: return 0
        return channels.sumOf { getCachedChatMessages(it.id)?.size ?: 0 }
    }

    fun getEstimatedCacheSize(): Long {
        var totalSize = 0L
        val keys = listOf(
            ACTIONS_KEY, ERRORS_KEY, FIELDS_CACHE_KEY, MEMBERS_CACHE_KEY,
            GROUPS_CACHE_KEY, FRONT_SESSIONS_CACHE_KEY, ID_MAPPING_KEY,
            USER_ME_CACHE_KEY, FRIENDS_CACHE_KEY, SENT_REQUESTS_CACHE_KEY,
            RECEIVED_REQUESTS_CACHE_KEY, BLOCKED_USERS_CACHE_KEY,
            BLOCKED_MEMBERS_CACHE_KEY, BLOCKED_SYSTEMS_CACHE_KEY,
            CHAT_CHANNELS_CACHE_KEY
        )
        
        for (key in keys) {
            totalSize += settings.getStringOrNull(key)?.length?.toLong() ?: 0L
        }

        // Add chat messages
        getCachedChatChannels()?.forEach { channel ->
            totalSize += settings.getStringOrNull(CHAT_MESSAGES_CACHE_PREFIX + channel.id)?.length?.toLong() ?: 0L
        }

        return totalSize
    }

    fun getCachedFrontSessions(): List<space.ourmosaic.app.system.FrontSession>? {
        val raw = settings.getStringOrNull(FRONT_SESSIONS_CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(ListSerializer(space.ourmosaic.app.system.FrontSession.serializer()), raw)
        } catch (e: Exception) {
            null
        }
    }

    fun saveIdMapping(serverId: String, localId: String) {
        val current = getIdMappings().toMutableMap()
        current[serverId] = localId
        settings[ID_MAPPING_KEY] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), current)
        _idMappings.value = current
    }

    fun getLocalId(serverId: String): String? {
        return getIdMappings()[serverId]
    }

    fun getServerId(localId: String?): String? {
        if (localId == null) return null
        // If it doesn't look like a local ID (doesn't contain our prefix underscore), return as is
        if (!localId.contains("_")) return localId
        
        val mappings = getIdMappings()
        // mappings is Map<serverId, localId>
        return mappings.entries.find { it.value == localId }?.key ?: localId
    }

    fun getIdMappings(): Map<String, String> {
        val raw = settings.getStringOrNull(ID_MAPPING_KEY) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun clearAllData() {
        settings.remove(ACTIONS_KEY)
        settings.remove(ERRORS_KEY)
        settings.remove(FIELDS_CACHE_KEY)
        settings.remove(MEMBERS_CACHE_KEY)
        settings.remove(GROUPS_CACHE_KEY)
        settings.remove(FRONT_SESSIONS_CACHE_KEY)
        settings.remove(ID_MAPPING_KEY)
        settings.remove(USER_ME_CACHE_KEY)
        settings.remove(FRIENDS_CACHE_KEY)
        settings.remove(SENT_REQUESTS_CACHE_KEY)
        settings.remove(RECEIVED_REQUESTS_CACHE_KEY)
        settings.remove("is_importing")
        
        _pendingActions.value = emptyList()
        _pendingActionsCount.value = 0
        _syncErrors.value = emptyList()
        _serverFrontSessions.value = null
        _serverMembers.value = null
        _serverUserMe.value = null
        _serverGroups.value = null
        _serverCustomFields.value = null
        _serverFriends.value = null
        _serverSentRequests.value = null
        _serverReceivedRequests.value = null
        _isImporting.value = false
    }

    fun getRawJson(key: String): String? {
        val fullKey = when (key) {
            "members" -> MEMBERS_CACHE_KEY
            "groups" -> GROUPS_CACHE_KEY
            "fields" -> FIELDS_CACHE_KEY
            "sessions" -> FRONT_SESSIONS_CACHE_KEY
            "friends" -> FRIENDS_CACHE_KEY
            "sent_requests" -> SENT_REQUESTS_CACHE_KEY
            "received_requests" -> RECEIVED_REQUESTS_CACHE_KEY
            "blocked_users" -> BLOCKED_USERS_CACHE_KEY
            "blocked_members" -> BLOCKED_MEMBERS_CACHE_KEY
            "blocked_systems" -> BLOCKED_SYSTEMS_CACHE_KEY
            "channels" -> CHAT_CHANNELS_CACHE_KEY
            "actions" -> ACTIONS_KEY
            "mappings" -> ID_MAPPING_KEY
            else -> if (key.startsWith("messages_")) CHAT_MESSAGES_CACHE_PREFIX + key.removePrefix("messages_") else null
        }
        return if (fullKey != null) settings.getStringOrNull(fullKey) else null
    }

    private fun resolveId(id: String?, mappings: Map<String, String>): String? {
        if (id == null) return null
        // mappings is serverId -> localId
        return mappings.entries.find { it.value == id }?.key ?: id
    }
}
