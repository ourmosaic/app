package space.ourmosaic.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.text.style.TextAlign
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.system.CreateSystemOrSubSystemDto
import space.ourmosaic.app.system.CustomField
import space.ourmosaic.app.system.FieldType
import space.ourmosaic.app.system.PrivacyLevel
import space.ourmosaic.app.system.UpdateCustomFieldDefinitionDto
import space.ourmosaic.app.system.SystemResponse
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import space.ourmosaic.app.system.UpdateSystemDto
import space.ourmosaic.app.system.SystemService
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.utils.Logger
import space.ourmosaic.app.utils.rememberBitmapFromBytes
import space.ourmosaic.app.utils.cropImage
import space.ourmosaic.app.utils.ColorUtils
import space.ourmosaic.app.components.MosaicAvatar
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.ResizeOptions
import space.ourmosaic.app.components.ImageCropperDialog
import space.ourmosaic.app.components.rememberReorderableState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(
    systemId: String? = null,
    i18n: I18nState,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    onManageMembers: (String?) -> Unit,
    onNavigateToSubSystem: (String) -> Unit,
    onNavigateToChat: (String?) -> Unit,
    offlineManager: OfflineManager,
    authService: AuthService,
    systemService: SystemService
) {
    val scope = rememberCoroutineScope()
    
    val userMe by offlineManager.cachedUserMe.collectAsState(null)
    val systems by offlineManager.cachedSystems.collectAsState(emptyList())
    
    val system = remember(systemId, userMe, systems) {
        if (systemId == null) {
            userMe?.system ?: systems.find { it.parentSystemId == null }
        } else {
            systems.find { it.id == systemId }
        }
    }

    val subSystems = remember(system, systems) {
        if (system == null) emptyList()
        else systems.filter { it.parentSystemId == system.id }
    }
    
    var isLoading by remember { mutableStateOf(userMe?.system == null && systemId == null) }
    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    val pendingCount by offlineManager.pendingActionsCount.collectAsState()
    
    var customName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("") }
    var frontPrivacy by remember { mutableStateOf(PrivacyLevel.PRIVATE) }
    var avatarUpdateTicket by remember { mutableStateOf(0) }
    
    val customFields by offlineManager.cachedCustomFields.collectAsState(emptyList())
    var localFields by remember { mutableStateOf<List<CustomField>>(emptyList()) }
    
    val reorderableState = rememberReorderableState(
        onMove = { from, to ->
            localFields = localFields.toMutableList().apply {
                add(to, removeAt(from))
            }
        },
        onDragEnd = {
            scope.launch {
                localFields.forEachIndexed { index, field ->
                    if (field.order != index) {
                        systemService.updateCustomField(field.id, UpdateCustomFieldDefinitionDto(order = index), systemId = systemId)
                    }
                }
            }
        }
    )

    LaunchedEffect(localFields) {
        reorderableState.updateKeys(localFields.map { it.id })
    }

    LaunchedEffect(customFields) {
        val fields = customFields
        if (fields != null && reorderableState.draggedKey == null) {
            localFields = fields.sortedBy { it.order }
        }
    }

    val isFieldsLoading = customFields == null

    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    val previewBitmap = rememberBitmapFromBytes(selectedImageBytes)

    val imagePicker = rememberImagePickerLauncher(
        scope = scope,
        selectionMode = SelectionMode.Single,
        resizeOptions = ResizeOptions(
            width = 512,
            height = 512,
            compressionQuality = 0.8
        ),
        onResult = { byteArrays ->
            val bytes = byteArrays.firstOrNull()
            if (bytes != null) {
                selectedImageBytes = bytes
            }
        }
    )

    LaunchedEffect(system) {
        if (system != null) {
            if (!isEditing) {
                customName = system.customName ?: ""
                description = system.description ?: ""
                colorHex = system.color ?: ""
                frontPrivacy = system.frontPrivacy ?: PrivacyLevel.PRIVATE
            }
        }
    }

    LaunchedEffect(Unit) {
        val result = authService.getUserMe()
        result.onSuccess { userResponse ->
            offlineManager.cacheUserMe(userResponse)
        }
        isLoading = false
        systemService.getCustomFields(systemId = systemId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n.text(MessageKey.SystemTitle)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
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
                    if (!isLoading) {
                        if (isEditing) {
                            IconButton(onClick = { isEditing = false }) {
                                Icon(Icons.Default.Close, contentDescription = i18n.text(MessageKey.ProfileCancel))
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        val dto = UpdateSystemDto(
                                            customName = customName.ifBlank { null },
                                            description = description.ifBlank { null },
                                            color = colorHex.ifBlank { null },
                                            frontPrivacy = frontPrivacy
                                        )
                                        systemService.updateSystem(dto, systemId = systemId)
                                        isEditing = false
                                        isSaving = false
                                    }
                                },
                                enabled = !isSaving
                            ) {
                                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                else Icon(Icons.Default.Check, contentDescription = i18n.text(MessageKey.ProfileSave))
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = i18n.text(MessageKey.ProfileEdit))
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (system == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(i18n.text(MessageKey.SetupSystemDescription), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { 
                        scope.launch {
                            isLoading = true
                            authService.createSystem().onSuccess {
                                authService.getUserMe().onSuccess { userResponse ->
                                    offlineManager.cacheUserMe(userResponse)
                                }
                            }
                            isLoading = false
                        }
                    }) {
                        Text(i18n.text(MessageKey.SetupCreateNew))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clickable(enabled = isEditing) { imagePicker.launch() },
                    contentAlignment = Alignment.Center
                ) {
                    MosaicAvatar(
                        avatarUrl = system?.avatarUrl,
                        avatarUpdateTicket = avatarUpdateTicket,
                        authService = authService,
                        size = 120.dp
                    )
                    
                    if (isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White)
                            } else {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }

                if (selectedImageBytes != null && previewBitmap != null) {
                    ImageCropperDialog(
                        bitmap = previewBitmap,
                        onDismiss = { selectedImageBytes = null },
                        onConfirm = { x, y, size ->
                            val bytes = selectedImageBytes!!
                            selectedImageBytes = null
                            scope.launch {
                                isSaving = true
                                try {
                                    val croppedBytes = cropImage(bytes, x, y, size)
                                    val result = systemService.uploadSystemAvatar(croppedBytes, systemId = systemId)
                                    result.onSuccess {
                                        avatarUpdateTicket++
                                    }
                                } catch (e: Exception) {
                                    Logger.e("SystemScreen", "Crop/Upload failed: ${e.message}")
                                }
                                isSaving = false
                            }
                        }
                    )
                }

                if (isEditing) {
                    var showColorPicker by remember { mutableStateOf(false) }
                    
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text(i18n.text(MessageKey.ProfileNameLabel)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(i18n.text(MessageKey.SystemDescriptionLabel)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Spacer(Modifier.height(8.dp))

                    var showPrivacyMenu by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(i18n.text(MessageKey.SystemFrontPrivacyLabel), style = MaterialTheme.typography.labelLarge)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showPrivacyMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                val icon = when (frontPrivacy) {
                                    PrivacyLevel.PUBLIC -> Icons.Default.Public
                                    PrivacyLevel.FRIENDS -> Icons.Default.Group
                                    PrivacyLevel.PRIVATE -> Icons.Default.Lock
                                }
                                Icon(icon, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = when (frontPrivacy) {
                                        PrivacyLevel.PUBLIC -> i18n.text(MessageKey.PrivacyPublic)
                                        PrivacyLevel.FRIENDS -> i18n.text(MessageKey.PrivacyFriends)
                                        PrivacyLevel.PRIVATE -> i18n.text(MessageKey.PrivacyPrivate)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = showPrivacyMenu,
                                onDismissRequest = { showPrivacyMenu = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                PrivacyLevel.entries.forEach { level ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            val icon = when (level) {
                                                PrivacyLevel.PUBLIC -> Icons.Default.Public
                                                PrivacyLevel.FRIENDS -> Icons.Default.Group
                                                PrivacyLevel.PRIVATE -> Icons.Default.Lock
                                            }
                                            Icon(icon, null, modifier = Modifier.size(18.dp))
                                        },
                                        text = {
                                            Text(
                                                when (level) {
                                                    PrivacyLevel.PUBLIC -> i18n.text(MessageKey.PrivacyPublic)
                                                    PrivacyLevel.FRIENDS -> i18n.text(MessageKey.PrivacyFriends)
                                                    PrivacyLevel.PRIVATE -> i18n.text(MessageKey.PrivacyPrivate)
                                                }
                                            )
                                        },
                                        onClick = {
                                            frontPrivacy = level
                                            showPrivacyMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showColorPicker = true }
                            .padding(vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = ColorUtils.parseHexColor(colorHex),
                                    shape = CircleShape
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(i18n.text(MessageKey.SystemColorLabel), style = MaterialTheme.typography.bodyLarge)
                            Text(colorHex.ifBlank { "Aucune" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (showColorPicker) {
                        space.ourmosaic.app.components.ColorPickerDialog(
                            initialColor = colorHex,
                            i18n = i18n,
                            onColorSelected = { 
                                colorHex = it
                                showColorPicker = false 
                            },
                            onDismiss = { showColorPicker = false }
                        )
                    }
                } else {
                    Text(
                        text = system?.customName ?: i18n.text(MessageKey.ProfileSystemName),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (!system?.description.isNullOrBlank()) {
                        Text(
                            text = system?.description ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Button(
                        onClick = { onManageMembers(system?.id) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(i18n.text(MessageKey.MembersManageTitle))
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { onNavigateToChat(system?.id) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(i18n.text(MessageKey.ChatTitle))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    var showCreateSubSystemDialog by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = i18n.text(MessageKey.SystemSubSystemsTitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            showCreateSubSystemDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }

                    if (showCreateSubSystemDialog) {
                        var subSystemName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showCreateSubSystemDialog = false },
                            title = { Text(i18n.text(MessageKey.SystemCreateSubSystemTitle)) },
                            text = {
                                OutlinedTextField(
                                    value = subSystemName,
                                    onValueChange = { subSystemName = it },
                                    label = { Text(i18n.text(MessageKey.SystemCreateSubSystemPlaceholder)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val rootSystem = systems.find { it.parentSystemId == null } ?: userMe?.system
                                        val parentId = rootSystem?.id ?: system?.parentSystemId ?: system?.id

                                        if (parentId != null) {
                                            scope.launch {
                                                systemService.createSubSystem(CreateSystemOrSubSystemDto(
                                                    customName = subSystemName,
                                                    parent = parentId
                                                )).onSuccess {
                                                    systemService.getSystems()
                                                }
                                                showCreateSubSystemDialog = false
                                            }
                                        }
                                    },
                                    enabled = subSystemName.isNotBlank()
                                ) {
                                    Text(i18n.text(MessageKey.CommonCreate))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCreateSubSystemDialog = false }) {
                                    Text(i18n.text(MessageKey.CommonCancel))
                                }
                            }
                        )
                    }

                    if (subSystems.isEmpty()) {
                        Text(
                            i18n.text(MessageKey.SystemSubSystemsEmpty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        subSystems.forEach { sub ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onNavigateToSubSystem(sub.id) }.padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val color = if (!sub.color.isNullOrBlank()) ColorUtils.parseHexColor(sub.color) else MaterialTheme.colorScheme.primary
                                    Box(Modifier.size(12.dp).background(color, CircleShape))
                                    Spacer(Modifier.width(12.dp))
                                    Text(sub.customName ?: i18n.text(MessageKey.ProfileSystemName))
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = i18n.text(MessageKey.SystemCustomFieldsTitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            scope.launch {
                                systemService.createCustomField(systemId = systemId)
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = i18n.text(MessageKey.CustomFieldAdd))
                        }
                    }

                    if (isFieldsLoading && localFields.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (localFields.isEmpty()) {
                        Text(
                            i18n.text(MessageKey.CustomFieldEmpty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var fieldToDelete by remember { mutableStateOf<CustomField?>(null) }

                        Column {
                            localFields.forEachIndexed { index, field ->
                                key(field.id) {
                                    CustomFieldItem(
                                        field = field,
                                        i18n = i18n,
                                        modifier = with(reorderableState) { Modifier.reorderableItem(index, field.id) },
                                        onUpdate = { dto ->
                                            scope.launch {
                                                systemService.updateCustomField(field.id, dto, systemId = systemId)
                                            }
                                        },
                                        onDelete = {
                                            fieldToDelete = field
                                        }
                                    )
                                }
                            }
                        }

                        if (fieldToDelete != null) {
                            var deleteTapCount by remember { mutableIntStateOf(0) }
                            AlertDialog(
                                onDismissRequest = { 
                                    fieldToDelete = null
                                    deleteTapCount = 0
                                },
                                title = { Text(i18n.text(MessageKey.CustomFieldDelete)) },
                                text = {
                                    Column {
                                        Text(i18n.text(MessageKey.DeleteCustomFieldConfirmText))
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
                                                val id = fieldToDelete?.id
                                                if (id != null) {
                                                    scope.launch {
                                                        systemService.deleteCustomField(id, systemId = systemId)
                                                    }
                                                }
                                                fieldToDelete = null
                                                deleteTapCount = 0
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (deleteTapCount >= 4) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(if (deleteTapCount >= 4) i18n.text(MessageKey.CommonDelete) else "${5 - deleteTapCount}...")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { 
                                        fieldToDelete = null
                                        deleteTapCount = 0
                                    }) {
                                        Text(i18n.text(MessageKey.CommonCancel))
                                    }
                                }
                            )
                        }
                    }
                }
                
                Text(
                    text = "ID: ${system?.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
fun FieldType.toDisplayName(i18n: I18nState): String {
    val key = when (this) {
        FieldType.STRING -> MessageKey.FieldTypeString
        FieldType.LONG_TEXT -> MessageKey.FieldTypeLongText
        FieldType.COLOR -> MessageKey.FieldTypeColor
        FieldType.DATE -> MessageKey.FieldTypeDate
        FieldType.NUMBER -> MessageKey.FieldTypeNumber
        FieldType.DATE_DAY_MONTH -> MessageKey.FieldTypeDateDayMonth
        FieldType.DATETIME -> MessageKey.FieldTypeDateTime
        FieldType.DATE_MONTH_YEAR -> MessageKey.FieldTypeDateMonthYear
    }
    return i18n.text(key)
}

@Composable
fun FieldTypeIcon(type: FieldType, tint: Color = LocalContentColor.current) {
    val icon = when (type) {
        FieldType.STRING -> Icons.Default.TextFields
        FieldType.LONG_TEXT -> Icons.AutoMirrored.Filled.Notes
        FieldType.COLOR -> Icons.Default.Palette
        FieldType.DATE, 
        FieldType.DATE_DAY_MONTH, 
        FieldType.DATE_MONTH_YEAR, 
        FieldType.DATETIME -> Icons.Default.Event
        FieldType.NUMBER -> Icons.Default.Numbers
    }
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
}

@Composable
fun CustomFieldItem(
    field: CustomField,
    i18n: I18nState,
    modifier: Modifier = Modifier,
    onUpdate: (UpdateCustomFieldDefinitionDto) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    
    var localName by remember(field.name) { mutableStateOf(field.name) }
    var localType by remember(field.type) { mutableStateOf(field.type) }
    var localPrivacy by remember(field.privacy) { mutableStateOf(field.privacy) }
    var localOrder by remember(field.order) { mutableStateOf(field.order) }
    
    val hasChanges = localName != field.name || 
                     localType != field.type || 
                     localPrivacy != field.privacy || 
                     localOrder != field.order

    val focusManager = LocalFocusManager.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    FieldTypeIcon(field.type, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = field.name.ifBlank { i18n.text(MessageKey.CustomFieldNoName) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${field.type.toDisplayName(i18n)} • ${field.privacy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (hasChanges && isExpanded) {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onUpdate(UpdateCustomFieldDefinitionDto(
                            name = localName,
                            type = localType,
                            privacy = localPrivacy,
                            order = localOrder
                        ))
                        isExpanded = false
                    }) {
                        Icon(Icons.Default.Save, contentDescription = i18n.text(MessageKey.ProfileSave), tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = i18n.text(MessageKey.CustomFieldDelete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    label = { Text(i18n.text(MessageKey.CustomFieldNameLabel)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    
                    var showTypeMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showTypeMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            FieldTypeIcon(localType)
                            Spacer(Modifier.width(8.dp))
                            Text(localType.toDisplayName(i18n), style = MaterialTheme.typography.bodySmall)
                        }
                        DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                            FieldType.entries.forEach { type ->
                                DropdownMenuItem(
                                    leadingIcon = { FieldTypeIcon(type) },
                                    text = { Text(type.toDisplayName(i18n)) },
                                    onClick = {
                                        localType = type
                                        showTypeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    
                    var showPrivacyMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showPrivacyMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            val icon = when(localPrivacy) {
                                PrivacyLevel.PUBLIC -> Icons.Default.Public
                                PrivacyLevel.FRIENDS -> Icons.Default.Group
                                PrivacyLevel.PRIVATE -> Icons.Default.Lock
                            }
                            Icon(icon, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(localPrivacy.name, style = MaterialTheme.typography.bodySmall)
                        }
                        DropdownMenu(expanded = showPrivacyMenu, onDismissRequest = { showPrivacyMenu = false }) {
                            PrivacyLevel.entries.forEach { level ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        val icon = when(level) {
                                            PrivacyLevel.PUBLIC -> Icons.Default.Public
                                            PrivacyLevel.FRIENDS -> Icons.Default.Group
                                            PrivacyLevel.PRIVATE -> Icons.Default.Lock
                                        }
                                        Icon(icon, null, modifier = Modifier.size(18.dp))
                                    },
                                    text = { Text(level.name) },
                                    onClick = {
                                        localPrivacy = level
                                        showPrivacyMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(i18n.text(MessageKey.CustomFieldOrderLabel), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    FilledIconButton(
                        onClick = { localOrder-- },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }
                    Text(localOrder.toString(), modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                    FilledIconButton(
                        onClick = { localOrder++ },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
