package space.ourmosaic.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import space.ourmosaic.app.i18n.MessageKey

sealed class Route(val icon: ImageVector, val titleKey: MessageKey) {
    data object Login : Route(Icons.Default.Lock, MessageKey.LoginTitle)
    data object Home : Route(Icons.Default.Home, MessageKey.HomeTitle)
    data object Profile : Route(Icons.Default.AccountCircle, MessageKey.ProfileTitle)
    data class System(val systemId: String? = null) : Route(Icons.Default.Settings, MessageKey.SystemTitle)
    data class MembersManage(val systemId: String? = null) : Route(Icons.Default.Groups, MessageKey.MembersManageTitle)
    data class MemberEdit(val memberId: String, val systemId: String? = null) : Route(Icons.Default.Edit, MessageKey.MembersManageTitle)
    data object Settings : Route(Icons.Default.Tune, MessageKey.SettingsTitle)
    data object SetupSystem : Route(Icons.Default.Add, MessageKey.SetupSystemTitle)
    data object Friends : Route(Icons.Default.People, MessageKey.FriendsTitle)
    data class FriendSystem(val friendId: String, val initialPage: Int = 0, val currentSystemId: String? = null) : Route(Icons.Default.People, MessageKey.FriendsTitle)
    data class MemberDetail(val memberId: String) : Route(Icons.Default.AccountCircle, MessageKey.ProfileTitle)
    data class FriendMemberDetail(val friendId: String, val memberId: String) : Route(Icons.Default.People, MessageKey.FriendsTitle)
    data object BlockedEntities : Route(Icons.Default.Lock, MessageKey.SafetyBlockedEntities)
    data object CacheDetail : Route(Icons.Default.Tune, MessageKey.SettingsViewCache)
    data object Draw : Route(Icons.Default.Edit, MessageKey.SettingsDraw)
    data class Chat(val systemId: String? = null) : Route(Icons.AutoMirrored.Filled.Chat, MessageKey.ChatTitle)
    data class ChatChannel(val channelId: String, val systemId: String? = null) : Route(Icons.AutoMirrored.Filled.Chat, MessageKey.ChatTitle)

    companion object {
        val all get() = listOf(Home, Profile, Friends, Chat(), System(), MembersManage(), Settings, BlockedEntities)
    }
}

