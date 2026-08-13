package space.ourmosaic.app

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val versionName: String by lazy {
        try {
            val context = MosaicApplication.INSTANCE
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    override val versionCode: Int by lazy {
        try {
            val context = MosaicApplication.INSTANCE
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun randomUUID(): String = java.util.UUID.randomUUID().toString()

actual fun md5(input: String): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

actual fun createSettings(): com.russhwolf.settings.Settings = 
    com.russhwolf.settings.SharedPreferencesSettings(
        MosaicApplication.INSTANCE.getSharedPreferences("mosaic_settings", android.content.Context.MODE_PRIVATE)
    )

actual fun createEncryptedSettings(): com.russhwolf.settings.Settings {
    return try {
        val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
            "mosaic_secure_settings",
            masterKeyAlias,
            MosaicApplication.INSTANCE,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        com.russhwolf.settings.SharedPreferencesSettings(sharedPreferences)
    } catch (e: Exception) {
        // En cas d'erreur de tag AEAD ou corruption du Keystore, on réinitialise les réglages sécurisés
        space.ourmosaic.app.utils.Logger.e("Platform", "Failed to create encrypted settings, clearing...", e)
        try {
            val context = MosaicApplication.INSTANCE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.deleteSharedPreferences("mosaic_secure_settings")
            } else {
                context.getSharedPreferences("mosaic_secure_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
            }
            
            val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
            val sharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
                "mosaic_secure_settings",
                masterKeyAlias,
                context,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            com.russhwolf.settings.SharedPreferencesSettings(sharedPreferences)
        } catch (e2: Exception) {
            // Fallback ultime sur des paramètres non-encryptés si le Keystore est totalement cassé
            space.ourmosaic.app.utils.Logger.e("Platform", "Critical failure in EncryptedSettings fallback", e2)
            createSettings()
        }
    }
}

@androidx.compose.runtime.Composable
actual fun rememberFilePickerLauncher(onResult: (FilePickerResult?) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "file.json"

                if (bytes != null) {
                    onResult(FilePickerResult(fileName, bytes))
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                space.ourmosaic.app.utils.Logger.e("Platform", "Failed to read picked file", e)
                onResult(null)
            }
        } else {
            onResult(null)
        }
    }
    return { launcher.launch("application/json") }
}


