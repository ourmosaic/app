package space.ourmosaic.app.system

import platform.UserNotifications.*
import platform.Foundation.*

actual fun updateFrontNotification(fronters: List<String>) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    
    if (fronters.isEmpty()) {
        center.removeDeliveredNotificationsWithIdentifiers(listOf("front_status"))
        return
    }

    // On ne demande plus l'autorisation ici, on se contente d'essayer d'afficher
    // Si l'autorisation n'est pas là, ça ne fera rien (comportement iOS standard)
    val content = UNMutableNotificationContent().apply {
        setTitle("Current fronters")
        setBody(fronters.joinToString(", "))
    }
    
    val request = UNNotificationRequest.requestWithIdentifier(
        "front_status",
        content,
        trigger = null
    )
    
    center.addNotificationRequest(request) { _ -> }
}

actual fun showSimpleNotification(title: String, message: String, id: Int?) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(message)
    }
    val request = UNNotificationRequest.requestWithIdentifier(
        id?.toString() ?: NSUUID().UUIDString,
        content,
        trigger = null
    )
    center.addNotificationRequest(request) { _ -> }
}

actual fun startSseBackgroundService() {
    // Background execution on iOS is handled differently (Background Tasks API)
    // For now, we rely on the app being active or standard push notifications.
}

actual fun stopSseBackgroundService() {
    // No-op
}

actual fun requestNotificationPermission() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ ->
        // Callback if needed
    }
}
