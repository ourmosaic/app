package space.ourmosaic.app

import platform.UIKit.UIDevice
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.UIKit.*
import platform.posix.memcpy

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun randomUUID(): String = platform.Foundation.NSUUID().UUIDString()

actual fun createSettings(): com.russhwolf.settings.Settings = com.russhwolf.settings.NSUserDefaultsSettings(platform.Foundation.NSUserDefaults.standardUserDefaults)

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createEncryptedSettings(): com.russhwolf.settings.Settings {
    return try {
        KeychainSettings(service = "space.ourmosaic.app.secure")
    } catch (e: Exception) {
        // Fallback to NSUserDefaults if Keychain is inaccessible (e.g. missing entitlements in dev)
        // This prevents the SIGABRT crash but is less secure.
        NSUserDefaultsSettings(platform.Foundation.NSUserDefaults.standardUserDefaults)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePickerLauncher(onResult: (FilePickerResult?) -> Unit): () -> Unit {
    val delegate = remember {
        object : platform.darwin.NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (url != null) {
                    val data = NSData.dataWithContentsOfURL(url)
                    if (data != null) {
                        val bytes = ByteArray(data.length.toInt())
                        bytes.usePinned { pinned ->
                            memcpy(pinned.addressOf(0), data.bytes, data.length)
                        }
                        val fileName = url.lastPathComponent ?: "file.json"
                        onResult(FilePickerResult(fileName, bytes))
                    } else {
                        onResult(null)
                    }
                } else {
                    onResult(null)
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                onResult(null)
            }
        }
    }

    return {
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.json"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
        )
        picker.delegate = delegate
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(picker, animated = true, completion = null)
    }
}
