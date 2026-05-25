package space.ourmosaic.app

import platform.UIKit.UIDevice
import com.russhwolf.settings.ExperimentalSettingsImplementation

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun randomUUID(): String = platform.Foundation.NSUUID().UUIDString()

actual fun createSettings(): com.russhwolf.settings.Settings = com.russhwolf.settings.NSUserDefaultsSettings(platform.Foundation.NSUserDefaults.standardUserDefaults)

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createEncryptedSettings(): com.russhwolf.settings.Settings = com.russhwolf.settings.KeychainSettings(service = "space.ourmosaic.app.auth")

