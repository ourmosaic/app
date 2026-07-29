package space.ourmosaic.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.ktor.client.request.header
import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import space.ourmosaic.app.auth.AuthService
import space.ourmosaic.app.utils.Logger

@Composable
fun MosaicAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    cornerRadius: Dp = 12.dp,
    avatarUpdateTicket: Int = 0,
    contentScale: ContentScale = ContentScale.Crop,
    authService: AuthService
) {
    val processedUrl = remember(avatarUrl) {
        if (avatarUrl.isNullOrBlank() || (avatarUrl.contains("undefined") && !avatarUrl.contains("undefined:undefined"))) {
            null
        } else if (avatarUrl.contains("undefined:undefined")) {
            val federation = authService.getFederation() ?: "api.ourmosaic.space"
            avatarUrl.replace("http://undefined:undefined", "https://$federation")
        } else {
            avatarUrl
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (processedUrl != null) {
            if (processedUrl.startsWith("cache://")) {
                val fileName = processedUrl.substring(8)
                val bitmap = space.ourmosaic.app.utils.rememberBitmapFromBytes(space.ourmosaic.app.utils.readFromCache(fileName))
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(size / 2.5f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val cacheKey = remember(processedUrl, avatarUpdateTicket) {
                    val separator = if (processedUrl.contains("?")) "&" else "?"
                    if (avatarUpdateTicket > 0) "$processedUrl${separator}t=$avatarUpdateTicket" else processedUrl
                }
                KamelImage(
                    resource = { 
                        asyncPainterResource(cacheKey) {
                            requestBuilder {
                                header(HttpHeaders.CacheControl, "max-age=31536000") // 1 year
                            }
                        }
                    },
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    onFailure = { error ->
                        Logger.e("MosaicAvatar", "KamelImage Error: ${error.message}")
                        // Fallback to cached version without ticket if it exists
                        Icon(
                            Icons.Default.Person,
                            null,
                            modifier = Modifier.size(size / 2.5f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        } else {
            Icon(
                Icons.Default.Person,
                null,
                modifier = Modifier.size(size / 2.5f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
