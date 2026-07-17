package com.corall.agrotrack.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (fromMs: Long, toMs: Long) -> Unit,
) {
    val dateState = rememberDateRangePickerState()
    val startTimeState = rememberTimePickerState(initialHour = 0, initialMinute = 0, is24Hour = true)
    val endTimeState   = rememberTimePickerState(initialHour = 23, initialMinute = 59, is24Hour = true)

    fun combine(dateMs: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = dateMs
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = dateState.selectedStartDateMillis
                    val to   = dateState.selectedEndDateMillis
                    if (from != null && to != null) {
                        onConfirm(
                            combine(from, startTimeState.hour, startTimeState.minute),
                            combine(to, endTimeState.hour, endTimeState.minute),
                        )
                    }
                },
                enabled = dateState.selectedStartDateMillis != null && dateState.selectedEndDateMillis != null,
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DateRangePicker(state = dateState, modifier = Modifier.height(420.dp))

            Text("Hora de inicio", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
            TimeInput(state = startTimeState, modifier = Modifier.padding(horizontal = 24.dp))

            Text("Hora de fin", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
            TimeInput(state = endTimeState, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
    }
}
