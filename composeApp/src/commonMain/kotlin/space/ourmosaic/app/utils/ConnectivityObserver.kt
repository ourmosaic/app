package space.ourmosaic.app.utils

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

interface ConnectivityObserver {
    fun observe(): Flow<Status>

    enum class Status {
        Available, Unavailable, Losing, Lost
    }
}

@Composable
expect fun rememberConnectivityObserver(): ConnectivityObserver
