package space.ourmosaic.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap?

expect fun getCachePath(): String?
expect fun writeToCache(fileName: String, bytes: ByteArray): String?
expect fun readFromCache(fileName: String): ByteArray?
expect fun deleteFromCache(fileName: String)

expect suspend fun cropImage(
    bytes: ByteArray,
    xOffsetPct: Float,
    yOffsetPct: Float,
    sizePct: Float,
    targetSize: Int = 512
): ByteArray
