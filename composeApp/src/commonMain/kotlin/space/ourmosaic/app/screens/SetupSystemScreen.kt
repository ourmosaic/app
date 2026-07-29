package space.ourmosaic.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import space.ourmosaic.app.FilePickerResult
import space.ourmosaic.app.rememberFilePickerLauncher
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.system.*

private enum class ImportSource { SIMPLY_PLURAL, AMPERSAND }
private enum class ImportMethod { API, JSON }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSystemScreen(
    i18n: I18nState,
    currentSystemId: String?,
    onSetupComplete: (String?) -> Unit,
    onSkip: () -> Unit,
    systemService: SystemService,
    sseService: SseService,
    offlineManager: OfflineManager,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()
    
    val userMe by authService.userMe.collectAsState()
    val hasSystem = userMe?.isSystem == true
    
    var isLoading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // États pour le dialogue d'import
    var importSource by remember { mutableStateOf<ImportSource?>(null) }
    var importMethod by remember { mutableStateOf(ImportMethod.API) }
    var apiKey by remember { mutableStateOf("") }
    var jsonText by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<FilePickerResult?>(null) }

    val filePickerLauncher = rememberFilePickerLauncher { result ->
        if (result != null) {
            selectedFile = result
            jsonText = result.content.decodeToString()
        }
    }

    LaunchedEffect(isImporting) {
        if (isImporting) {
            sseService.events.collect { event ->
                if (event.topic == SseTopics.IMPORT) {
                    try {
                        val payload = systemService.json.decodeFromJsonElement<ImportEventPayload>(event.payload)
                        when (payload.event) {
                            ImportEvents.COMPLETED -> {
                                isImporting = false
                                offlineManager.setImporting(false)
                                onSetupComplete(payload.systemId)
                            }
                            ImportEvents.FAILED -> {
                                isImporting = false
                                offlineManager.setImporting(false)
                                errorMessage = "Import failed: ${payload.error}"
                            }
                        }
                    } catch (e: Exception) {
                        println("Error decoding ImportEventPayload: ${e.message}")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (hasSystem) i18n.text(MessageKey.SetupSubSystemTitle) 
                        else i18n.text(MessageKey.SetupSystemTitle)
                    ) 
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (hasSystem) i18n.text(MessageKey.SetupSubSystemDescription)
                       else i18n.text(MessageKey.SetupSystemDescription),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            if (!hasSystem) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = i18n.text(MessageKey.SetupImportantNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        
                        val result = if (hasSystem) {
                            val parentId = userMe?.systems?.find { it.parentSystemId == null }?.id
                                ?: userMe?.system?.parentSystemId
                                ?: userMe?.system?.id

                            if (parentId == null) {
                                Result.failure(Exception("Root system not found"))
                            } else {
                                systemService.createSubSystem(CreateSystemOrSubSystemDto(
                                    parent = parentId
                                )).map { it.id }
                            }
                        } else {
                            authService.createSystem().map { authService.userMe.value?.system?.id ?: "" }
                        }

                        isLoading = false
                        if (result.isSuccess) {
                            onSetupComplete(result.getOrNull())
                        } else {
                            errorMessage = result.exceptionOrNull()?.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (hasSystem) i18n.text(MessageKey.SetupCreateSubSystem)
                    else i18n.text(MessageKey.SetupCreateNew)
                )
            }

            if (!hasSystem) {
                Spacer(modifier = Modifier.height(16.dp))

                // Simply Plural
                OutlinedButton(
                    onClick = { 
                        importSource = ImportSource.SIMPLY_PLURAL
                        importMethod = ImportMethod.API
                        apiKey = ""
                        jsonText = ""
                        importSource = ImportSource.SIMPLY_PLURAL
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n.text(MessageKey.SetupImportSimplyPlural))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ampersand
                OutlinedButton(
                    onClick = { 
                        importSource = ImportSource.AMPERSAND
                        importMethod = ImportMethod.JSON
                        jsonText = ""
                        importSource = ImportSource.AMPERSAND
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(i18n.text(MessageKey.SetupImportAmpersand))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = onSkip,
                enabled = !isLoading
            ) {
                Text(i18n.text(MessageKey.CommonBack))
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    if (isImporting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = i18n.text(MessageKey.SetupImportingMessage),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (importSource != null) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) importSource = null },
            title = { 
                Text(
                    if (importSource == ImportSource.SIMPLY_PLURAL) i18n.text(MessageKey.SetupImportSimplyPlural)
                    else i18n.text(MessageKey.SetupImportAmpersand)
                ) 
            },
            text = {
                Column {
                    Text(i18n.text(MessageKey.SetupImportDescription))
                    
                    if (importSource == ImportSource.SIMPLY_PLURAL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = importMethod == ImportMethod.API,
                                onClick = { importMethod = ImportMethod.API }
                            )
                            Text(i18n.text(MessageKey.SetupImportMethodApi))
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                selected = importMethod == ImportMethod.JSON,
                                onClick = { importMethod = ImportMethod.JSON }
                            )
                            Text(i18n.text(MessageKey.SetupImportMethodJson))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (importMethod == ImportMethod.API) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(i18n.text(MessageKey.SetupApiKeyLabel)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        if (importSource == ImportSource.AMPERSAND) {
                            Text(
                                i18n.text(MessageKey.SetupImportAmpersandNote),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = filePickerLauncher,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(i18n.text(MessageKey.SetupImportFileLabel))
                        }

                        if (selectedFile != null) {
                            Text(
                                text = i18n.text(MessageKey.SetupImportFileSelected, selectedFile!!.name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (jsonText.isNotBlank() && selectedFile == null) {
                             // Fallback or indicator that text was pasted (though we prefer file)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            val source = importSource!!
                            val method = importMethod
                            val data = jsonText
                            importSource = null
                            selectedFile = null
                            
                            val result = when (source) {
                                ImportSource.SIMPLY_PLURAL -> {
                                    if (method == ImportMethod.API) authService.importFromSimplyPlural(apiKey)
                                    else authService.importFromSimplyPluralJson(data)
                                }
                                ImportSource.AMPERSAND -> authService.importFromAmpersand(data)
                            }

                            if (result.isSuccess) {
                                isImporting = true
                                offlineManager.setImporting(true)
                            } else {
                                isLoading = false
                                errorMessage = result.exceptionOrNull()?.message
                            }
                        }
                    },
                    enabled = (if (importMethod == ImportMethod.API) apiKey.isNotBlank() else jsonText.isNotBlank()) && !isLoading
                ) {
                    Text(i18n.text(MessageKey.SetupImportButton))
                }
            },
            dismissButton = {
                TextButton(onClick = { importSource = null }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }
}
