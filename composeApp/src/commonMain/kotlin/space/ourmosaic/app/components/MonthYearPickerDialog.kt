package space.ourmosaic.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPickerDialog(
    initialValue: String?,
    onValueSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    i18n: I18nState
) {
    val parts = initialValue?.split("-") ?: emptyList()
    val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    
    var monthStr by remember { mutableStateOf(parts.getOrNull(0) ?: "") }
    var yearStr by remember { mutableStateOf(parts.getOrNull(1) ?: now.year.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.text(MessageKey.FieldTypeDateMonthYear)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = monthStr,
                        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) monthStr = it },
                        modifier = Modifier.width(80.dp),
                        label = { Text("MM") },
                        placeholder = { Text("01") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center)
                    )
                    
                    Text("/", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 12.dp))
                    
                    OutlinedTextField(
                        value = yearStr,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) yearStr = it },
                        modifier = Modifier.width(120.dp),
                        label = { Text("YYYY") },
                        placeholder = { Text("2024") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center)
                    )
                }
                
                val month = monthStr.toIntOrNull()
                val year = yearStr.toIntOrNull()
                val isValid = month != null && month in 1..12 && year != null && year > 0
                
                if (!isValid && (monthStr.isNotEmpty() || yearStr.isNotEmpty())) {
                    Text(
                        text = "Date invalide",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            val month = monthStr.toIntOrNull()
            val year = yearStr.toIntOrNull()
            val isValid = month != null && month in 1..12 && year != null && year > 0

            TextButton(
                enabled = isValid,
                onClick = {
                    if (isValid) {
                        onValueSelected("${month.toString().padStart(2, '0')}-${year}")
                    }
                }
            ) {
                Text(i18n.text(MessageKey.CommonSave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(i18n.text(MessageKey.CommonCancel))
            }
        }
    )
}
