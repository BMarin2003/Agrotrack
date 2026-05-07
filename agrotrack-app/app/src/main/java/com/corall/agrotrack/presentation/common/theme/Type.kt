package com.corall.agrotrack.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AgroTrackTypography = Typography(
    // Valor de temperatura en el SensorCard (grande y legible)
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 48.sp,
        lineHeight = 52.sp,
    ),
    // Nombre del sensor
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 18.sp,
        lineHeight = 24.sp,
    ),
    // Labels de alertas
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    ),
    // Timestamps
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
    ),
)
