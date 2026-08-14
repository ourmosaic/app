package space.ourmosaic.app.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable

@Serializable
data class SerializedOffset(val x: Float, val y: Float)

@Serializable
data class SerializedPath(val points: List<SerializedOffset>)

class AppSettings {
    private val settings = Settings()
    
    private val HIDE_DORMANT_KEY = "hide_dormant_members"
    private val FRONT_NOTIF_KEY = "show_front_notification"
    private val HIDE_IN_FOLDERS_KEY = "hide_members_in_folders_at_root"
    private val SELECTED_SYSTEM_ID_KEY = "selected_system_id"
    private val DRAWING_DATA_KEY = "drawing_data"

    private val json = Json { ignoreUnknownKeys = true }

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

    private var _hideMembersInFoldersAtRoot by mutableStateOf(settings.getBoolean(HIDE_IN_FOLDERS_KEY, false))
    var hideMembersInFoldersAtRoot: Boolean
        get() = _hideMembersInFoldersAtRoot
        set(value) {
            _hideMembersInFoldersAtRoot = value
            settings[HIDE_IN_FOLDERS_KEY] = value
        }

    private var _selectedSystemId by mutableStateOf(settings.getStringOrNull(SELECTED_SYSTEM_ID_KEY))
    var selectedSystemId: String?
        get() = _selectedSystemId
        set(value) {
            _selectedSystemId = value
            if (value == null) {
                settings.remove(SELECTED_SYSTEM_ID_KEY)
            } else {
                settings[SELECTED_SYSTEM_ID_KEY] = value
            }
        }

    private var _drawingData by mutableStateOf(settings.getStringOrNull(DRAWING_DATA_KEY))
    var drawingData: List<SerializedPath>
        get() {
            return try {
                _drawingData?.let { json.decodeFromString<List<SerializedPath>>(it) } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val serialized = json.encodeToString(value)
            _drawingData = serialized
            settings[DRAWING_DATA_KEY] = serialized
        }
}
