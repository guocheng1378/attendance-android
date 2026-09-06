package com.eta.attendance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.max

/** 一根柱：[label] 为轴下文案（由调用方按语言传入），[highlight] 标记当前项。 */
internal data class BarEntry(val label: String, val value: Double, val highlight: Boolean = false)

/**
 * 柱状图。柱体与轴标签颜色取自 [LocalAppColors] 的 chart\* 字段，浅色/深色下都可读。
 *
 * 注意：当前全仓库零引用（无调用点），保留待接线或删除。
 */
@Composable
internal fun BarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    height: Int = 170
) {
    val c = LocalAppColors.current
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
                // 先钳住最小高度再算顶部 y，否则矮柱会从基线下方溢出
                val h = ((e.value / maxValue).toFloat() * usable).coerceAtLeast(2f)
                val x = i * slot + (slot - barW) / 2f
                val y = size.height - h
                val base = if (e.highlight) c.chartBarHi else c.chartBar
                val brush = Brush.verticalGradient(listOf(base, base.copy(alpha = 0.5f)))
                drawRoundRect(
                    brush,
                    Offset(x, y),
                    Size(barW, h),
                    CornerRadius(barW / 2f, barW / 2f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            entries.forEach { e ->
                // 保留 chartText 自带的透明度，非高亮项在其基础上再压暗
                val labelColor = if (e.highlight) c.chartText else c.chartText.copy(alpha = c.chartText.alpha * 0.55f)
                Text(
                    e.label,
                    fontSize = 9.sp,
                    color = labelColor,
                    fontWeight = if (e.highlight) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
