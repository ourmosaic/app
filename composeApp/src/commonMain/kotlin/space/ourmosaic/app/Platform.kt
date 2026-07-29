package space.ourmosaic.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun randomUUID(): String

expect fun createSettings(): com.russhwolf.settings.Settings

expect fun createEncryptedSettings(): com.russhwolf.settings.Settings

data class FilePickerResult(val name: String, val content: ByteArray)

@androidx.compose.runtime.Composable
expect fun rememberFilePickerLauncher(onResult: (FilePickerResult?) -> Unit): () -> Unit

