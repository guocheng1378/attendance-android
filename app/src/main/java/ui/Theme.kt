package ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * 颜色模式：0 跟随系统；1/4 强制浅色；2/5/6 强制深色。
 * 由 `AppTheme` 在最外层 CompositionLocalProvider 里下发，供 [isInDarkTheme] 读取。
 */
val LocalColorMode = compositionLocalOf { 0 }

/**
 * 当前是否深色。`component.liquid.LiquidGlassNavigationBar` 只用这一个信号决定阴影/描边 alpha。
 * 不走 MiuixTheme：miuix 0.9.4-rc01 的 `isDark` 挂在 `ThemeController` 实例上，composable 侧读不到。
 */
@Composable
fun isInDarkTheme(): Boolean = when (LocalColorMode.current) {
    1, 4 -> false
    2, 5, 6 -> true
    else -> isSystemInDarkTheme()
}
