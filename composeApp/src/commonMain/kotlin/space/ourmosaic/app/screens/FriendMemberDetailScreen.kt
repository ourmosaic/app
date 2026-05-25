package space.ourmosaic.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.components.MosaicAvatar
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey
import space.ourmosaic.app.system.MemberResponse
import space.ourmosaic.app.utils.ColorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendMemberDetailScreen(
    member: MemberResponse,
    i18n: I18nState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    authService: AuthService
) {
    val userMe by authService.userMe.collectAsState()
    val isOwnMember = userMe?.system?.id == member.systemId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(member.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = i18n.text(MessageKey.CommonBack))
                    }
                },
                actions = {
                    if (isOwnMember) {
                        IconButton(onClick = { onEdit(member.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = i18n.text(MessageKey.CommonEdit))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MosaicAvatar(
                avatarUrl = member.avatarUrl,
                size = 120.dp,
                cornerRadius = 60.dp,
                authService = authService
            )

            Text(
                text = member.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            if (!member.pronouns.isNullOrBlank()) {
                Text(
                    text = member.pronouns,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!member.role.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = member.role,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (!member.color.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(ColorUtils.parseHexColor(member.color), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(member.color, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!member.description.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = i18n.text(MessageKey.ProfileDescriptionLabel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(text = member.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (member.customFieldValues.isNotEmpty()) {
                Text(
                    text = "Custom Fields",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold
                )
                
                member.customFieldValues.forEach { field ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = field.customField?.name ?: "Unknown Field",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (field.customField?.type == space.ourmosaic.app.system.FieldType.COLOR) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(ColorUtils.parseHexColor(field.value), CircleShape)
                                            .padding(end = 8.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = field.value,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
