package com.eta.attendance

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Serializable
sealed interface Route : NavKey {
    @Serializable data object CheckIn : Route
    @Serializable data object Stats : Route
    @Serializable data object Salary : Route
    @Serializable data object Settings : Route
}

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
    val backStack = rememberNavBackStack<Route>(Route.CheckIn)
    val navController = remember { NavController(backStack) }
    val c = LocalAppColors.current
    // 背景专用 backdrop：只录制 GlassBackground（不含面板），供卡片/底栏采样 → 打破自引用递归
    val backdropBg = rememberLayerBackdrop { drawRect(c.glassFill); drawContent() }
    // 主层 backdrop：录制 背景+内容，仅用于本层绘制，不被任何卡片采样
    val backdropMain = rememberLayerBackdrop { drawRect(c.glassFill); drawContent() }
    Column(Modifier.fillMaxSize()) {
        // 内容区：占满除底栏外的全部高度（weight 而非 Box.align，避免底栏浮到顶端）
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // 主层：背景+内容同处一个 layerBackdrop 盒内就地绘制
            Box(Modifier.fillMaxSize().layerBackdrop(backdropMain)) {
            // 仅把背景录进 backdropBg：卡片采样它、而它不含面板本身 → 不递归
            Box(Modifier.fillMaxSize().layerBackdrop(backdropBg)) {
                GlassBackground()
            }
            CompositionLocalProvider(LocalBackdrop provides backdropBg) {
                NavDisplay(navController = navController, modifier = Modifier.fillMaxSize()) {
                    entry<Route.CheckIn> { CheckInPanel(onOpenSettings = { navController.push(Route.Settings) }) }
                    entry<Route.Stats> { StatsPanel() }
                    entry<Route.Salary> { SalaryPanel2() }
                    entry<Route.Settings> { SettingsPanel() }
                }
            }
        }
        }
        // 底栏：作为 Column 第二项，固定高度、稳居底部
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).safeDrawingPadding()) {
            BottomNavBar(navController, backdropBg)
        }
    }
}


// ===================== 签到页 =====================

@Composable
private fun CheckInPanel(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val today = AttendanceStore.today()
    var selDate by remember { mutableStateOf(today) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isToday = selDate == today
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
    val lateNow = isToday && isLate(timeStr, Config.workStart(context))
    val employees = remember { Config.employees(context) }
    val picks = remember { mutableStateMapOf<Int, Status>() }
    LaunchedEffect(selDate) {
        picks.clear()
        AttendanceStore.forDate(context, selDate).forEach { picks[it.employeeId] = it.status }
    }
    val c = LocalAppColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 24.dp)
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
            if (isToday) {
                Text(timeStr, fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("$selDate · " + context.getString(if (lateNow) R.string.late else R.string.on_time), fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
            } else {
                Text(selDate, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("补录模式", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassButton("‹", modifier = Modifier.width(56.dp)) { selDate = shiftDate(selDate, -1) }
            Text(if (isToday) "今天" else selDate, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            GlassButton("›", modifier = Modifier.width(56.dp)) { if (selDate < today) selDate = shiftDate(selDate, 1) }
        }
        Spacer(Modifier.height(16.dp))
        Text(context.getString(R.string.tap_name_hint), fontSize = 14.sp, color = Color.White)
        Text("共 ${AttendanceStore.all(context).size} 条记录", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("全选全天", false) { employees.forEach { picks[it.id] = Status.FULL } }
            StatusChip("全选半天", false) { employees.forEach { picks[it.id] = Status.HALF } }
            StatusChip("全选缺勤", false) { employees.forEach { picks[it.id] = Status.ABSENT } }
            StatusChip("清空", false) { picks.clear() }
        }
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
            val hhmm = if (isToday) timeStr.substring(0, 5) else ""
            picks.forEach { (id, st) ->
                AttendanceStore.upsert(context, AttendanceRecord(id, selDate, st, hhmm, lateNow && st == Status.FULL))
            }
            Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun isLate(hhmmss: String, workStart: String): Boolean =
    runCatching { hhmmss.substring(0, 5) > workStart }.getOrDefault(false)

private fun shiftDate(date: String, delta: Int): String {
    val c = Calendar.getInstance()
    runCatching { c.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: c.time }
    c.add(Calendar.DAY_OF_MONTH, delta)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

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
            .padding(16.dp).padding(bottom = 24.dp)
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
        GlassCard(Modifier.fillMaxWidth()) {
            Text("月历总览 $ym", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            MonthGrid(employees, AttendanceStore.all(context), ym)
        }
        Spacer(Modifier.height(16.dp))
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
    var remOn by remember { mutableStateOf(Config.reminderEnabled(context)) }
    var remH by remember { mutableStateOf(Config.reminderHour(context).toString()) }
    var remM by remember { mutableStateOf(Config.reminderMinute(context).toString()) }
    var sub by remember { mutableStateOf(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 24.dp)
    ) {
        if (sub == 0) {
            Text(context.getString(R.string.tab_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(16.dp))
            SettingEntry("外观与语言", "主题、配色、界面语言") { sub = 1 }
            SettingEntry("考勤与提醒", "上下班时间、未打卡提醒") { sub = 2 }
            SettingEntry("工资与员工", "工资规则、员工薪资") { sub = 3 }
            SettingEntry("数据与同步", "本地备份、WebDAV、GitHub、Supabase") { sub = 4 }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton("‹", modifier = Modifier.width(56.dp)) { sub = 0 }
                Spacer(Modifier.width(12.dp))
                Text(when (sub) { 1 -> "外观与语言"; 2 -> "考勤与提醒"; 3 -> "工资与员工"; else -> "数据与同步" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            when (sub) {
                1 -> {

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

                }
                2 -> {

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

        // 未打卡提醒
        GlassCard(Modifier.fillMaxWidth()) {
            Text("未打卡提醒", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text("到点检查当天签到，未打卡发通知", fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            SwitchPreference(checked = remOn, onCheckedChange = { nv ->
                remOn = nv
                Config.saveReminder(context, nv, remH.toIntOrNull() ?: 9, remM.toIntOrNull() ?: 0)
                if (nv) { Reminder.ensureChannel(context); Reminder.schedule(context) } else Reminder.cancel(context)
                Toast.makeText(context, if (nv) "已开启提醒" else "已关闭提醒", Toast.LENGTH_SHORT).show()
            }, title = "每日提醒", summary = "到点检查当天签到，未打卡发通知")
            Spacer(Modifier.height(8.dp))
            NumField("时(0-23)", remH) { remH = it }
            Spacer(Modifier.height(6.dp))
            NumField("分(0-59)", remM) { remM = it }
            Spacer(Modifier.height(8.dp))
            GlassButton("保存提醒时间", modifier = Modifier.fillMaxWidth()) {
                Config.saveReminder(context, remOn, remH.toIntOrNull() ?: 9, remM.toIntOrNull() ?: 0)
                if (remOn) Reminder.schedule(context)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(12.dp))

                }
                3 -> {

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

                }
                4 -> {

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

        // 从 GitHub 导入
        GlassCard(Modifier.fillMaxWidth()) {
            Text("从 GitHub 导入考勤", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text("粘贴 attendance-tracker 备份 JSON 的 raw 链接，按姓名匹配员工并覆盖当月记录", fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            var gUrl by remember { mutableStateOf("https://raw.githubusercontent.com/guocheng1378/attendance-tracker/main/backup/attendance-2026-08-31_070529.json") }
            TextField(value = gUrl, onValueChange = { gUrl = it }, label = "数据 URL", useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassButton("导入", primary = true, modifier = Modifier.fillMaxWidth()) {
                scope.launch {
                    val n = AttendanceStore.importFromTracker(context, gUrl)
                    Toast.makeText(context, if (n >= 0) "已导入 $n 条" else "导入失败", Toast.LENGTH_SHORT).show()
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
        }
    }
}

@Composable
private fun SettingEntry(title: String, summary: String, onClick: () -> Unit) {
    ArrowPreference(title = title, summary = summary, onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
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
    var base by remember { mutableStateOf(Config.employees(context)) }
    val dwMap = remember { mutableStateMapOf<Int, String>() }
    val mbMap = remember { mutableStateMapOf<Int, String>() }
    val bnMap = remember { mutableStateMapOf<Int, String>() }
    val adMap = remember { mutableStateMapOf<Int, String>() }
    LaunchedEffect(base) {
        base.forEach { e ->
            if (!dwMap.containsKey(e.id)) dwMap[e.id] = e.dailyWage.toInt().toString()
            if (!mbMap.containsKey(e.id)) mbMap[e.id] = if (e.monthlyBase > 0) e.monthlyBase.toInt().toString() else ""
            if (!bnMap.containsKey(e.id)) bnMap[e.id] = if (e.bonus > 0) e.bonus.toInt().toString() else ""
            if (!adMap.containsKey(e.id)) adMap[e.id] = if (e.advance > 0) e.advance.toInt().toString() else ""
        }
    }
    base.forEach { e ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.nameZh, Modifier.width(56.dp), fontSize = 14.sp, color = c.textPrimary)
                GlassButton("删", modifier = Modifier.width(48.dp)) {
                    Config.removeEmployee(context, e.id)
                    dwMap.remove(e.id); mbMap.remove(e.id); bnMap.remove(e.id); adMap.remove(e.id)
                    base = Config.employees(context)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = dwMap[e.id] ?: "", onValueChange = { dwMap[e.id] = it }, label = "日薪", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = mbMap[e.id] ?: "", onValueChange = { mbMap[e.id] = it }, label = "月薪", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = bnMap[e.id] ?: "", onValueChange = { bnMap[e.id] = it }, label = "奖金", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = adMap[e.id] ?: "", onValueChange = { adMap[e.id] = it }, label = "预支", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    GlassButton("保存员工薪资", modifier = Modifier.fillMaxWidth()) {
        Config.saveEmployees(
            context, base.map { e ->
                e.copy(
                    dailyWage = dwMap[e.id]?.toDoubleOrNull() ?: e.dailyWage,
                    monthlyBase = mbMap[e.id]?.toDoubleOrNull() ?: 0.0,
                    bonus = bnMap[e.id]?.toDoubleOrNull() ?: 0.0,
                    advance = adMap[e.id]?.toDoubleOrNull() ?: 0.0
                )
            }
        )
        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
    }
    Spacer(Modifier.height(12.dp))
    var nl by remember { mutableStateOf("") }
    var nz by remember { mutableStateOf("") }
    var nd by remember { mutableStateOf("") }
    Text("添加员工", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = nl, onValueChange = { nl = it }, label = "老挝名", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        TextField(value = nz, onValueChange = { nz = it }, label = "中文名", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(value = nd, onValueChange = { nd = it }, label = "日薪", useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        GlassButton("添加", primary = true, modifier = Modifier.width(80.dp)) {
            if (nl.isNotBlank() || nz.isNotBlank()) {
                Config.addEmployee(context, nl, nz, nd.toDoubleOrNull() ?: 0.0)
                nl = ""; nz = ""; nd = ""; base = Config.employees(context)
            }
        }
    }
}

@Composable
private fun MonthGrid(employees: List<Employee>, recs: List<AttendanceRecord>, ym: String) {
    val c = LocalAppColors.current
    val grid = HashMap<Int, HashMap<Int, AttendanceRecord>>()
    recs.forEach { r ->
        if (r.date.startsWith(ym)) {
            val d = r.date.substring(8).toIntOrNull() ?: return@forEach
            grid.getOrPut(r.employeeId) { HashMap() }[d] = r
        }
    }
    val parts = ym.split("-")
    val cal = Calendar.getInstance()
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
    val nDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            Text("员工", Modifier.width(72.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            (1..nDays).forEach { Text("$it", Modifier.width(28.dp), fontSize = 11.sp, color = c.textSecondary) }
        }
        Spacer(Modifier.height(4.dp))
        employees.forEach { e ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(e.nameZh, Modifier.width(72.dp), fontSize = 12.sp, color = c.textPrimary)
                val byDay = grid[e.id]
                (1..nDays).forEach { d ->
                    val r = byDay?.get(d)
                    val sym: String
                    val col: Color
                    when (r?.status) {
                        Status.FULL -> { if (r.late) { sym = "迟"; col = Color(0xFFFFB74D) } else { sym = "√"; col = Color(0xFF4CAF50) } }
                        Status.HALF -> { sym = "◇"; col = Color(0xFF64B5F6) }
                        Status.ABSENT -> { sym = "×"; col = Color(0xFFEF5350) }
                        null -> { sym = "·"; col = c.textSecondary.copy(alpha = 0.4f) }
                    }
                    Text(sym, Modifier.width(28.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = col)
                }
            }
        }
    }
}
