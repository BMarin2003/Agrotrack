package com.corall.agrotrack.presentation.common.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val background = color.copy(alpha = 0.14f)
    val shape = RoundedCornerShape(4.dp)
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }

    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, color = background, shape = shape, modifier = modifier) {
            label()
        }
    } else {
        Surface(color = background, shape = shape, modifier = modifier) {
            label()
        }
    }
}
