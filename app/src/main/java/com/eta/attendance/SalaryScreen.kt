package com.eta.attendance

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SalaryPanel2() {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val employees = remember { Config.employees(context) }
    var ym by remember { mutableStateOf(SimpleDateFormat("yyyy-MM", Locale.US).format(Date())) }
    // 预支 / 备注编辑缓存（切换月份时重建）
    val advMap = remember(ym) { mutableStateMapOf<Int, String>() }
    val rmMap = remember(ym) { mutableStateMapOf<Int, String>() }

    // 考勤数据版本号：Store 写库 → SharedPreferences 变化 → 缓存失效（避免每次重组全量读盘）
    val dataVersion = rememberAttendanceVersion(context)
    val summary = remember(ym, dataVersion) { AttendanceStore.monthSummary(context, ym) }
    val closureDays = remember(ym, dataVersion) { AttendanceStore.companyAbsentDays(context, ym) }
    // 非法年月不按 30 天兜底：dim == null 时不计算，页面标红
    val dim = remember(ym) { SalaryEngine.daysInMonthOrNull(ym) }
    val pays: List<MonthPay> = if (dim == null) emptyList() else employees.map { e ->
        val arr = summary[e.id] ?: IntArray(3)
        SalaryEngine.compute(
            emp = e, ym = ym,
            full = arr[0], half = arr[1], absent = arr[2],
            advance = advMap[e.id]?.toDoubleOrNull() ?: Config.getAdvance(context, e.id, ym),
            remark = rmMap[e.id] ?: Config.getRemark(context, e.id, ym),
            closureDays = closureDays,
        )
    }
    val totalAdv = pays.sumOf { it.advance }
    val totalNet = pays.sumOf { it.net }
    val totalPayable = pays.sumOf { it.payable }
    val accent = if (c.palette.id == "mono") c.textPrimary else c.palette.accent
    val errColor = MiuixTheme.colorScheme.error

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.sp_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)

        // 月份切换
        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton("‹", modifier = Modifier.width(56.dp)) { ym = shiftYm(ym, -1) }
                val ymp = ym.split("-")
                // 年月非法时标红，不按 30 天口径出数
                Text(
                    if (ymp.size == 2) stringResource(R.string.sp_ym_fmt, ymp[0], ymp[1]) else ym,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (dim == null) errColor else c.textPrimary
                )
                GlassButton("›", modifier = Modifier.width(56.dp)) { ym = shiftYm(ym, 1) }
            }
        }

        // 全员总工资（先出总额）
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sp_total_net), fontSize = 13.sp, color = c.textPrimary.copy(alpha = 0.85f))
            Text("${k(totalNet)} LAK", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.height(10.dp))
            SumRow(stringResource(R.string.sp_total_payable), "${k(totalPayable)} LAK", error = totalPayable < 0)
            SumRow(stringResource(R.string.sp_total_advance), "-${totalAdv.toLong()} LAK", neg = true)
            SumRow(stringResource(R.string.sp_headcount), stringResource(R.string.sp_person_fmt, pays.size))
        }

        if (employees.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sp_no_emp), fontSize = 13.sp, color = c.textSecondary)
            }
        }

        // 每个员工
        pays.forEach { p ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(p.nameZh.ifBlank { p.nameLo }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text(p.nameLo, fontSize = 13.sp, color = c.textPrimary.copy(alpha = 0.8f))
                        Text(
                            stringResource(R.string.sp_attend_fmt, fmtNum(p.attend), p.expectedDays, p.fullDays, p.halfDays, p.absentDays),
                            fontSize = 12.sp, color = c.textPrimary.copy(alpha = 0.8f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${k(p.net)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
                        Text(stringResource(R.string.sp_net_lak), fontSize = 11.sp, color = c.textPrimary.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                SumRow(stringResource(R.string.sp_monthly), "${k(p.monthly)} LAK")
                // 日薪 =（月薪+奖金）÷ 当月天数，奖金摊在里面，不另计一笔相加
                SumRow(stringResource(R.string.sp_bonus), "${k(p.bonus)} LAK")
                SumRow(stringResource(R.string.sp_daily_rate), "${Math.round(p.dailyRate)} LAK")
                SumRow(stringResource(R.string.sp_gross), "${k(p.gross)} LAK")
                if (p.penaltyDays > 0) SumRow(stringResource(R.string.sp_penalty_fmt, p.penaltyDays), "-${k(p.penalty)} LAK", neg = true)
                // 应发为负 = 预支超出应发（欠款），用 error 色标出，不静默显示负数
                SumRow(stringResource(R.string.sp_payable), "${k(p.payable)} LAK", error = p.payable < 0)
                if (p.payable < 0) Text(
                    stringResource(R.string.advance_over_fmt, "${k(-p.payable)} LAK"),
                    fontSize = 11.sp, color = errColor
                )
                Spacer(Modifier.height(8.dp))
                // 预支文本本地持有：仅在能解析为非负数时落盘，否则保留旧值并标红
                val advText = advMap[p.employeeId] ?: (if (p.advance > 0) p.advance.toInt().toString() else "")
                val advNum = advText.toDoubleOrNull()
                val advErr = advText.isNotEmpty() && (advNum == null || advNum < 0.0)
                TextField(
                    value = advText,
                    onValueChange = {
                        advMap[p.employeeId] = it
                        val v = it.toDoubleOrNull()
                        if (v != null && v >= 0.0) Config.setAdvance(context, p.employeeId, ym, v)
                    },
                    label = stringResource(R.string.sp_advance_hint),
                    useLabelAsPlaceholder = true,
                    colors = TextFieldDefaults.textFieldColors(
                        borderColor = if (advErr) errColor else MiuixTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = rmMap[p.employeeId] ?: p.remark,
                    onValueChange = {
                        rmMap[p.employeeId] = it
                        Config.setRemark(context, p.employeeId, ym, it)
                    },
                    label = stringResource(R.string.sp_remark),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 年月非法时不出数，也不导出
        if (dim != null) {
            GlassButton(stringResource(R.string.sp_export_csv), modifier = Modifier.fillMaxWidth()) {
                exportMonth(context, ym, pays)
            }
        }

        Text(
            stringResource(R.string.sp_rule),
            fontSize = 11.sp, color = c.textPrimary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SumRow(label: String, value: String, neg: Boolean = false, error: Boolean = false) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = c.textSecondary)
        Text(
            value, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = if (error) MiuixTheme.colorScheme.error else if (neg) Color(0xFFFF7A7A) else c.textPrimary
        )
    }
}

private fun k(x: Double): Long = Math.round(x / 1000.0) * 1000L

private fun fmtNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

/**
 * 考勤数据版本号：Store 每次写库都会更新 attendance_data/records，
 * 监听该 prefs 变化并自增，供月份汇总的 remember 作为失效 key。
 */
@Composable
private fun rememberAttendanceVersion(context: Context): Int {
    var version by remember { mutableStateOf(0) }
    val sp = remember(context) { context.getSharedPreferences(AttendanceStore.PREFS_NAME, Context.MODE_PRIVATE) }
    val listener = remember { SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version += 1 } }
    DisposableEffect(sp) {
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return version
}

private fun shiftYm(ym: String, dir: Int): String {
    val p = ym.split("-")
    var y = p.getOrNull(0)?.toIntOrNull() ?: return ym
    var m = p.getOrNull(1)?.toIntOrNull() ?: return ym
    m += dir
    if (m < 1) { m = 12; y -= 1 }
    if (m > 12) { m = 1; y += 1 }
    return String.format(Locale.US, "%04d-%02d", y, m)
}

private fun exportMonth(context: Context, ym: String, pays: List<MonthPay>) {
    val sb = StringBuilder()
    // 表头取自资源（zh/lo 各一份），列序与下面的数据行严格一一对应
    context.getString(R.string.csv_header).split(',').joinTo(sb, separator = ",") { csvEsc(it.trim()) }
    sb.append('\n')
    pays.forEach { p ->
        sb.append(csvEsc(ym)).append(',')
            .append(csvEsc(p.nameZh.ifBlank { p.nameLo })).append(',')
            .append(p.expectedDays).append(',')
            .append(p.fullDays).append(',').append(p.halfDays).append(',').append(p.absentDays).append(',')
            .append(fmtNum(p.attend)).append(',').append(Math.round(p.dailyRate)).append(',')
            .append(p.monthly.toLong()).append(',').append(p.bonus.toLong()).append(',').append(p.penaltyDays).append(',').append(k(p.penalty)).append(',')
            .append(p.advance.toLong()).append(',').append(k(p.payable)).append(',').append(k(p.net)).append(',')
            .append(csvEsc(p.remark)).append('\n')
    }
    val dir = java.io.File(context.getExternalFilesDir(null), "exports")
    dir.mkdirs()
    val f = java.io.File(dir, context.getString(R.string.csv_filename_fmt, ym))
    f.writeText(sb.toString())
    Toast.makeText(context, context.getString(R.string.sp_exported_fmt, f.absolutePath), Toast.LENGTH_LONG).show()
}

private fun csvEsc(v: String): String =
    if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v
