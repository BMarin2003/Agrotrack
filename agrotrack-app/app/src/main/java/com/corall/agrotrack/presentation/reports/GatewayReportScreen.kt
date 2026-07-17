package com.corall.agrotrack.presentation.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.presentation.common.components.LoadingState

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Green  = Color(0xFF22C55E)
private val Red    = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayReportScreen(viewModel: GatewayReportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var gatewayExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                if (uiState.downloadFormat == DownloadFormat.PDF) {
                    val report = uiState.report
                    if (report != null) {
                        // TEMP: Placeholder Sensor object scaffolding for current ReportPdfBuilder.build signature.
                        // Will be removed when ReportPdfBuilder.buildGatewayLevel() replaces this call (Task 9).
                        val sensors = report.sensors.map { s ->
                            com.corall.agrotrack.domain.model.Sensor(
                                id = s.sensorId, gatewayId = report.gatewayId, gatewayName = report.gatewayName,
                                name = s.name, identifier = "", type = "temperature", unit = s.unit,
                                location = "", enable = true,
                            ) to s.readings
                        }
                        val doc = ReportPdfBuilder.build(viewModel.periodLabel(), sensors)
                        doc.writeTo(out)
                        doc.close()
                    }
                } else {
                    out.write(viewModel.buildExportContent().toByteArray())
                }
            } ?: error("No se pudo abrir el archivo destino")
        }.onFailure { e -> viewModel.onDownloadError(e.message ?: "No se pudo guardar el archivo") }
            .onSuccess { viewModel.onDownloadError(null) }
    }

    if (showDatePicker) {
        DateTimeRangeDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to -> viewModel.setCustomRange(from, to); showDatePicker = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        if (uiState.isLoadingGateways) { LoadingState(); return@Column }
        if (uiState.gateways.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay gateways disponibles", color = Muted)
            }
            return@Column
        }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan, unfocusedBorderColor = Muted.copy(alpha = 0.4f),
            focusedTextColor = White, unfocusedTextColor = White,
        )

        val selectedName = uiState.gateways.firstOrNull { it.id == uiState.selectedGatewayId }?.name ?: "Seleccionar"
        ExposedDropdownMenuBox(expanded = gatewayExpanded, onExpandedChange = { gatewayExpanded = it }) {
            OutlinedTextField(
                value = selectedName, onValueChange = {}, readOnly = true,
                label = { Text("Gateway") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gatewayExpanded) },
                colors = fieldColors, modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = gatewayExpanded, onDismissRequest = { gatewayExpanded = false }, containerColor = CardBg) {
                uiState.gateways.forEach { gw ->
                    DropdownMenuItem(text = { Text(gw.name, color = White) }, onClick = { viewModel.selectGateway(gw.id); gatewayExpanded = false })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("24h", "7d", "30d").forEach { range ->
                FilterChip(
                    selected = !uiState.isCustomRange && uiState.range == range,
                    onClick  = { viewModel.selectRange(range) },
                    label    = { Text(range) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan, selectedLabelColor = Color(0xFF0D1B2A)),
                )
            }
            FilterChip(
                selected = uiState.isCustomRange,
                onClick  = { showDatePicker = true },
                label    = { Text("Personalizado") },
                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan, selectedLabelColor = Color(0xFF0D1B2A)),
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = viewModel::generateReport,
            enabled = uiState.selectedGatewayId != null && !uiState.isLoadingReport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan),
        ) {
            if (uiState.isLoadingReport) CircularProgressIndicator(color = Color(0xFF0D1B2A), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Text("Generar reporte", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        if (!uiState.hasGenerated) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Configura los filtros y genera el reporte", color = Muted, fontSize = 14.sp)
            }
            return@Column
        }
        if (uiState.isLoadingReport) { LoadingState(); return@Column }

        uiState.error?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }

        val report = uiState.report
        if (report == null || report.readingCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin datos para el período seleccionado", color = Muted, fontSize = 14.sp)
            }
            return@Column
        }

        Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(report.gatewayName, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${report.sensorCount} sensores · ${report.readingCount} lecturas", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    StatMini("Mín", report.tempMin)
                    StatMini("Prom", report.tempAvg)
                    StatMini("Máx", report.tempMax)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Alertas: ${report.alertsTotal} (${report.alertsUnresolved} sin resolver)",
                    color = if (report.alertsUnresolved > 0) Color(0xFFF59E0B) else Muted, fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        DownloadSection(
            format = uiState.downloadFormat,
            error  = uiState.downloadError,
            onFormat = viewModel::selectDownloadFormat,
            onDownload = {
                val fileName = "reporte_gateway_${report.gatewayId}_${System.currentTimeMillis()}.${uiState.downloadFormat.extension}"
                saveFileLauncher.launch(fileName)
            },
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(report.sensors, key = { it.sensorId }) { sensor ->
                Column {
                    Text(sensor.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (sensor.readings.isEmpty()) {
                        Text("Sin datos en el período", color = Muted, fontSize = 12.sp)
                    } else {
                        SummaryCard(sensor.readings)
                        Spacer(Modifier.height(8.dp))
                        SparklineChart(sensor.readings)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.let { "%.1f°C".format(it) } ?: "—", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}
