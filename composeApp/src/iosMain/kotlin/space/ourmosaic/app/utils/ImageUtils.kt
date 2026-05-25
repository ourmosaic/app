package space.ourmosaic.app.utils

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

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

actual fun writeToCache(fileName: String, bytes: ByteArray): String? {
    // TODO: Implement for iOS if needed for full parity, but we focus on Android for now
    return null
}

actual fun readFromCache(fileName: String): ByteArray? = null
actual fun deleteFromCache(fileName: String) {}

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
