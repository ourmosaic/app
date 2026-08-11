package space.ourmosaic.app.utils

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

@Composable
actual fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap? {
    return remember(bytes) {
        if (bytes != null) {
            try {
                Image.makeFromEncoded(bytes).let { 
                    Bitmap.makeFromImage(it).asComposeImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

actual fun getCachePath(): String? {
    return NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
}

@OptIn(ExperimentalForeignApi::class)
actual fun writeToCache(fileName: String, bytes: ByteArray): String? {
    val cacheDir = getCachePath() ?: return null
    val path = "$cacheDir/$fileName"
    val data: NSData = bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    }
    val created = NSFileManager.defaultManager.createFileAtPath(path, data, null)
    return if (created) path else null
}

@OptIn(ExperimentalForeignApi::class)
actual fun readFromCache(fileName: String): ByteArray? {
    val cacheDir = getCachePath() ?: return null
    val path = "$cacheDir/$fileName"
    val url = NSURL.fileURLWithPath(path)
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    val size = data.length.toInt()
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return out
}

@OptIn(ExperimentalForeignApi::class)
actual fun deleteFromCache(fileName: String) {
    val cacheDir = getCachePath() ?: return
    val path = "$cacheDir/$fileName"
    try {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    } catch (_: Throwable) { }
}

actual suspend fun cropImage(
    bytes: ByteArray,
    xOffsetPct: Float,
    yOffsetPct: Float,
    sizePct: Float,
    targetSize: Int
): ByteArray {
    val image = Image.makeFromEncoded(bytes)
    val width = image.width
    val height = image.height

    val size = (minOf(width, height) * sizePct).toInt()
    val x = (width * xOffsetPct).toInt().coerceIn(0, (width - size).coerceAtLeast(0))
    val y = (height * yOffsetPct).toInt().coerceIn(0, (height - size).coerceAtLeast(0))

    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(targetSize, targetSize)
    val canvas = surface.canvas

    val srcRect = org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat())
    val dstRect = org.jetbrains.skia.Rect.makeXYWH(0f, 0f, targetSize.toFloat(), targetSize.toFloat())

    canvas.drawImageRect(image, srcRect, dstRect)
    
    val data = surface.makeImageSnapshot().encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 85)
    return data?.bytes ?: bytes
}
