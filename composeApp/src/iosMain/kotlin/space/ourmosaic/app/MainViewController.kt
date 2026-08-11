package space.ourmosaic.app

import androidx.compose.ui.window.ComposeUIViewController
import space.ourmosaic.app.utils.Logger

fun MainViewController() = ComposeUIViewController { 
    Logger.d("MainViewController", "Starting App")
    App() 
}