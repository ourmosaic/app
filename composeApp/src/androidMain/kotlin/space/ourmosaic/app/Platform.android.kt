package space.ourmosaic.app

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun randomUUID(): String = java.util.UUID.randomUUID().toString()

actual fun createSettings(): com.russhwolf.settings.Settings = 
    com.russhwolf.settings.SharedPreferencesSettings(
        MosaicApplication.INSTANCE.getSharedPreferences("mosaic_settings", android.content.Context.MODE_PRIVATE)
    )

actual fun createEncryptedSettings(): com.russhwolf.settings.Settings {
    val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
    val sharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
        "mosaic_secure_settings",
        masterKeyAlias,
        MosaicApplication.INSTANCE,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    return com.russhwolf.settings.SharedPreferencesSettings(sharedPreferences)
}


