package space.ourmosaic.app.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class AppSettings {
    private val settings = Settings()
    
    private val HIDE_DORMANT_KEY = "hide_dormant_members"
    private val FRONT_NOTIF_KEY = "show_front_notification"

    private var _hideDormantMembers by mutableStateOf(settings.getBoolean(HIDE_DORMANT_KEY, false))
    var hideDormantMembers: Boolean
        get() = _hideDormantMembers
        set(value) {
            _hideDormantMembers = value
            settings[HIDE_DORMANT_KEY] = value
        }

    private var _showFrontNotification by mutableStateOf(settings.getBoolean(FRONT_NOTIF_KEY, true))
    var showFrontNotification: Boolean
        get() = _showFrontNotification
        set(value) {
            _showFrontNotification = value
            settings[FRONT_NOTIF_KEY] = value
        }
}
