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

    private const val PREFS = "attendance_data"
    private const val KEY = "records"
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    private fun sp(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun all(c: Context): MutableList<AttendanceRecord> {
        val raw = sp(c).getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<AttendanceRecord>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    AttendanceRecord(
                        o.getInt("employeeId"),
                        o.getString("date"),
                        Status.valueOf(o.getString("status")),
                        o.optString("checkInTime"),
                        o.optBoolean("late")
                    )
                )
            }
        }
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
        sp(c).edit().putString(KEY, arr.toString()).apply()
    }

    /** 记录/更新某员工某天状态（同员工同天覆盖）。 */
    fun upsert(c: Context, rec: AttendanceRecord) {
        val list = all(c)
        list.removeAll { it.employeeId == rec.employeeId && it.date == rec.date }
        list.add(rec)
        saveAll(c, list)
    }

    /** 批量记录/更新：一次读写，避免 O(n²)。 */
    fun upsertBatch(c: Context, recs: List<AttendanceRecord>) {
        if (recs.isEmpty()) return
        val list = all(c)
        recs.forEach { rec ->
            list.removeAll { it.employeeId == rec.employeeId && it.date == rec.date }
            list.add(rec)
        }
        saveAll(c, list)
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

    /** 导入 CSV：格式 date,employeeId,status,checkInTime,late（跳过表头） */
    fun importCsv(c: Context, csv: String): Int {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return 0
        val list = all(c)
        var n = 0
        val start = if (lines[0].startsWith("date,")) 1 else 0
        for (i in start until lines.size) {
            val parts = parseCsvLine(lines[i])
            if (parts.size < 4) continue
            val date = parts[0]
            val empId = parts[1].toIntOrNull() ?: continue
            val status = runCatching { Status.valueOf(parts[2]) }.getOrNull() ?: continue
            val time = parts[3]
            val late = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
            val rec = AttendanceRecord(empId, date, status, time, late)
            list.removeAll { it.employeeId == empId && it.date == date }
            list.add(rec); n++
        }
        saveAll(c, list)
        return n
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

    /** 导入备份：同员工同天覆盖，返回条数 */
    fun importBackup(c: Context, json: String): Int {
        val arr = JSONObject(json).optJSONArray("records") ?: JSONArray()
        val list = all(c); var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val rec = AttendanceRecord(o.getInt("employeeId"), o.getString("date"),
                Status.valueOf(o.getString("status")), o.optString("checkInTime"), o.optBoolean("late"))
            list.removeAll { it.employeeId == rec.employeeId && it.date == rec.date }
            list.add(rec); n++
        }
        saveAll(c, list); return n
    }

    /** 从 Tracker JSON 导入（按姓名匹配员工）。IO 线程执行。 */
    suspend fun importFromTracker(c: Context, url: String): Int = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url.trim()).build()
            val body = client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
                ?: return@withContext -1
            val arr = JSONArray(body)
            val emps = Config.employees(c).toMutableList()
            fun empIdFor(zh: String, lo: String): Int {
                emps.find { it.nameZh == zh }?.let { return it.id }
                val id = (emps.maxOfOrNull { it.id } ?: 0) + 1
                emps.add(Employee(id, lo, zh, "", 150000.0, 0.0, 0.0, 0.0))
                return id
            }
            val list = all(c)
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = o.getString("d")
                val z = o.getString("z")
                val lo = o.optString("n")
                val status = when (o.optString("s")) {
                    "f" -> Status.FULL
                    "h" -> Status.HALF
                    else -> Status.ABSENT
                }
                val t = o.optString("t")
                val hhmm = if (t.length >= 16) t.substring(11, 16) else ""
                val id = empIdFor(z, lo)
                val rec = AttendanceRecord(id, d, status, hhmm, false)
                list.removeAll { it.employeeId == id && it.date == d }
                list.add(rec); n++
            }
            Config.saveEmployees(c, emps)
            saveAll(c, list)
            n
        } catch (e: Exception) {
            -1
        }
    }

    /** 推送到 Supabase（upsert）。IO 线程执行。 */
    suspend fun pushToSupabase(c: Context): Boolean = withContext(Dispatchers.IO) {
        if (!Config.cloudEnabled(c)) return@withContext false
        val url = Config.supabaseUrl(c).trimEnd('/') + "/rest/v1/attendance"
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
        val req = Request.Builder()
            .url(url)
            .header("apikey", Config.supabaseKey(c))
            .header("Authorization", "Bearer " + Config.supabaseKey(c))
            .header("Prefer", "resolution=merge-duplicates")
            .post(arr.toString().toRequestBody(JSON))
            .build()
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /** WebDAV 上传备份（坚果云）。IO 线程执行。 */
    suspend fun pushToDav(c: Context): Boolean = withContext(Dispatchers.IO) {
        if (!Config.davEnabled(c)) return@withContext false
        val req = Request.Builder().url(davFullUrl(c))
            .header("Authorization", davAuth(c))
            .put(exportBackup(c).toRequestBody(JSON))
            .build()
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    /** WebDAV 下载并导入，返回条数（-1 失败）。IO 线程执行。 */
    suspend fun pullFromDav(c: Context): Int = withContext(Dispatchers.IO) {
        if (!Config.davEnabled(c)) return@withContext -1
        val req = Request.Builder().url(davFullUrl(c)).header("Authorization", davAuth(c)).get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext -1
                val body = resp.body?.string() ?: return@withContext -1
                importBackup(c, body)
            }
        }.getOrDefault(-1)
    }

    private fun davFullUrl(c: Context): String {
        val base = Config.davUrl(c).trimEnd('/')
        val path = Config.davPath(c)
        return base + (if (path.startsWith("/")) path else "/$path")
    }

    private fun davAuth(c: Context): String {
        val cred = Config.davUser(c) + ":" + Config.davPass(c)
        return "Basic " + android.util.Base64.encodeToString(cred.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
