package space.ourmosaic.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.components.MosaicAvatar
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.navigation.Route
import space.ourmosaic.app.system.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendSystemScreen(
    friendId: String,
    initialPage: Int = 0,
    i18n: I18nState,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    onTabChange: (Int) -> Unit,
    systemService: SystemService,
    authService: AuthService
) {
    var friendSystem by remember { mutableStateOf<FriendSystemView?>(null) }
    var allMembers by remember { mutableStateOf<List<MemberResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    var showReportDialog by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pagerState.currentPage) {
        onTabChange(pagerState.currentPage)
    }

    suspend fun loadData() {
        isLoading = true
        systemService.getFriendSystem(friendId).onSuccess {
            friendSystem = it
            allMembers = it.members
        }.onFailure {
            println("Failed to load friend system for $friendId: ${it.message}")
            it.printStackTrace()
            snackbarHostState.showSnackbar("Failed to load friend system: ${it.message}")
        }
        
        // Try to load full member list from specific endpoint if available/needed
        systemService.getFriendMembers(friendId).onSuccess {
            allMembers = it
        }
        
        isLoading = false
    }

    LaunchedEffect(friendId) {
        loadData()
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
                    title = { Text(friendSystem?.customName ?: i18n.text(MessageKey.FriendsTitle)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = i18n.text(MessageKey.CommonBack))
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            isSearchActive = true
                            scope.launch { pagerState.animateScrollToPage(1) }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = i18n.text(MessageKey.CommonSearch))
                        }
                        IconButton(onClick = { scope.launch { loadData() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = i18n.text(MessageKey.CommonRefresh))
                        }
                        IconButton(onClick = { showPermissionsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Permissions")
                        }
                        IconButton(onClick = { showReportDialog = true }) {
                            Icon(Icons.Default.Report, contentDescription = i18n.text(MessageKey.CommonReport))
                        }
                        IconButton(onClick = { showBlockDialog = true }) {
                            Icon(Icons.Default.Block, contentDescription = i18n.text(MessageKey.CommonBlock))
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (friendSystem == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("System not found or access denied")
            }
        } else {
            val system = friendSystem!!
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                    ) {
                        Text(i18n.text(MessageKey.ProfileSystemTitle), modifier = Modifier.padding(16.dp))
                    }
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                    ) {
                        Text(i18n.text(MessageKey.FriendSharingMembersList), modifier = Modifier.padding(16.dp))
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    SystemHeader(system, authService)
                                }

                                // Status of what I share with this friend
                                item {
                                    SharingStatusCard(system.permissions, i18n)
                                }

                                if (system.activeFrontSessions.isNotEmpty()) {
                                    item {
                                        Text(
                                        i18n.text(MessageKey.FrontActive),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    }
                                    items(system.activeFrontSessions) { session ->
                                        ActiveFrontListItem(session, authService, i18n) {
                                            onNavigate(Route.FriendMemberDetail(friendId, session.member.id))
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            val filteredMembers = if (searchQuery.isBlank()) {
                                allMembers
                            } else {
                                allMembers.filter { it.name.contains(searchQuery, ignoreCase = true) || it.pronouns?.contains(searchQuery, ignoreCase = true) == true }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (filteredMembers.isEmpty()) {
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (allMembers.isEmpty()) {
                                                    if (system.activeFrontSessions.isNotEmpty()) {
                                                        i18n.text(MessageKey.FriendMembersPrivateButFronting, system.customName ?: "This friend")
                                                    } else {
                                                        i18n.text(MessageKey.FriendMembersNotShared, system.customName ?: "This friend")
                                                    }
                                                } else i18n.text(MessageKey.MembersEmpty),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredMembers) { member ->
                                        MemberListItem(member, authService, i18n) {
                                            onNavigate(Route.FriendMemberDetail(friendId, member.id))
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

    if (showPermissionsDialog && friendSystem != null) {
        FriendPermissionsDialog(
            permissions = friendSystem!!.permissions,
            i18n = i18n,
            onDismiss = { showPermissionsDialog = false },
            onUpdate = { dto ->
                scope.launch {
                    showPermissionsDialog = false
                    systemService.updateFriendshipPermissions(friendId, dto).onSuccess {
                        snackbarHostState.showSnackbar("Permissions updated")
                        loadData()
                    }.onFailure {
                        println("Failed to update friendship permissions: ${it.message}")
                        it.printStackTrace()
                        snackbarHostState.showSnackbar("Failed to update friend settings: ${it.message}")
                    }
                }
            }
        )
    }

    if (showReportDialog) {
        SafetyActionDialog(
            title = i18n.text(MessageKey.CommonReport),
            confirmLabel = i18n.text(MessageKey.CommonReport),
            reasonHint = i18n.text(MessageKey.ReportReasonHint),
            i18n = i18n,
            minReasonLength = 10,
            onDismiss = { showReportDialog = false },
            onConfirm = { reason ->
                scope.launch {
                    showReportDialog = false
                    systemService.reportEntity(ReportRequest(ReportType.SYSTEM, friendSystem!!.id, reason))
                        .onSuccess {
                            launch { snackbarHostState.showSnackbar(i18n.text(MessageKey.ReportSuccess)) }
                        }
                        .onFailure {
                            launch { snackbarHostState.showSnackbar(i18n.text(MessageKey.ReportError, it.message ?: "Unknown error")) }
                        }
                }
            }
        )
    }

    if (showBlockDialog) {
        SafetyActionDialog(
            title = i18n.text(MessageKey.CommonBlock),
            confirmLabel = i18n.text(MessageKey.CommonBlock),
            reasonHint = i18n.text(MessageKey.BlockReasonHint),
            i18n = i18n,
            onDismiss = { showBlockDialog = false },
            onConfirm = { reason ->
                scope.launch {
                    systemService.blockEntity(BlockRequest(BlockType.SYSTEM, friendSystem!!.id, reason))
                        .onSuccess {
                            launch { snackbarHostState.showSnackbar(i18n.text(MessageKey.BlockSuccess)) }
                            showBlockDialog = false
                            onBack()
                        }
                        .onFailure {
                            launch { snackbarHostState.showSnackbar(i18n.text(MessageKey.BlockError, it.message ?: "Unknown error")) }
                        }
                }
            }
        )
    }
}

@Composable
fun SystemHeader(system: FriendSystemView, authService: AuthService) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            MosaicAvatar(
                avatarUrl = system.avatarUrl,
                size = 80.dp,
                cornerRadius = 40.dp,
                authService = authService
            )

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = system.customName ?: "No Name",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Friend System",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (!system.description.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(text = system.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun ActiveFrontListItem(session: ActiveFrontSession, authService: AuthService, i18n: I18nState, onClick: () -> Unit) {
    val displayTime = remember(session.startTime) {
        try {
            // "2024-05-24T18:18:18Z" -> "18:18"
            session.startTime.split("T").lastOrNull()?.take(5) ?: session.startTime
        } catch (e: Exception) {
            session.startTime
        }
    }

    FriendMemberItem(
        member = session.member,
        authService = authService,
        i18n = i18n,
        isFronting = true,
        supportingText = i18n.text(MessageKey.MemberSince, displayTime),
        onClick = onClick
    )
}

@Composable
fun MemberListItem(member: MemberResponse, authService: AuthService, i18n: I18nState, onClick: () -> Unit) {
    FriendMemberItem(member = member, authService = authService, i18n = i18n, onClick = onClick)
}

@Composable
fun FriendMemberItem(
    member: MemberResponse,
    authService: AuthService,
    i18n: I18nState,
    isFronting: Boolean = false,
    supportingText: String? = null,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isFronting)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (!member.color.isNullOrBlank()) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(space.ourmosaic.app.utils.ColorUtils.parseHexColor(member.color))
                )
            }
            Row(
                modifier = Modifier.padding(12.dp).padding(start = if (!member.color.isNullOrBlank()) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MosaicAvatar(
                    avatarUrl = member.avatarUrl,
                    size = 48.dp,
                    cornerRadius = 24.dp,
                    authService = authService
                )

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val subText = supportingText ?: member.pronouns
                    if (!subText.isNullOrBlank()) {
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isFronting) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            i18n.text(MessageKey.FrontActive),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharingStatusCard(permissions: FriendshipPermissions, i18n: I18nState) {
    if (permissions.canViewFront || permissions.canViewSharedMembers) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        i18n.text(MessageKey.FriendSharingSettingsLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (permissions.canViewFront) {
                    Text("• " + i18n.text(MessageKey.FriendSharingFrontingStatus), style = MaterialTheme.typography.bodySmall)
                }
                if (permissions.canViewSharedMembers) {
                    Text("• " + i18n.text(MessageKey.FriendSharingMembersListStatus), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun FriendPermissionsDialog(
    permissions: FriendshipPermissions,
    i18n: I18nState,
    onDismiss: () -> Unit,
    onUpdate: (UpdateFriendshipPermissionsDto) -> Unit
) {
    var canViewFront by remember { mutableStateOf(permissions.canViewFront) }
    var canReceiveFrontNotifications by remember { mutableStateOf(permissions.canReceiveFrontNotifications) }
    var canViewSharedMembers by remember { mutableStateOf(permissions.canViewSharedMembers) }
    var notifyMeOnFriendFrontChange by remember { mutableStateOf(permissions.notifyMeOnFriendFrontChange) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.text(MessageKey.FriendPermissionsTitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(i18n.text(MessageKey.FriendPermissionsOutboundSection), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                
                PermissionSwitch(
                    label = i18n.text(MessageKey.FriendPermissionViewFront),
                    checked = canViewFront,
                    onCheckedChange = { canViewFront = it }
                )
                PermissionSwitch(
                    label = i18n.text(MessageKey.FriendPermissionViewMembers),
                    checked = canViewSharedMembers,
                    onCheckedChange = { canViewSharedMembers = it }
                )
                PermissionSwitch(
                    label = i18n.text(MessageKey.FriendPermissionReceiveNotifications),
                    checked = canReceiveFrontNotifications,
                    onCheckedChange = { canReceiveFrontNotifications = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(i18n.text(MessageKey.FriendPermissionsInboundSection), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                
                PermissionSwitch(
                    label = i18n.text(MessageKey.FriendPermissionNotifyMe),
                    checked = notifyMeOnFriendFrontChange,
                    onCheckedChange = { notifyMeOnFriendFrontChange = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdate(UpdateFriendshipPermissionsDto(
                    canViewFront = canViewFront,
                    canReceiveFrontNotifications = canReceiveFrontNotifications,
                    canViewSharedMembers = canViewSharedMembers,
                    notifyMeOnFriendFrontChange = notifyMeOnFriendFrontChange
                ))
            }) {
                Text(i18n.text(MessageKey.ProfileSave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
