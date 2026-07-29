package space.ourmosaic.app.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import space.ourmosaic.app.utils.Logger
import space.ourmosaic.app.MainActivity
import android.Manifest

import android.app.PendingIntent
import kotlin.random.Random

private const val CHANNEL_ID = "front_status_channel"
private const val NOTIFICATION_ID = 1001

private var appContext: Context? = null

fun initNotificationService(context: Context) {
    appContext = context.applicationContext
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Front Status"
        val descriptionText = "Shows current fronters"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(false)
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

actual fun updateFrontNotification(fronters: List<String>) {
    val context = appContext ?: return
    
    val intent = Intent(context, FrontForegroundService::class.java).apply {
        putStringArrayListExtra("fronters", ArrayList(fronters))
    }

    if (fronters.isEmpty()) {
        context.stopService(intent)
    } else {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Sécurité pour les cas où l'app est en background et ne peut plus lancer de service
            Logger.e("NotificationService", "Failed to start service", e)
        }
    }
}

actual fun requestNotificationPermission() {
    val context = appContext ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Note: We need the activity to request permissions. 
            // Since we're in a KMP project, we might need a better way to get the current activity,
            // but for now, we can try to use the context if it's an activity or just skip.
            (context as? android.app.Activity)?.let {
                ActivityCompat.requestPermissions(
                    it,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}

actual fun showSimpleNotification(title: String, message: String, id: Int?) {
    val context = appContext ?: return
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    notificationManager.notify(id ?: Random.nextInt(), notification)
}

actual fun startSseBackgroundService(systemId: String?) {
    val context = appContext ?: return
    val intent = Intent(context, SseForegroundService::class.java).apply {
        putExtra("system_id", systemId)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

actual fun stopSseBackgroundService() {
    val context = appContext ?: return
    val intent = Intent(context, SseForegroundService::class.java)
    context.stopService(intent)
}
