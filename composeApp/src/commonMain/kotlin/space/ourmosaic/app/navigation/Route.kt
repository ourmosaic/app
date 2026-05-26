package space.ourmosaic.app.navigation

import androidx.compose.material.icons.Icons
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
    data object System : Route(Icons.Default.Settings, MessageKey.SystemTitle)
    data object MembersManage : Route(Icons.Default.Groups, MessageKey.MembersManageTitle)
    data class MemberEdit(val memberId: String) : Route(Icons.Default.Edit, MessageKey.MembersManageTitle)
    data object Settings : Route(Icons.Default.Tune, MessageKey.SettingsTitle)
    data object SetupSystem : Route(Icons.Default.Add, MessageKey.SetupSystemTitle)
    data object Friends : Route(Icons.Default.People, MessageKey.FriendsTitle)
    data class FriendSystem(val friendId: String) : Route(Icons.Default.People, MessageKey.FriendsTitle)
    data class MemberDetail(val memberId: String) : Route(Icons.Default.AccountCircle, MessageKey.ProfileTitle)
    data class FriendMemberDetail(val friendId: String, val memberId: String) : Route(Icons.Default.People, MessageKey.FriendsTitle)
    data object BlockedEntities : Route(Icons.Default.Lock, MessageKey.SafetyBlockedEntities)

    companion object {
        val all get() = listOf(Home, Profile, Friends, System, MembersManage, Settings, BlockedEntities)
    }
}

