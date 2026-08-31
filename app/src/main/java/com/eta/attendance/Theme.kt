package com.eta.attendance

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 主题模式：跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 一套配色：主色 + 背景三段渐变 + 强调色 + 两个光斑色 */
data class Palette(
    val id: String,
    val nameZh: String,
    val key: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val accent: Color,
    val glowA: Color,
    val glowB: Color,
)

object Palettes {
    val ALL = listOf(
        Palette("ocean", "海蓝", Color(0xFF3482FF), Color(0xFF3D6BFF), Color(0xFF7B5CFF), Color(0xFF2BB8FF), Color(0xFF6EA8FF), Color(0xFFFFD166), Color(0xFF90E8FF)),
        Palette("sunset", "暖阳", Color(0xFFFF7A45), Color(0xFFFF8E53), Color(0xFFFF5C8A), Color(0xFFB14BFF), Color(0xFFFFB088), Color(0xFFFFE08A), Color(0xFFFF9EB8)),
        Palette("forest", "青绿", Color(0xFF12B886), Color(0xFF06D6A0), Color(0xFF1FA97A), Color(0xFF3D9BFF), Color(0xFF6EE7C0), Color(0xFFD6F9E6), Color(0xFF9BE8D0)),
        Palette("grape", "葡萄", Color(0xFF8B5CF6), Color(0xFF7B5CFF), Color(0xFFB14BFF), Color(0xFF4C6BFF), Color(0xFFB79CFF), Color(0xFFFFD1F0), Color(0xFFA0C4FF)),
        Palette("mono", "黑白", Color(0xFFBDBDBD), Color(0xFF2B2B2B), Color(0xFF141414), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF4A4A4A), Color(0xFF8A8A8A)),
    )
    fun byId(id: String): Palette = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

/** 下发给玻璃/背景/文字的一组语义色 */
data class AppColors(
    val palette: Palette,
    val isDark: Boolean,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val navFill: Color,
    val navSelected: Color,
    val chipIdle: Color,
    val chipIdleText: Color,
)

fun buildAppColors(p: Palette, dark: Boolean): AppColors = if (dark) {
    AppColors(
        palette = p, isDark = true,
        glassFill = Color.White.copy(alpha = 0.10f),
        glassFillStrong = Color.White.copy(alpha = 0.16f),
        glassBorder = Color.White.copy(alpha = 0.20f),
        glassHighlight = Color.White.copy(alpha = 0.28f),
        textPrimary = Color.White,
        textSecondary = Color.White.copy(alpha = 0.62f),
        navFill = Color(0xCC141726),
        navSelected = p.accent.copy(alpha = 0.30f),
        chipIdle = Color.White.copy(alpha = 0.12f),
        chipIdleText = Color.White.copy(alpha = 0.85f),
    )
} else {
    AppColors(
        palette = p, isDark = false,
        glassFill = Color.White.copy(alpha = 0.55f),
        glassFillStrong = Color.White.copy(alpha = 0.72f),
        glassBorder = Color.White.copy(alpha = 0.72f),
        glassHighlight = Color.White.copy(alpha = 0.9f),
        textPrimary = Color(0xCC1A1D2B),
        textSecondary = Color(0x991A1D2B),
        navFill = Color(0xEEFFFFFF),
        navSelected = p.key.copy(alpha = 0.14f),
        chipIdle = Color.White.copy(alpha = 0.5f),
        chipIdleText = Color(0xCC1A1D2B),
    )
}

val LocalAppColors = staticCompositionLocalOf { buildAppColors(Palettes.ALL[0], false) }

@Composable
fun AppTheme(
    mode: ThemeMode,
    paletteId: String,
    content: @Composable () -> Unit,
) {
    val palette = Palettes.byId(paletteId)
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorMode = when (mode) {
        ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
        ThemeMode.LIGHT -> ColorSchemeMode.Light
        ThemeMode.DARK -> ColorSchemeMode.Dark
    }
    val controller = remember(mode, paletteId, dark) {
        ThemeController(colorMode, keyColor = palette.key)
    }
    val appColors = remember(palette, dark) { buildAppColors(palette, dark) }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalAppColors provides appColors) {
            content()
        }
    }
}
