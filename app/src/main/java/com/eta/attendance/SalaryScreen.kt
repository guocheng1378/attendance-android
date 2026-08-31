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
    val pays = employees.map { e ->
        val arr = summary[e.id] ?: IntArray(3)
        SalaryEngine.compute(
            emp = e, ym = ym,
            full = arr[0], half = arr[1], absent = arr[2],
            advance = advMap[e.id]?.toDoubleOrNull() ?: Config.getAdvance(context, e.id, ym),
            remark = rmMap[e.id] ?: Config.getRemark(context, e.id, ym),
        )
    }
    val totalGross = pays.sumOf { it.gross }
    val totalAdv = pays.sumOf { it.advance }
    val totalNet = pays.sumOf { it.net }
    val accent = c.palette.accent

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("考勤工资核算", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // 月份切换
        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton("‹", modifier = Modifier.width(56.dp)) { ym = shiftYm(ym, -1) }
                Text(ymLabel(ym), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                GlassButton("›", modifier = Modifier.width(56.dp)) { ym = shiftYm(ym, 1) }
            }
        }

        // 全员总工资（先出总额）
        GlassCard(Modifier.fillMaxWidth()) {
            Text("本月全员实发合计", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            Text("${totalNet.toLong()} LAK", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.height(10.dp))
            SumRow("应发合计", "${totalGross.toLong()} LAK")
            SumRow("预支合计", "-${totalAdv.toLong()} LAK", neg = true)
            SumRow("人数", "${pays.size} 人")
        }

        if (employees.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("暂无员工，请到 设置 › 工资 › 员工薪资 添加。", fontSize = 13.sp, color = c.textSecondary)
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
                        Text(p.nameZh.ifBlank { p.nameLo }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "出勤 ${fmtNum(p.attend)} / 应出勤 ${p.expectedDays} 天（${p.fullDays}全 ${p.halfDays}半 ${p.absentDays}缺）",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${p.net.toLong()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
                        Text("实发 LAK", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                SumRow("月薪", "${p.monthly.toLong()} LAK")
                if (p.penaltyDays > 0) SumRow("扣减（${p.penaltyDays} 天）", "-${p.penalty.toLong()} LAK", neg = true)
                SumRow("应发", "${p.gross.toLong()} LAK")
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = advMap[p.employeeId] ?: (if (p.advance > 0) p.advance.toInt().toString() else ""),
                    onValueChange = {
                        advMap[p.employeeId] = it
                        Config.setAdvance(context, p.employeeId, ym, it.toDoubleOrNull() ?: 0.0)
                    },
                    label = "预支 (LAK)",
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
                    label = "备注",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        GlassButton("导出本月工资 CSV", modifier = Modifier.fillMaxWidth()) {
            exportMonth(context, ym, pays)
        }

        Text(
            "规则：应出勤=当月天数−${SalaryEngine.FREE_DAYS}；出勤折算=全天×1+半天×0.5；一天工资=月薪÷应出勤；出勤<应出勤扣1天、<应出勤÷2扣2天；实发=月薪−扣减−预支。",
            fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)
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
    val sb = StringBuilder("姓名,全天,半天,缺勤,出勤折算,应出勤,月薪,扣减天,扣减,应发,预支,实发,备注\n")
    pays.forEach { p ->
        sb.append(csvEsc(p.nameZh)).append(',')
            .append(p.fullDays).append(',').append(p.halfDays).append(',').append(p.absentDays).append(',')
            .append(fmtNum(p.attend)).append(',').append(p.expectedDays).append(',')
            .append(p.monthly.toLong()).append(',').append(p.penaltyDays).append(',').append(p.penalty.toLong()).append(',')
            .append(p.gross.toLong()).append(',').append(p.advance.toLong()).append(',').append(p.net.toLong()).append(',')
            .append(csvEsc(p.remark)).append('\n')
    }
    val dir = java.io.File(context.getExternalFilesDir(null), "exports")
    dir.mkdirs()
    val f = java.io.File(dir, "工资_$ym.csv")
    f.writeText(sb.toString())
    Toast.makeText(context, "已导出 ${f.absolutePath}", Toast.LENGTH_LONG).show()
}

private fun csvEsc(v: String): String =
    if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v
