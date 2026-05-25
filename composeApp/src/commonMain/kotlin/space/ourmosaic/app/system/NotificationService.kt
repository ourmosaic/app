package space.ourmosaic.app.system

expect fun updateFrontNotification(fronters: List<String>)
expect fun requestNotificationPermission()
expect fun showSimpleNotification(title: String, message: String, id: Int? = null)
expect fun startSseBackgroundService()
expect fun stopSseBackgroundService()
