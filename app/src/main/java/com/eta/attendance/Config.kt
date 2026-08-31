package com.eta.attendance

import android.content.Context
import android.content.SharedPreferences

/** 员工：老挝文名 + 中文名（复用原 config.js EMPLOYEES）。 */
data class Employee(val id: Int, val nameLo: String, val nameZh: String)

/**
 * 运行时配置：界面语言、上下班时间、员工名单、Supabase 云端参数。
 * 持久化到 SharedPreferences；Supabase 默认值来自 BuildConfig（CI 注入）。
 */
object Config {

    private const val PREFS = "attendance_config"
    private const val KEY_LOCALE = "locale"
    private const val KEY_WORK_START = "work_start"
    private const val KEY_WORK_END = "work_end"
    private const val KEY_SB_URL = "supabase_url"
    private const val KEY_SB_KEY = "supabase_key"

    val EMPLOYEES = listOf(
        Employee(1, "ໂອນ", "盘"),
        Employee(2, "ມູ", "姆"),
        Employee(3, "ຣິມ", "松"),
        Employee(4, "ບາວ", "巴"),
        Employee(5, "ຊົງ", "恩"),
        Employee(6, "ຈົງ", "乐昂"),
        Employee(7, "ເບີນ", "文"),
        Employee(8, "ຕົງ", "米"),
        Employee(9, "ຈົງ", "拽"),
        Employee(10, "ກິນ", "春"),
        Employee(11, "ຄົງ", "研"),
        Employee(12, "ມົວ", "罗")
    )

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 界面语言："zh" 中文（默认），"lo" 老挝文 */
    fun locale(c: Context): String = sp(c).getString(KEY_LOCALE, "zh") ?: "zh"
    fun saveLocale(c: Context, v: String) { sp(c).edit().putString(KEY_LOCALE, v).apply() }

    fun workStart(c: Context): String = sp(c).getString(KEY_WORK_START, "08:00") ?: "08:00"
    fun workEnd(c: Context): String = sp(c).getString(KEY_WORK_END, "17:00") ?: "17:00"
    fun saveWorkTime(c: Context, start: String, end: String) {
        sp(c).edit().putString(KEY_WORK_START, start).putString(KEY_WORK_END, end).apply()
    }

    fun supabaseUrl(c: Context): String =
        sp(c).getString(KEY_SB_URL, BuildConfig.SUPABASE_URL) ?: BuildConfig.SUPABASE_URL
    fun supabaseKey(c: Context): String =
        sp(c).getString(KEY_SB_KEY, BuildConfig.SUPABASE_KEY) ?: BuildConfig.SUPABASE_KEY
    fun saveSupabase(c: Context, url: String, key: String) {
        sp(c).edit().putString(KEY_SB_URL, url.trim()).putString(KEY_SB_KEY, key.trim()).apply()
    }
    fun cloudEnabled(c: Context): Boolean =
        supabaseUrl(c).isNotBlank() && supabaseKey(c).isNotBlank()
}
