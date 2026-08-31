package com.eta.attendance

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal enum class PayRange { DAY, MONTH, YEAR }

private data class RB(val start: String, val end: String, val label: String)

@Composable
internal fun SalaryPanel2() {
    val context = LocalContext.current
    val all = remember { mutableStateOf(AttendanceStore.all(context)) }
    var empId by remember { mutableStateOf(Config.EMPLOYEES.firstOrNull()?.id ?: -1) }
    var range by remember { mutableStateOf(PayRange.MONTH) }
    var anchor by remember { mutableStateOf(System.currentTimeMillis()) }
    val prefs = context.getSharedPreferences("attendance_data", Context.MODE_PRIVATE)
    var dailyWage by remember { mutableStateOf(prefs.getFloat("daily_wage", 150000f).toDouble()) }
    var wageInput by remember { mutableStateOf(dailyWage.toInt().toString()) }

    val empRecords = all.value.filter { it.employeeId == empId }
    val b = rangeBounds(range, anchor)
    val inRange = empRecords.filter { it.date >= b.start && it.date < b.end }
    val full = inRange.count { it.status == Status.FULL }
    val half = inRange.count { it.status == Status.HALF }
    val days = full + half * 0.5
    val pay = (days * dailyWage).toLong()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("工资统计", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

        if (Config.EMPLOYEES.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("员工", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Config.EMPLOYEES.forEach { e ->
                        StatusChip(e.nameZh, e.id == empId) { empId = e.id }
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayRange.values().forEach { r ->
                    StatusChip(payRangeLabel(r), r == range) { range = r }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton("‹") { anchor = shiftRange(range, anchor, -1) }
                Text(b.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                GlassButton("›") { anchor = shiftRange(range, anchor, 1) }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text("预计工资", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Text("$pay LAK", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6EA8FF))
            Text("出勤 $days 天（全天 $full · 半天 $half）", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(chartTitle(range), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            BarChart(buildChart(range, anchor, empRecords, dailyWage))
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text("明细记录", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(6.dp))
            if (inRange.isEmpty()) {
                Text("该范围内暂无记录", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            } else {
                inRange.sortedByDescending { it.date }.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(r.date, fontSize = 13.sp, color = Color.White)
                        Text(statusLabel(r.status), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        val d = daysOf(r.status)
                        Text(
                            "${fmtNum(d)}天 · ${(d * dailyWage).toLong()} LAK",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6EA8FF)
                        )
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text("日薪设置（LAK）", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            TextField(
                value = wageInput,
                onValueChange = { wageInput = it },
                label = "日薪",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            GlassButton("保存日薪", primary = true, modifier = Modifier.fillMaxWidth()) {
                dailyWage = wageInput.toDoubleOrNull() ?: dailyWage
                prefs.edit().putFloat("daily_wage", dailyWage.toFloat()).apply()
                all.value = AttendanceStore.all(context)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
        }

        GlassButton("导出本范围 CSV", modifier = Modifier.fillMaxWidth()) {
            exportRange(context, empId, inRange, dailyWage)
        }
    }
}

private fun daysOf(s: Status): Double = when (s) {
    Status.FULL -> 1.0
    Status.HALF -> 0.5
    Status.ABSENT -> 0.0
}

private fun fmtNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

private fun payRangeLabel(r: PayRange): String = when (r) {
    PayRange.DAY -> "日"
    PayRange.MONTH -> "月"
    PayRange.YEAR -> "年"
}

private fun statusLabel(s: Status): String = when (s) {
    Status.FULL -> "全天"
    Status.HALF -> "半天"
    Status.ABSENT -> "缺勤"
}

private fun chartTitle(r: PayRange): String = when (r) {
    PayRange.DAY -> "本月每日工资"
    PayRange.MONTH -> "本年每月工资"
    PayRange.YEAR -> "近五年工资"
}

private fun ymd(c: Calendar): String = String.format(
    Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
)

private fun rangeBounds(range: PayRange, anchor: Long): RB {
    val c = Calendar.getInstance(); c.timeInMillis = anchor
    val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1
    return when (range) {
        PayRange.DAY -> { val d = ymd(c); RB(d, d + "~", d) }
        PayRange.MONTH -> {
            val start = String.format(Locale.US, "%04d-%02d-01", y, m)
            val end = if (m == 12) String.format(Locale.US, "%04d-01-01", y + 1)
            else String.format(Locale.US, "%04d-%02d-01", y, m + 1)
            RB(start, end, String.format(Locale.US, "%04d年%02d月", y, m))
        }
        PayRange.YEAR -> RB(
            String.format(Locale.US, "%04d-01-01", y),
            String.format(Locale.US, "%04d-01-01", y + 1),
            "${y}年"
        )
    }
}

private fun shiftRange(range: PayRange, anchor: Long, dir: Int): Long {
    val c = Calendar.getInstance(); c.timeInMillis = anchor
    when (range) {
        PayRange.DAY -> c.add(Calendar.DAY_OF_MONTH, dir)
        PayRange.MONTH -> c.add(Calendar.MONTH, dir)
        PayRange.YEAR -> c.add(Calendar.YEAR, dir)
    }
    return c.timeInMillis
}

private fun buildChart(range: PayRange, anchor: Long, recs: List<AttendanceRecord>, wage: Double): List<BarEntry> {
    val c = Calendar.getInstance(); c.timeInMillis = anchor
    val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
    fun sumDays(pred: (AttendanceRecord) -> Boolean): Double =
        recs.filter(pred).sumOf { daysOf(it.status) }
    return when (range) {
        PayRange.DAY -> {
            c.set(Calendar.DAY_OF_MONTH, 1)
            val dim = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            (1..dim).map { day ->
                val ds = String.format(Locale.US, "%04d-%02d-%02d", y, m, day)
                BarEntry(if (day % 5 == 0 || day == dim) day.toString() else "", sumDays { it.date == ds } * wage, day == d)
            }
        }
        PayRange.MONTH -> (1..12).map { mo ->
            val prefix = String.format(Locale.US, "%04d-%02d", y, mo)
            BarEntry("$mo", sumDays { it.date.startsWith(prefix) } * wage, mo == m)
        }
        PayRange.YEAR -> ((y - 4)..y).map { yy ->
            BarEntry(yy.toString(), sumDays { it.date.startsWith(yy.toString()) } * wage, yy == y)
        }
    }
}

private fun exportRange(context: Context, empId: Int, recs: List<AttendanceRecord>, wage: Double) {
    val e = Config.EMPLOYEES.firstOrNull { it.id == empId }
    val sb = StringBuilder("date,status,days,wageLAK\n")
    recs.sortedBy { it.date }.forEach { r ->
        val d = daysOf(r.status)
        sb.append(r.date).append(',').append(r.status.name).append(',')
            .append(d).append(',').append((d * wage).toLong()).append('\n')
    }
    val name = "salary_${e?.nameZh ?: empId}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv"
    val dir = java.io.File(context.getExternalFilesDir(null), "exports")
    dir.mkdirs()
    val f = java.io.File(dir, name)
    f.writeText(sb.toString())
    Toast.makeText(context, "已导出 ${f.absolutePath}", Toast.LENGTH_LONG).show()
}
