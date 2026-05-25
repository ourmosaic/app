package space.ourmosaic.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import space.ourmosaic.app.i18n.AppLanguage
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

@Composable
fun LanguageSwitcher(i18n: I18nState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LanguageButton(
            label = i18n.text(MessageKey.LanguageSystem),
            isSelected = i18n.appLanguage == AppLanguage.System,
            onClick = { i18n.appLanguage = AppLanguage.System },
        )
        LanguageButton(
            label = i18n.text(MessageKey.LanguageFrench),
            isSelected = i18n.appLanguage == AppLanguage.French,
            onClick = { i18n.appLanguage = AppLanguage.French },
        )
        LanguageButton(
            label = i18n.text(MessageKey.LanguageEnglish),
            isSelected = i18n.appLanguage == AppLanguage.English,
            onClick = { i18n.appLanguage = AppLanguage.English },
        )
    }
}

@Composable
private fun LanguageButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val buttonLabel = if (isSelected) "[$label]" else label
    Button(onClick = onClick) {
        Text(buttonLabel)
    }
}

