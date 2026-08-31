package com.eta.attendance

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceApp() {
    val context = LocalContext.current
    val mode = remember { Config.themeMode(context) }
    val palette = remember { Config.paletteId(context) }
    AppTheme(mode = mode, paletteId = palette) {
        AttendanceScreen()
    }
}

@Composable
private fun AttendanceScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        GlassBackground()
        when (selectedTab) {
            1 -> StatsPanel()
            2 -> SalaryPanel2()
            3 -> SettingsPanel()
            else -> CheckInPanel(onOpenSettings = { selectedTab = 3 })
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BottomNavBar(selectedTab) { selectedTab = it }
        }
    }
}

// ===================== 签到页 =====================

@Composable
private fun CheckInPanel(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
    val lateNow = isLate(timeStr, Config.workStart(context))
    val today = AttendanceStore.today()
    val employees = remember { Config.employees(context) }
    val picks = remember { mutableStateMapOf<Int, Status>() }
    LaunchedEffect(today) {
        AttendanceStore.forDate(context, today).forEach { picks[it.employeeId] = it.status }
    }
    val c = LocalAppColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 110.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(context.getString(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(context.getString(R.string.app_subtitle), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            }
            GlassIconButton(MiuixIcons.Settings, onOpenSettings)
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeStr, fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "$dateStr · " + context.getString(if (lateNow) R.string.late else R.string.on_time),
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(context.getString(R.string.tap_name_hint), fontSize = 14.sp, color = Color.White)
        Text("共 ${AttendanceStore.all(context).size} 条记录", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        employees.forEach { e ->
            val sel = picks[e.id]
            GlassCard(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(e.nameLo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                        Text(e.nameZh, fontSize = 13.sp, color = c.textSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(context.getString(R.string.status_full), sel == Status.FULL) { picks[e.id] = Status.FULL }
                        StatusChip(context.getString(R.string.status_half), sel == Status.HALF) { picks[e.id] = Status.HALF }
                        StatusChip(context.getString(R.string.status_absent), sel == Status.ABSENT) { picks[e.id] = Status.ABSENT }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassButton(context.getString(R.string.save), primary = true, modifier = Modifier.fillMaxWidth()) {
            val hhmm = timeStr.substring(0, 5)
            picks.forEach { (id, st) ->
                AttendanceStore.upsert(context, AttendanceRecord(id, today, st, hhmm, lateNow && st == Status.FULL))
            }
            Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun isLate(hhmmss: String, workStart: String): Boolean =
    runCatching { hhmmss.substring(0, 5) > workStart }.getOrDefault(false)

// ===================== 统计页 =====================

@Composable
private fun StatsPanel() {
    val context = LocalContext.current
    val ym = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val summary = AttendanceStore.monthSummary(context, ym)
    val employees = remember { Config.employees(context) }
    val c = LocalAppColors.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp)
    ) {
        Text(context.getString(R.string.month_summary), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.employees), Modifier.weight(2f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(context.getString(R.string.status_full), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(context.getString(R.string.status_half), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(context.getString(R.string.status_absent), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            }
            Spacer(Modifier.height(8.dp))
            employees.forEach { e ->
                val a = summary[e.id] ?: IntArray(3)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${e.nameLo} ${e.nameZh}", Modifier.weight(2f), fontSize = 14.sp, color = c.textPrimary)
                    Text("${a[0]}", Modifier.weight(1f), fontSize = 14.sp, color = c.textPrimary)
                    Text("${a[1]}", Modifier.weight(1f), fontSize = 14.sp, color = c.textPrimary)
                    Text("${a[2]}", Modifier.weight(1f), fontSize = 14.sp, color = c.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassButton(context.getString(R.string.export_csv), primary = true, modifier = Modifier.fillMaxWidth()) {
            val csv = AttendanceStore.toCsv(context)
            val f = File(context.getExternalFilesDir(null), "attendance.csv")
            runCatching { f.writeText(csv) }
            Toast.makeText(context, "CSV: ${f.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}

// ===================== 设置页 =====================

@Composable
private fun SettingsPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = LocalAppColors.current
    var start by remember { mutableStateOf(Config.workStart(context)) }
    var end by remember { mutableStateOf(Config.workEnd(context)) }
    var url by remember { mutableStateOf(Config.supabaseUrl(context)) }
    var key by remember { mutableStateOf(Config.supabaseKey(context)) }
    var dUrl by remember { mutableStateOf(Config.davUrl(context)) }
    var dUser by remember { mutableStateOf(Config.davUser(context)) }
    var dPass by remember { mutableStateOf(Config.davPass(context)) }
    var dPath by remember { mutableStateOf(Config.davPath(context)) }
    val lang = Config.locale(context)
    val mode = Config.themeMode(context)
    val palette = Config.paletteId(context)
    val rule = Config.payRule(context)
    var expDays by remember { mutableStateOf(rule.expectedDays.toString()) }
    var otW by remember { mutableStateOf(rule.otRateWeekday.toString()) }
    var otWe by remember { mutableStateOf(rule.otRateWeekend.toString()) }
    var otH by remember { mutableStateOf(rule.otRateHoliday.toString()) }
    var lateDed by remember { mutableStateOf(rule.lateDeduction.toString()) }
    var meal by remember { mutableStateOf(rule.mealAllowance.toString()) }
    var transport by remember { mutableStateOf(rule.transportAllowance.toString()) }
    var housing by remember { mutableStateOf(rule.housingAllowance.toString()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp)
    ) {
        Text(context.getString(R.string.tab_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        // 外观：主题模式 + 配色
        GlassCard(Modifier.fillMaxWidth()) {
            Text("外观", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text("主题模式", fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("跟随", mode == ThemeMode.SYSTEM) { Config.saveThemeMode(context, ThemeMode.SYSTEM); (context as? Activity)?.recreate() }
                StatusChip("浅色", mode == ThemeMode.LIGHT) { Config.saveThemeMode(context, ThemeMode.LIGHT); (context as? Activity)?.recreate() }
                StatusChip("深色", mode == ThemeMode.DARK) { Config.saveThemeMode(context, ThemeMode.DARK); (context as? Activity)?.recreate() }
            }
            Spacer(Modifier.height(12.dp))
            Text("配色", fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palettes.ALL.forEach { p ->
                    val sel = p.id == palette
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(p.bgTop, p.bgBottom)))
                            .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else c.glassBorder, RoundedCornerShape(12.dp))
                            .clickable { Config.savePalette(context, p.id); (context as? Activity)?.recreate() }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 语言
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.language), fontSize = 14.sp, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("中文", primary = lang == "zh", modifier = Modifier.weight(1f)) {
                    Config.saveLocale(context, "zh"); (context as? Activity)?.recreate()
                }
                GlassButton("ລາວ", primary = lang == "lo", modifier = Modifier.weight(1f)) {
                    Config.saveLocale(context, "lo"); (context as? Activity)?.recreate()
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 考勤规则
        GlassCard(Modifier.fillMaxWidth()) {
            Text("考勤规则", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.work_start), fontSize = 12.sp, color = c.textSecondary)
            TextField(value = start, onValueChange = { start = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.work_end), fontSize = 12.sp, color = c.textSecondary)
            TextField(value = end, onValueChange = { end = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassButton(context.getString(R.string.save), modifier = Modifier.fillMaxWidth()) {
                Config.saveWorkTime(context, start, end)
                Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(12.dp))

        // 工资规则
        GlassCard(Modifier.fillMaxWidth()) {
            Text("工资规则", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            NumField("应出勤天数", expDays) { expDays = it }
            NumField("平日加班倍率", otW) { otW = it }
            NumField("周末加班倍率", otWe) { otWe = it }
            NumField("节日加班倍率", otH) { otH = it }
            NumField("每次迟到扣款(LAK)", lateDed) { lateDed = it }
            NumField("餐补(LAK)", meal) { meal = it }
            NumField("交通补(LAK)", transport) { transport = it }
            NumField("住房补(LAK)", housing) { housing = it }
            GlassButton("保存工资规则", modifier = Modifier.fillMaxWidth()) {
                Config.savePayRule(
                    context, rule.copy(
                        expectedDays = expDays.toIntOrNull() ?: rule.expectedDays,
                        otRateWeekday = otW.toDoubleOrNull() ?: rule.otRateWeekday,
                        otRateWeekend = otWe.toDoubleOrNull() ?: rule.otRateWeekend,
                        otRateHoliday = otH.toDoubleOrNull() ?: rule.otRateHoliday,
                        lateDeduction = lateDed.toDoubleOrNull() ?: rule.lateDeduction,
                        mealAllowance = meal.toDoubleOrNull() ?: rule.mealAllowance,
                        transportAllowance = transport.toDoubleOrNull() ?: rule.transportAllowance,
                        housingAllowance = housing.toDoubleOrNull() ?: rule.housingAllowance,
                    )
                )
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(12.dp))

        // 员工薪资
        GlassCard(Modifier.fillMaxWidth()) {
            Text("员工薪资", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text("日薪/月薪（基普）；月薪>0 时优先按月薪折算", fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            EmployeeEditor(context)
        }
        Spacer(Modifier.height(12.dp))

        // 数据备份
        GlassCard(Modifier.fillMaxWidth()) {
            Text("数据备份", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("导出备份", modifier = Modifier.weight(1f)) {
                    val txt = AttendanceStore.exportBackup(context)
                    File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                        "attendance_backup.json"
                    ).writeText(txt)
                    Toast.makeText(context, "已导出到 Download", Toast.LENGTH_SHORT).show()
                }
                GlassButton("导入备份", primary = true, modifier = Modifier.weight(1f)) {
                    val f = File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                        "attendance_backup.json"
                    )
                    if (f.exists()) {
                        val n = AttendanceStore.importBackup(context, f.readText())
                        Toast.makeText(context, "已导入 $n 条", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "未找到备份文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // WebDAV / 坚果云
        GlassCard(Modifier.fillMaxWidth()) {
            Text("坚果云 / WebDAV", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            TextField(value = dUrl, onValueChange = { dUrl = it }, label = "WebDAV 地址", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = dUser, onValueChange = { dUser = it }, label = "账号(邮箱)", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = dPass, onValueChange = { dPass = it }, label = "应用密码", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = dPath, onValueChange = { dPath = it }, label = "远端路径", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassButton("保存 WebDAV", modifier = Modifier.fillMaxWidth()) {
                Config.saveDav(context, dUrl, dUser, dPass, dPath)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("上传备份", modifier = Modifier.weight(1f)) {
                    scope.launch { val ok = AttendanceStore.pushToDav(context); Toast.makeText(context, if (ok) "已上传" else "上传失败", Toast.LENGTH_SHORT).show() }
                }
                GlassButton("从云端恢复", primary = true, modifier = Modifier.weight(1f)) {
                    scope.launch { val n = AttendanceStore.pullFromDav(context); Toast.makeText(context, if (n >= 0) "恢复 $n 条" else "恢复失败", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Supabase
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.cloud), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            TextField(value = url, onValueChange = { url = it }, label = context.getString(R.string.supabase_url), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = key, onValueChange = { key = it }, label = context.getString(R.string.supabase_key), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(context.getString(R.string.save), modifier = Modifier.weight(1f)) {
                    Config.saveSupabase(context, url, key)
                    Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
                }
                GlassButton(context.getString(R.string.sync_now), primary = true, modifier = Modifier.weight(1f)) {
                    scope.launch {
                        val ok = AttendanceStore.pushToSupabase(context)
                        Toast.makeText(context, context.getString(if (ok) R.string.synced else R.string.sync_off), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalAppColors.current
    Text(label, fontSize = 12.sp, color = c.textSecondary)
    TextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EmployeeEditor(context: Context) {
    val c = LocalAppColors.current
    val base = remember { Config.employees(context) }
    val dwMap = remember { mutableStateMapOf<Int, String>().apply { base.forEach { put(it.id, it.dailyWage.toInt().toString()) } } }
    val mbMap = remember { mutableStateMapOf<Int, String>().apply { base.forEach { put(it.id, if (it.monthlyBase > 0) it.monthlyBase.toInt().toString() else "") } } }
    base.forEach { e ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(e.nameZh, Modifier.width(56.dp), fontSize = 14.sp, color = c.textPrimary)
            TextField(value = dwMap[e.id] ?: "", onValueChange = { dwMap[e.id] = it }, label = "日薪", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
            TextField(value = mbMap[e.id] ?: "", onValueChange = { mbMap[e.id] = it }, label = "月薪", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(8.dp))
    GlassButton("保存员工薪资", modifier = Modifier.fillMaxWidth()) {
        Config.saveEmployees(
            context, base.map { e ->
                e.copy(
                    dailyWage = dwMap[e.id]?.toDoubleOrNull() ?: e.dailyWage,
                    monthlyBase = mbMap[e.id]?.toDoubleOrNull() ?: 0.0
                )
            }
        )
        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
    }
}
