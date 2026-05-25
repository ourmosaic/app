package space.ourmosaic.app.utils

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap

private var appContext: Context? = null

fun initImageUtils(context: Context) {
    appContext = context.applicationContext
}

@Composable
actual fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap? {
    return remember(bytes) {
        if (bytes != null) {
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

actual fun getCachePath(): String? = appContext?.cacheDir?.absolutePath

actual fun writeToCache(fileName: String, bytes: ByteArray): String? {
    val dir = appContext?.cacheDir ?: return null
    val file = java.io.File(dir, fileName)
    try {
        file.writeBytes(bytes)
        return file.absolutePath
    } catch (e: Exception) {
        return null
    }
}

actual fun readFromCache(fileName: String): ByteArray? {
    val dir = appContext?.cacheDir ?: return null
    val file = java.io.File(dir, fileName)
    return if (file.exists()) file.readBytes() else null
}

actual fun deleteFromCache(fileName: String) {
    val dir = appContext?.cacheDir ?: return
    val file = java.io.File(dir, fileName)
    if (file.exists()) file.delete()
}

actual suspend fun cropImage(
    bytes: ByteArray,
    xOffsetPct: Float,
    yOffsetPct: Float,
    sizePct: Float,
    targetSize: Int
): ByteArray {
    val options = BitmapFactory.Options().apply { inMutable = true }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val width = bitmap.width
    val height = bitmap.height

    // Calculate side length based on the smaller dimension
    var size = (minOf(width, height) * sizePct).toInt()
    if (size < 1) size = 1
    
    // Ensure size doesn't exceed actual dimensions
    size = minOf(size, width, height)

    // Calculate coordinates and ensure the window stays within bounds
    val x = (width * xOffsetPct).toInt().coerceIn(0, (width - size).coerceAtLeast(0))
    val y = (height * yOffsetPct).toInt().coerceIn(0, (height - size).coerceAtLeast(0))

    // Final check before creating bitmap to avoid the crash
    val safeX = if (x + size > width) (width - size).coerceAtLeast(0) else x
    val safeY = if (y + size > height) (height - size).coerceAtLeast(0) else y

    val cropped = android.graphics.Bitmap.createBitmap(bitmap, safeX, safeY, size, size)
    val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)

    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
    return out.toByteArray()
}
