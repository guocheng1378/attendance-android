package com.eta.attendance

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    val advance: Double = 0.0,
)

/**
 * 运行时配置：语言、上下班时间、主题、配色、工资规则、员工名单、Supabase、WebDAV。
 * 敏感字段（API Key、密码）使用 EncryptedSharedPreferences 加密存储。
 */
object Config {

    private const val PREFS = "attendance_config"
    private const val SECURE_PREFS = "attendance_secure"
    private const val KEY_LOCALE = "locale"
    private const val KEY_WORK_START = "work_start"
    private const val KEY_WORK_END = "work_end"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PALETTE = "palette"
    private const val KEY_PAY_RULE = "pay_rule"
    private const val KEY_EMPLOYEES = "employees"
    private const val KEY_DAV_URL = "dav_url"
    private const val KEY_DAV_USER = "dav_user"
    private const val KEY_DAV_PASS = "dav_pass"
    private const val KEY_DAV_PATH = "dav_path"
    private const val KEY_REMINDER_ON = "reminder_on"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MIN = "reminder_min"
    private const val KEY_SB_URL = "supabase_url"
    private const val KEY_SB_KEY = "supabase_key"
    private const val KEY_AUTO_BACKUP = "auto_backup"
    private const val KEY_EMP_SEED = "emp_seed"
    private const val EMP_SEED_VERSION = 3

    private val DEFAULT_EMPLOYEES = listOf(
        Employee(1, "ໂອນ", "盘", monthlyBase = 4500000.0, bonus = 0.0),
        Employee(2, "ມູ", "姆", monthlyBase = 4000000.0, bonus = 500000.0),
        Employee(3, "ຣິມ", "松", monthlyBase = 4000000.0, bonus = 0.0),
        Employee(4, "ບາວ", "巴", monthlyBase = 4000000.0, bonus = 0.0),
        Employee(5, "ຊົງ", "恩", monthlyBase = 4000000.0, bonus = 0.0),
        Employee(6, "ຈົງ", "乐昂", monthlyBase = 4000000.0, bonus = 0.0),
        Employee(7, "ເບີນ", "文", monthlyBase = 4000000.0, bonus = 0.0),
        Employee(8, "ຕົງ", "米", monthlyBase = 4000000.0, bonus = 0.0),
        Employee(9, "ຈົງ", "拽", monthlyBase = 4500000.0, bonus = 500000.0),
        Employee(10, "ກິນ", "春", monthlyBase = 4500000.0, bonus = 500000.0),
        Employee(11, "ຄົງ", "研", monthlyBase = 4000000.0, bonus = 300000.0),
        Employee(12, "ມົວ", "罗", monthlyBase = 4000000.0, bonus = 0.0),
    )

    /** 兼容旧代码的默认名单 */
    val EMPLOYEES: List<Employee> = DEFAULT_EMPLOYEES

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 加密 SharedPreferences（敏感数据：API Key、密码） */
    private fun secureSp(c: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(c)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            c, SECURE_PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // 语言
    fun locale(c: Context): String = sp(c).getString(KEY_LOCALE, "zh") ?: "zh"
    fun saveLocale(c: Context, v: String) { sp(c).edit().putString(KEY_LOCALE, v).apply() }

    // 上下班时间
    fun workStart(c: Context): String = sp(c).getString(KEY_WORK_START, "08:00") ?: "08:00"
    fun workEnd(c: Context): String = sp(c).getString(KEY_WORK_END, "17:00") ?: "17:00"
    fun saveWorkTime(c: Context, start: String, end: String) {
        sp(c).edit().putString(KEY_WORK_START, start).putString(KEY_WORK_END, end).apply()
    }

    /** 校验 HH:mm 格式 */
    fun isValidTime(v: String): Boolean =
        v.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))

    /** 校验正数（薪资/倍率等） */
    fun isValidPositiveNumber(v: String): Boolean =
        v.toDoubleOrNull()?.let { it >= 0 } == true

    /** 校验正整数 */
    fun isValidPositiveInt(v: String): Boolean =
        v.toIntOrNull()?.let { it >= 0 } == true

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
        if (sp(c).getInt(KEY_EMP_SEED, 0) < EMP_SEED_VERSION) {
            saveEmployees(c, DEFAULT_EMPLOYEES)
            sp(c).edit().putInt(KEY_EMP_SEED, EMP_SEED_VERSION).apply()
            return DEFAULT_EMPLOYEES
        }
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
                        o.optDouble("monthlyBase", 0.0), o.optDouble("bonus", 0.0), o.optDouble("advance", 0.0)
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
                    .put("monthlyBase", e.monthlyBase).put("bonus", e.bonus).put("advance", e.advance)
            )
        }
        sp(c).edit().putString(KEY_EMPLOYEES, arr.toString()).apply()
    }

    fun addEmployee(c: Context, nameLo: String, nameZh: String, monthly: Double, bonus: Double = 0.0): Int {
        val list = employees(c).toMutableList()
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        list.add(Employee(id, nameLo.trim(), nameZh.trim(), "", 0.0, monthly, bonus, 0.0))
        saveEmployees(c, list)
        return id
    }

    fun removeEmployee(c: Context, id: Int) {
        saveEmployees(c, employees(c).filter { it.id != id })
    }

    // 预支 / 备注：按 员工×月份(yyyy-MM) 存储
    private const val KEY_ADV_REMARK = "adv_remark"
    private fun advRemarkRoot(c: Context): JSONObject {
        val raw = sp(c).getString(KEY_ADV_REMARK, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }
    private fun advRemarkChild(c: Context, empId: Int, ym: String): JSONObject {
        return advRemarkRoot(c).optJSONObject("$empId|$ym") ?: JSONObject()
    }
    fun getAdvance(c: Context, empId: Int, ym: String): Double =
        advRemarkChild(c, empId, ym).optDouble("adv", 0.0)
    fun getRemark(c: Context, empId: Int, ym: String): String =
        advRemarkChild(c, empId, ym).optString("remark", "")
    fun setAdvance(c: Context, empId: Int, ym: String, v: Double) {
        val root = advRemarkRoot(c); val key = "$empId|$ym"
        val o = root.optJSONObject(key) ?: JSONObject(); o.put("adv", v); root.put(key, o)
        sp(c).edit().putString(KEY_ADV_REMARK, root.toString()).apply()
    }
    fun setRemark(c: Context, empId: Int, ym: String, v: String) {
        val root = advRemarkRoot(c); val key = "$empId|$ym"
        val o = root.optJSONObject(key) ?: JSONObject(); o.put("remark", v); root.put(key, o)
        sp(c).edit().putString(KEY_ADV_REMARK, root.toString()).apply()
    }

    // Supabase（敏感数据用加密存储）
    fun supabaseUrl(c: Context): String {
        val secure = runCatching { secureSp(c) }.getOrNull()
        val v = secure?.getString(KEY_SB_URL, null) ?: sp(c).getString(KEY_SB_URL, BuildConfig.SUPABASE_URL)
        return v ?: BuildConfig.SUPABASE_URL
    }
    fun supabaseKey(c: Context): String {
        val secure = runCatching { secureSp(c) }.getOrNull()
        val v = secure?.getString(KEY_SB_KEY, null) ?: sp(c).getString(KEY_SB_KEY, BuildConfig.SUPABASE_KEY)
        return v ?: BuildConfig.SUPABASE_KEY
    }
    fun saveSupabase(c: Context, url: String, key: String) {
        val secure = runCatching { secureSp(c) }.getOrNull()
        if (secure != null) {
            secure.edit().putString(KEY_SB_URL, url.trim()).putString(KEY_SB_KEY, key.trim()).apply()
        } else {
            sp(c).edit().putString(KEY_SB_URL, url.trim()).putString(KEY_SB_KEY, key.trim()).apply()
        }
    }
    fun cloudEnabled(c: Context): Boolean =
        supabaseUrl(c).isNotBlank() && supabaseKey(c).isNotBlank()

    // WebDAV（坚果云）— 密码用加密存储
    fun davUrl(c: Context): String = sp(c).getString(KEY_DAV_URL, "") ?: ""
    fun davUser(c: Context): String = sp(c).getString(KEY_DAV_USER, "") ?: ""
    fun davPass(c: Context): String {
        val secure = runCatching { secureSp(c) }.getOrNull()
        return secure?.getString(KEY_DAV_PASS, null) ?: sp(c).getString(KEY_DAV_PASS, "") ?: ""
    }
    fun davPath(c: Context): String = sp(c).getString(KEY_DAV_PATH, "/attendance_backup.json") ?: "/attendance_backup.json"
    fun saveDav(c: Context, url: String, user: String, pass: String, path: String) {
        sp(c).edit()
            .putString(KEY_DAV_URL, url.trim())
            .putString(KEY_DAV_USER, user.trim())
            .putString(KEY_DAV_PATH, path.ifBlank { "/attendance_backup.json" })
            .apply()
        // 密码存加密区
        val secure = runCatching { secureSp(c) }.getOrNull()
        if (secure != null) {
            secure.edit().putString(KEY_DAV_PASS, pass).apply()
        } else {
            sp(c).edit().putString(KEY_DAV_PASS, pass).apply()
        }
    }
    fun davEnabled(c: Context): Boolean =
        davUrl(c).isNotBlank() && davUser(c).isNotBlank()

    fun reminderEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_REMINDER_ON, false)
    fun reminderHour(c: Context): Int = sp(c).getInt(KEY_REMINDER_HOUR, 9)
    fun reminderMinute(c: Context): Int = sp(c).getInt(KEY_REMINDER_MIN, 0)
    fun saveReminder(c: Context, enabled: Boolean, hour: Int, minute: Int) {
        sp(c).edit()
            .putBoolean(KEY_REMINDER_ON, enabled)
            .putInt(KEY_REMINDER_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_REMINDER_MIN, minute.coerceIn(0, 59))
            .apply()
    }

    // 自动备份
    fun autoBackupEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_AUTO_BACKUP, false)
    fun saveAutoBackup(c: Context, enabled: Boolean) {
        sp(c).edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }
}
