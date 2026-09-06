package com.eta.attendance

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import component.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.runtime.compositionLocalOf
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.nav.core.NavController

val LocalBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * 高光倾斜跟随参数：
 * - 角度分桶 [TILT_BUCKET_DEG]（度），小于该步长的抖动不产生新值；
 * - 两次写入 state 的最小间隔 [TILT_MIN_INTERVAL_MS]（毫秒）。
 * 库的 `rememberTiltLight` 每个传感器样本（SENSOR_DELAY_GAME ≈ 50Hz）都写一次 state，
 * 这里把重组频率压到最多约 8 次/秒，且手机静止时为 0 次。
 */
private const val TILT_BUCKET_DEG = 3f
private const val TILT_MIN_INTERVAL_MS = 120L
private const val RAD_TO_DEG = 57.29578f

/**
 * 设备倾斜驱动的高光光源，替代库的 `rememberTiltLight`。
 *
 * 与库版本的差异：[enabled] 为 false 时完全不注册传感器监听（库版本无条件注册），
 * 并对角度做分桶 + 时间节流，静止时不写 state。
 * 位置换算与库一致：`x = base.x + sensitivity * roll`、`y = base.y - sensitivity * pitch`，
 * 其中 roll/pitch 为 [SensorManager.getOrientation] 给出的弧度值。
 */
@Composable
internal fun rememberTiltLightSource(
    enabled: Boolean,
    basePosition: LightPosition,
    sensitivity: Float,
): LightSource {
    val context = LocalContext.current
    val light = remember(basePosition, sensitivity) {
        mutableStateOf(
            LightSource(
                position = basePosition,
                color = Color.White.copy(alpha = 0.9f),
                intensity = 0.85f,
            )
        )
    }
    DisposableEffect(enabled, context, basePosition, sensitivity) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || sensor == null) return@DisposableEffect onDispose { }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var lastPitchBucket = Int.MIN_VALUE
        var lastRollBucket = Int.MIN_VALUE
        var lastWriteAt = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastWriteAt < TILT_MIN_INTERVAL_MS) return
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // orientation = [azimuth, pitch, roll]，弧度
                val pitchBucket = (orientation[1] * RAD_TO_DEG / TILT_BUCKET_DEG).roundToInt()
                val rollBucket = (orientation[2] * RAD_TO_DEG / TILT_BUCKET_DEG).roundToInt()
                if (pitchBucket == lastPitchBucket && rollBucket == lastRollBucket) return
                lastPitchBucket = pitchBucket
                lastRollBucket = rollBucket
                lastWriteAt = now
                val pitchRad = pitchBucket * TILT_BUCKET_DEG / RAD_TO_DEG
                val rollRad = rollBucket * TILT_BUCKET_DEG / RAD_TO_DEG
                light.value = LightSource(
                    position = LightPosition(
                        x = basePosition.x + sensitivity * rollRad,
                        y = basePosition.y - sensitivity * pitchRad,
                        z = basePosition.z,
                    ),
                    color = light.value.color,
                    intensity = light.value.intensity,
                )
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    return light.value
}

@Composable
internal fun rememberGlassColors() = BlurDefaults.blurColors(blendColors = listOf(BlendColorEntry(color = Color.White.copy(alpha = 0.12f), mode = BlurBlendMode.Screen)), brightness = 0.03f, contrast = 1.07f, saturation = 1.1f)

/**
 * 玻璃卡片的高光样式。[enabled] 应在没有真实背景层（走不到 textureBlur）时传 false，
 * 以免白白挂着传感器监听。
 */
@Composable
internal fun rememberGlassHighlight(enabled: Boolean = true): Highlight {
    val tilt = rememberTiltLightSource(enabled = enabled, basePosition = LightPosition(0.5f, 0.7f, -0.5f), sensitivity = 0.14f)
    return remember(tilt) { Highlight(width = 1.1.dp, alpha = 0.9f, style = BloomStroke(color = Color.White.copy(alpha = 0.07f), innerBlurRadius = 4.dp, primaryLight = tilt, secondaryLight = LightSource(position = LightPosition(0.5f, 0.3f, -0.5f), color = Color.White.copy(alpha = 0.5f), intensity = 0.45f), dualPeak = true)) }
}

/**
 * 向 [other] 线性插值四个通道：[fraction] 为 0 时保持原色，为 1 时完全变成 [other]。
 * 用于把饱和配色压向中性底，降低背景的色相强度。
 */
private fun Color.mixWith(other: Color, fraction: Float): Color = Color(
    red + (other.red - red) * fraction,
    green + (other.green - green) * fraction,
    blue + (other.blue - blue) * fraction,
    alpha + (other.alpha - alpha) * fraction,
)

/**
 * 背景：非 mono 配色的三段渐变先向中性底插值 62%（只保留 38% 原色相），
 * 呈接近纯色、微带色调的效果；彩色光斑只留极淡的色调提示，深色下整体压暗。
 */
@Composable
internal fun GlassBackground() {
    val c = LocalAppColors.current
    val p = c.palette
    val dim = if (c.isDark) 0.45f else 1f
    fun d(col: Color) = col.copy(alpha = col.alpha * dim)
    val mono = p.id == "mono"
    val neutral = if (c.isDark) Color(0xFF101216) else Color(0xFFF6F7F9)
    fun mute(col: Color) = d(col.mixWith(neutral, 0.62f))
    val bgCols = if (mono) {
        if (c.isDark) listOf(Color(0xFF0B0B0B), Color(0xFF000000), Color(0xFF131313))
        else listOf(Color(0xFFFFFFFF), Color(0xFFF4F4F4), Color(0xFFE7E7E7))
    } else listOf(mute(p.bgTop), mute(p.bgMid), mute(p.bgBottom))
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(bgCols))
        )
        if (!mono) {
            Box(
                Modifier.size(240.dp).align(Alignment.TopStart)
                    .offset(x = (-100).dp, y = (-60).dp)
                    .background(Brush.radialGradient(listOf(p.glowA.copy(alpha = 0.14f * dim), Color.Transparent)))
            )
            Box(
                Modifier.size(220.dp).align(Alignment.CenterEnd)
                    .offset(x = (-60).dp, y = (-140).dp)
                    .background(Brush.radialGradient(listOf(p.glowB.copy(alpha = 0.10f * dim), Color.Transparent)))
            )
            Box(
                Modifier.size(260.dp).align(Alignment.BottomStart)
                    .offset(x = (-140).dp, y = 80.dp)
                    .background(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.10f * dim), Color.Transparent)))
            )
        } else {
            Box(
                Modifier.size(320.dp).align(Alignment.Center)
                    .background(Brush.radialGradient(listOf(Color.Gray.copy(alpha = 0.10f * dim), Color.Transparent)))
            )
        }
        Box(
            Modifier.size(180.dp).align(Alignment.CenterStart)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f * dim), Color.Transparent)))
        )
    }
}

/** 液态玻璃卡片：半透明填充 + 弱顶部高光 + 亮边 + 轻悬浮阴影（整体压到低对比，避免卡片发白、投影过重） */
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
    // 没有背景层时走不到 textureBlur，高光（及其传感器监听）没必要挂着
    val glassHighlight = rememberGlassHighlight(enabled = backdrop != null)
    val base = if (backdrop != null) {
        Modifier.textureBlur(backdrop = backdrop, shape = shape, blurRadius = 16f, noiseCoefficient = 0.003f, colors = glassColors, highlight = glassHighlight)
    } else {
        Modifier.background(c.glassFill)
    }
    Column(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(shape)
            .then(base)
            .wrapContentHeight()
            .background(
                Brush.verticalGradient(
                    0f to c.glassHighlight.copy(alpha = if (c.isDark) 0.08f else 0.22f),
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
            .sizeIn(minHeight = 48.dp)
            .scale(scale)
            .shadow(8.dp, shape, spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(bg)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (primary) 0.12f else 0.18f),
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
    // 外层只负责 ≥48dp 的触控热区，视觉仍是内层那颗 44dp 方块
    // （Compose 1.12 已移除 minimumInteractiveComponentSize，这里直接用 sizeIn 钳最小值）
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .clip(shape)
                .background(c.glassFillStrong)
                .border(1.dp, c.glassBorder, shape)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.textPrimary)
        }
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
    // 同上：热区补到 ≥48dp，胶囊视觉尺寸不变（clickable 必须挂在外层，否则热区仍是小胶囊）
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(bg)
                .border(1.dp, c.glassBorder, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(label, fontSize = 13.sp, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/**
 * 悬浮液态玻璃底部导航：改用成品组件 [IosLiquidGlassNavigationBar]
 * （`component/liquid/`，改编 Kyant0/AndroidLiquidGlass，Apache-2.0），
 * 不再手写 textureBlur + 滑动胶囊。外层 Box 只是一个纯布局容器，不再采样 [backdrop]：
 * 所有磨砂、压暗与染色都在胶囊自身的圆角形状内完成（组件内部的 drawBackdrop）。
 * 此前外层那圈全宽矩形的 textureBlur 已移除——它比胶囊更宽也更高（胶囊左右各缩进
 * 24dp），浅色主题下会在底栏周围画出一条近白色的横带。
 *
 * [backdrop] 由**调用方**注册在内容的兄弟层上（AttendanceScreen 里含 NavDisplay 的
 * 那个 Box），胶囊自己只采样不注册——同一个 LayerBackdrop 若被祖先节点 `layerBackdrop`
 * 注册、又被其后代 `textureBlur` 采样，会在 Android 上形成 RenderNode 父子环，
 * prepareTree 无限递归直接 native 崩溃。[isBlurActive] 即 `backdrop != null`
 * （无 RuntimeShader 时调用方传 null，组件走无模糊降级）。
 *
 * 签名仍收 [NavController]（调用点在 AttendanceApp，不动）。但 NavController 持有
 * SnapshotStateList 且非 @Stable，本函数每次宿主重组都要重新派生入参、自身无法 skip；
 * 要连这一步也省掉，需要调用方直接传 selectedTab / onSelect。
 */
@Composable
internal fun BottomNavBar(
    navController: NavController,
    backdrop: LayerBackdrop?,
    isBlurActive: Boolean,
) {
    val context = LocalContext.current
    val items = listOf(
        NavigationItem(context.getString(R.string.tab_checkin), MiuixIcons.Ok),
        NavigationItem(context.getString(R.string.tab_stats), MiuixIcons.GridView),
        NavigationItem(context.getString(R.string.tab_salary), MiuixIcons.BankCards),
        NavigationItem(context.getString(R.string.tab_settings), MiuixIcons.Settings),
    )
    val routes = remember { listOf(Route.CheckIn, Route.Stats, Route.Salary, Route.Settings) }
    val current = navController.backStack.lastOrNull()
    val selectedTab = routes.indexOfFirst { it == current }.coerceAtLeast(0)
    val onSelect: (Int) -> Unit = remember(navController, routes) {
        { idx: Int -> navController.replace(routes[idx]) }
    }
    Box(Modifier.fillMaxWidth()) {
        IosLiquidGlassNavigationBar(
            items = items,
            selectedIndex = selectedTab,
            onItemClick = onSelect,
            backdrop = backdrop,
            isBlurActive = isBlurActive,
        )
    }
}
