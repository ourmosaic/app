package space.ourmosaic.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import kotlin.time.Instant
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialValue: String?,
    onValueSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    i18n: I18nState
) {
    var showTimePicker by remember { mutableStateOf(false) }
    
    val initialInstant = try {
        initialValue?.let { kotlin.time.Instant.parse(it) } ?: kotlin.time.Clock.System.now()
    } catch (e: Exception) {
        kotlin.time.Clock.System.now()
    }
    
    val initialDateTime = initialInstant.toLocalDateTime(TimeZone.currentSystemDefault())

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialInstant.toEpochMilliseconds()
    )
    
    val timePickerState = rememberTimePickerState(
        initialHour = initialDateTime.hour,
        initialMinute = initialDateTime.minute,
        is24Hour = true
    )

    if (!showTimePicker) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showTimePicker = true }) {
                    Text(i18n.text(MessageKey.CommonSave)) // Reuse "Save" for "Next" or just use a literal
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(i18n.text(MessageKey.CommonCancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val dateMillis = datePickerState.selectedDateMillis ?: initialInstant.toEpochMilliseconds()
                        val date = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                        val resultDateTime = LocalDateTime(
                            date.year, date.monthNumber, date.dayOfMonth,
                            timePickerState.hour, timePickerState.minute
                        )
                        onValueSelected(resultDateTime.toInstant(TimeZone.currentSystemDefault()).toString())
                    }
                ) {
                    Text(i18n.text(MessageKey.CommonSave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(i18n.text(MessageKey.CommonBack))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}
