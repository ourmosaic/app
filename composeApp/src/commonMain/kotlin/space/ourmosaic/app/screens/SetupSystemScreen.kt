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
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.system.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSystemScreen(
    i18n: I18nState,
    onSetupComplete: () -> Unit,
    onSkip: () -> Unit,
    systemService: SystemService,
    sseService: SseService,
    offlineManager: OfflineManager,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }

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
                                onSetupComplete()
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
                title = { Text(i18n.text(MessageKey.SetupSystemTitle)) }
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
                text = i18n.text(MessageKey.SetupSystemDescription),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = i18n.text(MessageKey.SetupImportantNote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

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
                        val result = authService.createSystem()
                        isLoading = false
                        if (result.isSuccess) {
                            onSetupComplete()
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
                Text(i18n.text(MessageKey.SetupCreateNew))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(i18n.text(MessageKey.SetupImportSimplyPlural))
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = onSkip,
                enabled = !isLoading
            ) {
                Text(i18n.text(MessageKey.CommonBack)) // Using Back/Cancel for skip
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
                            text = i18n.text(MessageKey.SetupImportingMessage), // Need to add this key or use a literal for now
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showImportDialog = false },
            title = { Text(i18n.text(MessageKey.SetupImportTitle)) },
            text = {
                Column {
                    Text(i18n.text(MessageKey.SetupImportDescription))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(i18n.text(MessageKey.SetupApiKeyLabel)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            showImportDialog = false
                            val result = authService.importFromSimplyPlural(apiKey)
                            if (result.isSuccess) {
                                // result.getOrNull() would be the importId if needed
                                isImporting = true
                                offlineManager.setImporting(true)
                            } else {
                                isLoading = false
                                errorMessage = result.exceptionOrNull()?.message
                            }
                        }
                    },
                    enabled = apiKey.isNotBlank() && !isLoading
                ) {
                    Text(i18n.text(MessageKey.SetupImportButton))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        )
    }
}
