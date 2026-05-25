package space.ourmosaic.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import space.ourmosaic.app.MainActivity

class FrontForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fronters = intent?.getStringArrayListExtra("fronters") ?: emptyList<String>()
        
        if (fronters.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // On crée une notification par défaut pour satisfaire Android immédiatement
        val notification = createNotification(fronters)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            // Handle ForegroundServiceStartNotAllowedException on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                space.ourmosaic.app.utils.Logger.e("FrontForegroundService", "Foreground service start not allowed", e)
            } else {
                space.ourmosaic.app.utils.Logger.e("FrontForegroundService", "Error starting foreground service", e)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun createNotification(fronters: List<String>): Notification {
        val contentText = if (fronters.isEmpty()) "No one is fronting" else fronters.joinToString(", ")
        val channelId = "front_status_channel"

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("target_route", "members_manage")
            // Crucial pour que l'extra soit mis à jour si l'app est déjà ouverte
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val pendingIntent = if (notificationIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Current fronters")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .apply {
                if (pendingIntent != null) {
                    setContentIntent(pendingIntent)
                }
            }
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
