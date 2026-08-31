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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val c = LocalAppColors.current
    // 每次进入组合时刷新数据
    var allRecords by remember { mutableStateOf(AttendanceStore.all(context)) }
    var employees by remember { mutableStateOf(Config.employees(context)) }
    var rule by remember { mutableStateOf(Config.payRule(context)) }
    var empId by remember { mutableStateOf(employees.firstOrNull()?.id ?: -1) }
    var range by remember { mutableStateOf(PayRange.MONTH) }
    var anchor by remember { mutableStateOf(System.currentTimeMillis()) }
    // 加班/请假临时输入
    var otWeekday by remember { mutableStateOf("0") }
    var otWeekend by remember { mutableStateOf("0") }
    var otHoliday by remember { mutableStateOf("0") }
    var leaveSick by remember { mutableStateOf("0") }
    var leavePersonal by remember { mutableStateOf("0") }
    var leaveAnnual by remember { mutableStateOf("0") }

    // 切换员工时重置加班/请假输入
    LaunchedEffect(empId) {
        otWeekday = "0"; otWeekend = "0"; otHoliday = "0"
        leaveSick = "0"; leavePersonal = "0"; leaveAnnual = "0"
    }

    val emp = employees.firstOrNull { it.id == empId }
    val empRecords = allRecords.filter { it.employeeId == empId }
    val b = rangeBounds(range, anchor)
    val inRange = empRecords.filter { it.date >= b.start && it.date < b.end }

    val input = SalaryEngine.aggregate(
        inRange, emp?.dailyWage ?: 0.0, emp?.monthlyBase ?: 0.0,
        emp?.bonus ?: 0.0, emp?.advance ?: 0.0
    ).copy(
        otHoursWeekday = otWeekday.toDoubleOrNull() ?: 0.0,
        otHoursWeekend = otWeekend.toDoubleOrNull() ?: 0.0,
        otHoursHoliday = otHoliday.toDoubleOrNull() ?: 0.0,
        leaveSick = leaveSick.toDoubleOrNull() ?: 0.0,
        leavePersonal = leavePersonal.toDoubleOrNull() ?: 0.0,
        leaveAnnual = leaveAnnual.toDoubleOrNull() ?: 0.0,
    )
    val pay = SalaryEngine.compute(input, rule)
    val accent = c.palette.accent

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(context.getString(R.string.salary_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

        if (employees.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.employee_label), fontSize = 13.sp, color = c.textSecondary)
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
                PayRange.entries.forEach { r ->
                    StatusChip(payRangeLabel(r, context), r == range) { range = r }
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

        // 加班 & 请假录入
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.ot_leave_title), fontSize = 13.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = otWeekday, onValueChange = { otWeekday = it }, label = context.getString(R.string.ot_weekday_hint), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = otWeekend, onValueChange = { otWeekend = it }, label = context.getString(R.string.ot_weekend_hint), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = otHoliday, onValueChange = { otHoliday = it }, label = context.getString(R.string.ot_holiday_hint), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = leaveSick, onValueChange = { leaveSick = it }, label = context.getString(R.string.leave_sick_hint), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = leavePersonal, onValueChange = { leavePersonal = it }, label = context.getString(R.string.leave_personal_hint), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = leaveAnnual, onValueChange = { leaveAnnual = it }, label = context.getString(R.string.leave_annual_hint), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
            }
        }

        // 工资明细
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.net_pay_label), fontSize = 13.sp, color = c.textSecondary)
            Text("${pay.netPay.toLong()} LAK", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.height(10.dp))
            PayRow(context.getString(R.string.pay_attend_days), "${fmtNum(pay.attendDays)} 天", c)
            PayRow(context.getString(R.string.pay_base), "${pay.basePay.toLong()} LAK", c)
            PayRow(context.getString(R.string.pay_overtime), "${pay.overtimePay.toLong()} LAK", c)
            PayRow(context.getString(R.string.pay_allowance), "${pay.allowance.toLong()} LAK", c)
            PayRow(context.getString(R.string.pay_bonus), "${pay.bonus.toLong()} LAK", c)
            PayRow(context.getString(R.string.pay_advance), "-${pay.advance.toLong()} LAK", c, negative = true)
            PayRow(context.getString(R.string.pay_deduction), "-${pay.deduction.toLong()} LAK", c, negative = true)
        }

        // 图表
        GlassCard(Modifier.fillMaxWidth()) {
            Text(chartTitle(range, context), fontSize = 13.sp, color = c.textSecondary)
            Spacer(Modifier.height(10.dp))
            BarChart(buildChart(range, anchor, empRecords, emp, rule))
        }

        // 明细记录
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.detail_records), fontSize = 13.sp, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            if (inRange.isEmpty()) {
                Text(context.getString(R.string.no_records_in_range), fontSize = 13.sp, color = c.textSecondary)
            } else {
                inRange.sortedByDescending { it.date }.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(r.date, fontSize = 13.sp, color = c.textPrimary)
                        Text(statusLabel(r.status, context), fontSize = 12.sp, color = c.textSecondary)
                        val d = daysOf(r.status)
                        Text(
                            "${fmtNum(d)}天 · ${(d * (emp?.dailyWage ?: 0.0)).toLong()} LAK",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent
                        )
                    }
                }
            }
        }

        GlassButton(context.getString(R.string.export_range_csv), modifier = Modifier.fillMaxWidth()) {
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

private fun payRangeLabel(r: PayRange, ctx: Context): String = when (r) {
    PayRange.DAY -> ctx.getString(R.string.range_day)
    PayRange.MONTH -> ctx.getString(R.string.range_month)
    PayRange.YEAR -> ctx.getString(R.string.range_year)
}

private fun statusLabel(s: Status, ctx: Context): String = when (s) {
    Status.FULL -> ctx.getString(R.string.status_full)
    Status.HALF -> ctx.getString(R.string.status_half)
    Status.ABSENT -> ctx.getString(R.string.status_absent)
}

private fun chartTitle(r: PayRange, ctx: Context): String = when (r) {
    PayRange.DAY -> ctx.getString(R.string.chart_day)
    PayRange.MONTH -> ctx.getString(R.string.chart_month)
    PayRange.YEAR -> ctx.getString(R.string.chart_year)
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
            SalaryEngine.aggregate(recs.filter(pred), emp.dailyWage, emp.monthlyBase, emp.bonus, emp.advance), rule
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
    sb.append("补贴,${pay.allowance.toLong()}\n奖金,${pay.bonus.toLong()}\n预支,${pay.advance.toLong()}\n扣款,${pay.deduction.toLong()}\n实发,${pay.netPay.toLong()}\n")
    val name = "salary_${emp?.nameZh ?: "x"}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.csv"
    val dir = java.io.File(context.getExternalFilesDir(null), "exports")
    dir.mkdirs()
    val f = java.io.File(dir, name)
    f.writeText(sb.toString())
    Toast.makeText(context, "已导出 ${f.absolutePath}", Toast.LENGTH_LONG).show()
}
