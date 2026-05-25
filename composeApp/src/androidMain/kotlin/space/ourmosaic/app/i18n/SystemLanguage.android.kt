package space.ourmosaic.app.i18n

import java.util.Locale

actual fun getSystemLanguageTag(): String = Locale.getDefault().toLanguageTag()

