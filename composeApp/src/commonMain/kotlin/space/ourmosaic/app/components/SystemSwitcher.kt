package space.ourmosaic.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.auth.UserMeResponse
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.navigation.NavState
import space.ourmosaic.app.navigation.Route
import space.ourmosaic.app.system.SystemContextManager
import space.ourmosaic.app.system.SystemResponse

@Composable
fun SystemSwitcher(
    userMe: UserMeResponse?,
    systems: List<SystemResponse>,
    currentSystemId: String?,
    systemContextManager: SystemContextManager,
    navState: NavState,
    i18n: I18nState,
    authService: AuthService,
    onCloseDrawer: () -> Unit
) {
    val allAvailableSystems = remember(userMe, systems) {
        val list = mutableListOf<SystemResponse>()
        userMe?.system?.let { list.add(it) }
        list.addAll(systems)
        list.distinctBy { it.id }
    }

    val currentSystem = remember(currentSystemId, userMe, systems) {
        if (currentSystemId == null) userMe?.system
        else systems.find { it.id == currentSystemId } ?: userMe?.system
    }

    val isSystemContext = remember(navState.currentRoute) {
        navState.currentRoute is Route.System ||
                navState.currentRoute is Route.MembersManage ||
                navState.currentRoute is Route.MemberEdit ||
                navState.currentRoute is Route.Chat ||
                navState.currentRoute is Route.ChatChannel
    }

    val expandedSystems = remember { mutableStateMapOf<String, Boolean>() }

    // Pre-expand to current system
    LaunchedEffect(currentSystem, allAvailableSystems) {
        var curr = currentSystem
        while (curr?.parentSystemId != null) {
            val parentId = curr.parentSystemId
            expandedSystems[parentId] = true
            curr = allAvailableSystems.find { it.id == parentId }
        }
    }

    val isSystem = userMe?.isSystem == true

    if (isSystem) {
        // Current System Header
        currentSystem?.let { system ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MosaicAvatar(
                    avatarUrl = system.avatarUrl,
                    authService = authService,
                    size = 48.dp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = system.customName ?: system.username ?: "System",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (system.parentSystemId != null) {
                        Text(
                            text = "Sub-system",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = i18n.text(MessageKey.SystemTitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
        )

        @Composable
        fun RenderSystemItem(system: SystemResponse, depth: Int = 0) {
            val children = remember(allAvailableSystems, system.id) {
                allAvailableSystems.filter { it.parentSystemId == system.id }
            }
            val isExpanded = expandedSystems[system.id] == true

            NavigationDrawerItem(
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            system.customName ?: system.username ?: "System",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (currentSystem?.id == system.id)
                                MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )

                        if (children.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    expandedSystems[system.id] = !isExpanded
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                                )
                            }
                        }
                    }
                },
                selected = isSystemContext && currentSystem?.id == system.id,
                onClick = {
                    systemContextManager.setSystem(system.id)
                    
                    val targetRoute = when (navState.currentRoute) {
                        is Route.Chat, is Route.ChatChannel -> Route.Chat(system.id)
                        is Route.MembersManage, is Route.MemberEdit -> Route.MembersManage(system.id)
                        else -> Route.System(system.id)
                    }
                    
                    navState.navigateTo(targetRoute)
                    onCloseDrawer()
                },
                icon = {
                    MosaicAvatar(
                        avatarUrl = system.avatarUrl,
                        authService = authService,
                        size = 24.dp
                    )
                },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .padding(start = (depth * 16).dp)
            )

            if (isExpanded) {
                children.forEach { subSystem ->
                    RenderSystemItem(subSystem, depth + 1)
                }
            }
        }

        val roots = allAvailableSystems.filter { system ->
            system.parentSystemId == null || allAvailableSystems.none { it.id == system.parentSystemId }
        }

        if (roots.isEmpty() && userMe.system != null) {
            RenderSystemItem(userMe.system!!)
        } else {
            roots.forEach { rootSystem ->
                RenderSystemItem(rootSystem)
            }
        }

        // Add Sub-system button
        NavigationDrawerItem(
            label = {
                Text(
                    i18n.text(MessageKey.SetupSystemTitle),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            selected = navState.currentRoute is Route.SetupSystem,
            onClick = {
                navState.navigateTo(Route.SetupSystem)
                onCloseDrawer()
            },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .height(48.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}
