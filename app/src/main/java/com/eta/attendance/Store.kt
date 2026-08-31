package com.eta.attendance

import android.content.Context
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

    /** 导出 CSV 文本 */
    fun toCsv(c: Context): String {
        val sb = StringBuilder()
        sb.append("date,employeeId,nameZh,nameLo,status,checkInTime,late\n")
        all(c).sortedWith(compareBy({ it.date }, { it.employeeId })).forEach { r ->
            val e = Config.EMPLOYEES.firstOrNull { it.id == r.employeeId }
            sb.append(r.date).append(',')
                .append(r.employeeId).append(',')
                .append(e?.nameZh ?: "").append(',')
                .append(e?.nameLo ?: "").append(',')
                .append(r.status.name).append(',')
                .append(r.checkInTime).append(',')
                .append(r.late).append('\n')
        }
        return sb.toString()
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

    /** 推送到 Supabase（upsert）。失败静默返回 false。 */
    suspend fun pushToSupabase(c: Context): Boolean {
        if (!Config.cloudEnabled(c)) return false
        val url = Config.supabaseUrl(c).trimEnd('/') + "/rest/v1/attendance"
        val arr = JSONArray()
        all(c).forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("employee_id", r.employeeId)
                    put("date", r.date)
                    put("status", r.status.name.lowercase())
                    put("check_in_time", r.checkInTime)
                    put("late", r.late)
                }
            )
        }
        val req = Request.Builder()
            .url(url)
            .header("apikey", Config.supabaseKey(c))
            .header("Authorization", "Bearer " + Config.supabaseKey(c))
            .header("Prefer", "resolution=merge-duplicates")
            .post(arr.toString().toRequestBody(JSON))
            .build()
        return runCatching {
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
