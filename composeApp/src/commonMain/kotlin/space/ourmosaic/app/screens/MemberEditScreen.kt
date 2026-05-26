package space.ourmosaic.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.ResizeOptions
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.system.*
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.utils.Logger
import space.ourmosaic.app.utils.rememberBitmapFromBytes
import space.ourmosaic.app.utils.cropImage
import space.ourmosaic.app.utils.ColorUtils
import space.ourmosaic.app.components.MosaicAvatar
import space.ourmosaic.app.components.ImageCropperDialog
import space.ourmosaic.app.components.ColorPickerDialog
import space.ourmosaic.app.offline.OfflineManager


@Composable
fun GroupTreeItem(
    group: MemberGroup,
    allGroups: List<MemberGroup>,
    selectedGroupIds: Set<String>,
    onToggle: (String) -> Unit,
    level: Int = 0
) {
    val children = allGroups.filter { it.parentId == group.id }

    Column(modifier = Modifier.padding(start = (level * 16).dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(group.id) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selectedGroupIds.contains(group.id),
                onCheckedChange = { onToggle(group.id) }
            )
            Spacer(Modifier.width(8.dp))
            Text(group.name ?: "Unnamed Group", style = MaterialTheme.typography.bodyLarge)
        }

        children.forEach { child ->
            GroupTreeItem(
                group = child,
                allGroups = allGroups,
                selectedGroupIds = selectedGroupIds,
                onToggle = onToggle,
                level = level + 1
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberEditScreen(
    memberId: String,
    i18n: I18nState,
    onBack: () -> Unit,
    systemService: SystemService,
    offlineManager: OfflineManager,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()

    var member by remember { mutableStateOf<MemberResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var avatarUpdateTicket by remember { mutableStateOf(0) }

    // Form states
    var name by remember { mutableStateOf("") }
    var pronouns by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var inDormancy by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PrivacyLevel.PRIVATE) }
    var fieldValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showColorPicker by remember { mutableStateOf(false) }
    var editingColorFieldId by remember { mutableStateOf<String?>(null) } // null = main member color, otherwise = custom field ID

    val allGroups = offlineManager.cachedGroups.collectAsState(emptyList()).value ?: emptyList()
    val customFields = offlineManager.cachedCustomFields.collectAsState(emptyList()).value ?: emptyList()

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

    val cachedMembers by offlineManager.cachedMembers.collectAsState(null)
    member = cachedMembers?.find { it.id == memberId }

    LaunchedEffect(memberId, cachedMembers) {
        val members = cachedMembers
        if (members != null) {
            val m = members.find { it.id == memberId }
            if (m != null && !isSaving) {
                name = m.name
                pronouns = m.pronouns ?: ""
                description = m.description ?: ""
                inDormancy = m.inDormancy
                role = m.role ?: ""
                colorHex = m.color ?: ""
                privacy = m.privacy
                fieldValues = m.customFieldValues.associate { v -> v.customFieldId to v.value }
                val gIds = m.groups.map { g -> g.groupId }.toSet()
                selectedGroupIds = gIds
                initialGroupIds = gIds
            }
            isLoading = false
        }
    }

    LaunchedEffect(memberId) {
        systemService.getMembers()
        systemService.getCustomFields()
        systemService.getGroups()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n.text(MessageKey.EditMember)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val updateDto = UpdateMemberDto(
                                    name = name,
                                    pronouns = pronouns.ifBlank { null },
                                    description = description.ifBlank { null },
                                    role = role.ifBlank { null },
                                    color = colorHex.ifBlank { null },
                                    privacy = privacy,
                                    inDormancy = inDormancy
                                )
                                val updateRes = systemService.updateMember(memberId, updateDto)
                                
                                // Update groups
                                val addedGroups = selectedGroupIds.filter { !initialGroupIds.contains(it) }
                                val removedGroups = initialGroupIds.filter { !selectedGroupIds.contains(it) }

                                if (selectedGroupIds != initialGroupIds) {
                                    systemService.updateMemberGroups(memberId, selectedGroupIds.toList())
                                }

                                fieldValues.forEach { (fieldId, value) ->
                                    val originalValue = member?.customFieldValues?.find { it.customFieldId == fieldId }?.value ?: ""
                                    if (value != originalValue) {
                                        systemService.updateMemberField(memberId, fieldId, value)
                                    }
                                }
                                onBack()
                                isSaving = false
                            }
                        },
                        enabled = !isSaving && name.isNotBlank()
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Icon(Icons.Default.Check, null)
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
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar (Square)
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clickable { imagePicker.launch() },
                    contentAlignment = Alignment.Center
                ) {
                    MosaicAvatar(
                        avatarUrl = member?.avatarUrl,
                        avatarUpdateTicket = avatarUpdateTicket,
                        authService = authService,
                        size = 150.dp
                    )
                    
                    if (isSaving) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(32.dp))
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
                                    val result = systemService.uploadMemberAvatar(memberId, croppedBytes)
                                    result.onSuccess {
                                        avatarUpdateTicket++
                                    }
                                } catch (e: Exception) {
                                    Logger.e("MemberEdit", "Crop/Upload failed: ${e.message}")
                                }
                                isSaving = false
                            }
                        }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(i18n.text(MessageKey.ProfileNameLabel)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pronouns,
                    onValueChange = { pronouns = it },
                    label = { Text(i18n.text(MessageKey.ProfilePronounsLabel)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text(i18n.text(MessageKey.ProfileRoleLabel)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { inDormancy = !inDormancy }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(i18n.text(MessageKey.MemberDormancyLabel))
                    Switch(
                        checked = inDormancy,
                        onCheckedChange = { inDormancy = it }
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(i18n.text(MessageKey.ProfileDescriptionLabel)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                // Main Member Color Selection
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingColorFieldId = null
                            showColorPicker = true
                        },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                i18n.text(MessageKey.SystemColorLabel),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                colorHex.ifBlank { "#000000" },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = ColorUtils.parseHexColor(colorHex),
                                    shape = CircleShape
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }

                if (showColorPicker) {
                    val initialColor = if (editingColorFieldId == null) {
                        colorHex
                    } else {
                        fieldValues[editingColorFieldId!!] ?: "#000000"
                    }

                    ColorPickerDialog(
                        initialColor = initialColor,
                        onDismiss = { showColorPicker = false },
                        onColorSelected = { newColor ->
                            if (editingColorFieldId == null) {
                                colorHex = newColor
                            } else {
                                fieldValues = fieldValues.toMutableMap().apply {
                                    put(editingColorFieldId!!, newColor)
                                }
                            }
                            showColorPicker = false
                        }
                    )
                }

                HorizontalDivider()

                // Privacy Selector
                var showPrivacyMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showPrivacyMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val icon = when (privacy) {
                            PrivacyLevel.PUBLIC -> Icons.Default.Public
                            PrivacyLevel.FRIENDS -> Icons.Default.Group
                            PrivacyLevel.PRIVATE -> Icons.Default.Lock
                        }
                        val label = when (privacy) {
                            PrivacyLevel.PUBLIC -> i18n.text(MessageKey.PrivacyPublic)
                            PrivacyLevel.FRIENDS -> i18n.text(MessageKey.PrivacyFriends)
                            PrivacyLevel.PRIVATE -> i18n.text(MessageKey.PrivacyPrivate)
                        }
                        Icon(icon, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                    DropdownMenu(
                        expanded = showPrivacyMenu,
                        onDismissRequest = { showPrivacyMenu = false }
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
                                    val label = when (level) {
                                        PrivacyLevel.PUBLIC -> i18n.text(MessageKey.PrivacyPublic)
                                        PrivacyLevel.FRIENDS -> i18n.text(MessageKey.PrivacyFriends)
                                        PrivacyLevel.PRIVATE -> i18n.text(MessageKey.PrivacyPrivate)
                                    }
                                    Text(label)
                                },
                                onClick = {
                                    privacy = level
                                    showPrivacyMenu = false
                                }
                            )
                        }
                    }
                }

                if (allGroups.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Groups", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())

                    val rootGroups = allGroups.filter { it.parentId == null }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        rootGroups.forEach { group ->
                            GroupTreeItem(
                                group = group,
                                allGroups = allGroups,
                                selectedGroupIds = selectedGroupIds,
                                onToggle = { id ->
                                    selectedGroupIds = if (selectedGroupIds.contains(id)) {
                                        selectedGroupIds - id
                                    } else {
                                        selectedGroupIds + id
                                    }
                                }
                            )
                        }
                    }
                }

                if (customFields.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Custom Fields", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())

                    customFields.forEach { field ->
                        val value = fieldValues[field.id] ?: ""

                        if (field.type == FieldType.COLOR) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { },
                                label = { Text(field.name) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingColorFieldId = field.id
                                        showColorPicker = true
                                    },
                                readOnly = true,
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                                trailingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(end = 8.dp)
                                            .background(
                                                color = ColorUtils.parseHexColor(value),
                                                shape = CircleShape
                                            )
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            .clickable {
                                                editingColorFieldId = field.id
                                                showColorPicker = true
                                            }
                                    )
                                }
                            )
                        } else {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    fieldValues = fieldValues.toMutableMap().apply { put(field.id, newValue) }
                                },
                                label = { Text(field.name) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = if (field.type == FieldType.LONG_TEXT) 3 else 1
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Delete Button
                var showDeleteConfirm by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(8.dp))
                    Text(i18n.text(MessageKey.CommonDelete))
                }

                if (showDeleteConfirm) {
                    var deleteTapCount by remember { mutableIntStateOf(0) }
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
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
                                            systemService.deleteMember(memberId)
                                            onBack()
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
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text(i18n.text(MessageKey.CommonCancel))
                            }
                        }
                    )
                }
            }
        }
    }
}

