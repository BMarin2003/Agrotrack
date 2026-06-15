package com.corall.agrotrack.presentation.gateways

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.presentation.gateways.components.AgroGradient
import com.corall.agrotrack.presentation.gateways.components.AgroHeader
import com.corall.agrotrack.presentation.gateways.components.EmptyState
import com.corall.agrotrack.presentation.gateways.components.LoadingState
import com.corall.agrotrack.presentation.gateways.components.SectionTitle
import com.corall.agrotrack.presentation.gateways.components.SensorCard

@Composable
fun GatewayDetailScreen(
    gatewayId: Int,
    onBack: () -> Unit,
    onSensorClick: (Int) -> Unit,
    viewModel: GatewayDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(gatewayId) {
        viewModel.start(gatewayId)
    }

    AgroGradient {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 39.dp),
        ) {
            AgroHeader(onBack = onBack)

            Text(
                text = "Bienvenido, Operador",
                color = Color(0xFFB6C2D1),
                fontSize = 14.sp,
            )

            SectionTitle(
                icon = Icons.Default.Sensors,
                title = "Sensores del gateway",
            )

            Text(
                text = uiState.gatewayName.ifBlank { "Gateway #$gatewayId" },
                color = Color(0xFFB6C2D1),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 13.dp),
            )

            when {
                uiState.isLoading -> LoadingState()

                uiState.error != null -> EmptyState(
                    text = uiState.error ?: "No se pudieron cargar los sensores",
                )

                uiState.sensors.isEmpty() -> EmptyState(
                    text = "No hay sensores disponibles",
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 18.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.sensors,
                            key = { _, sensor -> sensor.id },
                        ) { index, sensor ->
                            SensorCard(
                                sensor = sensor,
                                highlighted = index == 0,
                                onClick = { onSensorClick(sensor.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}