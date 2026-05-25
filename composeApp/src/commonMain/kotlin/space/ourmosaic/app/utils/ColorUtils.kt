package space.ourmosaic.app.utils

import androidx.compose.ui.graphics.Color

object ColorUtils {
    fun parseHexColor(hex: String): Color {
        if (hex.isBlank()) return Color.Transparent
        
        return try {
            val cleanHex = hex.removePrefix("#")
            val longHex = when (cleanHex.length) {
                3 -> {
                    val r = cleanHex[0]
                    val g = cleanHex[1]
                    val b = cleanHex[2]
                    "FF$r$r$g$g$b$b"
                }
                6 -> "FF$cleanHex"
                8 -> cleanHex
                else -> return Color.Transparent
            }
            Color(longHex.toLong(16))
        } catch (e: Exception) {
            Color.Transparent
        }
    }
}
