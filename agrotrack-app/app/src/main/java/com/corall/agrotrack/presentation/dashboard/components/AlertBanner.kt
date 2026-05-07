package com.corall.agrotrack.presentation.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corall.agrotrack.presentation.common.theme.StatusCritical

@Composable
fun AlertBanner(
    alertCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alertCount == 0) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(StatusCritical.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Warning, null, tint = StatusCritical, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "$alertCount alerta${if (alertCount > 1) "s" else ""} activa${if (alertCount > 1) "s" else ""}",
                color      = StatusCritical,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "Toca para ver el detalle",
                color    = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
        Text("Ver →", color = StatusCritical, fontWeight = FontWeight.Bold)
    }
}
