package space.ourmosaic.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class IosConnectivityObserver : ConnectivityObserver {
    override fun observe(): Flow<ConnectivityObserver.Status> {
        return flowOf(ConnectivityObserver.Status.Available)
    }
}

@Composable
actual fun rememberConnectivityObserver(): ConnectivityObserver {
    return remember { IosConnectivityObserver() }
}
