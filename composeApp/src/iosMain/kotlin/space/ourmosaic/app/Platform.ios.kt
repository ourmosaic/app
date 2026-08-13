package space.ourmosaic.app

import platform.UIKit.UIDevice
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.posix.memcpy
import platform.CoreCrypto.CC_MD5

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val versionName: String = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "unknown"
    override val versionCode: Int = (NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 0
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun randomUUID(): String = platform.Foundation.NSUUID().UUIDString()

@OptIn(ExperimentalForeignApi::class)
actual fun md5(input: String): String {
    val data = input.encodeToByteArray()
    val digest = UByteArray(16)
    data.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_MD5(inputPinned.addressOf(0), data.size.toUInt(), digestPinned.addressOf(0))
        }
    }
    return digest.joinToString("") { it.toString(16).padStart(2, '0') }
}

actual fun createSettings(): com.russhwolf.settings.Settings = com.russhwolf.settings.NSUserDefaultsSettings(platform.Foundation.NSUserDefaults.standardUserDefaults)

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createEncryptedSettings(): com.russhwolf.settings.Settings {
    return try {
        KeychainSettings(service = "space.ourmosaic.app")
    } catch (e: Throwable) {
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
