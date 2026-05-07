package com.corall.agrotrack.presentation.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corall.agrotrack.core.network.WsConnectionState
import com.corall.agrotrack.core.util.NetworkStatus

@Composable
fun ConnectionStatusBar(
    networkStatus: NetworkStatus,
    wsState: WsConnectionState,
    modifier: Modifier = Modifier,
) {
    val (bgColor, label) = when {
        networkStatus != NetworkStatus.Available ->
            Color(0xFFB71C1C) to "SIN CONEXIÓN — Mostrando último estado conocido"
        wsState == WsConnectionState.Reconnecting ->
            Color(0xFFE65100) to "Reconectando con el servidor…"
        wsState == WsConnectionState.Disconnected ->
            Color(0xFF6D4C41) to "Desconectado del servidor"
        wsState == WsConnectionState.Connected ->
            Color(0xFF2E7D32) to "En vivo"
        else ->
            Color(0xFF1565C0) to "Conectando…"
    }

    val showBar = networkStatus != NetworkStatus.Available ||
                  wsState != WsConnectionState.Connected

    AnimatedVisibility(
        visible = showBar,
        enter   = expandVertically(),
        exit    = shrinkVertically(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(vertical = 6.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = label,
                color      = Color.White,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
