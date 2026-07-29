package space.ourmosaic.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.system.*
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.utils.Logger
import space.ourmosaic.app.utils.ColorUtils
import space.ourmosaic.app.components.MosaicAvatar

enum class FrontMode {
    NONE, ADD, SET
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MembersManageScreen(
    systemId: String? = null,
    i18n: I18nState,
    appSettings: AppSettings,
    onBack: () -> Unit,
    onEditMember: (String) -> Unit,
    offlineManager: OfflineManager,
    systemService: SystemService,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var currentGroupId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var showFrontMenu by remember { mutableStateOf(false) }
    var frontMode by remember { mutableStateOf<FrontMode>(FrontMode.NONE) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedMemberIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkTransferDialog by remember { mutableStateOf(false) }
    var showDeleteGroupConfirm by remember { mutableStateOf(false) }
    
    val isSelectionMode = selectedMemberIds.isNotEmpty()
    
    val pendingCount by offlineManager.pendingActionsCount.collectAsState()

    // Back navigation handling
    space.ourmosaic.app.navigation.BackHandler(enabled = currentGroupId != null) {
        val cachedGroupsValue = (offlineManager.getCachedGroups() ?: emptyList())
        val parent = cachedGroupsValue.find { it.id == currentGroupId }?.parentId
        currentGroupId = parent
    }

    val cachedMembers by offlineManager.cachedMembers.collectAsState(initial = emptyList())
    val cachedSessions by offlineManager.cachedFrontSessions.collectAsState(initial = emptyList())
    val cachedGroups by offlineManager.cachedGroups.collectAsState(initial = emptyList())

    val activeSessions = cachedSessions?.filter { it.endTime == null } ?: emptyList()
    val displayGroups = (cachedGroups ?: emptyList())
        .filter { group ->
            val expectedSystemId = systemId ?: appSettings.selectedSystemId
            (group.systemId == expectedSystemId || group.id.startsWith("pending_")) && 
            group.parentId == currentGroupId 
        }
        .sortedBy { it.name?.lowercase() }
    
    val displayMembers = (cachedMembers ?: emptyList()).filter { member ->
        // Priority to data matching the current systemId context
        val expectedSystemId = systemId ?: appSettings.selectedSystemId
        member.systemId == expectedSystemId || member.id.startsWith("pending_")
    }.map { member ->
        member.copy(currentFrontSessions = activeSessions.filter { it.memberId == member.id })
    }.let { membersList ->
        val filtered = if (isSearchActive && searchQuery.isNotBlank()) {
            membersList.filter { it.name.contains(searchQuery, ignoreCase = true) || it.pronouns?.contains(searchQuery, ignoreCase = true) == true }
        } else if (currentGroupId == null) {
            if (appSettings.hideMembersInFoldersAtRoot) {
                membersList.filter { it.groups.isEmpty() }
            } else {
                membersList
            }
        } else {
            membersList.filter { m -> m.groups.any { it.groupId == currentGroupId } }
        }.let { list ->
            if (appSettings.hideDormantMembers) {
                list.filter { !it.inDormancy }
            } else {
                list
            }
        }
        filtered.sortedBy { it.name.lowercase() }
    }

    val currentGroup = cachedGroups?.find { it.id == currentGroupId }

    fun refresh() {
        scope.launch {
            isLoading = true
            offlineManager.resetMemoryCache()
            systemService.getMembers(systemId)
            systemService.getFrontSessions(systemId)
            systemService.getGroups(systemId)
            isLoading = false
        }
    }

    LaunchedEffect(systemId) {
        refresh()
    }

    // Update front notification based on cached data
    LaunchedEffect(cachedMembers, cachedSessions) {
        val membersList = cachedMembers ?: emptyList()
        val sessionsList = cachedSessions ?: emptyList()
        val fronterNames = membersList.filter { m -> 
            sessionsList.any { it.memberId == m.id && it.endTime == null } 
        }.map { it.name }
        
        if (appSettings.showFrontNotification) {
            updateFrontNotification(fronterNames)
        } else {
            updateFrontNotification(emptyList())
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(i18n.text(MessageKey.CommonSearch)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Stop Search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else if (isSelectionMode) {
                TopAppBar(
                    title = { Text(i18n.text(MessageKey.MembersSelected, selectedMemberIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedMemberIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = i18n.text(MessageKey.CommonCancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                val members = (cachedMembers ?: emptyList()).filter { selectedMemberIds.contains(it.id) }
                                val allDormant = members.all { it.inDormancy }
                                members.forEach { m ->
                                    systemService.updateMember(m.id, UpdateMemberDto(inDormancy = !allDormant), systemId = systemId)
                                }
                                selectedMemberIds = emptySet()
                            }
                        }) {
                            val members = (cachedMembers ?: emptyList()).filter { selectedMemberIds.contains(it.id) }
                            val allDormant = members.all { it.inDormancy }
                            Icon(
                                if (allDormant) Icons.Default.BedtimeOff else Icons.Default.Bedtime, 
                                contentDescription = i18n.text(MessageKey.BulkActionDormancy)
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val members = (cachedMembers ?: emptyList()).filter { selectedMemberIds.contains(it.id) }
                                val someFronting = members.any { m -> activeSessions.any { it.memberId == m.id } }
                                
                                members.forEach { m ->
                                    val sessions = activeSessions.filter { it.memberId == m.id }
                                    if (someFronting) {
                                        sessions.forEach { s -> systemService.endFrontSession(m.id, s.id, systemId = systemId) }
                                    } else {
                                        systemService.startFrontSession(m.id, systemId = systemId)
                                    }
                                }
                                selectedMemberIds = emptySet()
                            }
                        }) {
                            val members = (cachedMembers ?: emptyList()).filter { selectedMemberIds.contains(it.id) }
                            val someFronting = members.any { m -> activeSessions.any { it.memberId == m.id } }
                            Icon(
                                if (someFronting) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, 
                                contentDescription = i18n.text(MessageKey.BulkActionFront)
                            )
                        }
                        IconButton(onClick = { showBulkTransferDialog = true }) {
                            Icon(Icons.Default.MoveToInbox, contentDescription = i18n.text(MessageKey.MemberTransferTitle))
                        }
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = i18n.text(MessageKey.BulkActionDelete))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { 
                        Column {
                            Text(currentGroup?.name ?: i18n.text(MessageKey.MembersManageTitle))
                            if (currentGroupId != null) {
                                Text(i18n.text(MessageKey.CommonFolder), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentGroupId != null) {
                                val parent = (cachedGroups ?: emptyList()).find { it.id == currentGroupId }?.parentId
                                currentGroupId = parent
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = i18n.text(MessageKey.CommonBack))
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = i18n.text(MessageKey.CommonSearch))
                        }
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = i18n.text(MessageKey.CommonRefresh))
                        }
                        if (frontMode != FrontMode.NONE) {
                            IconButton(onClick = { frontMode = FrontMode.NONE }) {
                                Icon(Icons.Default.Close, contentDescription = i18n.text(MessageKey.CommonCancel))
                            }
                        }
                        if (pendingCount > 0) {
                            IconButton(onClick = { offlineManager.triggerSync() }) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(Icons.Default.Sync, contentDescription = "Retry Sync")
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.offset(x = 4.dp, y = (-4).dp)
                                    ) {
                                        Text("$pendingCount")
                                    }
                                }
                            }
                        }
                        if (currentGroupId != null) {
                            IconButton(onClick = { showEditGroupDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = i18n.text(MessageKey.CommonEdit))
                            }
                             IconButton(onClick = {
                                 showDeleteGroupConfirm = true
                             }) {
                                 Icon(Icons.Default.Delete, contentDescription = i18n.text(MessageKey.CommonDelete))
                             }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (frontMode == FrontMode.NONE && !isSelectionMode) {
                BottomAppBar(
                    actions = {
                        IconButton(onClick = { showAddGroupDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = i18n.text(MessageKey.MembersFolderNew))
                        }
                        IconButton(onClick = { showFrontMenu = true }) {
                            Icon(Icons.Default.People, contentDescription = i18n.text(MessageKey.FrontManagementTitle))
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = i18n.text(MessageKey.MembersAdd))
                        }
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (isLoading && displayMembers.isEmpty() && displayGroups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (frontMode != FrontMode.NONE) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (frontMode == FrontMode.ADD) i18n.text(MessageKey.FrontModeLabelAdd) else i18n.text(MessageKey.FrontModeLabelSet),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (displayMembers.isEmpty() && displayGroups.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (isSearchActive) "No members found" else i18n.text(MessageKey.MembersEmpty))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Folders first
                        if (!isSearchActive) {
                            items(displayGroups) { group ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    onClick = { currentGroupId = group.id }
                                ) {
                                    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                        if (!group.color.isNullOrBlank()) {
                                            Box(
                                                Modifier
                                                    .fillMaxHeight()
                                                    .width(6.dp)
                                                    .background(ColorUtils.parseHexColor(group.color))
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.padding(16.dp).padding(start = if (!group.color.isNullOrBlank()) 8.dp else 0.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Folder, 
                                                contentDescription = null, 
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Text(group.name ?: i18n.text(MessageKey.MembersEmpty), fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.ChevronRight, null)
                                        }
                                    }
                                }
                            }
                        }

                        // Then Members
                        items(displayMembers) { member ->
                            val isFront = member.currentFrontSessions.any { it.endTime == null }
                            val isSelected = selectedMemberIds.contains(member.id)
                            
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .combinedClickable(
                                            onClick = { 
                                                if (isSelectionMode) {
                                                    selectedMemberIds = if (isSelected) selectedMemberIds - member.id else selectedMemberIds + member.id
                                                } else if (frontMode == FrontMode.NONE) {
                                                    onEditMember(member.id)
                                                } else {
                                                    scope.launch {
                                                        if (frontMode == FrontMode.SET) {
                                                            // Terminer les autres sessions d'abord
                                                            displayMembers.forEach { m ->
                                                                m.currentFrontSessions.filter { it.endTime == null }.forEach { s ->
                                                                    systemService.endFrontSession(m.id, s.id, systemId = systemId)
                                                                }
                                                            }
                                                            systemService.startFrontSession(member.id, systemId = systemId)
                                                        } else {
                                                            if (isFront) {
                                                                member.currentFrontSessions.filter { it.endTime == null }.forEach { s ->
                                                                    systemService.endFrontSession(member.id, s.id, systemId = systemId)
                                                                }
                                                            } else {
                                                                systemService.startFrontSession(member.id, systemId = systemId)
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!isSelectionMode) {
                                                    selectedMemberIds = setOf(member.id)
                                                }
                                            }
                                        )
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { 
                                                selectedMemberIds = if (isSelected) selectedMemberIds - member.id else selectedMemberIds + member.id
                                            },
                                            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterStart)
                                        )
                                    }
                                    
                                    val colorBarWidth = 6.dp
                                    val startPadding = if (isSelectionMode) 48.dp else 0.dp

                                    if (!member.color.isNullOrBlank()) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight()
                                                .width(colorBarWidth)
                                                .padding(start = startPadding)
                                                .background(ColorUtils.parseHexColor(member.color))
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.padding(16.dp).padding(start = startPadding + (if (!member.color.isNullOrBlank()) 8.dp else 0.dp)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MosaicAvatar(
                                            avatarUrl = member.avatarUrl,
                                            size = 50.dp,
                                            cornerRadius = 8.dp,
                                            authService = authService
                                        )
                                        
                                        Spacer(Modifier.width(16.dp))
                                        
                                        Column(Modifier.weight(1f)) {
                                            Text(member.name, fontWeight = FontWeight.Bold)
                                            if (!member.pronouns.isNullOrBlank()) {
                                                Text(member.pronouns, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        val memberColor = if (!member.color.isNullOrBlank()) {
                                            ColorUtils.parseHexColor(member.color)
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }

                                        if (frontMode != FrontMode.NONE) {
                                            Icon(
                                                imageVector = if (isFront) Icons.Default.ArrowDownward else Icons.Default.Add,
                                                contentDescription = null,
                                                tint = if (isFront) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        } else if (isFront) {
                                            Surface(
                                                onClick = {
                                                    scope.launch {
                                                        member.currentFrontSessions.filter { it.endTime == null }.forEach { s ->
                                                            systemService.endFrontSession(member.id, s.id, systemId = systemId)
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(4.dp),
                                                color = memberColor.copy(alpha = 0.1f),
                                                border = BorderStroke(1.dp, memberColor.copy(alpha = 0.5f)),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.ArrowDownward,
                                                        contentDescription = "End Front",
                                                        tint = memberColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        var groupColor by remember { mutableStateOf("#3F51B5") } // Default color
        var showColorPicker by remember { mutableStateOf(false) }
        var isCreating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isCreating) showAddGroupDialog = false },
            title = { Text(i18n.text(MessageKey.MembersFolderNew)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text(i18n.text(MessageKey.MembersFolderName)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreating
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!isCreating) showColorPicker = true }
                            .padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = ColorUtils.parseHexColor(groupColor),
                                    shape = CircleShape
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(i18n.text(MessageKey.MembersFolderColor), style = MaterialTheme.typography.bodyLarge)
                            Text(groupColor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (showColorPicker) {
                        space.ourmosaic.app.components.ColorPickerDialog(
                            initialColor = groupColor,
                            i18n = i18n,
                            onColorSelected = { 
                                groupColor = it
                                showColorPicker = false 
                            },
                            onDismiss = { showColorPicker = false }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isCreating = true
                            systemService.createGroup(CreateGroupDto(
                                name = groupName, 
                                color = groupColor.ifBlank { null },
                                parentId = currentGroupId
                            ), systemId = systemId)
                            showAddGroupDialog = false
                            isCreating = false
                        }
                    },
                    enabled = groupName.isNotBlank() && !isCreating
                ) {
                    if (isCreating) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Text(i18n.text(MessageKey.CommonCreate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGroupDialog = false }, enabled = !isCreating) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (showEditGroupDialog && currentGroup != null) {
        var groupName by remember { mutableStateOf(currentGroup.name ?: "") }
        var groupColor by remember { mutableStateOf(currentGroup.color ?: "#3F51B5") }
        var showColorPicker by remember { mutableStateOf(false) }
        var isUpdating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isUpdating) showEditGroupDialog = false },
            title = { Text(i18n.text(MessageKey.MembersFolderEdit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text(i18n.text(MessageKey.MembersFolderName)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUpdating
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!isUpdating) showColorPicker = true }
                            .padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = ColorUtils.parseHexColor(groupColor),
                                    shape = CircleShape
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(i18n.text(MessageKey.MembersFolderColor), style = MaterialTheme.typography.bodyLarge)
                            Text(groupColor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (showColorPicker) {
                        space.ourmosaic.app.components.ColorPickerDialog(
                            initialColor = groupColor,
                            i18n = i18n,
                            onColorSelected = { 
                                groupColor = it
                                showColorPicker = false 
                            },
                            onDismiss = { showColorPicker = false }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isUpdating = true
                            systemService.updateGroup(currentGroup.id, CreateGroupDto(
                                name = groupName, 
                                color = groupColor.ifBlank { null },
                                parentId = currentGroup.parentId
                            ), systemId = systemId)
                            showEditGroupDialog = false
                            isUpdating = false
                        }
                    },
                    enabled = groupName.isNotBlank() && !isUpdating
                ) {
                    if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Text(i18n.text(MessageKey.CommonSave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGroupDialog = false }, enabled = !isUpdating) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (showFrontMenu) {
        ModalBottomSheet(onDismissRequest = { showFrontMenu = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text(i18n.text(MessageKey.FrontModeAdd)) },
                    leadingContent = { Icon(Icons.Default.GroupAdd, null) },
                    modifier = Modifier.clickable { 
                        frontMode = FrontMode.ADD
                        showFrontMenu = false 
                    }
                )
                ListItem(
                    headlineContent = { Text(i18n.text(MessageKey.FrontModeSet)) },
                    leadingContent = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.clickable { 
                        frontMode = FrontMode.SET
                        showFrontMenu = false 
                    }
                )
            }
        }
    }

    if (showBulkDeleteConfirm) {
        var deleteTapCount by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { 
                showBulkDeleteConfirm = false 
                deleteTapCount = 0
            },
            title = { Text(i18n.text(MessageKey.DeleteMemberConfirmTitle)) },
            text = {
                Column {
                    Text(i18n.text(MessageKey.DeleteMemberConfirmText))
                    if (deleteTapCount > 0) {
                        LinearProgressIndicator(
                            progress = { deleteTapCount / 5f },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTapCount++
                        if (deleteTapCount >= 5) {
                            scope.launch {
                                selectedMemberIds.forEach { id ->
                                    systemService.deleteMember(id, systemId = systemId)
                                }
                                selectedMemberIds = emptySet()
                                showBulkDeleteConfirm = false
                                deleteTapCount = 0
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (deleteTapCount >= 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (deleteTapCount >= 4) i18n.text(MessageKey.DeleteMemberConfirmAction) else "${5 - deleteTapCount}...")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showBulkDeleteConfirm = false 
                    deleteTapCount = 0
                }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (showDeleteGroupConfirm && currentGroupId != null) {
        var deleteTapCount by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { 
                showDeleteGroupConfirm = false 
                deleteTapCount = 0
            },
            title = { Text(i18n.text(MessageKey.DeleteFolderConfirmTitle)) },
            text = {
                Column {
                    Text(i18n.text(MessageKey.DeleteFolderConfirmText))
                    if (deleteTapCount > 0) {
                        LinearProgressIndicator(
                            progress = { deleteTapCount / 3f },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTapCount++
                        if (deleteTapCount >= 3) {
                            scope.launch {
                                val idToDelete = currentGroupId!!
                                val parent = (cachedGroups ?: emptyList()).find { it.id == idToDelete }?.parentId
                                systemService.deleteGroup(idToDelete, systemId = systemId)
                                currentGroupId = parent
                                showDeleteGroupConfirm = false
                                deleteTapCount = 0
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (deleteTapCount >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (deleteTapCount >= 2) i18n.text(MessageKey.CommonDelete) else "${3 - deleteTapCount}...")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteGroupConfirm = false 
                    deleteTapCount = 0
                }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (showBulkTransferDialog) {
        val systems by offlineManager.cachedSystems.collectAsState(initial = emptyList())
        var selectedTargetSystemId by remember { mutableStateOf<String?>(null) }
        var isTransferring by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isTransferring) showBulkTransferDialog = false },
            title = { Text(i18n.text(MessageKey.MemberTransferTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(i18n.text(MessageKey.MemberTransferTarget))
                    
                    val otherSystems = systems.filter { it.id != (systemId ?: "me") && it.id != "me" }
                    
                    if (otherSystems.isEmpty()) {
                        Text(i18n.text(MessageKey.SystemSubSystemsEmpty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        otherSystems.forEach { system ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTargetSystemId = system.id }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTargetSystemId == system.id,
                                    onClick = { selectedTargetSystemId = system.id }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(system.customName ?: system.username ?: "Unknown")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = selectedTargetSystemId ?: return@TextButton
                        scope.launch {
                            isTransferring = true
                            selectedMemberIds.forEach { memberId ->
                                systemService.transferMember(memberId, TransferMemberDto(targetSystemId = targetId), systemId = systemId)
                            }
                            selectedMemberIds = emptySet()
                            showBulkTransferDialog = false
                            isTransferring = false
                        }
                    },
                    enabled = selectedTargetSystemId != null && !isTransferring
                ) {
                    if (isTransferring) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Text(i18n.text(MessageKey.CommonSave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkTransferDialog = false }, enabled = !isTransferring) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var isCreating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isCreating) showAddDialog = false },
            title = { Text(i18n.text(MessageKey.MembersAdd)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(i18n.text(MessageKey.ProfileNameLabel)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCreating
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isCreating = true
                            systemService.createMember(CreateMemberDto(name = name), systemId = systemId).onSuccess {
                                showAddDialog = false
                            }
                            isCreating = false
                        }
                    },
                    enabled = name.isNotBlank() && !isCreating
                ) {
                    if (isCreating) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Text(i18n.text(MessageKey.ProfileSave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }, enabled = !isCreating) {
                    Text(i18n.text(MessageKey.ProfileCancel))
                }
            }
        )
    }
}
