package space.ourmosaic.app.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import space.ourmosaic.app.i18n.I18nState
import space.ourmosaic.app.i18n.MessageKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    initialValue: String?,
    onValueSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    i18n: I18nState,
    title: String = i18n.text(MessageKey.CustomFieldTypeLabel),
    confirmText: String = i18n.text(MessageKey.CommonSave),
    dismissText: String = i18n.text(MessageKey.CommonCancel)
) {
    val initialTimestamp = try {
        initialValue?.let { Instant.parse(it).toEpochMilliseconds() }
    } catch (e: Exception) {
        null
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialTimestamp
    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val instant = Instant.fromEpochMilliseconds(it)
                        onValueSelected(instant.toString())
                    }
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
