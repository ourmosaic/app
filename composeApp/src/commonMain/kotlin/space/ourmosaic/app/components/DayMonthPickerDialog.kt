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
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayMonthPickerDialog(
    initialValue: String?,
    onValueSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    i18n: I18nState
) {
    val parts = initialValue?.split("-") ?: emptyList()
    var dayStr by remember { mutableStateOf(parts.getOrNull(0) ?: "") }
    var monthStr by remember { mutableStateOf(parts.getOrNull(1) ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(i18n.text(MessageKey.FieldTypeDateDayMonth)) },
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
                        value = dayStr,
                        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) dayStr = it },
                        modifier = Modifier.width(80.dp),
                        label = { Text("JJ") },
                        placeholder = { Text("01") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center)
                    )
                    
                    Text("/", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 12.dp))
                    
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
                }
                
                val day = dayStr.toIntOrNull()
                val month = monthStr.toIntOrNull()
                val isValid = day != null && day in 1..31 && month != null && month in 1..12
                
                if (!isValid && (dayStr.isNotEmpty() || monthStr.isNotEmpty())) {
                    Text(
                        text = "Date invalide",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            val day = dayStr.toIntOrNull()
            val month = monthStr.toIntOrNull()
            val isValid = day != null && day in 1..31 && month != null && month in 1..12

            TextButton(
                enabled = isValid,
                onClick = {
                    if (isValid) {
                        onValueSelected("${day!!.toString().padStart(2, '0')}-${month!!.toString().padStart(2, '0')}")
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
