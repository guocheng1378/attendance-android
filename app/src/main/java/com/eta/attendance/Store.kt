package com.eta.attendance

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Status { FULL, HALF, ABSENT }

data class AttendanceRecord(
    val employeeId: Int,
    val date: String,        // yyyy-MM-dd
    val status: Status,
    val checkInTime: String, // HH:mm
    val late: Boolean = false
)

/**
 * 考勤数据仓库：本地以 JSON 存 SharedPreferences；可选推送 Supabase(rest/v1/attendance)。
 * 数据量小（十几人 × 每天一条），无需 Room。
 */
object AttendanceStore {

    /** 考勤记录 SharedPreferences 文件名；UI 侧监听该 prefs 变化做缓存失效，故对外公开。 */
    const val PREFS_NAME = "attendance_data"
    private const val KEY = "records"
    /** 本地最近一次落盘时间（毫秒），用于与备份 exportedAt 比较，决定是否拒绝导入 */
    private const val KEY_MTIME = "mtime"
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    /** 上次 all() 解析时跳过的坏记录条数（含整段 JSON 无法解析的情况，此时记为 1） */
    @Volatile
    var lastSkipped: Int = 0
        private set

    /** 上次 GitHub/Tracker 导入中按姓名未匹配到任何员工的名单（UI 可提示，不静默丢弃） */
    @Volatile
    var lastUnmatched: List<String> = emptyList()
        private set

    private fun sp(c: Context) = c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 取字符串资源（无参时不走格式化，避免文案里的 % 被吃掉） */
    private fun str(c: Context, res: Int, vararg args: Any): String =
        if (args.isEmpty()) c.getString(res) else c.getString(res, *args)

    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /**
     * 日期是否真实存在：先过 [DATE_RE]，再查该年该月的实际天数（含闰年），
     * 挡掉 `2026-13-99`、`2026-02-30` 这类「格式对但日历上不存在」的串——
     * 这类记录落在任何月份视图里都查不出来，等于悄悄丢数据。
     */
    private fun validDate(s: String): Boolean {
        if (!DATE_RE.matches(s)) return false
        val y = s.substring(0, 4).toInt()
        val m = s.substring(5, 7).toInt()
        val d = s.substring(8, 10).toInt()
        return runCatching { d in 1..java.time.YearMonth.of(y, m).lengthOfMonth() }.getOrDefault(false)
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /**
     * 读取全部记录：逐条 runCatching，坏掉的那条跳过而不影响其余记录；
     * 跳过条数写入 [lastSkipped] 供 UI 提示（整段 JSON 无法解析时记为 1，因为无法逐条计数）。
     */
    fun all(c: Context): MutableList<AttendanceRecord> {
        val raw = sp(c).getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<AttendanceRecord>()
        var skipped = 0
        val arr = runCatching { JSONArray(raw) }.getOrNull()
        if (arr == null) {
            // 整段损坏：无法逐条统计，记 1 条以便 UI 至少提示一次「数据有问题」
            skipped = 1
        } else {
            for (i in 0 until arr.length()) {
                val rec = runCatching {
                    val o = arr.getJSONObject(i)
                    AttendanceRecord(
                        o.getInt("employeeId"),
                        o.getString("date"),
                        Status.valueOf(o.getString("status")),
                        o.optString("checkInTime"),
                        o.optBoolean("late")
                    )
                }.getOrNull()
                if (rec == null) skipped++ else list.add(rec)
            }
        }
        lastSkipped = skipped
        if (skipped > 0) android.util.Log.w("AttendanceStore", "考勤数据解析跳过 $skipped 条坏记录")
        return list
    }

    private fun saveAll(c: Context, list: List<AttendanceRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("employeeId", r.employeeId)
                    put("date", r.date)
                    put("status", r.status.name)
                    put("checkInTime", r.checkInTime)
                    put("late", r.late)
                }
            )
        }
        // 回写即「清洗」：上次解析跳过的坏条会在本次落盘时自然消失
        sp(c).edit().putString(KEY, arr.toString()).putLong(KEY_MTIME, System.currentTimeMillis()).apply()
    }

    /** 记录/更新某员工某天状态（同员工同天覆盖）。 */
    fun upsert(c: Context, rec: AttendanceRecord) {
        val list = all(c)
        list.removeAll { it.employeeId == rec.employeeId && it.date == rec.date }
        list.add(rec)
        saveAll(c, list)
    }

    /**
     * 批量记录/更新：一次读写，用 (员工,日期) 键的 HashSet 批量剔除旧条目，复杂度 O(n+m)；
     * 同批次内同员工同天只保留最后一条。
     * recs 为空时不再提前返回，仍走一次正常读写（顺带刷新 [lastSkipped] 等解析状态）；
     * 但本方法无从得知「用户想清掉哪一天」，清空某天的记录请用 [clearDay]。
     */
    fun upsertBatch(c: Context, recs: List<AttendanceRecord>) {
        val list = all(c)
        if (recs.isNotEmpty()) {
            val merged = LinkedHashMap<Pair<Int, String>, AttendanceRecord>()
            recs.forEach { merged[it.employeeId to it.date] = it }
            val keys = HashSet(merged.keys)
            list.removeAll { (it.employeeId to it.date) in keys }
            list.addAll(merged.values)
        }
        saveAll(c, list)
    }

    /** 清空某天的全部考勤记录，返回被删除的条数。 */
    fun clearDay(c: Context, date: String): Int {
        val list = all(c)
        val before = list.size
        list.removeAll { it.date == date }
        if (list.size != before) saveAll(c, list)
        return before - list.size
    }

    fun forDate(c: Context, date: String): List<AttendanceRecord> =
        all(c).filter { it.date == date }

    /** 月度汇总：员工 id -> [full, half, absent] */
    fun monthSummary(c: Context, yearMonth: String): Map<Int, IntArray> {
        val map = HashMap<Int, IntArray>()
        all(c).filter { it.date.startsWith(yearMonth) }.forEach { r ->
            val a = map.getOrPut(r.employeeId) { IntArray(3) }
            when (r.status) {
                Status.FULL -> a[0]++
                Status.HALF -> a[1]++
                Status.ABSENT -> a[2]++
            }
        }
        return map
    }

    /**
     * 当月「全员没来」天数：某天**有记录的人数等于员工总数**且这些记录全部为 ABSENT 才算停工日。
     * 只给部分人补记过 ABSENT（例如只有 3 人签到页被填过）不再被误算成全员缺席；
     * 员工名单为空（总数 0）时一律不算。
     */
    fun companyAbsentDays(c: Context, yearMonth: String): Int {
        val total = Config.employees(c).size
        if (total == 0) return 0
        val byDate = all(c).filter { it.date.startsWith(yearMonth) }.groupBy { it.date }
        return byDate.count { (_, recs) ->
            recs.map { it.employeeId }.distinct().size == total && recs.all { it.status == Status.ABSENT }
        }
    }

    /** CSV 字段转义：含逗号/引号/换行的字段加双引号 */
    private fun csvField(v: String): String {
        return if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            "\"${v.replace("\"", "\"\"")}\""
        } else v
    }

    /** 导出 CSV 文本 */
    fun toCsv(c: Context): String {
        val sb = StringBuilder()
        sb.append("date,employeeId,nameZh,nameLo,status,checkInTime,late\n")
        all(c).sortedWith(compareBy({ it.date }, { it.employeeId })).forEach { r ->
            val e = Config.employees(c).firstOrNull { it.id == r.employeeId }
            sb.append(csvField(r.date)).append(',')
                .append(r.employeeId).append(',')
                .append(csvField(e?.nameZh ?: "")).append(',')
                .append(csvField(e?.nameLo ?: "")).append(',')
                .append(r.status.name).append(',')
                .append(csvField(r.checkInTime)).append(',')
                .append(r.late).append('\n')
        }
        return sb.toString()
    }

    /**
     * 导入 CSV。列位置由表头决定，兼容两种写法：
     *  - [toCsv] 导出的 `date,employeeId,nameZh,nameLo,status,checkInTime,late`（可直接回灌，姓名列忽略）
     *  - 无表头的旧 5 列位置格式 `date,employeeId,status,checkInTime,late`
     * 旧实现固定按 1/2/3 列取 status，读自己导出的 7 列文件时会把姓名当状态解析，整份文件一条都进不去。
     * 坏行（日期非法 / 工号非数字 / 状态不在 FULL|HALF|ABSENT）跳过并写日志，返回值只计成功条数。
     */
    fun importCsv(c: Context, csv: String): Int {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return 0
        val header = parseCsvLine(lines[0]).map { it.trim().lowercase() }
        val byHeader = header.contains("date") && header.contains("employeeid") && header.contains("status")
        fun col(name: String, legacy: Int) = if (byHeader) header.indexOf(name) else legacy
        val dDate = col("date", 0)
        val dEmp = col("employeeid", 1)
        val dStatus = col("status", 2)
        val dTime = col("checkintime", 3)
        val dLate = col("late", 4)
        val minCols = maxOf(dDate, dEmp, dStatus) + 1
        val first = if (byHeader) 1 else 0
        val valid = mutableListOf<AttendanceRecord>()
        var bad = 0
        for (i in first until lines.size) {
            val parts = parseCsvLine(lines[i])
            if (parts.size < minCols) { bad++; continue }
            val date = parts[dDate].trim()
            val empId = parts[dEmp].trim().toIntOrNull()
            val status = if (empId == null) null
                else runCatching { Status.valueOf(parts[dStatus].trim().uppercase()) }.getOrNull()
            if (empId == null || status == null || !validDate(date)) { bad++; continue }
            valid.add(
                AttendanceRecord(
                    empId, date, status,
                    if (dTime in parts.indices) parts[dTime].trim() else "",
                    if (dLate in parts.indices) parts[dLate].trim().toBooleanStrictOrNull() ?: false else false,
                )
            )
        }
        if (bad > 0) android.util.Log.w("AttendanceStore", "CSV 导入跳过 $bad 条坏记录/非法日期")
        if (valid.isNotEmpty()) upsertBatch(c, valid)
        return valid.size
    }

    /** 简单 CSV 行解析（支持双引号字段） */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        for (ch in line) {
            when {
                ch == '"' -> inQuote = !inQuote
                ch == ',' && !inQuote -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }

    /** 导出全量备份 JSON */
    fun exportBackup(c: Context): String {
        val arr = JSONArray()
        all(c).forEach { r ->
            arr.put(JSONObject().apply {
                put("employeeId", r.employeeId); put("date", r.date)
                put("status", r.status.name); put("checkInTime", r.checkInTime); put("late", r.late)
            })
        }
        return JSONObject().apply {
            put("version", 1); put("exportedAt", today()); put("records", arr)
        }.toString()
    }

    /**
     * 备份导入结果：ok=是否写入本地，count=写入条数，message=可展示原因（含坏条告警）。
     * conflict=true 表示被「备份比本地旧」拦下：UI 应弹二次确认，确认后带 force=true 重试。
     */
    data class ImportResult(
        val ok: Boolean,
        val count: Int,
        val message: String,
        val conflict: Boolean = false,
    )

    /**
     * 导入备份（带结果详情）：同员工同天覆盖。
     * - 整体 JSON 解析失败 → ok=false（原数据保持不变）
     * - exportedAt 早于本地最近落盘日期且 force=false → ok=false，提示可再点一次强制覆盖，
     *   避免用一份旧备份悄悄盖掉新数据
     * - 单条解析失败或日期非法 → 只跳过该条并计入告警，其余照常导入
     */
    fun importBackupResult(c: Context, json: String, force: Boolean = false): ImportResult {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return ImportResult(false, 0, str(c, R.string.import_failed))
        val exportedAt = root.optString("exportedAt", "")
        val localDate = localSavedDate(c)
        if (!force && DATE_RE.matches(exportedAt) && localDate != null && exportedAt < localDate) {
            // yyyy-MM-dd 字典序即时间序，可直接比较
            return ImportResult(false, 0, str(c, R.string.import_conflict_fmt, exportedAt), conflict = true)
        }
        val arr = root.optJSONArray("records") ?: JSONArray()
        val valid = mutableListOf<AttendanceRecord>()
        var bad = 0
        for (i in 0 until arr.length()) {
            val rec = runCatching {
                val o = arr.getJSONObject(i)
                AttendanceRecord(
                    o.getInt("employeeId"), o.getString("date"),
                    Status.valueOf(o.getString("status")), o.optString("checkInTime"), o.optBoolean("late")
                )
            }.getOrNull()
            if (rec == null || !validDate(rec.date)) { bad++; continue }
            valid.add(rec)
        }
        if (bad > 0) android.util.Log.w("AttendanceStore", "备份导入忽略 $bad 条坏记录/非法日期")
        if (valid.isNotEmpty()) upsertBatch(c, valid)
        var msg = str(c, R.string.imported_fmt, valid.size)
        if (bad > 0) msg += "（" + str(c, R.string.import_bad_date_fmt, bad) + "）"
        return ImportResult(true, valid.size, msg)
    }

    /** 本地最近一次落盘日期（yyyy-MM-dd），从未写入过返回 null */
    private fun localSavedDate(c: Context): String? {
        val t = sp(c).getLong(KEY_MTIME, 0L)
        if (t <= 0L) return null
        return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t)) }.getOrNull()
    }

    /** Tracker 单条原始数据（内部解析用，未匹配员工前不落库） */
    private data class TrackerRow(
        val date: String,
        val nameZh: String,
        val nameLo: String,
        val status: Status,
        val time: String,
    )

    /** Tracker 导入结果：ok=请求与解析是否成功，count=写入条数，unmatched=按姓名没匹配到员工的名单。 */
    data class TrackerImportResult(val ok: Boolean, val count: Int, val unmatched: List<String>)

    /**
     * 从 Tracker JSON 导入：**只按姓名匹配现有员工**（先中文名、再老挝文名，两侧去空白），
     * 匹配不上的一律跳过并记入 [lastUnmatched]，由 UI 提示用户先去设置里加人。
     * 旧实现会悄悄新建员工，且用位置参数把默认日薪塞进 dailyWage、monthlyBase 恒为 0，
     * 既污染名单又让新人工资恒为 0，故不再新建。IO 线程执行。
     */
    suspend fun importFromTrackerResult(c: Context, url: String): TrackerImportResult =
        withContext(Dispatchers.IO) {
            val req = runCatching { Request.Builder().url(url.trim()).build() }.getOrNull()
                ?: return@withContext TrackerImportResult(false, 0, emptyList())
            val body = runCatching {
                client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull() ?: return@withContext TrackerImportResult(false, 0, emptyList())
            val arr = runCatching { JSONArray(body) }.getOrNull()
                ?: return@withContext TrackerImportResult(false, 0, emptyList())

            val byZh = HashMap<String, Int>()
            val byLo = HashMap<String, Int>()
            Config.employees(c).forEach { e ->
                val zh = e.nameZh.trim(); val lo = e.nameLo.trim()
                if (zh.isNotEmpty()) byZh.putIfAbsent(zh, e.id)
                if (lo.isNotEmpty()) byLo.putIfAbsent(lo, e.id)
            }

            val pending = mutableListOf<AttendanceRecord>()
            val unmatched = mutableListOf<String>()
            var bad = 0
            for (i in 0 until arr.length()) {
                val row = runCatching {
                    val o = arr.getJSONObject(i)
                    val t = o.optString("t")
                    TrackerRow(
                        date = o.getString("d"),
                        nameZh = o.getString("z").trim(),
                        nameLo = o.optString("n").trim(),
                        status = when (o.optString("s")) {
                            "f" -> Status.FULL
                            "h" -> Status.HALF
                            else -> Status.ABSENT
                        },
                        time = if (t.length >= 16) t.substring(11, 16) else ""
                    )
                }.getOrNull()
                if (row == null || !validDate(row.date)) { bad++; continue }
                val id = byZh[row.nameZh] ?: byLo[row.nameLo]
                if (id == null) {
                    unmatched += if (row.nameZh.isNotBlank()) row.nameZh else row.nameLo
                    continue
                }
                pending.add(AttendanceRecord(id, row.date, row.status, row.time, false))
            }
            lastUnmatched = unmatched.toList()
            if (unmatched.isNotEmpty()) {
                android.util.Log.w("AttendanceStore", "GitHub 导入未匹配名单：" + unmatched.joinToString("、"))
            }
            if (bad > 0) android.util.Log.w("AttendanceStore", "GitHub 导入跳过 $bad 条坏记录")
            upsertBatch(c, pending)
            TrackerImportResult(true, pending.size, lastUnmatched)
        }

    /** Supabase 同步结果：ok=是否成功，code=HTTP 码（-1 网络异常，0 未配置），message=可展示原因。 */
    data class SupaResult(val ok: Boolean, val code: Int, val message: String)

    /**
     * 推送到 Supabase（upsert）。区分「未配置」与「请求失败」并带回 HTTP 码，
     * 免得 UI 只拿到一个笼统的 false。IO 线程执行。
     */
    suspend fun pushToSupabaseResult(c: Context): SupaResult = withContext(Dispatchers.IO) {
        if (!Config.cloudEnabled(c)) return@withContext SupaResult(false, 0, str(c, R.string.supa_not_cfg))
        val arr = JSONArray()
        all(c).forEach { r ->
            arr.put(JSONObject().apply {
                put("employee_id", r.employeeId)
                put("date", r.date)
                put("status", r.status.name.lowercase())
                put("check_in_time", r.checkInTime)
                put("late", r.late)
            })
        }
        // Builder 构造（非法 URL 会抛）与请求一起兜底，异常归为网络错误码 -1
        runCatching {
            val req = Request.Builder()
                .url(Config.supabaseUrl(c).trimEnd('/') + "/rest/v1/attendance")
                .header("apikey", Config.supabaseKey(c))
                .header("Authorization", "Bearer " + Config.supabaseKey(c))
                .header("Prefer", "resolution=merge-duplicates")
                .post(arr.toString().toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { it.code }
        }.fold(
            onSuccess = { code ->
                if (code in 200..299) SupaResult(true, code, str(c, R.string.synced))
                else SupaResult(false, code, str(c, R.string.supa_fail, code.toString()))
            },
            onFailure = { SupaResult(false, -1, str(c, R.string.supa_fail, it.message ?: it.javaClass.simpleName)) }
        )
    }

    /** WebDAV 上传结果：ok=是否成功，code=HTTP 码（-1 网络错误，0 未配置），message=可展示原因。 */
    data class DavResult(val ok: Boolean, val code: Int, val message: String)

    /** WebDAV 上传备份（坚果云）。先确保父文件夹存在（坚果云不允许在根目录放文件），再 PUT。IO 线程执行。 */
    suspend fun pushToDav(c: Context): DavResult = withContext(Dispatchers.IO) {
        if (!Config.davEnabled(c)) return@withContext DavResult(false, 0, str(c, R.string.dav_not_cfg))
        if (!hasHttpScheme(c)) return@withContext DavResult(false, -1, str(c, R.string.dav_no_scheme))
        ensureParentFolders(c)?.let { return@withContext it }
        // Builder 构造（非法 URL 会抛）与请求一起兜底
        runCatching {
            val req = Request.Builder().url(davFullUrl(c))
                .header("Authorization", davAuth(c))
                .put(exportBackup(c).toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                if (resp.isSuccessful) DavResult(true, code, str(c, R.string.dav_ok))
                else DavResult(false, code, davHint(c, code))
            }
        }.getOrElse { DavResult(false, -1, str(c, R.string.dav_net_fmt, it.message ?: it.javaClass.simpleName)) }
    }

    /** WebDAV 下载导入结果：ok=是否写入本地，count=条数，message=可展示原因，conflict 同 [ImportResult.conflict]。 */
    data class DavPullResult(
        val ok: Boolean,
        val count: Int,
        val message: String,
        val conflict: Boolean = false,
    )

    /**
     * WebDAV 下载并导入。force=false 时沿用 [importBackupResult] 的新旧检查：
     * 云端备份比本地旧会拒绝合并，message 提示可再点一次强制覆盖。IO 线程执行。
     */
    suspend fun pullFromDavResult(c: Context, force: Boolean = false): DavPullResult = withContext(Dispatchers.IO) {
        if (!Config.davEnabled(c)) return@withContext DavPullResult(false, 0, str(c, R.string.dav_not_cfg))
        if (!hasHttpScheme(c)) return@withContext DavPullResult(false, 0, str(c, R.string.dav_no_scheme))
        val fetched = runCatching {
            val req = Request.Builder().url(davFullUrl(c)).header("Authorization", davAuth(c)).get().build()
            client.newCall(req).execute().use { resp -> Triple(resp.isSuccessful, resp.code, resp.body?.string()) }
        }.getOrElse {
            return@withContext DavPullResult(false, 0, str(c, R.string.dav_net_fmt, it.message ?: it.javaClass.simpleName))
        }
        val (ok, code, body) = fetched
        if (!ok) return@withContext DavPullResult(false, 0, davHint(c, code))
        if (body.isNullOrBlank()) return@withContext DavPullResult(false, 0, str(c, R.string.backup_not_found))
        val res = importBackupResult(c, body, force)
        DavPullResult(res.ok, res.count, res.message, res.conflict)
    }

    /**
     * 归一化完整 URL：坚果云等 WebDAV 不允许把文件放在共享根目录，
     * 若路径没有父文件夹（如 /attendance_backup.json），自动归到 /attendance/ 子目录。
     */
    private fun davFullUrl(c: Context): String {
        val base = Config.davUrl(c).trim().trimEnd('/')
        val segs = Config.davPath(c).split('/').filter { it.isNotBlank() }
        val fileName = segs.lastOrNull() ?: "attendance_backup.json"
        val dirs = segs.dropLast(1).ifEmpty { listOf("attendance") }
        return base + "/" + (dirs + fileName).joinToString("/")
    }

    /**
     * 逐级 MKCOL 创建 base 之下、文件名之上的目录。
     * 坚果云对共享根 /dav/ 的 MKCOL 会返回 403，故跳过 base 段；除 401 认证错误外其它码都继续，最终由 PUT 判定。
     */
    private fun ensureParentFolders(c: Context): DavResult? {
        val uri = runCatching { URI(davFullUrl(c)) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val authority = uri.authority ?: return null
        val baseSegs = (runCatching { URI(Config.davUrl(c).trim()).rawPath }.getOrNull() ?: "")
            .split('/').filter { it.isNotBlank() }
        val dirSegs = (uri.rawPath ?: "").split('/').filter { it.isNotBlank() }.dropLast(1)
        val toCreate = if (dirSegs.size > baseSegs.size) dirSegs.subList(baseSegs.size, dirSegs.size) else emptyList()
        var acc = if (baseSegs.isEmpty()) "" else "/" + baseSegs.joinToString("/")
        for (seg in toCreate) {
            acc += "/" + seg
            val mkUrl = "$scheme://$authority$acc/"
            val code = runCatching {
                val req = Request.Builder().url(mkUrl)
                    .header("Authorization", davAuth(c))
                    .method("MKCOL", null)
                    .build()
                client.newCall(req).execute().use { it.code }
            }.getOrElse {
                return DavResult(false, -1, str(c, R.string.dav_net_fmt, it.message ?: it.javaClass.simpleName))
            }
            if (code == 401) return DavResult(false, 401, davHint(c, 401))
        }
        return null
    }

    /** WebDAV 地址是否带 http/https 前缀（缺前缀时 OkHttp 直接抛异常，这里提前拦下给明确提示） */
    private fun hasHttpScheme(c: Context): Boolean {
        val scheme = runCatching { URI(Config.davUrl(c).trim()).scheme }.getOrNull()
        return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    }

    /** 按 HTTP 码给出可展示的中文/老文提示（全部走字符串资源，不再硬编码） */
    private fun davHint(c: Context, code: Int): String = when (code) {
        401 -> str(c, R.string.dav_401)
        403 -> str(c, R.string.dav_403)
        404 -> str(c, R.string.dav_404)
        409 -> str(c, R.string.dav_409)
        507 -> str(c, R.string.dav_507)
        else -> str(c, R.string.dav_net_fmt, "HTTP $code")
    }

    private fun davAuth(c: Context): String {
        val cred = Config.davUser(c) + ":" + Config.davPass(c)
        return "Basic " + android.util.Base64.encodeToString(cred.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
