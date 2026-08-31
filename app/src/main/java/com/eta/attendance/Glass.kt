package com.eta.attendance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 动态渐变背景：随配色变化，深色下整体压暗 */
@Composable
internal fun GlassBackground() {
    val c = LocalAppColors.current
    val p = c.palette
    val dim = if (c.isDark) 0.45f else 1f
    fun d(col: Color) = col.copy(alpha = col.alpha * dim)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(d(p.bgTop), d(p.bgMid), d(p.bgBottom)))
            )
        )
        Box(
            Modifier.size(300.dp).align(Alignment.TopStart)
                .offset(x = (-100).dp, y = (-60).dp)
                .background(Brush.radialGradient(listOf(p.glowA.copy(alpha = 0.4f * dim), Color.Transparent)))
        )
        Box(
            Modifier.size(280.dp).align(Alignment.CenterEnd)
                .offset(x = (-60).dp, y = (-140).dp)
                .background(Brush.radialGradient(listOf(p.glowB.copy(alpha = 0.33f * dim), Color.Transparent)))
        )
        Box(
            Modifier.size(340.dp).align(Alignment.BottomStart)
                .offset(x = (-140).dp, y = 80.dp)
                .background(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.33f * dim), Color.Transparent)))
        )
        Box(
            Modifier.size(180.dp).align(Alignment.CenterStart)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f * dim), Color.Transparent)))
        )
    }
}

/** 液态玻璃卡片：半透明填充 + 顶部高光 + 亮边 + 悬浮阴影 */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        modifier = modifier
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(shape)
            .background(c.glassFill)
            .background(
                Brush.verticalGradient(
                    0f to c.glassHighlight.copy(alpha = if (c.isDark) 0.16f else 0.5f),
                    0.45f to Color.Transparent
                )
            )
            .border(1.dp, c.glassBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
internal fun GlassButton(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "btnScale")
    val bg = if (primary) MiuixTheme.colorScheme.primary.copy(alpha = if (pressed) 1f else 0.92f)
    else c.glassFillStrong
    val fg = if (primary) MiuixTheme.colorScheme.onPrimary else c.textPrimary
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(8.dp, shape, spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(bg)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (primary) 0.28f else 0.4f),
                    0.5f to Color.Transparent
                )
            )
            .border(1.dp, c.glassBorder, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "btnScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(c.glassFillStrong)
            .border(1.dp, c.glassBorder, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.textPrimary)
    }
}

@Composable
internal fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(
        if (selected) MiuixTheme.colorScheme.primary else c.chipIdle, label = "chipBg"
    )
    val fg = if (selected) MiuixTheme.colorScheme.onPrimary else c.chipIdleText
    Box(
        Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, c.glassBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 悬浮液态玻璃底部导航：半透明 + 大圆角 + 悬浮阴影 + 滑动胶囊指示器 */
@Composable
internal fun BottomNavBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val labels = listOf(
        context.getString(R.string.tab_checkin),
        context.getString(R.string.tab_stats),
        context.getString(R.string.tab_salary),
        context.getString(R.string.tab_settings),
    )
    val pill = RoundedCornerShape(26.dp)
    val selPill = RoundedCornerShape(20.dp)
    val indicator by animateFloatAsState(selectedTab.toFloat(), animationSpec = tween(260), label = "navInd")
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, pill, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.28f))
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(pill)
                .background(c.navFill)
                .border(1.dp, c.glassBorder, pill)
                .padding(6.dp)
        ) {
            val itemW = maxWidth / labels.size
            Box(
                Modifier
                    .offset(x = itemW * indicator)
                    .width(itemW)
                    .fillMaxHeight()
                    .clip(selPill)
                    .background(c.navSelected)
            )
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, label = "navScale")
                    val fg = if (selectedTab == i) MiuixTheme.colorScheme.primary else c.textSecondary
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .clip(selPill)
                            .clickable(interactionSource = interactionSource, indication = null) { onSelect(i) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontSize = 14.sp, color = fg,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
