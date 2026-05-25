package space.ourmosaic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import space.ourmosaic.app.system.initNotificationService
import space.ourmosaic.app.system.updateFrontNotification
import space.ourmosaic.app.system.FrontSession
import space.ourmosaic.app.system.MemberResponse
import space.ourmosaic.app.offline.OfflineManager
import space.ourmosaic.app.utils.initImageUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        io.kamel.core.applicationContext = this.applicationContext
        super.onCreate(savedInstanceState)
        
        initNotificationService(this)
        initImageUtils(this)
        checkNotificationPermission()
        startFrontNotificationEarly()

        val targetRoute = intent?.getStringExtra("target_route")

        setContent {
            App(initialTargetRoute = targetRoute)
        }
    }

    private fun startFrontNotificationEarly() {
        val settings = com.russhwolf.settings.Settings()
        val isEnabled = settings.getBoolean("show_front_notification", true)
        val token = settings.getStringOrNull("access_token")

        if (isEnabled && token != null) {
            val offlineManager = OfflineManager(settings)
            val sessions = offlineManager.getCachedFrontSessions()
            val members = offlineManager.getCachedMembers()

            val active = sessions?.filter { it.endTime == null } ?: emptyList()
            if (active.isNotEmpty()) {
                val fronterNames = active.map { session ->
                    session.member?.name
                        ?: members?.find { it.id == session.memberId }?.name
                        ?: session.memberId
                }
                updateFrontNotification(fronterNames)
            }
        }
    }

    private fun checkNotificationPermission() {
        val settings = com.russhwolf.settings.Settings()
        val isEnabled = settings.getBoolean("show_front_notification", true)

        if (isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}