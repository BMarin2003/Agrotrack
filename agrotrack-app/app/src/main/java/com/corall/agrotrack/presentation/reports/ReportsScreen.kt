package com.corall.agrotrack.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.domain.model.Sensor
import com.corall.agrotrack.domain.model.SensorReading
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.LoadingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Green  = Color(0xFF22C55E)
private val Red    = Color(0xFFEF4444)

private val RANGES = listOf("24h", "7d", "30d")

// HUs: seleccionar sensor(es), definir rango (fijo o personalizado), generar
// con un toque, gráfico historial, resumen estadístico
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack:    () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        CustomRangeDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to ->
                viewModel.setCustomRange(from, to)
                showDatePicker = false
            },
        )
    }

    AgroGradient {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            AgroHeader(onBack = onBack)

            Text(
                text       = "Historial de Temperaturas",
                color      = White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "Selecciona uno o más sensores y el período a analizar.",
                color    = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            if (uiState.isLoadingSensors) { LoadingState(); return@Column }

            if (uiState.sensors.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay sensores disponibles", color = Muted)
                }
                return@Column
            }

            SensorMultiSelect(
                sensors  = uiState.sensors,
                selected = uiState.selectedSensors,
                onToggle = viewModel::toggleSensor,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEach { range ->
                    val active = !uiState.isCustomRange && uiState.range == range
                    FilterChip(
                        selected = active,
                        onClick  = { viewModel.selectRange(range) },
                        label    = { Text(range) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cyan,
                            selectedLabelColor     = Color(0xFF0D1B2A),
                        ),
                    )
                }
                FilterChip(
                    selected = uiState.isCustomRange,
                    onClick  = { showDatePicker = true },
                    label    = { Text("Personalizado") },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan,
                        selectedLabelColor     = Color(0xFF0D1B2A),
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = viewModel::loadReport,
                enabled  = uiState.selectedSensors.isNotEmpty() && !uiState.isLoadingReport,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Cyan),
            ) {
                if (uiState.isLoadingReport) {
                    CircularProgressIndicator(color = Color(0xFF0D1B2A), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Generar reporte", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (!uiState.hasGenerated) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Configura los filtros y genera el reporte", color = Muted, fontSize = 14.sp)
                }
                return@Column
            }

            if (uiState.isLoadingReport) {
                LoadingState()
                return@Column
            }

            uiState.error?.let { err ->
                Text(err, color = Red, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }

            val reports = uiState.reports
            if (reports.values.all { it.isEmpty() }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin datos para el período seleccionado", color = Muted, fontSize = 14.sp)
                }
                return@Column
            }

            if (reports.size == 1) {
                val readings = reports.values.first()
                SummaryCard(readings)
                Spacer(Modifier.height(12.dp))
                SparklineChart(readings)
                Spacer(Modifier.height(16.dp))

                Text("Lecturas", color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(readings.asReversed(), key = { it.id }) { reading ->
                        ReadingRow(reading)
                    }
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.selectedSensors.toList(), key = { it.id }) { sensor ->
                        val readings = reports[sensor.id].orEmpty()
                        Column {
                            Text(sensor.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            if (readings.isEmpty()) {
                                Text("Sin datos en el período", color = Muted, fontSize = 12.sp)
                            } else {
                                SummaryCard(readings)
                                Spacer(Modifier.height(8.dp))
                                SparklineChart(readings)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorMultiSelect(
    sensors:  List<Sensor>,
    selected: Set<Sensor>,
    onToggle: (Sensor) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        selected.isEmpty()  -> "Seleccionar sensor(es)"
        selected.size == 1  -> selected.first().name
        else                -> "${selected.size} sensores seleccionados"
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = label,
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Sensores", color = Muted) },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Cyan,
                unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                focusedTextColor     = White,
                unfocusedTextColor   = White,
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            containerColor   = Color(0xFF132238),
        ) {
            sensors.forEach { sensor ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected.contains(sensor),
                                onCheckedChange = { onToggle(sensor) },
                                colors = CheckboxDefaults.colors(checkedColor = Cyan),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(sensor.name, color = White)
                        }
                    },
                    // No cerramos el menú: el usuario puede tildar varios sensores seguidos.
                    onClick = { onToggle(sensor) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (fromMs: Long, toMs: Long) -> Unit,
) {
    val state = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = state.selectedStartDateMillis
                    val to   = state.selectedEndDateMillis
                    if (from != null && to != null) onConfirm(from, to)
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(readings: List<SensorReading>) {
    val temps = readings.mapNotNull { it.temperature }
    if (temps.isEmpty()) return
    val min = temps.min()
    val max = temps.max()
    val avg = temps.average()

    Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem("Mín",  min, Color(0xFF38BDF8))
            StatItem("Prom", avg, Muted)
            StatItem("Máx",  max, Red)
        }
    }
}

@Composable
private fun StatItem(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%.1f°C".format(value), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SparklineChart(readings: List<SensorReading>) {
    val temps = readings.mapNotNull { it.temperature?.toFloat() }
    if (temps.size < 2) return

    Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(12.dp)) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxWidth().height(100.dp),
            ) {
                val min   = temps.min()
                val max   = temps.max()
                val range = (max - min).coerceAtLeast(0.5f)
                val w     = size.width
                val h     = size.height
                val path  = Path()
                temps.forEachIndexed { i, t ->
                    val x = i.toFloat() / (temps.size - 1) * w
                    val y = h - ((t - min) / range * h * 0.85f + h * 0.07f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = Cyan, style = Stroke(width = 2.dp.toPx()))
                val firstY = h - ((temps.first() - min) / range * h * 0.85f + h * 0.07f)
                val lastY  = h - ((temps.last()  - min) / range * h * 0.85f + h * 0.07f)
                drawCircle(color = Green, radius = 4.dp.toPx(), center = Offset(0f, firstY))
                drawCircle(color = Cyan,  radius = 4.dp.toPx(), center = Offset(w,  lastY))
            }
        }
    }
}

@Composable
private fun ReadingRow(reading: SensorReading) {
    val label = reading.temperature?.let { "%.1f°C".format(it) } ?: "—"
    val date  = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        .format(Date(reading.receivedAt))

    Surface(color = Color(0xFF0D1B2A), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(date,  color = Muted, fontSize = 12.sp)
        }
    }
}
