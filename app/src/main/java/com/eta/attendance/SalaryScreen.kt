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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
    val employees = remember { Config.employees(context) }
    val rule = remember { Config.payRule(context) }
    val c = LocalAppColors.current
    var empId by remember { mutableStateOf(employees.firstOrNull()?.id ?: -1) }
    var range by remember { mutableStateOf(PayRange.MONTH) }
    var anchor by remember { mutableStateOf(System.currentTimeMillis()) }

    val emp = employees.firstOrNull { it.id == empId }
    val empRecords = all.value.filter { it.employeeId == empId }
    val b = rangeBounds(range, anchor)
    val inRange = empRecords.filter { it.date >= b.start && it.date < b.end }
    val input = SalaryEngine.aggregate(inRange, emp?.dailyWage ?: 0.0, emp?.monthlyBase ?: 0.0, emp?.bonus ?: 0.0)
    val pay = SalaryEngine.compute(input, rule)
    val accent = c.palette.accent

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("工资", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

        if (employees.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("员工", fontSize = 13.sp, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    employees.forEach { e ->
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
                GlassButton("‹", modifier = Modifier.width(56.dp)) { anchor = shiftRange(range, anchor, -1) }
                Text(b.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                GlassButton("›", modifier = Modifier.width(56.dp)) { anchor = shiftRange(range, anchor, 1) }
            }
        }

        // 工资明细
        GlassCard(Modifier.fillMaxWidth()) {
            Text("实发合计", fontSize = 13.sp, color = c.textSecondary)
            Text("${pay.netPay.toLong()} LAK", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.height(10.dp))
            PayRow("出勤天数", "${fmtNum(pay.attendDays)} 天", c)
            PayRow("基本工资", "${pay.basePay.toLong()} LAK", c)
            PayRow("加班费", "${pay.overtimePay.toLong()} LAK", c)
            PayRow("补贴", "${pay.allowance.toLong()} LAK", c)
            PayRow("奖金", "${pay.bonus.toLong()} LAK", c)
            PayRow("扣款", "-${pay.deduction.toLong()} LAK", c, negative = true)
        }

        // 图表
        GlassCard(Modifier.fillMaxWidth()) {
            Text(chartTitle(range), fontSize = 13.sp, color = c.textSecondary)
            Spacer(Modifier.height(10.dp))
            BarChart(buildChart(range, anchor, empRecords, emp, rule))
        }

        // 明细记录
        GlassCard(Modifier.fillMaxWidth()) {
            Text("明细记录", fontSize = 13.sp, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            if (inRange.isEmpty()) {
                Text("该范围内暂无记录", fontSize = 13.sp, color = c.textSecondary)
            } else {
                inRange.sortedByDescending { it.date }.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(r.date, fontSize = 13.sp, color = c.textPrimary)
                        Text(statusLabel(r.status), fontSize = 12.sp, color = c.textSecondary)
                        val d = daysOf(r.status)
                        Text(
                            "${fmtNum(d)}天 · ${(d * (emp?.dailyWage ?: 0.0)).toLong()} LAK",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent
                        )
                    }
                }
            }
        }

        GlassButton("导出本范围 CSV", modifier = Modifier.fillMaxWidth()) {
            exportRange(context, emp, inRange, pay)
        }
    }
}

@Composable
private fun PayRow(label: String, value: String, c: AppColors, negative: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = c.textSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (negative) Color(0xFFFF7A7A) else c.textPrimary)
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
    PayRange.DAY -> "本月每日实发"
    PayRange.MONTH -> "本年每月实发"
    PayRange.YEAR -> "近五年实发"
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

private fun buildChart(
    range: PayRange,
    anchor: Long,
    recs: List<AttendanceRecord>,
    emp: Employee?,
    rule: PayRule,
): List<BarEntry> {
    if (emp == null) return emptyList()
    val c = Calendar.getInstance(); c.timeInMillis = anchor
    val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
    fun net(pred: (AttendanceRecord) -> Boolean): Double =
        SalaryEngine.compute(
            SalaryEngine.aggregate(recs.filter(pred), emp.dailyWage, emp.monthlyBase, emp.bonus), rule
        ).netPay
    return when (range) {
        PayRange.DAY -> {
            c.set(Calendar.DAY_OF_MONTH, 1)
            val dim = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            (1..dim).map { day ->
                val ds = String.format(Locale.US, "%04d-%02d-%02d", y, m, day)
                BarEntry(if (day % 5 == 0 || day == dim) day.toString() else "", net { it.date == ds }, day == d)
            }
        }
        PayRange.MONTH -> (1..12).map { mo ->
            val prefix = String.format(Locale.US, "%04d-%02d", y, mo)
            BarEntry("$mo", net { it.date.startsWith(prefix) }, mo == m)
        }
        PayRange.YEAR -> ((y - 4)..y).map { yy ->
            BarEntry(yy.toString(), net { it.date.startsWith(yy.toString()) }, yy == y)
        }
    }
}

private fun exportRange(context: Context, emp: Employee?, recs: List<AttendanceRecord>, pay: PayBreakdown) {
    val sb = StringBuilder("date,status,days\n")
    recs.sortedBy { it.date }.forEach { r ->
        sb.append(r.date).append(',').append(r.status.name).append(',').append(daysOf(r.status)).append('\n')
    }
    sb.append("\n出勤天数,${pay.attendDays}\n基本工资,${pay.basePay.toLong()}\n加班费,${pay.overtimePay.toLong()}\n")
    sb.append("补贴,${pay.allowance.toLong()}\n奖金,${pay.bonus.toLong()}\n扣款,${pay.deduction.toLong()}\n实发,${pay.netPay.toLong()}\n")
    val name = "salary_${emp?.nameZh ?: "x"}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv"
    val dir = java.io.File(context.getExternalFilesDir(null), "exports")
    dir.mkdirs()
    val f = java.io.File(dir, name)
    f.writeText(sb.toString())
    Toast.makeText(context, "已导出 ${f.absolutePath}", Toast.LENGTH_LONG).show()
}
