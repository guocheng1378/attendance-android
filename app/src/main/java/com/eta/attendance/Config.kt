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
 * 运行时配置：语言、上下班时间、主题、配色、员工名单、Supabase、WebDAV、自动备份。
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
    /** 历史分配过的最大员工 id（删除员工后不回退，避免 id 复用导致考勤错挂） */
    private const val KEY_ID_HIGH_WATER = "emp_id_high_water"
    /** 自动备份连续失败次数 */
    private const val KEY_AUTO_BACKUP_FAILS = "auto_backup_fails"
    private const val EMP_SEED_VERSION = 3

    /** 新建员工的默认月薪（基普）：与内置名单的主流档位一致，避免导入/新增时月薪恒为 0 */
    const val DEFAULT_MONTHLY_BASE = 4000000.0

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

    @Volatile private var secureSpCache: SharedPreferences? = null

    /**
     * 加密 SharedPreferences（敏感数据：API Key、密码）。
     * 实例创建开销大且重复创建可能失败，故缓存复用；用 applicationContext 防止持有 Activity。
     * 创建失败不写缓存，异常照旧向上抛，由调用方 runCatching 决定降级行为。
     */
    private fun secureSp(c: Context): SharedPreferences {
        secureSpCache?.let { return it }
        synchronized(this) {
            secureSpCache?.let { return it }
            val ctx = c.applicationContext ?: c
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                ctx, SECURE_PREFS, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            secureSpCache = created
            return created
        }
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

    // 主题模式与配色
    fun themeMode(c: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(sp(c).getString(KEY_THEME_MODE, "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
    fun saveThemeMode(c: Context, m: ThemeMode) { sp(c).edit().putString(KEY_THEME_MODE, m.name).apply() }
    fun paletteId(c: Context): String = sp(c).getString(KEY_PALETTE, "ocean") ?: "ocean"
    fun savePalette(c: Context, id: String) { sp(c).edit().putString(KEY_PALETTE, id).apply() }

    /**
     * 员工名单。三种情形分清楚：
     * - 从未存过（raw==null）：返回内置默认名单，[employeesFallback]=false（首次启动属正常）
     * - 存过但解析失败：返回默认名单并置 [employeesFallback]=true，此时 [saveEmployees]
     *   拒绝把默认名单固化回磁盘，否则真名单就永久丢了
     * - 存过且解析成功但为空：返回空列表（用户确实删光了人，尊重之），flag=false
     */
    fun employees(c: Context): List<Employee> {
        if (sp(c).getInt(KEY_EMP_SEED, 0) < EMP_SEED_VERSION) {
            writeEmployees(c, DEFAULT_EMPLOYEES)
            sp(c).edit().putInt(KEY_EMP_SEED, EMP_SEED_VERSION).apply()
            employeesFallback = false
            return DEFAULT_EMPLOYEES
        }
        val raw = sp(c).getString(KEY_EMPLOYEES, null)
            ?: return DEFAULT_EMPLOYEES.also { employeesFallback = false }
        val parsed = runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<Employee>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Employee(
                        o.getInt("id"), o.getString("nameLo"), o.getString("nameZh"),
                        o.optString("position"), o.optDouble("dailyWage", 150000.0),
                        o.optDouble("monthlyBase", DEFAULT_MONTHLY_BASE),
                        o.optDouble("bonus", 0.0), o.optDouble("advance", 0.0)
                    )
                )
            }
            list
        }.getOrNull()
        if (parsed == null) {
            employeesFallback = true
            android.util.Log.w("Config", "员工名单解析失败，临时回落到内置默认名单")
            return DEFAULT_EMPLOYEES
        }
        employeesFallback = false
        return parsed
    }

    /** 上次 [employees] 是否因解析失败回落到内置名单（true 时 UI 应提示，且不要覆盖保存） */
    @Volatile
    var employeesFallback: Boolean = false
        private set

    /**
     * 保存员工名单。返回 false 表示**未写入**：[employeesFallback] 为真且传入的正是内置默认名单，
     * 此时写回会把「解析失败」固化成用户数据、覆盖掉原本还能人工恢复的名单。
     */
    fun saveEmployees(c: Context, list: List<Employee>): Boolean {
        if (employeesFallback && list == DEFAULT_EMPLOYEES) return false
        writeEmployees(c, list)
        employeesFallback = false
        return true
    }

    /** 实际写盘（绕过回落守卫，仅供 seed 迁移与本对象内部使用），并顺带抬高 id 高水位 */
    private fun writeEmployees(c: Context, list: List<Employee>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().put("id", e.id).put("nameLo", e.nameLo).put("nameZh", e.nameZh)
                    .put("position", e.position).put("dailyWage", e.dailyWage)
                    .put("monthlyBase", e.monthlyBase).put("bonus", e.bonus).put("advance", e.advance)
            )
        }
        val maxId = list.maxOfOrNull { it.id } ?: 0
        val ed = sp(c).edit().putString(KEY_EMPLOYEES, arr.toString())
        if (maxId > sp(c).getInt(KEY_ID_HIGH_WATER, 0)) ed.putInt(KEY_ID_HIGH_WATER, maxId)
        ed.apply()
    }

    /**
     * 下一个员工 id = max(历史高水位, 当前名单最大 id) + 1。
     * 删除员工后高水位不回退，故离职者的历史考勤不会被错挂到同名新人身上。
     */
    private fun nextEmployeeId(c: Context, list: List<Employee>): Int {
        val high = sp(c).getInt(KEY_ID_HIGH_WATER, 0)
        val nowMax = list.maxOfOrNull { it.id } ?: 0
        return maxOf(high, nowMax) + 1
    }

    fun addEmployee(c: Context, nameLo: String, nameZh: String, monthly: Double, bonus: Double = 0.0): Int {
        val list = employees(c).toMutableList()
        val id = nextEmployeeId(c, list)
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

    // Supabase（敏感数据用加密存储；下面的明文回退仅用于读历史版本遗留的数据，新写入一律只进加密区）
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
    /**
     * 保存 Supabase 配置。返回 false = 加密区不可用，此时**一个字段都不写**（不再降级存明文，
     * 避免 anon key / URL 以明文落在 SharedPreferences 里），UI 应据此提示云端同步无法开启。
     */
    fun saveSupabase(c: Context, url: String, key: String): Boolean {
        val secure = runCatching { secureSp(c) }.getOrNull() ?: return false
        return runCatching {
            secure.edit().putString(KEY_SB_URL, url.trim()).putString(KEY_SB_KEY, key.trim()).apply()
            true
        }.getOrDefault(false)
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
    fun davPath(c: Context): String = sp(c).getString(KEY_DAV_PATH, "/attendance/attendance_backup.json") ?: "/attendance/attendance_backup.json"
    /**
     * 保存 WebDAV 配置。地址/账号/路径不敏感，照常写普通 sp；
     * 密码只写加密区 —— 加密区不可用时返回 false 且**不存明文密码**（不再降级），
     * UI 应提示用户「本机安全存储不可用，密码未保存」。
     */
    fun saveDav(c: Context, url: String, user: String, pass: String, path: String): Boolean {
        sp(c).edit()
            .putString(KEY_DAV_URL, url.trim())
            .putString(KEY_DAV_USER, user.trim())
            .putString(KEY_DAV_PATH, path.ifBlank { "/attendance/attendance_backup.json" })
            .apply()
        val secure = runCatching { secureSp(c) }.getOrNull() ?: return false
        return runCatching {
            secure.edit().putString(KEY_DAV_PASS, pass).apply()
            true
        }.getOrDefault(false)
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

    /**
     * 自动备份连续失败计数：成功一次即清零，用于「连续失败若干次才通知」，
     * 免得每天断网都弹一条通知刷屏。
     */
    fun autoBackupFailures(c: Context): Int = sp(c).getInt(KEY_AUTO_BACKUP_FAILS, 0)

    /** 失败计数 +1 并返回新值 */
    fun incrAutoBackupFailures(c: Context): Int {
        val n = autoBackupFailures(c) + 1
        sp(c).edit().putInt(KEY_AUTO_BACKUP_FAILS, n).apply()
        return n
    }

    fun resetAutoBackupFailures(c: Context) {
        sp(c).edit().putInt(KEY_AUTO_BACKUP_FAILS, 0).apply()
    }
}
