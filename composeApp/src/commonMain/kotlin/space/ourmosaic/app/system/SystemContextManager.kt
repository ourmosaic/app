package space.ourmosaic.app.system

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.ourmosaic.app.auth.AuthService

class SystemContextManager(
    private val settings: Settings = Settings(),
    private val authService: AuthService
) {
    private val _currentSystemId = MutableStateFlow<String?>(settings.getStringOrNull("selected_system_id"))
    val currentSystemId: StateFlow<String?> = _currentSystemId.asStateFlow()

    init {
        // If no system selected, default to the main one from auth
        if (_currentSystemId.value == null) {
            val rootId = settings.getStringOrNull("system_id")
            if (rootId != null) {
                setSystem(rootId)
            }
        }
    }

    fun setSystem(systemId: String?) {
        if (systemId == null) {
            settings.remove("selected_system_id")
        } else {
            settings["selected_system_id"] = systemId
        }
        _currentSystemId.value = systemId
    }

    fun getEffectiveSystemId(): String? {
        val selected = _currentSystemId.value
        val root = settings.getStringOrNull("system_id")
        
        // If the selected system is the root system, we often use null or "@me" in API calls
        return if (selected == root) null else selected
    }
    
    fun isRootSystem(): Boolean {
        val selected = _currentSystemId.value
        val root = settings.getStringOrNull("system_id")
        return selected == null || selected == root
    }
}
