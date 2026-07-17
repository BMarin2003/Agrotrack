package com.corall.agrotrack.presentation.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.corall.agrotrack.domain.model.GatewayStatus
import com.corall.agrotrack.domain.model.GeneralReportRow
import com.corall.agrotrack.presentation.common.components.LoadingState

private val Cyan   = Color(0xFF62C9FF)
private val Muted  = Color(0xFF94A3B8)
private val White  = Color(0xFFF8FAFC)
private val CardBg = Color(0xFF132238)
private val Green  = Color(0xFF22C55E)
private val Red    = Color(0xFFEF4444)
private val Blue   = Color(0xFF38BDF8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralReportScreen(viewModel: GeneralReportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                if (uiState.downloadFormat == DownloadFormat.PDF) {
                    uiState.report?.let { report ->
                        val doc = ReportPdfBuilder.buildGeneralLevel(viewModel.periodLabel(), report)
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
            enabled = !uiState.isLoadingReport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan),
        ) {
            if (uiState.isLoadingReport) CircularProgressIndicator(color = Color(0xFF0D1B2A), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Text("Generar reporte", color = Color(0xFF0D1B2A), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        if (!uiState.hasGenerated) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Genera el reporte general de todos los gateways", color = Muted, fontSize = 14.sp)
            }
            return@Column
        }
        if (uiState.isLoadingReport) { LoadingState(); return@Column }
        uiState.error?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }

        val report = uiState.report
        if (report == null || report.gateways.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin gateways registrados", color = Muted, fontSize = 14.sp)
            }
            return@Column
        }

        Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TotalMini("Gateways", report.totalGatewayCount.toString())
                TotalMini("Sensores", report.totalSensorCount.toString())
                TotalMini("Alertas", report.totalAlertCount.toString())
            }
        }

        Spacer(Modifier.height(16.dp))

        DownloadSection(
            format = uiState.downloadFormat,
            error  = uiState.downloadError,
            onFormat = viewModel::selectDownloadFormat,
            onDownload = {
                val fileName = "reporte_general_${System.currentTimeMillis()}.${uiState.downloadFormat.extension}"
                saveFileLauncher.launch(fileName)
            },
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(report.gateways, key = { it.gatewayId }) { row -> GatewayRow(row) }
        }
    }
}

@Composable
private fun TotalMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun GatewayRow(row: GeneralReportRow) {
    val statusColor = when (row.status) {
        GatewayStatus.Online -> Green
        GatewayStatus.Offline -> Red
        GatewayStatus.Maintenance -> Blue
    }
    Surface(color = CardBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                    Text(row.status.name, color = statusColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            val tempText = row.tempAvg?.let { "%.1f°C prom (mín %.1f / máx %.1f)".format(it, row.tempMin ?: 0.0, row.tempMax ?: 0.0) } ?: "Sin lecturas en el período"
            Text(tempText, color = Muted, fontSize = 12.sp)
            Text(
                "${row.sensorCount} sensores · ${row.alertsTotal} alertas (${row.alertsUnresolved} sin resolver)",
                color = if (row.alertsUnresolved > 0) Color(0xFFF59E0B) else Muted, fontSize = 11.sp,
            )
        }
    }
}
