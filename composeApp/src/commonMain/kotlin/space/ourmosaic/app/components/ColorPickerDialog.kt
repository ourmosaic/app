package space.ourmosaic.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import space.ourmosaic.app.utils.ColorUtils
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

@Composable
fun ColorPickerDialog(
    initialColor: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    i18n: I18nState,
    title: String = i18n.text(MessageKey.SystemColorLabel),
    confirmText: String = i18n.text(MessageKey.CommonSave),
    dismissText: String = i18n.text(MessageKey.CommonCancel)
) {
    val controller = rememberColorPickerController()
    var selectedColor by remember { mutableStateOf(ColorUtils.parseHexColor(initialColor)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(selectedColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )

                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(10.dp),
                    controller = controller,
                    onColorChanged = { colorEnvelope ->
                        selectedColor = colorEnvelope.color
                    },
                    initialColor = ColorUtils.parseHexColor(initialColor)
                )

                BrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp)
                        .padding(10.dp),
                    controller = controller,
                    initialColor = ColorUtils.parseHexColor(initialColor)
                )
                
                Text(
                    text = "#${selectedColorToHex(selectedColor)}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onColorSelected("#${selectedColorToHex(selectedColor)}")
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

private fun selectedColorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("%02X%02X%02X", r, g, b)
}

private fun String.Companion.format(format: String, vararg args: Any?): String {
    // Basic implementation since String.format might not be available in commonMain
    return args.fold(format) { acc, arg ->
        val replacement = when (arg) {
            is Int -> {
                val hex = arg.toString(16).uppercase()
                if (hex.length < 2) "0$hex" else hex
            }
            else -> arg.toString()
        }
        acc.replaceFirst("%02X", replacement)
    }
}
