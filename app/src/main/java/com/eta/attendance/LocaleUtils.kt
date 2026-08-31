package com.eta.attendance

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用界面语言：中文 / 老挝文。
 * 在 Activity 创建前调用 [apply]，切换语言后 recreate Activity 生效。
 */
object LocaleUtils {

    private const val LO_LANG = "lo"
    private const val ZH_LANG = "zh"

    fun currentLocale(context: Context): String = Config.locale(context)

    fun apply(context: Context) {
        val lang = currentLocale(context)
        val locale = if (lang == LO_LANG) Locale(LO_LANG, "LA") else Locale(ZH_LANG, "CN")
        Locale.setDefault(locale)
        val conf = Configuration(context.resources.configuration)
        conf.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }
}
