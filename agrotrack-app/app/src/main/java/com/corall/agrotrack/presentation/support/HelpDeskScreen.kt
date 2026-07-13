package com.corall.agrotrack.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corall.agrotrack.presentation.common.components.AgroGradient
import com.corall.agrotrack.presentation.common.components.AgroHeader

private val Cyan  = Color(0xFF62C9FF)
private val Muted = Color(0xFF94A3B8)
private val White = Color(0xFFF8FAFC)

// HU: acceder a la mesa de ayuda para gestionar solicitudes del cliente
@Composable
fun HelpDeskScreen(onBack: () -> Unit) {
    AgroGradient {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            AgroHeader(onBack = onBack)

            Text(
                text       = "Mesa de Ayuda",
                color      = White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "Gestiona solicitudes y consultas de los clientes.",
                color    = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
            )

            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector        = Icons.Default.HeadsetMic,
                        contentDescription = null,
                        tint               = Cyan.copy(alpha = 0.4f),
                        modifier           = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text      = "Mesa de ayuda",
                        color     = White,
                        fontSize  = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = "El módulo de tickets estará disponible\ncuando se implemente en el backend.",
                        color     = Muted,
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
