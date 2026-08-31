package com.eta.attendance

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 员工：老挝文名 + 中文名 + 岗位 + 薪资（日薪/月薪/奖金，单位基普 LAK） */
data class Employee(
    val id: Int,
    val nameLo: String,
    val nameZh: String,
    val position: String = "",
    val dailyWage: Double = 150000.0,
    val monthlyBase: Double = 0.0,
    val bonus: Double = 0.0,
)

/**
 * 运行时配置：语言、上下班时间、主题、配色、工资规则、员工名单、Supabase、WebDAV。
 * 全部持久化到 SharedPreferences；Supabase 默认值来自 BuildConfig（CI 注入）。
 */
object Config {

    private const val PREFS = "attendance_config"
    private const val KEY_LOCALE = "locale"
    private const val KEY_WORK_START = "work_start"
    private const val KEY_WORK_END = "work_end"
    private const val KEY_SB_URL = "supabase_url"
    private const val KEY_SB_KEY = "supabase_key"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PALETTE = "palette"
    private const val KEY_PAY_RULE = "pay_rule"
    private const val KEY_EMPLOYEES = "employees"
    private const val KEY_DAV_URL = "dav_url"
    private const val KEY_DAV_USER = "dav_user"
    private const val KEY_DAV_PASS = "dav_pass"
    private const val KEY_DAV_PATH = "dav_path"

    private val DEFAULT_EMPLOYEES = listOf(
        Employee(1, "ແຫນ", "盘"),
        Employee(2, "ນ້ອຍ", "姆"),
        Employee(3, "ນົວ", "松"),
        Employee(4, "ບາ", "巴"),
        Employee(5, "ເອົາ", "糖"),
        Employee(6, "ແກ້ວ", "乐昂"),
        Employee(7, "ອິ", "文"),
        Employee(8, "ຕົວ", "米"),
        Employee(9, "ແພງ", "挽"),
        Employee(10, "ຄຳ", "春"),
        Employee(11, "ແນນ", "研"),
        Employee(12, "ດາວ", "孙"),
        Employee(13, "ຕາວ", "罗"),
    )

    /** 兼容旧代码的默认名单 */
    val EMPLOYEES: List<Employee> = DEFAULT_EMPLOYEES

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 语言
    fun locale(c: Context): String = sp(c).getString(KEY_LOCALE, "zh") ?: "zh"
    fun saveLocale(c: Context, v: String) { sp(c).edit().putString(KEY_LOCALE, v).apply() }

    // 上下班时间
    fun workStart(c: Context): String = sp(c).getString(KEY_WORK_START, "08:00") ?: "08:00"
    fun workEnd(c: Context): String = sp(c).getString(KEY_WORK_END, "17:00") ?: "17:00"
    fun saveWorkTime(c: Context, start: String, end: String) {
        sp(c).edit().putString(KEY_WORK_START, start).putString(KEY_WORK_END, end).apply()
    }

    // 主题模式与配色
    fun themeMode(c: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(sp(c).getString(KEY_THEME_MODE, "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    fun saveThemeMode(c: Context, m: ThemeMode) { sp(c).edit().putString(KEY_THEME_MODE, m.name).apply() }
    fun paletteId(c: Context): String = sp(c).getString(KEY_PALETTE, "ocean") ?: "ocean"
    fun savePalette(c: Context, id: String) { sp(c).edit().putString(KEY_PALETTE, id).apply() }

    // 工资规则
    fun payRule(c: Context): PayRule {
        val raw = sp(c).getString(KEY_PAY_RULE, null) ?: return PayRule()
        return runCatching {
            val o = JSONObject(raw)
            PayRule(
                expectedDays = o.optInt("expectedDays", 26),
                workHoursPerDay = o.optDouble("workHoursPerDay", 8.0),
                otRateWeekday = o.optDouble("otRateWeekday", 1.5),
                otRateWeekend = o.optDouble("otRateWeekend", 2.0),
                otRateHoliday = o.optDouble("otRateHoliday", 3.0),
                lateDeduction = o.optDouble("lateDeduction", 10000.0),
                lateGraceMinutes = o.optInt("lateGraceMinutes", 5),
                sickPayFactor = o.optDouble("sickPayFactor", 0.5),
                personalPayFactor = o.optDouble("personalPayFactor", 0.0),
                annualPayFactor = o.optDouble("annualPayFactor", 1.0),
                mealAllowance = o.optDouble("mealAllowance", 0.0),
                transportAllowance = o.optDouble("transportAllowance", 0.0),
                housingAllowance = o.optDouble("housingAllowance", 0.0),
            )
        }.getOrDefault(PayRule())
    }
    fun savePayRule(c: Context, r: PayRule) {
        val o = JSONObject()
            .put("expectedDays", r.expectedDays).put("workHoursPerDay", r.workHoursPerDay)
            .put("otRateWeekday", r.otRateWeekday).put("otRateWeekend", r.otRateWeekend)
            .put("otRateHoliday", r.otRateHoliday).put("lateDeduction", r.lateDeduction)
            .put("lateGraceMinutes", r.lateGraceMinutes).put("sickPayFactor", r.sickPayFactor)
            .put("personalPayFactor", r.personalPayFactor).put("annualPayFactor", r.annualPayFactor)
            .put("mealAllowance", r.mealAllowance).put("transportAllowance", r.transportAllowance)
            .put("housingAllowance", r.housingAllowance)
        sp(c).edit().putString(KEY_PAY_RULE, o.toString()).apply()
    }

    // 员工名单（可持久化增删改）
    fun employees(c: Context): List<Employee> {
        val raw = sp(c).getString(KEY_EMPLOYEES, null) ?: return DEFAULT_EMPLOYEES
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<Employee>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Employee(
                        o.getInt("id"), o.getString("nameLo"), o.getString("nameZh"),
                        o.optString("position"), o.optDouble("dailyWage", 150000.0),
                        o.optDouble("monthlyBase", 0.0), o.optDouble("bonus", 0.0)
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_EMPLOYEES else list
        }.getOrDefault(DEFAULT_EMPLOYEES)
    }
    fun saveEmployees(c: Context, list: List<Employee>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().put("id", e.id).put("nameLo", e.nameLo).put("nameZh", e.nameZh)
                    .put("position", e.position).put("dailyWage", e.dailyWage)
                    .put("monthlyBase", e.monthlyBase).put("bonus", e.bonus)
            )
        }
        sp(c).edit().putString(KEY_EMPLOYEES, arr.toString()).apply()
    }

    // Supabase
    fun supabaseUrl(c: Context): String =
        sp(c).getString(KEY_SB_URL, BuildConfig.SUPABASE_URL) ?: BuildConfig.SUPABASE_URL
    fun supabaseKey(c: Context): String =
        sp(c).getString(KEY_SB_KEY, BuildConfig.SUPABASE_KEY) ?: BuildConfig.SUPABASE_KEY
    fun saveSupabase(c: Context, url: String, key: String) {
        sp(c).edit().putString(KEY_SB_URL, url.trim()).putString(KEY_SB_KEY, key.trim()).apply()
    }
    fun cloudEnabled(c: Context): Boolean =
        supabaseUrl(c).isNotBlank() && supabaseKey(c).isNotBlank()

    // WebDAV（坚果云）
    fun davUrl(c: Context): String = sp(c).getString(KEY_DAV_URL, "") ?: ""
    fun davUser(c: Context): String = sp(c).getString(KEY_DAV_USER, "") ?: ""
    fun davPass(c: Context): String = sp(c).getString(KEY_DAV_PASS, "") ?: ""
    fun davPath(c: Context): String = sp(c).getString(KEY_DAV_PATH, "/attendance_backup.json") ?: "/attendance_backup.json"
    fun saveDav(c: Context, url: String, user: String, pass: String, path: String) {
        sp(c).edit()
            .putString(KEY_DAV_URL, url.trim())
            .putString(KEY_DAV_USER, user.trim())
            .putString(KEY_DAV_PASS, pass)
            .putString(KEY_DAV_PATH, path.ifBlank { "/attendance_backup.json" })
            .apply()
    }
    fun davEnabled(c: Context): Boolean =
        davUrl(c).isNotBlank() && davUser(c).isNotBlank()
}
