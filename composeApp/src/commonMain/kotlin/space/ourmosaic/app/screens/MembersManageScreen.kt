package space.ourmosaic.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersManageScreen(
    i18n: I18nState,
    appSettings: AppSettings,
    onBack: () -> Unit,
    onEditMember: (String) -> Unit,
    offlineManager: OfflineManager,
    systemService: SystemService,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()
    
    var currentGroupId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var showFrontMenu by remember { mutableStateOf(false) }
    var frontMode by remember { mutableStateOf<FrontMode>(FrontMode.NONE) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
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
        .filter { it.parentId == currentGroupId }
        .sortedBy { it.name?.lowercase() }
    
    val displayMembers = (cachedMembers ?: emptyList()).map { member ->
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
            systemService.getMembers()
            systemService.getFrontSessions()
            systemService.getGroups()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
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
                                 scope.launch {
                                     systemService.deleteGroup(currentGroupId!!)
                                     val parent = (cachedGroups ?: emptyList()).find { it.id == currentGroupId }?.parentId
                                     currentGroupId = parent
                                 }
                             }) {
                                 Icon(Icons.Default.Delete, contentDescription = i18n.text(MessageKey.CommonDelete))
                             }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (frontMode == FrontMode.NONE) {
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
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { 
                                    if (frontMode == FrontMode.NONE) {
                                        onEditMember(member.id)
                                    } else {
                                        scope.launch {
                                            if (frontMode == FrontMode.SET) {
                                                // Terminer les autres sessions d'abord
                                                displayMembers.forEach { m ->
                                                    m.currentFrontSessions.filter { it.endTime == null }.forEach { s ->
                                                        systemService.endFrontSession(m.id, s.id)
                                                    }
                                                }
                                                systemService.startFrontSession(member.id)
                                            } else {
                                                if (isFront) {
                                                    member.currentFrontSessions.filter { it.endTime == null }.forEach { s ->
                                                        systemService.endFrontSession(member.id, s.id)
                                                    }
                                                } else {
                                                    systemService.startFrontSession(member.id)
                                                }
                                            }
                                        }
                                    }
                                }
                            ) {
                                Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                    if (!member.color.isNullOrBlank()) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight()
                                                .width(6.dp)
                                                .background(ColorUtils.parseHexColor(member.color))
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.padding(16.dp).padding(start = if (!member.color.isNullOrBlank()) 8.dp else 0.dp),
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
                                                            systemService.endFrontSession(member.id, s.id)
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
                            ))
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
                            ))
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
                            systemService.createMember(CreateMemberDto(name = name)).onSuccess {
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
