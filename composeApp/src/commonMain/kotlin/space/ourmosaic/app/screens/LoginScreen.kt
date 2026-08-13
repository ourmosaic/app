package space.ourmosaic.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

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
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
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
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    if (powProgress != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Chargement... (${powProgress})")
                    }
                }
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
    }
}
