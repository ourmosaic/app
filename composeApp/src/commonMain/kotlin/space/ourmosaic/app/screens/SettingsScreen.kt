package space.ourmosaic.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.i18n.AppLanguage
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.getPlatform
import space.ourmosaic.app.system.AppSettings
import space.ourmosaic.app.system.requestNotificationPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    i18n: I18nState,
    appSettings: AppSettings,
    currentSystemId: String?,
    onOpenDrawer: () -> Unit,
    onLogout: () -> Unit,
    offlineManager: space.ourmosaic.app.offline.OfflineManager,
    systemService: space.ourmosaic.app.system.SystemService,
    authService: AuthService,
    onNavigate: (space.ourmosaic.app.navigation.Route) -> Unit,
    theme: space.ourmosaic.app.AppTheme,
    onThemeChange: (space.ourmosaic.app.AppTheme) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n.text(MessageKey.SettingsTitle)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Section Langue
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = i18n.text(MessageKey.LanguageLabel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                LanguageRadioGroup(i18n = i18n)
            }

            // Section Theme
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = i18n.text(MessageKey.ThemeLabel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                ThemeRadioGroup(i18n = i18n, currentTheme = theme, onThemeChange = onThemeChange)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = i18n.text(MessageKey.SettingsHideDormantMembers),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = appSettings.hideDormantMembers,
                        onCheckedChange = { appSettings.hideDormantMembers = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = i18n.text(MessageKey.SettingsHideMembersInFoldersAtRoot),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = appSettings.hideMembersInFoldersAtRoot,
                        onCheckedChange = { appSettings.hideMembersInFoldersAtRoot = it }
                    )
                }
            }

            // Section Notifications
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = i18n.text(MessageKey.SettingsShowFrontNotification),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = appSettings.showFrontNotification,
                        onCheckedChange = { 
                            appSettings.showFrontNotification = it
                            if (it) {
                                requestNotificationPermission()
                            }
                        }
                    )
                }
            }

            // Section Cache
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = i18n.text(MessageKey.SettingsClearCacheLabel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = {
                        offlineManager.clearAllData()
                        scope.launch {
                            systemService.getMembers(currentSystemId)
                            systemService.getFrontSessions(currentSystemId)
                            systemService.getGroups(currentSystemId)
                            systemService.getCustomFields(currentSystemId)
                            authService.getUserMe().getOrNull()?.let { offlineManager.cacheUserMe(it) }
                            snackbarHostState.showSnackbar(i18n.text(MessageKey.SettingsClearCacheSuccess))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(i18n.text(MessageKey.SettingsClearCacheButton))
                }
            }

            // Section Safety
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = i18n.text(MessageKey.SafetyTitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = { onNavigate(space.ourmosaic.app.navigation.Route.BlockedEntities) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(i18n.text(MessageKey.SafetyBlockedEntities))
                }
            }

            // Section Technical
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = i18n.text(MessageKey.SettingsTechnical),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = { onNavigate(space.ourmosaic.app.navigation.Route.CacheDetail) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(i18n.text(MessageKey.SettingsViewCache))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Section Logout
            Button(
                onClick = {
                    scope.launch {
                        authService.logout(offlineManager)
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(i18n.text(MessageKey.LogoutButton))
            }

            Spacer(Modifier.height(16.dp))

            val platform = remember { getPlatform() }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "v${platform.versionName} (${platform.versionCode})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun ThemeRadioGroup(i18n: I18nState, currentTheme: space.ourmosaic.app.AppTheme, onThemeChange: (space.ourmosaic.app.AppTheme) -> Unit) {
    val options = listOf(
        space.ourmosaic.app.AppTheme.System to i18n.text(MessageKey.ThemeSystem),
        space.ourmosaic.app.AppTheme.Light to i18n.text(MessageKey.ThemeLight),
        space.ourmosaic.app.AppTheme.Dark to i18n.text(MessageKey.ThemeDark)
    )

    Column(Modifier.selectableGroup()) {
        options.forEach { (theme, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = (currentTheme == theme),
                        onClick = { onThemeChange(theme) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (currentTheme == theme),
                    onClick = null
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
fun LanguageRadioGroup(i18n: I18nState) {
    val options = listOf(
        AppLanguage.System to i18n.text(MessageKey.LanguageSystem),
        AppLanguage.French to i18n.text(MessageKey.LanguageFrench),
        AppLanguage.English to i18n.text(MessageKey.LanguageEnglish)
    )

    Column(Modifier.selectableGroup()) {
        options.forEach { (language, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = (i18n.appLanguage == language),
                        onClick = { i18n.appLanguage = language },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (i18n.appLanguage == language),
                    onClick = null // null because of selectable modifier
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

