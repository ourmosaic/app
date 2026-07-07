package space.ourmosaic.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.components.MosaicAvatar
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.system.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    i18n: I18nState,
    onBack: () -> Unit,
    systemService: SystemService,
    offlineManager: OfflineManager,
    authService: AuthService,
    onFriendClick: (String) -> Unit
) {
    val friends by offlineManager.cachedFriends.collectAsState(initial = emptyList())
    val receivedRequests by offlineManager.cachedReceivedRequests.collectAsState(initial = emptyList())
    val sentRequests by offlineManager.cachedSentRequests.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    var showAddFriendDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        systemService.getFriends()
        systemService.getReceivedFriendRequests()
        systemService.getSentFriendRequests()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n.text(MessageKey.FriendsTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = i18n.text(MessageKey.CommonBack))
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        scope.launch {
                            systemService.getFriends()
                            systemService.getReceivedFriendRequests()
                            systemService.getSentFriendRequests()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = i18n.text(MessageKey.CommonRefresh))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddFriendDialog = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = i18n.text(MessageKey.FriendsAdd))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(i18n.text(MessageKey.FriendsList)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { 
                        BadgedBox(badge = {
                            if (receivedRequests.isNotEmpty()) {
                                Badge { Text(receivedRequests.size.toString()) }
                            }
                        }) {
                            Text(i18n.text(MessageKey.FriendsRequests))
                        }
                    }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(i18n.text(MessageKey.FriendsSentRequests)) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) { page ->
                when (page) {
                    0 -> FriendsList(friends, i18n, systemService, authService, snackbarHostState, onFriendClick)
                    1 -> FriendRequestsList(receivedRequests, i18n, systemService, authService, snackbarHostState)
                    2 -> SentRequestsList(sentRequests, i18n, systemService, authService, snackbarHostState)
                }
            }
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            i18n = i18n,
            onDismiss = { showAddFriendDialog = false },
            onSend = { username, federationUrl ->
                scope.launch {
                    val result = systemService.sendFriendRequest(SendFriendRequestDto(
                        username = username,
                        federationUrl = federationUrl
                    ))
                    
                    showAddFriendDialog = false
                    
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsRequestSentSuccess))
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsRequestSentError, error))
                    }
                }
            }
        )
    }
}

@Composable
fun FriendsList(
    friends: List<SystemResponse>,
    i18n: I18nState,
    systemService: SystemService,
    authService: AuthService,
    snackbarHostState: SnackbarHostState,
    onFriendClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var friendToRemove by remember { mutableStateOf<SystemResponse?>(null) }

    if (friends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(i18n.text(MessageKey.FriendsEmpty))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(friends) { friend ->
                ListItem(
                    modifier = Modifier.clickable { onFriendClick(friend.id) },
                    headlineContent = { Text(friend.customName ?: friend.username ?: "No Name") },
                    supportingContent = { Text(friend.id) },
                    leadingContent = {
                        MosaicAvatar(
                            avatarUrl = friend.avatarUrl,
                            authService = authService,
                            size = 40.dp
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { 
                            friendToRemove = friend
                        }) {
                            Icon(Icons.Default.PersonRemove, contentDescription = i18n.text(MessageKey.FriendsRemove), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }

        if (friendToRemove != null) {
            var deleteTapCount by remember { mutableIntStateOf(0) }
            AlertDialog(
                onDismissRequest = { 
                    friendToRemove = null
                    deleteTapCount = 0
                },
                title = { Text(i18n.text(MessageKey.FriendsRemove)) },
                text = { 
                    Column {
                        Text(i18n.text(
                            MessageKey.FriendsRemoveConfirmText, 
                            friendToRemove?.customName ?: friendToRemove?.username ?: ""
                        )) 
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
                                val id = friendToRemove?.id
                                if (id != null) {
                                    scope.launch {
                                        val result = systemService.removeFriend(id)
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsRemoveSuccess))
                                        } else {
                                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                            snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsRemoveError, error))
                                        }
                                    }
                                }
                                friendToRemove = null
                                deleteTapCount = 0
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
                        friendToRemove = null
                        deleteTapCount = 0
                    }) {
                        Text(i18n.text(MessageKey.CommonCancel))
                    }
                }
            )
        }
    }
}

@Composable
fun FriendRequestsList(
    requests: List<FriendRequestResponse>,
    i18n: I18nState,
    systemService: SystemService,
    authService: AuthService,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(i18n.text(MessageKey.FriendsRequestsEmpty))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(requests) { request ->
                val sender = request.sender
                ListItem(
                    headlineContent = { Text(sender?.customName ?: sender?.username ?: "Unknown") },
                    supportingContent = { Text(sender?.id ?: "") },
                    leadingContent = {
                        MosaicAvatar(
                            avatarUrl = sender?.avatarUrl,
                            authService = authService,
                            size = 40.dp
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { 
                                scope.launch {
                                    val result = systemService.respondToFriendRequest(request.id, true)
                                    if (result.isSuccess) {
                                        snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsAcceptSuccess))
                                    } else {
                                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                        snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsAcceptError, error))
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = i18n.text(MessageKey.FriendsAccept), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { 
                                scope.launch {
                                    val result = systemService.respondToFriendRequest(request.id, false)
                                    if (result.isSuccess) {
                                        snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsRefuseSuccess))
                                    } else {
                                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                        snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsRefuseError, error))
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = i18n.text(MessageKey.FriendsRefuse), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SentRequestsList(
    requests: List<FriendRequestResponse>,
    i18n: I18nState,
    systemService: SystemService,
    authService: AuthService,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var requestToCancel by remember { mutableStateOf<FriendRequestResponse?>(null) }

    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(i18n.text(MessageKey.FriendsRequestsEmpty))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(requests) { request ->
                val recipient = request.recipient
                ListItem(
                    headlineContent = { Text(recipient?.customName ?: recipient?.username ?: "Unknown") },
                    supportingContent = { Text(recipient?.id ?: "") },
                    leadingContent = {
                        MosaicAvatar(
                            avatarUrl = recipient?.avatarUrl,
                            authService = authService,
                            size = 40.dp
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { 
                            requestToCancel = request
                        }) {
                            Icon(Icons.Default.Close, contentDescription = i18n.text(MessageKey.FriendsCancel), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }

        if (requestToCancel != null) {
            var deleteTapCount by remember { mutableIntStateOf(0) }
            AlertDialog(
                onDismissRequest = { 
                    requestToCancel = null
                    deleteTapCount = 0
                },
                title = { Text(i18n.text(MessageKey.FriendsCancel)) },
                text = { 
                    Column {
                        Text(i18n.text(
                            MessageKey.FriendsCancelRequestConfirmText, 
                            requestToCancel?.recipient?.customName ?: requestToCancel?.recipient?.username ?: ""
                        )) 
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
                                val id = requestToCancel?.id
                                if (id != null) {
                                    scope.launch {
                                        val result = systemService.cancelFriendRequest(id)
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsCancelSuccess))
                                        } else {
                                            val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                            snackbarHostState.showSnackbar(i18n.text(MessageKey.FriendsCancelError, error))
                                        }
                                    }
                                }
                                requestToCancel = null
                                deleteTapCount = 0
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
                        requestToCancel = null
                        deleteTapCount = 0
                    }) {
                        Text(i18n.text(MessageKey.CommonCancel))
                    }
                }
            )
        }
    }
}

@Composable
fun AddFriendDialog(
    i18n: I18nState,
    onDismiss: () -> Unit,
    onSend: (String, String?) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var federationUrl by remember { mutableStateOf("") }
    var showFederationField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.text(MessageKey.FriendsAdd)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(i18n.text(MessageKey.FriendsSearchPlaceholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (showFederationField) {
                    OutlinedTextField(
                        value = federationUrl,
                        onValueChange = { federationUrl = it },
                        label = { Text(i18n.text(MessageKey.FriendsFederationLabel)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("example.com") }
                    )
                }

                TextButton(
                    onClick = { showFederationField = !showFederationField },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        if (showFederationField) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(i18n.text(MessageKey.FriendsOtherServer))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (username.isNotBlank()) {
                        onSend(username, if (showFederationField && federationUrl.isNotBlank()) federationUrl else null)
                    }
                },
                enabled = username.isNotBlank() && (!showFederationField || federationUrl.isNotBlank())
            ) {
                Text(i18n.text(MessageKey.FriendsSendRequest))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.text(MessageKey.CommonCancel))
            }
        }
    )
}
