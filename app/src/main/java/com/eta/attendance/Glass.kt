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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.highlight.rememberTiltLight
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.spring
import top.yukonga.miuix.kmp.nav.core.NavController

val LocalBackdrop = compositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun rememberGlassColors() = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(color = Color.White.copy(alpha = 0.12f), mode = BlurBlendMode.Screen)), brightness = 0.03f, contrast = 1.07f, saturation = 1.1f)

@Composable
internal fun rememberGlassHighlight(): Highlight {
    val tilt = rememberTiltLight(basePosition = LightPosition(0.5f, 0.7f, -0.5f), color = Color.White.copy(alpha = 0.9f), intensity = 0.85f, sensitivity = 0.14f)
    return remember(tilt) { Highlight(width = 1.1.dp, alpha = 0.9f, style = BloomStroke(color = Color.White.copy(alpha = 0.07f), innerBlurRadius = 4.dp, primaryLight = tilt, secondaryLight = LightSource(position = LightPosition(0.5f, 0.3f, -0.5f), color = Color.White.copy(alpha = 0.5f), intensity = 0.45f), dualPeak = true)) }
}

/** 动态渐变背景：随配色变化，深色下整体压暗 */
@Composable
internal fun GlassBackground() {
    val c = LocalAppColors.current
    val p = c.palette
    val dim = if (c.isDark) 0.45f else 1f
    fun d(col: Color) = col.copy(alpha = col.alpha * dim)
    val mono = p.id == "mono"
    val bgCols = if (mono) {
        if (c.isDark) listOf(Color(0xFF0B0B0B), Color(0xFF000000), Color(0xFF131313))
        else listOf(Color(0xFFFFFFFF), Color(0xFFF4F4F4), Color(0xFFE7E7E7))
    } else listOf(d(p.bgTop), d(p.bgMid), d(p.bgBottom))
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(bgCols))
        )
        if (!mono) {
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
        } else {
            Box(
                Modifier.size(320.dp).align(Alignment.Center)
                    .background(Brush.radialGradient(listOf(Color.Gray.copy(alpha = 0.10f * dim), Color.Transparent)))
            )
        }
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
    val backdrop = LocalBackdrop.current
    val glassColors = rememberGlassColors()
    val glassHighlight = rememberGlassHighlight()
    val base = if (backdrop != null) {
        Modifier.textureBlur(backdrop = backdrop, shape = shape, blurRadius = 16f, noiseCoefficient = 0.003f, colors = glassColors, highlight = glassHighlight)
    } else {
        Modifier.background(c.glassFill)
    }
    Column(
        modifier = modifier
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.22f))
            .clip(shape)
            .then(base)
            .wrapContentHeight()
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

/** 悬浮液态玻璃底部导航：miuix-blur 真背景模糊 + 大圆角 + 悬浮阴影 + 滑动胶囊指示器 */
@Composable
internal fun BottomNavBar(navController: NavController, backdrop: LayerBackdrop) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val labels = listOf(
        context.getString(R.string.tab_checkin),
        context.getString(R.string.tab_stats),
        context.getString(R.string.tab_salary),
        context.getString(R.string.tab_settings),
    )
    val routes = listOf(Route.CheckIn, Route.Stats, Route.Salary, Route.Settings)
    val current = navController.backStack.lastOrNull()
    val selectedTab = routes.indexOfFirst { it == current }.coerceAtLeast(0)
    val pill = RoundedCornerShape(26.dp)
    val selPill = RoundedCornerShape(20.dp)
    val glassColors = rememberGlassColors()
    val highlight = rememberGlassHighlight()
    val indicator by animateFloatAsState(selectedTab.toFloat(), animationSpec = spring(stiffness = 380f, dampingRatio = 0.85f), label = "navInd")
    Box(Modifier.fillMaxWidth().shadow(18.dp, pill, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.28f))) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(pill)
                .textureBlur(
                    backdrop = backdrop,
                    shape = pill,
                    blurRadius = 22f,
                    noiseCoefficient = 0.003f,
                    colors = glassColors,
                    highlight = highlight,
                )
                .padding(6.dp),
        ) {
            val itemW = maxWidth / labels.size
            Box(Modifier.offset(x = itemW * indicator).width(itemW).fillMaxHeight().clip(selPill).background(c.navSelected))
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { idx, label ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, label = "navScale" + idx)
                    val fg = if (selectedTab == idx) MiuixTheme.colorScheme.primary else c.textSecondary
                    Box(modifier = Modifier.weight(1f).scale(scale).clip(selPill).clickable(interactionSource = interactionSource, indication = null) { navController.replace(routes[idx]) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 14.sp, color = fg, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
