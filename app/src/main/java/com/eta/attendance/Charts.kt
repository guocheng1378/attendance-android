package com.eta.attendance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

internal data class BarEntry(val label: String, val value: Double, val highlight: Boolean = false)

private val BarNormal = Color(0xFF6EA8FF)
private val BarHi = Color(0xFF3D78FF)

@Composable
internal fun BarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    height: Int = 170
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val maxValue = max(1.0, entries.maxOfOrNull { it.value } ?: 1.0)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
        ) {
            if (entries.isEmpty()) return@Canvas
            val n = entries.size
            val slot = size.width / n
            val barW = slot * 0.55f
            val usable = size.height * 0.88f
            entries.forEachIndexed { i, e ->
                val h = (e.value / maxValue).toFloat() * usable
                val x = i * slot + (slot - barW) / 2f
                val y = size.height - h
                val base = if (e.highlight) BarHi else BarNormal
                val brush = Brush.verticalGradient(listOf(base, base.copy(alpha = 0.5f)))
                drawRoundRect(
                    brush,
                    Offset(x, y),
                    Size(barW, h.coerceAtLeast(2f)),
                    CornerRadius(barW / 2f, barW / 2f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            entries.forEach { e ->
                Text(
                    text = e.label,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = if (e.highlight) 0.9f else 0.5f),
                    fontWeight = if (e.highlight) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
