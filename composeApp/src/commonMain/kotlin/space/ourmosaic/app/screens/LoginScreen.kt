package space.ourmosaic.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

import space.ourmosaic.app.getPlatform

@Composable
fun DrawingDialog(i18n: I18nState, powProgress: Int, difficulty: Int? = null) {
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    val platform = remember { getPlatform() }
    val showDifficulty = remember(platform.versionName) {
        platform.versionName.contains("-debug") || platform.versionName.contains("-beta")
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = i18n.text(MessageKey.PowTitle),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (showDifficulty && difficulty != null) {
                    Text(
                        text = "Difficulty: $difficulty",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = i18n.text(MessageKey.PowDescription),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPath?.lineTo(change.position.x, change.position.y)
                                        
                                        // Force recomposition
                                        val p = currentPath
                                        currentPath = null
                                        currentPath = p
                                    },
                                    onDragEnd = {
                                        currentPath?.let { paths = paths + it }
                                        currentPath = null
                                    }
                                )
                            }
                    ) {
                        paths.forEach { path ->
                            drawPath(
                                path = path,
                                color = Color.Gray,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        currentPath?.let { path ->
                            drawPath(
                                path = path,
                                color = Color.Gray,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    IconButton(
                        onClick = { paths = emptyList() },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = i18n.text(MessageKey.PowEffacer))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = i18n.text(MessageKey.PowCalculEnCours, powProgress),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    i18n: I18nState,
    authService: AuthService,
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var federation by remember { mutableStateOf("api.ourmosaic.space") }
    
    var isLoading by remember { mutableStateOf(false) }
    var powProgress by remember { mutableStateOf<Int?>(null) }
    var powDifficulty by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (isRegisterMode && powProgress != null) {
        DrawingDialog(i18n, powProgress!!, powDifficulty)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isRegisterMode) i18n.text(MessageKey.RegisterButton) else i18n.text(MessageKey.LoginTitle),
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(if (isRegisterMode) "Username" else i18n.text(MessageKey.LoginUsername)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                if (isRegisterMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        enabled = !isLoading
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(i18n.text(MessageKey.LoginPassword)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = federation,
                    onValueChange = { federation = it },
                    label = { Text(i18n.text(MessageKey.LoginFederation)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            powProgress = null
                            
                            try {
                                val result = if (isRegisterMode) {
                                    val challengeResult = authService.getPowChallenge(federation)
                                    if (challengeResult.isFailure) {
                                        throw challengeResult.exceptionOrNull() ?: Exception("Failed to get challenge")
                                    }
                                    val challenge = challengeResult.getOrThrow()
                                    powDifficulty = challenge.difficulty
                                    
                                    powProgress = 0
                                    
                                    val solution = withContext(Dispatchers.Default) {
                                        authService.solvePow(challenge) { tries ->
                                            scope.launch { powProgress = tries }
                                        }
                                    }
                                    
                                    val verifyResult = authService.verifyPowChallenge(federation, challenge.id, solution)
                                    if (verifyResult.isFailure) {
                                        throw verifyResult.exceptionOrNull() ?: Exception("Failed to verify PoW")
                                    }
                                    val powToken = verifyResult.getOrThrow()
                                    
                                    authService.register(federation, username, email, password, powToken)
                                } else {
                                    authService.login(federation, username, password)
                                }
                                
                                if (result.isSuccess) {
                                    onLoginSuccess()
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "An error occurred"
                            } finally {
                                isLoading = false
                                powProgress = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank() && (!isRegisterMode || email.isNotBlank())
                ) {
                    if (isLoading && !isRegisterMode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isRegisterMode) i18n.text(MessageKey.RegisterButton) else i18n.text(MessageKey.LoginButton))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { isRegisterMode = !isRegisterMode },
                    enabled = !isLoading
                ) {
                    Text(if (isRegisterMode) i18n.text(MessageKey.RegisterHasAccount) else i18n.text(MessageKey.LoginNoAccount))
                }
            }

            val platform = remember { getPlatform() }
            Text(
                text = "v${platform.versionName} (${platform.versionCode})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}
