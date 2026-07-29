@file:OptIn(ExperimentalMaterial3Api::class)

package space.ourmosaic.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.components.MosaicAvatar
import space.ourmosaic.app.components.SystemSwitcher
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.navigation.Route
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.system.*
import space.ourmosaic.app.utils.Logger
import space.ourmosaic.app.utils.DateTimeUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import space.ourmosaic.app.offline.ChatMessageQueueType

@Composable
fun ChatScreen(
    channelId: String? = null,
    systemId: String? = null,
    chatService: ChatService,
    authService: AuthService,
    offlineManager: OfflineManager,
    systemService: SystemService,
    systemContextManager: SystemContextManager,
    navState: space.ourmosaic.app.navigation.NavState,
    i18n: I18nState,
    onNavigate: (Route) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val channels by offlineManager.cachedChatChannels(systemId).collectAsState(initial = emptyList())
    val currentChannel = remember(channelId, channels) {
        channels.find { it.id == channelId }
    }
    var messages by remember { mutableStateOf<List<ChatMessageResponse>>(emptyList()) }
    var members by remember { mutableStateOf(emptyList<MemberResponse>()) }
    var lastSenders by remember { mutableStateOf(emptyList<MemberResponse>()) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var selectedSender by remember { mutableStateOf<MemberResponse?>(null) }
    var showSenderPicker by remember { mutableStateOf(false) }
    var senderSearchQuery by remember { mutableStateOf("") }
    var showChannelCreateDialog by remember { mutableStateOf(false) }
    var newChannelName by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<ChatMessageResponse?>(null) }
    var messageToConfirmDelete by remember { mutableStateOf<ChatMessageResponse?>(null) }
    var channelToConfirmDelete by remember { mutableStateOf<ChatChannelResponse?>(null) }
    var hasMoreMessages by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var drawerTab by remember { mutableStateOf(0) } // 0 = Channels, 1 = Menu

    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val activeSessions by offlineManager.cachedFrontSessions.collectAsState(initial = null)
    val activeMemberIds = remember(activeSessions) {
        activeSessions?.filter { it.endTime == null }?.map { it.memberId }?.toSet() ?: emptySet()
    }

    fun sortMessages(list: List<ChatMessageResponse>): List<ChatMessageResponse> {
        return list.sortedByDescending { it.createdAt ?: it.timestamp }
    }

    LaunchedEffect(imeVisible, messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(0)
        }
    }

    // Initial load
    LaunchedEffect(systemId) {
        chatService.getChatChannels(systemId).onSuccess {
            offlineManager.cacheChatChannels(it, systemId)
        }.onFailure {
            // Handle error
        }
        systemService.getMembers(systemId).onSuccess { 
            members = it
            if (selectedSender == null && it.isNotEmpty()) {
                selectedSender = it.first()
            }
        }
        systemService.getActiveFrontSessions(systemId = systemId).onSuccess {
            offlineManager.cacheFrontSessions(it, systemId = systemId)
        }
    }

    // Load messages and last senders when channel changes
    LaunchedEffect(channelId, systemId) {
        if (channelId != null) {
            messages = offlineManager.getCachedChatMessages(channelId, systemId) ?: emptyList()
            hasMoreMessages = true
            isLoadingMessages = true
            chatService.getMessages(channelId, systemId).onSuccess { 
                val newMessages = sortMessages(it)
                messages = newMessages
                offlineManager.cacheChatMessages(channelId, newMessages, systemId)
                hasMoreMessages = it.size >= 50
            }
            chatService.getLastKnownSenders(channelId, systemId).onSuccess { lastSenders = it }
            isLoadingMessages = false
        }
    }

    fun loadMoreMessages() {
        if (isLoadingMore || !hasMoreMessages || channelId == null) return
        
        scope.launch {
            isLoadingMore = true
            chatService.getMessages(channelId, systemId, offset = messages.size).onSuccess { 
                if (it.isEmpty()) {
                    hasMoreMessages = false
                } else {
                    val combined = sortMessages(messages + it)
                    messages = combined
                    offlineManager.cacheChatMessages(channelId, combined, systemId)
                    hasMoreMessages = it.size >= 50
                }
            }.onFailure {
                hasMoreMessages = false
            }
            isLoadingMore = false
        }
    }

    val sortedLastSenders = remember(lastSenders, activeMemberIds) {
        lastSenders.sortedWith(
            compareByDescending<MemberResponse> { activeMemberIds.contains(it.id) }
                .thenBy { it.name }
        )
    }

    val sendMessage = {
        if (messageText.isNotBlank() && selectedSender != null && channelId != null) {
            val content = messageText
            val sender = selectedSender!!
            val tempId = "temp-${Clock.System.now().toEpochMilliseconds()}"
            val timestamp = Clock.System.now().toString()
            
            val pendingMessage = ChatMessageResponse(
                id = tempId,
                content = content,
                senderId = sender.id,
                channelId = channelId,
                timestamp = timestamp,
                sender = sender,
                isPending = true
            )
            
            messages = listOf(pendingMessage) + messages
            messageText = ""
            
            scope.launch {
                chatService.sendMessage(channelId, sender.id, content, systemId).onSuccess { newMessage ->
                    messages = sortMessages(messages.map { if (it.id == tempId) newMessage else it })
                    offlineManager.cacheChatMessages(channelId, messages, systemId)
                    chatService.getLastKnownSenders(channelId, systemId).onSuccess { lastSenders = it }
                }.onFailure { error ->
                    val isNetworkError = error.message?.let { msg ->
                        msg.contains("Failed to connect") || 
                        msg.contains("Connection refused") || 
                        msg.contains("No route") ||
                        msg.contains("Network") ||
                        msg.contains("timeout") ||
                        error.cause?.toString()?.contains("IOException") == true
                    } ?: false
                    
                    if (isNetworkError) {
                        offlineManager.enqueueChatMessage(ChatMessageQueueType.SEND, channelId, content, sender.id, null, systemId)
                        messages = messages.map { if (it.id == tempId) it.copy(isPending = true, isFailed = false) else it }
                    } else {
                        messages = messages.map { if (it.id == tempId) it.copy(isPending = false, isFailed = true) else it }
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                val userMe by offlineManager.cachedUserMe.collectAsState(authService.userMe.value)
                val systems by offlineManager.cachedSystems.collectAsState(emptyList())

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SystemSwitcher(
                        userMe = userMe,
                        systems = systems,
                        currentSystemId = systemId,
                        systemContextManager = systemContextManager,
                        navState = navState,
                        i18n = i18n,
                        authService = authService,
                        onCloseDrawer = { scope.launch { drawerState.close() } }
                    )

                    Spacer(Modifier.height(12.dp))

                    TabRow(selectedTabIndex = drawerTab) {
                        Tab(
                            selected = drawerTab == 0,
                            onClick = { drawerTab = 0 },
                            text = { Text(i18n.text(MessageKey.ChatTabChannels)) },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, null) }
                        )
                        Tab(
                            selected = drawerTab == 1,
                            onClick = { drawerTab = 1 },
                            text = { Text(i18n.text(MessageKey.ChatTabMainMenu)) },
                            icon = { Icon(Icons.Default.Menu, null) }
                        )
                    }

                    if (drawerTab == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(i18n.text(MessageKey.ChatTitle), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { showChannelCreateDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Create Channel")
                            }
                        }
                        HorizontalDivider()
                        // Use a Column here instead of LazyColumn since the parent is scrollable
                        channels.forEach { channel ->
                            val isSelected = channel.id == channelId
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Text(channel.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        if (!channel.description.isNullOrBlank()) {
                                            Text(
                                                channel.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                },
                                selected = isSelected,
                                onClick = {
                                    onNavigate(Route.ChatChannel(channel.id, systemId))
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Default.Tag, null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    } else {
                        val isSystem = userMe?.isSystem == true

                        Spacer(Modifier.height(16.dp))

                        Route.all.filter { route ->
                            if (!isSystem) {
                                route !is Route.System && route !is Route.MembersManage
                            } else true
                        }.forEach { route ->
                            val isSelected = when (route) {
                                is Route.System -> navState.currentRoute is Route.System
                                is Route.Chat -> navState.currentRoute is Route.Chat || navState.currentRoute is Route.ChatChannel
                                is Route.MembersManage -> navState.currentRoute is Route.MembersManage || navState.currentRoute is Route.MemberEdit
                                else -> navState.currentRoute == route
                            }

                            NavigationDrawerItem(
                                label = { Text(i18n.text(route.titleKey)) },
                                selected = isSelected,
                                onClick = {
                                    val targetRoute = when (route) {
                                        is Route.System -> Route.System(systemId)
                                        is Route.Chat -> Route.Chat(systemId)
                                        is Route.MembersManage -> Route.MembersManage(systemId)
                                        else -> route
                                    }
                                    onNavigate(targetRoute)
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(route.icon, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                if (channelId == null) {
                    TopAppBar(
                        title = { Text(i18n.text(MessageKey.ChatTitle)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Channels")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open Channels")
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (channelId != null) {
                                    IconButton(onClick = { onNavigate(Route.Chat(systemId)) }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Chat")
                                    }
                                }
                                Icon(Icons.Default.Tag, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(currentChannel?.name ?: "", maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                channelToConfirmDelete = currentChannel
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Channel")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (channelId == null) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            Spacer(Modifier.height(16.dp))
                            Text(i18n.text(MessageKey.ChatNoChannels))
                            Button(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.padding(16.dp)) {
                                Text("Open Channel List")
                            }
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (isLoadingMessages) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(
                                state = scrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                reverseLayout = true
                            ) {
                                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                                    if (index == messages.size - 1 && hasMoreMessages && !isLoadingMore) {
                                        LaunchedEffect(Unit) {
                                            loadMoreMessages()
                                        }
                                    }

                                    val sender = message.sender ?: members.find { it.id == message.senderId }
                                    val olderMessage = if (index + 1 < messages.size) messages[index + 1] else null

                                    val isSameSender = olderMessage?.senderId == message.senderId
                                    val isRecent = if (olderMessage != null) {
                                        val currentTs = message.createdAt ?: message.timestamp
                                        val olderTs = olderMessage.createdAt ?: olderMessage.timestamp
                                        if (currentTs != null && olderTs != null) {
                                            try {
                                                val currentInstant = Instant.parse(currentTs)
                                                val olderInstant = Instant.parse(olderTs)
                                                (olderInstant.toEpochMilliseconds() - currentInstant.toEpochMilliseconds()) < 5 * 60 * 1000 // 5 minutes (reversed list)
                                            } catch (_: Exception) { true }
                                        } else true
                                    } else false

                                    val showSenderInfo = !isSameSender || !isRecent

                                    Column {
                                        ChatMessageItem(
                                            message = message.copy(sender = sender),
                                            showSenderInfo = showSenderInfo,
                                            authService = authService,
                                            onEdit = { editingMessage = it },
                                            onDelete = { messageToConfirmDelete = it },
                                            onRetry = { retryMsg ->
                                                if (retryMsg.isEditFailed) {
                                                    // Retry edit
                                                    val editMsg = retryMsg.copy(isPendingEdit = true, isEditFailed = false)
                                                    messages = sortMessages(messages.map { if (it.id == retryMsg.id) editMsg else it })
                                                    offlineManager.cacheChatMessages(retryMsg.channelId, messages, systemId)
                                                    
                                                    scope.launch {
                                                        chatService.editMessage(retryMsg.channelId, retryMsg.id, retryMsg.content, systemId).onSuccess { updated ->
                                                            messages = sortMessages(messages.map { if (it.id == updated.id) updated.copy(isEdited = true) else it })
                                                            offlineManager.cacheChatMessages(retryMsg.channelId, messages, systemId)
                                                        }.onFailure {
                                                            messages = messages.map { if (it.id == retryMsg.id) editMsg.copy(isPendingEdit = false, isEditFailed = true) else it }
                                                        }
                                                    }
                                                } else {
                                                    // Retry send
                                                    messages = messages.filter { it.id != retryMsg.id }
                                                    messageText = retryMsg.content
                                                    sendMessage()
                                                }
                                            }
                                        )
                                        if (showSenderInfo && index < messages.size - 1) {
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(messages.size) {
                                // Handled by the global LaunchedEffect(imeVisible, messages.size)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                    ) {
                        if (sortedLastSenders.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                sortedLastSenders.take(5).forEach { sender ->
                                    val memberColor = sender.color?.let { Color(it.removePrefix("#").toLong(16) or 0xFF000000) } ?: MaterialTheme.colorScheme.outline
                                    Box(
                                        Modifier
                                            .size(38.dp)
                                            .then(if (selectedSender?.id == sender.id)
                                                Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                                                else Modifier)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, memberColor, CircleShape)
                                            .clickable { selectedSender = sender },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MosaicAvatar(
                                            avatarUrl = sender.avatarUrl,
                                            authService = authService,
                                            size = 30.dp,
                                            cornerRadius = 15.dp
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            tonalElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showSenderPicker = true }) {
                                    MosaicAvatar(
                                        avatarUrl = selectedSender?.avatarUrl,
                                        authService = authService,
                                        size = 40.dp
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                TextField(
                                    value = messageText,
                                    onValueChange = {
                                        messageText = it
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(i18n.text(MessageKey.ChatMessagePlaceholder)) },
                                    maxLines = 4,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )

                                IconButton(
                                    onClick = { sendMessage() },
                                    enabled = messageText.isNotBlank() && selectedSender != null
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSenderPicker) {
        AlertDialog(
            onDismissRequest = { showSenderPicker = false },
            title = { Text(i18n.text(MessageKey.ChatSelectSender)) },
            text = {
                Column {
                    TextField(
                        value = senderSearchQuery,
                        onValueChange = { senderSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(i18n.text(MessageKey.CommonSearch)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    
                    val filteredMembers = remember(members, senderSearchQuery, activeMemberIds) {
                        members.filter { it.name.contains(senderSearchQuery, ignoreCase = true) }
                            .sortedWith(
                                compareByDescending<MemberResponse> { activeMemberIds.contains(it.id) }
                                    .thenBy { it.name }
                            )
                    }

                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(filteredMembers) { member ->
                            val memberColor = member.color?.let { Color(it.removePrefix("#").toLong(16) or 0xFF000000) } ?: MaterialTheme.colorScheme.onSurface
                            val isAtFront = activeMemberIds.contains(member.id)
                            
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        selectedSender = member
                                        showSenderPicker = false
                                        senderSearchQuery = ""
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    MosaicAvatar(avatarUrl = member.avatarUrl, authService = authService, size = 32.dp, cornerRadius = 16.dp)
                                    if (isAtFront) {
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(10.dp)
                                                .background(Color(0xFF4CAF50), CircleShape)
                                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        member.name, 
                                        color = memberColor, 
                                        fontWeight = if (isAtFront) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isAtFront) {
                                        Text("At front", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showChannelCreateDialog) {
        AlertDialog(
            onDismissRequest = { showChannelCreateDialog = false },
            title = { Text(i18n.text(MessageKey.ChatCreateChannel)) },
            text = {
                TextField(
                    value = newChannelName,
                    onValueChange = { newChannelName = it },
                    label = { Text(i18n.text(MessageKey.ChatNewChannelName)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newChannelName.isNotBlank()) {
                        scope.launch {
                            chatService.createChannel(newChannelName, systemId).onSuccess { newChannel ->
                                offlineManager.cacheChatChannels(channels + newChannel, systemId)
                                onNavigate(Route.ChatChannel(newChannel.id, systemId))
                                showChannelCreateDialog = false
                                newChannelName = ""
                            }
                        }
                    }
                }) {
                    Text(i18n.text(MessageKey.CommonCreate))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChannelCreateDialog = false }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (editingMessage != null) {
        var text by remember { mutableStateOf(editingMessage!!.content) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text(i18n.text(MessageKey.ChatEditMessage)) },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val msg = editingMessage!!
                    if (text != msg.content) {
                        val updatedMessage = msg.copy(
                            content = text,
                            isPendingEdit = true,
                            isEditFailed = false
                        )
                        messages = sortMessages(messages.map { if (it.id == msg.id) updatedMessage else it })
                        offlineManager.cacheChatMessages(msg.channelId, messages, systemId)
                        editingMessage = null
                        
                        scope.launch {
                            chatService.editMessage(msg.channelId, msg.id, text, systemId).onSuccess { updated ->
                                messages = sortMessages(messages.map { if (it.id == updated.id) updated.copy(isEdited = true) else it })
                                offlineManager.cacheChatMessages(msg.channelId, messages, systemId)
                            }.onFailure { error ->
                                val isNetworkError = error.message?.let { errMsg ->
                                    errMsg.contains("Failed to connect") || 
                                    errMsg.contains("Connection refused") || 
                                    errMsg.contains("No route") ||
                                    errMsg.contains("Network") ||
                                    errMsg.contains("timeout") ||
                                    error.cause?.toString()?.contains("IOException") == true
                                } ?: false
                                
                                if (isNetworkError) {
                                    offlineManager.enqueueChatMessage(ChatMessageQueueType.EDIT, msg.channelId, text, msg.senderId, msg.id, systemId)
                                    messages = messages.map { if (it.id == msg.id) updatedMessage.copy(isPendingEdit = true, isEditFailed = false) else it }
                                } else {
                                    messages = messages.map { if (it.id == msg.id) updatedMessage.copy(isPendingEdit = false, isEditFailed = true) else it }
                                }
                            }
                        }
                    } else {
                        editingMessage = null
                    }
                }) {
                    Text(i18n.text(MessageKey.CommonSave))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (messageToConfirmDelete != null) {
        var deleteTapCount by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { 
                messageToConfirmDelete = null
                deleteTapCount = 0
            },
            title = { Text(i18n.text(MessageKey.ChatDeleteMessage)) },
            text = {
                Column {
                    Text(i18n.text(MessageKey.ChatDeleteMessageConfirm))
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
                            val msg = messageToConfirmDelete!!
                            scope.launch {
                                chatService.deleteMessage(msg.channelId, msg.id, systemId).onSuccess {
                                    messages = messages.filter { it.id != msg.id }
                                    offlineManager.cacheChatMessages(msg.channelId, messages, systemId)
                                    messageToConfirmDelete = null
                                    deleteTapCount = 0
                                }
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
                    messageToConfirmDelete = null
                    deleteTapCount = 0
                }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }

    if (channelToConfirmDelete != null) {
        var deleteTapCount by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { 
                channelToConfirmDelete = null
                deleteTapCount = 0
            },
            title = { Text(i18n.text(MessageKey.ChatDeleteChannel)) },
            text = {
                Column {
                    Text(i18n.text(MessageKey.ChatDeleteChannelConfirm))
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
                            val channelId = channelToConfirmDelete!!.id
                            scope.launch {
                                chatService.deleteChannel(channelId, systemId).onSuccess {
                                    offlineManager.cacheChatChannels(channels.filter { it.id != channelId }, systemId)
                                    onNavigate(Route.Chat(systemId))
                                    channelToConfirmDelete = null
                                    deleteTapCount = 0
                                }
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
                    channelToConfirmDelete = null
                    deleteTapCount = 0
                }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessageResponse,
    showSenderInfo: Boolean = true,
    authService: AuthService,
    onEdit: (ChatMessageResponse) -> Unit,
    onDelete: (ChatMessageResponse) -> Unit,
    onRetry: (ChatMessageResponse) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val memberColor = remember(message.sender?.color, isDark) {
        val rawColor = message.sender?.color?.let { Color(it.removePrefix("#").toLong(16) or 0xFF000000) }
            ?: return@remember null
        
        // Ensure contrast
        val luminance = rawColor.luminance()
        if (isDark && luminance < 0.2f) {
            // If background is dark and name is too dark, lighten it
            rawColor.copy(
                red = (rawColor.red + 0.4f).coerceAtMost(1f),
                green = (rawColor.green + 0.4f).coerceAtMost(1f),
                blue = (rawColor.blue + 0.4f).coerceAtMost(1f)
            )
        } else if (!isDark && luminance > 0.8f) {
            // If background is light and name is too light, darken it
            rawColor.copy(
                red = (rawColor.red - 0.4f).coerceAtLeast(0f),
                green = (rawColor.green - 0.4f).coerceAtLeast(0f),
                blue = (rawColor.blue - 0.4f).coerceAtLeast(0f)
            )
        } else {
            rawColor
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showSenderInfo) {
            MosaicAvatar(
                avatarUrl = message.sender?.avatarUrl,
                authService = authService,
                size = 40.dp
            )
        } else {
            Spacer(Modifier.width(40.dp))
        }
        
        Column(Modifier.weight(1f)) {
            if (showSenderInfo) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.sender?.name ?: "Unknown",
                        style = MaterialTheme.typography.labelLarge,
                        color = memberColor ?: MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateTimeUtils.formatChatMessageTimestamp(message.createdAt ?: message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (message.isEdited || message.isPendingEdit) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (message.isPendingEdit) "Editing..." else "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.isEditFailed) MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = if (message.isEditFailed) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            
            Surface(
                color = if (message.isFailed) MaterialTheme.colorScheme.errorContainer 
                        else if (message.isEditFailed) MaterialTheme.colorScheme.errorContainer
                        else if (message.isPending || message.isPendingEdit) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = if (showSenderInfo) 0.dp else 12.dp,
                    topEnd = 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                )
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(message.content)
                    if (message.isFailed || message.isEditFailed) {
                        TextButton(onClick = { onRetry(message) }) {
                            Text(
                                if (message.isEditFailed) "Retry Edit" else "Retry",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { 
                    onEdit(message)
                    showMenu = false 
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { 
                    onDelete(message)
                    showMenu = false 
                },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}
