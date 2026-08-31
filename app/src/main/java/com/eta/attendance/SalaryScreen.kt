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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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

    val summary = AttendanceStore.monthSummary(context, ym)
    val closureDays = AttendanceStore.companyAbsentDays(context, ym)
    val pays = employees.map { e ->
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
                Text(if (ymp.size == 2) stringResource(R.string.sp_ym_fmt, ymp[0], ymp[1]) else ym, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                GlassButton("›", modifier = Modifier.width(56.dp)) { ym = shiftYm(ym, 1) }
            }
        }

        // 全员总工资（先出总额）
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sp_total_net), fontSize = 13.sp, color = c.textPrimary.copy(alpha = 0.85f))
            Text("${k(totalNet)} LAK", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.height(10.dp))
            SumRow(stringResource(R.string.sp_total_payable), "${k(totalPayable)} LAK")
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
                SumRow(stringResource(R.string.sp_bonus), "${k(p.bonus)} LAK")
                if (p.penaltyDays > 0) SumRow(stringResource(R.string.sp_penalty_fmt, p.penaltyDays), "-${k(p.penalty)} LAK", neg = true)
                SumRow(stringResource(R.string.sp_payable), "${k(p.payable)} LAK")
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = advMap[p.employeeId] ?: (if (p.advance > 0) p.advance.toInt().toString() else ""),
                    onValueChange = {
                        advMap[p.employeeId] = it
                        Config.setAdvance(context, p.employeeId, ym, it.toDoubleOrNull() ?: 0.0)
                    },
                    label = stringResource(R.string.sp_advance_hint),
                    useLabelAsPlaceholder = true,
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

        GlassButton(stringResource(R.string.sp_export_csv), modifier = Modifier.fillMaxWidth()) {
            exportMonth(context, ym, pays)
        }

        Text(
            stringResource(R.string.sp_rule),
            fontSize = 11.sp, color = c.textPrimary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SumRow(label: String, value: String, neg: Boolean = false) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = c.textSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (neg) Color(0xFFFF7A7A) else c.textPrimary)
    }
}

private fun k(x: Double): Long = Math.round(x / 1000.0) * 1000L

private fun fmtNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

private fun ymLabel(ym: String): String {
    val p = ym.split("-")
    return if (p.size == 2) "${p[0]}年${p[1]}月" else ym
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
    val sb = StringBuilder("姓名,老挝文,全天,半天,缺勤,出勤折算,应出勤,月薪,奖金,扣减天,扣减,应发,预支,实发,备注\n")
    pays.forEach { p ->
        sb.append(csvEsc(p.nameZh)).append(',').append(csvEsc(p.nameLo)).append(',')
            .append(p.fullDays).append(',').append(p.halfDays).append(',').append(p.absentDays).append(',')
            .append(fmtNum(p.attend)).append(',').append(p.expectedDays).append(',')
            .append(p.monthly.toLong()).append(',').append(p.bonus.toLong()).append(',').append(p.penaltyDays).append(',').append(k(p.penalty)).append(',')
            .append(k(p.payable)).append(',').append(p.advance.toLong()).append(',').append(k(p.net)).append(',')
            .append(csvEsc(p.remark)).append('\n')
    }
    val dir = java.io.File(context.getExternalFilesDir(null), "exports")
    dir.mkdirs()
    val f = java.io.File(dir, "工资_$ym.csv")
    f.writeText(sb.toString())
    Toast.makeText(context, context.getString(R.string.sp_exported_fmt, f.absolutePath), Toast.LENGTH_LONG).show()
}

private fun csvEsc(v: String): String =
    if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v
