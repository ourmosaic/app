package space.ourmosaic.app.system

import space.ourmosaic.app.utils.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.offline.PendingActionType
import kotlinx.coroutines.*
import kotlinx.datetime.Instant

class SyncWorker(
    private val systemService: SystemService,
    private val offlineManager: OfflineManager,
    private val authService: AuthService
) {
    private val TAG = "SyncWorker"
    private var isSyncing = false
    private val json = Json { ignoreUnknownKeys = true }
    private var syncJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (syncJob?.isActive == true) return
        
        syncJob = scope.launch {
            offlineManager.syncTrigger.collect {
                if (authService.getAccessToken() != null) {
                    sync()
                }
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun resolveServerId(id: String?): String? {
        return offlineManager.getServerId(id)
    }
    
    /**
     * Replaces any occurrence of a temporary ID with its corresponding server UUID in a JSON string.
     */
    private fun resolveIdsInJson(jsonString: String): String {
        val mappings = offlineManager.getIdMappings()
        if (mappings.isEmpty()) return jsonString
        
        var result = jsonString
        // Mappings are serverId -> localId. We want to find localId and replace with serverId.
        for ((serverId, localId) in mappings) {
            if (localId.contains("_")) {
                result = result.replace(localId, serverId)
            }
        }
        return result
    }

    suspend fun sync() {
        // We remove the isSyncing check because start() uses collect, 
        // which ensures sequential execution.
        isSyncing = true
        
        try {
            Logger.d(TAG, "Sync loop started")
            var madeProgress = true
            
            while (madeProgress) {
                madeProgress = false
                val actions = offlineManager.getPendingActions()
                if (actions.isEmpty()) break

                Logger.d(TAG, "Processing ${actions.size} actions in queue")

                // 1. Process everything EXCEPT front sessions first
                // This ensures members, groups, etc. exist on server before we sync sessions referencing them
                for (action in actions) {
                    if (action.type == PendingActionType.START_FRONT || action.type == PendingActionType.END_FRONT) continue
                    
                    val resolvedPayload = resolveIdsInJson(action.jsonPayload)
                    
                    val result: Result<Any> = try {
                        when (action.type) {
                            PendingActionType.CREATE_MEMBER -> {
                                val dto = json.decodeFromString(CreateMemberDto.serializer(), resolvedPayload)
                                systemService.createMember(dto, fromSync = true).map { realMember ->
                                    offlineManager.saveIdMapping(realMember.id, action.id)
                                    val currentMembers = offlineManager.getCachedMembers()?.toMutableList() ?: mutableListOf()
                                    currentMembers.removeAll { it.id == action.id }
                                    if (currentMembers.none { it.id == realMember.id }) {
                                        currentMembers.add(realMember)
                                    }
                                    offlineManager.cacheMembers(currentMembers)
                                    realMember
                                }
                            }
                            PendingActionType.UPDATE_MEMBER -> {
                                val memberId = resolveServerId(action.memberId) ?: throw Exception("Missing memberId")
                                val dto = json.decodeFromString(UpdateMemberDto.serializer(), resolvedPayload)
                                systemService.updateMember(memberId, dto, fromSync = true)
                            }
                            PendingActionType.DELETE_MEMBER -> {
                                val memberId = resolveServerId(action.memberId) ?: throw Exception("Missing memberId")
                                systemService.deleteMember(memberId, fromSync = true)
                            }
                            PendingActionType.UPDATE_MEMBER_FIELD -> {
                                val memberId = resolveServerId(action.memberId) ?: throw Exception("Missing memberId")
                                val fieldId = resolveServerId(action.fieldId) ?: throw Exception("Missing fieldId")
                                systemService.updateMemberField(memberId, fieldId, action.jsonPayload, fromSync = true)
                            }
                            PendingActionType.UPDATE_SYSTEM -> {
                                val dto = json.decodeFromString(UpdateSystemDto.serializer(), resolvedPayload)
                                systemService.updateSystem(dto, fromSync = true)
                            }
                            PendingActionType.CREATE_GROUP -> {
                                val dto = json.decodeFromString(CreateGroupDto.serializer(), resolvedPayload)
                                systemService.createGroup(dto, fromSync = true).map { realGroup ->
                                    offlineManager.saveIdMapping(realGroup.id, action.id)
                                    val currentGroups = offlineManager.getCachedGroups()?.toMutableList() ?: mutableListOf()
                                    currentGroups.removeAll { it.id == action.id }
                                    if (currentGroups.none { it.id == realGroup.id }) {
                                        currentGroups.add(realGroup)
                                    }
                                    offlineManager.cacheGroups(currentGroups)
                                    realGroup
                                }
                            }
                            PendingActionType.UPDATE_GROUP -> {
                                val groupId = resolveServerId(action.memberId) ?: throw Exception("Missing groupId")
                                val dto = json.decodeFromString(CreateGroupDto.serializer(), resolvedPayload)
                                systemService.updateGroup(groupId, dto, fromSync = true)
                            }
                            PendingActionType.DELETE_GROUP -> {
                                val groupId = resolveServerId(action.memberId) ?: throw Exception("Missing groupId")
                                systemService.deleteGroup(groupId, fromSync = true)
                            }
                            PendingActionType.UPDATE_MEMBER_GROUPS -> {
                                val memberId = resolveServerId(action.memberId) ?: throw Exception("Missing memberId")
                                val dto = json.decodeFromString(UpdateMemberGroupsDto.serializer(), resolvedPayload)
                                systemService.updateMemberGroups(memberId, dto.groupIds, fromSync = true)
                            }
                            PendingActionType.UPLOAD_AVATAR -> {
                                val fileName = action.jsonPayload
                                val imageBytes = space.ourmosaic.app.utils.readFromCache(fileName) ?: throw Exception("Cached image not found: $fileName")
                                val targetId = if (action.memberId == "@me") "@me" else resolveServerId(action.memberId)
                                
                                val uploadResult = if (targetId == "@me") {
                                    systemService.uploadSystemAvatar(imageBytes, fromSync = true)
                                } else {
                                    val memberId = targetId ?: throw Exception("Missing memberId")
                                    systemService.uploadMemberAvatar(memberId, imageBytes, fromSync = true)
                                }
                                
                                if (uploadResult.isSuccess) {
                                    space.ourmosaic.app.utils.deleteFromCache(fileName)
                                }
                                uploadResult
                            }
                            PendingActionType.CREATE_CUSTOM_FIELD -> {
                                systemService.createCustomField(fromSync = true).map { realField ->
                                    offlineManager.saveIdMapping(realField.id, action.id)
                                    val currentFields = offlineManager.getCachedCustomFields()?.toMutableList() ?: mutableListOf()
                                    currentFields.removeAll { it.id == action.id }
                                    if (currentFields.none { it.id == realField.id }) {
                                        currentFields.add(realField)
                                    }
                                    offlineManager.cacheCustomFields(currentFields)
                                    realField
                                }
                            }
                            PendingActionType.UPDATE_CUSTOM_FIELD -> {
                                val fieldId = resolveServerId(action.fieldId) ?: throw Exception("Missing fieldId")
                                val dto = json.decodeFromString(UpdateCustomFieldDefinitionDto.serializer(), resolvedPayload)
                                systemService.updateCustomField(fieldId, dto, fromSync = true)
                            }
                            PendingActionType.DELETE_CUSTOM_FIELD -> {
                                val fieldId = resolveServerId(action.fieldId) ?: throw Exception("Missing fieldId")
                                systemService.deleteCustomField(fieldId, fromSync = true)
                            }
                            else -> Result.success(Unit)
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                    if (result.isSuccess) {
                        offlineManager.removeAction(action.id)
                        madeProgress = true
                    } else {
                        val error = result.exceptionOrNull()
                        val msg = error?.message ?: ""
                        Logger.e(TAG, "Sync error for ${action.id}: $msg")
                        
                        val isPermanentError = msg.contains("400") || msg.contains("401") || 
                                               msg.contains("403") || msg.contains("404") || 
                                               msg.contains("405") || msg.contains("422") ||
                                               msg.contains("409") || msg.contains("Conflict") ||
                                               msg.contains("500") || msg.contains("501")

                        
                        if (isPermanentError) {
                            Logger.w(TAG, "Permanent error detected, discarding action.")
                            offlineManager.reportSyncError(
                                space.ourmosaic.app.offline.SyncError(
                                    id = action.id,
                                    actionType = action.type,
                                    message = msg,
                                    timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                )
                            )
                            offlineManager.removeAction(action.id)
                            madeProgress = true
                        } else {
                            // Transient error (network), stop this loop iteration
                            break 
                        }
                    }
                }

                // 2. Process front sessions bulk AFTER everything else
                val remainingActions = offlineManager.getPendingActions()
                val frontActions = remainingActions.filter { it.type == PendingActionType.START_FRONT || it.type == PendingActionType.END_FRONT }
                if (frontActions.isNotEmpty()) {
                    Logger.d(TAG, "Syncing ${frontActions.size} front sessions bulk")
                    val result = systemService.syncFrontSessions()
                    if (result.isSuccess) {
                        madeProgress = true
                        Logger.d(TAG, "Bulk sync successful")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: ""
                        Logger.e(TAG, "Bulk sync failed: $error")
                        break
                    }
                }
            }
        } finally {
            isSyncing = false
            Logger.d(TAG, "Sync loop finished")
        }
    }
}
