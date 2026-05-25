package space.ourmosaic.app.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun getSystemLanguageTag(): String {
    val preferredLanguage = NSLocale.preferredLanguages.firstOrNull() as? String
    return preferredLanguage ?: "en"
}

