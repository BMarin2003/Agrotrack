package com.corall.agrotrack.presentation.gateways

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader
import com.corall.agrotrack.presentation.common.components.EmptyState
import com.corall.agrotrack.presentation.gateways.components.GatewayCard
import com.corall.agrotrack.presentation.common.components.LoadingState
import com.corall.agrotrack.presentation.gateways.components.SectionTitle

@Composable
fun GatewaysScreen(
    onBack: () -> Unit,
    onGatewayClick: (Int) -> Unit,
    viewModel: GatewaysViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                icon = Icons.Default.Router,
                title = "Gateways disponibles",
                subtitle = "Selecciona un gateway para monitorear",
            )

            when {
                uiState.isLoading -> LoadingState()

                uiState.error != null -> EmptyState(
                    text = uiState.error ?: "No se pudieron cargar los gateways",
                )

                uiState.gateways.isEmpty() -> EmptyState(
                    text = "No hay gateways disponibles",
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.gateways, key = { it.id }) { gateway ->
                            GatewayCard(
                                gateway = gateway,
                                onClick = { onGatewayClick(gateway.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}