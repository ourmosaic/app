package space.ourmosaic.app.utils

import kotlinx.datetime.*
import kotlinx.datetime.number
import kotlin.time.Instant

object DateTimeUtils {
    fun formatChatMessageTimestamp(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        
        return try {
            val instant = Instant.parse(isoString)
            val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            
            val isToday = localDateTime.date == now.date
            val isYesterday = localDateTime.date.toEpochDays() == now.date.toEpochDays() - 1
            
            val time = "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
            
            when {
                isToday -> time
                isYesterday -> "Yesterday $time"
                localDateTime.year == now.year -> {
                    "${localDateTime.day} ${localDateTime.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} $time"
                }
                else -> {
                    "${localDateTime.day}/${localDateTime.month.number}/${localDateTime.year} $time"
                }
            }
        } catch (e: Exception) {
            isoString // Fallback to raw string if parsing fails
        }
    }
}
