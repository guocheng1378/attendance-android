package com.eta.attendance

import android.app.Activity
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
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
    val backdropBg = rememberLayerBackdrop { drawRect(c.glassFill); drawContent() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdropBg)) {
            GlassBackground()
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CompositionLocalProvider(LocalBackdrop provides backdropBg) {
                    NavDisplay(navController = navController, modifier = Modifier.fillMaxSize()) {
                        entry<Route.CheckIn> { CheckInPanel(onOpenSettings = { navController.push(Route.Settings) }) }
                        entry<Route.Stats> { StatsPanel() }
                        entry<Route.Salary> { SalaryPanel2() }
                        entry<Route.Settings> { SettingsPanel() }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .safeDrawingPadding()
            ) {
                BottomNavBar(navController, backdropBg)
            }
        }
    }
}


// ===================== 通用组件 =====================

/** Miuix WindowDialog 风格确认对话框 */
@Composable
private fun GlassConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WindowDialog(
        title = title,
        summary = message,
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("取消", modifier = Modifier.weight(1f)) { onDismiss() }
            GlassButton("确定", primary = true, modifier = Modifier.weight(1f)) { onConfirm() }
        }
    }
}

/** 月度日期选择对话框 (WindowDialog) */
@Composable
private fun MonthDatePicker(
    currentMonth: String,  // yyyy-MM
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    val cal = Calendar.getInstance()
    val parts = currentMonth.split("-")
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
    val nDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
    val today = AttendanceStore.today()

    WindowDialog(
        title = currentMonth,
        show = true,
        onDismissRequest = onDismiss,
    ) {
        // 星期标题
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, Modifier.weight(1f), fontSize = 12.sp, color = c.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        // 日期网格
        val totalCells = firstDow + nDays
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val idx = row * 7 + col
                    val day = idx - firstDow + 1
                    if (day in 1..nDays) {
                        val dateStr = String.format(Locale.US, "%s-%02d", currentMonth, day)
                        val isSelected = dateStr == selectedDate
                        val isToday = dateStr == today
                        Box(
                            Modifier.weight(1f).padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isSelected -> MiuixTheme.colorScheme.primary
                                        isToday -> c.navSelected
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onDateSelected(dateStr) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$day", fontSize = 14.sp,
                                color = if (isSelected) Color.White else c.textPrimary,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 带 loading 状态的保存按钮 */
@Composable
private fun SavingButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current

    GlassButton(
        text = if (saving) "" else text,
        primary = true,
        modifier = modifier,
        onClick = {
            if (!saving) {
                saving = true
                scope.launch {
                    try {
                        onClick()
                        // 震动反馈
                        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        vib?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    } finally {
                        saving = false
                    }
                }
            }
        }
    ) {
        if (saving) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}


// ===================== 签到页 =====================

@Composable
private fun CheckInPanel(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val today = AttendanceStore.today()
    val todayCal = Calendar.getInstance()
    val currentYm = SimpleDateFormat("yyyy-MM", Locale.US).format(todayCal.time)
    var selDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
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

    // 日期选择对话框
    if (showDatePicker) {
        val pickerYm = selDate.substring(0, 7)
        MonthDatePicker(
            currentMonth = pickerYm,
            selectedDate = selDate,
            onDateSelected = { selDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

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
                Text(context.getString(R.string.backfill_mode), fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.height(16.dp))
        // 日期导航：‹ 日期(可点击) ›
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassButton("‹", modifier = Modifier.width(56.dp)) { selDate = shiftDate(selDate, -1) }
            Text(
                if (isToday) "今天" else selDate,
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clickable { showDatePicker = true }
            )
            GlassButton("›", modifier = Modifier.width(56.dp)) { if (selDate < today) selDate = shiftDate(selDate, 1) }
        }
        Spacer(Modifier.height(16.dp))
        Text(context.getString(R.string.tap_name_hint), fontSize = 14.sp, color = Color.White)
        Text(context.getString(R.string.record_count_fmt, AttendanceStore.all(context).size), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        // 全选按钮横向滚动
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(context.getString(R.string.select_all_full), false) { employees.forEach { picks[it.id] = Status.FULL } }
            StatusChip(context.getString(R.string.select_all_half), false) { employees.forEach { picks[it.id] = Status.HALF } }
            StatusChip(context.getString(R.string.select_all_absent), false) { employees.forEach { picks[it.id] = Status.ABSENT } }
            StatusChip(context.getString(R.string.clear_all), false) { picks.clear() }
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
        // 保存按钮（带 loading + 震动）
        SavingButton(context.getString(R.string.save), Modifier.fillMaxWidth()) {
            val hhmm = if (isToday) timeStr.substring(0, 5) else ""
            val records = picks.map { (id, st) ->
                val isLateForRec = if (isToday) lateNow else isLate(hhmm, Config.workStart(context))
                AttendanceRecord(id, selDate, st, hhmm, isLateForRec && st == Status.FULL)
            }
            AttendanceStore.upsertBatch(context, records)
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
    val c = LocalAppColors.current
    val employees = remember { Config.employees(context) }
    // 月份导航
    var anchorMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cal = remember(anchorMs) { Calendar.getInstance().apply { timeInMillis = anchorMs } }
    val ym = remember(cal) { String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1) }
    val summary = remember(ym) { AttendanceStore.monthSummary(context, ym) }
    val allRecords = remember(ym) { AttendanceStore.all(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 24.dp)
    ) {
        Text(context.getString(R.string.month_summary), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        // 月份导航
        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton("‹", modifier = Modifier.width(56.dp)) {
                    cal.add(Calendar.MONTH, -1); anchorMs = cal.timeInMillis
                }
                Text(ym, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                GlassButton("›", modifier = Modifier.width(56.dp)) {
                    val now = Calendar.getInstance()
                    if (cal.get(Calendar.YEAR) < now.get(Calendar.YEAR) ||
                        cal.get(Calendar.MONTH) < now.get(Calendar.MONTH)
                    ) {
                        cal.add(Calendar.MONTH, 1); anchorMs = cal.timeInMillis
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
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
            Text(context.getString(R.string.month_overview_fmt, ym), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            MonthGrid(employees, allRecords, ym)
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
    var remOn by remember { mutableStateOf(Config.reminderEnabled(context)) }
    var remH by remember { mutableStateOf(Config.reminderHour(context).toString()) }
    var remM by remember { mutableStateOf(Config.reminderMinute(context).toString()) }
    var sub by remember { mutableStateOf(0) }
    // 异步操作 loading 状态
    var syncLoading by remember { mutableStateOf(false) }
    var davUploadLoading by remember { mutableStateOf(false) }
    var davRestoreLoading by remember { mutableStateOf(false) }
    var ghImportLoading by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 24.dp)
    ) {
        if (sub == 0) {
            Text(context.getString(R.string.tab_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(16.dp))
            SettingEntry(context.getString(R.string.settings_appearance), context.getString(R.string.settings_appearance_desc)) { sub = 1 }
            SettingEntry(context.getString(R.string.settings_attendance), context.getString(R.string.settings_attendance_desc)) { sub = 2 }
            SettingEntry(context.getString(R.string.settings_salary), context.getString(R.string.settings_salary_desc)) { sub = 3 }
            SettingEntry(context.getString(R.string.settings_data), context.getString(R.string.settings_data_desc)) { sub = 4 }
        } else {
            // 改进的返回按钮：← 返回
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton("← 返回", modifier = Modifier.width(100.dp)) { sub = 0 }
                Spacer(Modifier.width(12.dp))
                Text(when (sub) { 1 -> context.getString(R.string.settings_appearance); 2 -> context.getString(R.string.settings_attendance); 3 -> context.getString(R.string.settings_salary); else -> context.getString(R.string.settings_data) }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            when (sub) {
                1 -> {

        // 外观：主题模式 + 配色
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.appearance), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.theme_mode), fontSize = 12.sp, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(context.getString(R.string.theme_follow), mode == ThemeMode.SYSTEM) { Config.saveThemeMode(context, ThemeMode.SYSTEM); (context as? Activity)?.recreate() }
                StatusChip(context.getString(R.string.theme_light), mode == ThemeMode.LIGHT) { Config.saveThemeMode(context, ThemeMode.LIGHT); (context as? Activity)?.recreate() }
                StatusChip(context.getString(R.string.theme_dark), mode == ThemeMode.DARK) { Config.saveThemeMode(context, ThemeMode.DARK); (context as? Activity)?.recreate() }
            }
            Spacer(Modifier.height(12.dp))
            Text(context.getString(R.string.palette_label), fontSize = 12.sp, color = c.textSecondary)
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
            Text(context.getString(R.string.attendance_rules), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.work_start), fontSize = 12.sp, color = c.textSecondary)
            TextField(value = start, onValueChange = { start = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.work_end), fontSize = 12.sp, color = c.textSecondary)
            TextField(value = end, onValueChange = { end = it }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassButton(context.getString(R.string.save), modifier = Modifier.fillMaxWidth()) {
                if (!Config.isValidTime(start) || !Config.isValidTime(end)) {
                    Toast.makeText(context, context.getString(R.string.invalid_time_format), Toast.LENGTH_SHORT).show()
                } else {
                    Config.saveWorkTime(context, start, end)
                    Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 未打卡提醒
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.reminder_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text(context.getString(R.string.reminder_desc), fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            SwitchPreference(checked = remOn, onCheckedChange = { nv ->
                remOn = nv
                Config.saveReminder(context, nv, remH.toIntOrNull() ?: 9, remM.toIntOrNull() ?: 0)
                if (nv) { Reminder.ensureChannel(context); Reminder.schedule(context) } else Reminder.cancel(context)
                Toast.makeText(context, if (nv) context.getString(R.string.reminder_on) else context.getString(R.string.reminder_off), Toast.LENGTH_SHORT).show()
            }, title = context.getString(R.string.reminder_daily), summary = context.getString(R.string.reminder_desc))
            Spacer(Modifier.height(8.dp))
            NumField(context.getString(R.string.reminder_hour_label), remH) { remH = it }
            Spacer(Modifier.height(6.dp))
            NumField(context.getString(R.string.reminder_min_label), remM) { remM = it }
            Spacer(Modifier.height(8.dp))
            GlassButton(context.getString(R.string.reminder_save), modifier = Modifier.fillMaxWidth()) {
                val h = remH.toIntOrNull()
                val m = remM.toIntOrNull()
                if (h == null || m == null || h !in 0..23 || m !in 0..59) {
                    Toast.makeText(context, context.getString(R.string.invalid_time_range), Toast.LENGTH_SHORT).show()
                } else {
                    Config.saveReminder(context, remOn, h, m)
                    if (remOn) Reminder.schedule(context)
                    Toast.makeText(context, context.getString(R.string.reminder_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(12.dp))

                }
                3 -> {

        // 工资规则
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.pay_rules), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            NumField(context.getString(R.string.expected_days), expDays) { expDays = it }
            NumField(context.getString(R.string.ot_rate_weekday), otW) { otW = it }
            NumField(context.getString(R.string.ot_rate_weekend), otWe) { otWe = it }
            NumField(context.getString(R.string.ot_rate_holiday), otH) { otH = it }
            NumField(context.getString(R.string.late_deduction), lateDed) { lateDed = it }
            NumField(context.getString(R.string.meal_allowance), meal) { meal = it }
            NumField(context.getString(R.string.transport_allowance), transport) { transport = it }
            NumField(context.getString(R.string.housing_allowance), housing) { housing = it }
            GlassButton(context.getString(R.string.save_pay_rules), modifier = Modifier.fillMaxWidth()) {
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
                Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(12.dp))

        // 员工薪资
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.employee_salary), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text(context.getString(R.string.employee_salary_desc), fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            EmployeeEditor(context)
        }
        Spacer(Modifier.height(12.dp))

                }
                4 -> {

        // 数据备份
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.data_backup), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(context.getString(R.string.export_backup), modifier = Modifier.weight(1f)) {
                    val txt = AttendanceStore.exportBackup(context)
                    File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                        "attendance_backup.json"
                    ).writeText(txt)
                    Toast.makeText(context, context.getString(R.string.exported_to_download), Toast.LENGTH_SHORT).show()
                }
                GlassButton(context.getString(R.string.import_backup), primary = true, modifier = Modifier.weight(1f)) {
                    val f = File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                        "attendance_backup.json"
                    )
                    if (f.exists()) {
                        val n = AttendanceStore.importBackup(context, f.readText())
                        Toast.makeText(context, context.getString(R.string.imported_fmt, n), Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, context.getString(R.string.backup_not_found), Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // WebDAV / 坚果云
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.webdav_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            TextField(value = dUrl, onValueChange = { dUrl = it }, label = context.getString(R.string.webdav_url), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = dUser, onValueChange = { dUser = it }, label = context.getString(R.string.webdav_user), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = dPass, onValueChange = { dPass = it }, label = context.getString(R.string.webdav_pass), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = dPath, onValueChange = { dPath = it }, label = context.getString(R.string.webdav_path), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassButton(context.getString(R.string.save_webdav), modifier = Modifier.fillMaxWidth()) {
                Config.saveDav(context, dUrl, dUser, dPass, dPath)
                Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 上传（带 loading）
                Box(modifier = Modifier.weight(1f)) {
                    GlassButton(
                        text = if (davUploadLoading) "" else context.getString(R.string.upload_backup),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!davUploadLoading) {
                            davUploadLoading = true
                            scope.launch {
                                try {
                                    val ok = AttendanceStore.pushToDav(context)
                                    Toast.makeText(context, if (ok) context.getString(R.string.uploaded) else context.getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
                                } finally { davUploadLoading = false }
                            }
                        }
                    }
                    if (davUploadLoading) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = c.textPrimary, strokeWidth = 2.dp)
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassButton(
                        text = if (davRestoreLoading) "" else context.getString(R.string.restore_from_cloud),
                        primary = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!davRestoreLoading) {
                            davRestoreLoading = true
                            scope.launch {
                                try {
                                    val n = AttendanceStore.pullFromDav(context)
                                    Toast.makeText(context, if (n >= 0) context.getString(R.string.restored_fmt, n) else context.getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                                } finally { davRestoreLoading = false }
                            }
                        }
                    }
                    if (davRestoreLoading) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            var autoBackupOn by remember { mutableStateOf(Config.autoBackupEnabled(context)) }
            SwitchPreference(checked = autoBackupOn, onCheckedChange = { nv ->
                autoBackupOn = nv
                Config.saveAutoBackup(context, nv)
                if (nv) AutoBackup.schedule(context) else AutoBackup.cancel(context)
                Toast.makeText(context, if (nv) context.getString(R.string.auto_backup_on) else context.getString(R.string.auto_backup_off), Toast.LENGTH_SHORT).show()
            }, title = context.getString(R.string.auto_backup), summary = context.getString(R.string.auto_backup_desc))
        }
        Spacer(Modifier.height(12.dp))

        // 从 GitHub 导入
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.import_from_github), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Text(context.getString(R.string.import_from_github_desc), fontSize = 11.sp, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            var gUrl by remember { mutableStateOf("https://raw.githubusercontent.com/guocheng1378/attendance-tracker/main/backup/attendance-2026-08-31_070529.json") }
            TextField(value = gUrl, onValueChange = { gUrl = it }, label = context.getString(R.string.data_url), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Box {
                GlassButton(
                    text = if (ghImportLoading) "" else context.getString(R.string.import_btn),
                    primary = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!ghImportLoading) {
                        ghImportLoading = true
                        scope.launch {
                            try {
                                val n = AttendanceStore.importFromTracker(context, gUrl)
                                Toast.makeText(context, if (n >= 0) context.getString(R.string.imported_fmt, n) else context.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                            } finally { ghImportLoading = false }
                        }
                    }
                }
                if (ghImportLoading) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    }
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
                Box(modifier = Modifier.weight(1f)) {
                    GlassButton(
                        text = if (syncLoading) "" else context.getString(R.string.sync_now),
                        primary = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!syncLoading) {
                            syncLoading = true
                            scope.launch {
                                try {
                                    val ok = AttendanceStore.pushToSupabase(context)
                                    Toast.makeText(context, context.getString(if (ok) R.string.synced else R.string.sync_off), Toast.LENGTH_SHORT).show()
                                } finally { syncLoading = false }
                            }
                        }
                    }
                    if (syncLoading) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        }
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
    // 删除确认对话框
    var deleteTarget by remember { mutableStateOf<Employee?>(null) }
    if (deleteTarget != null) {
        GlassConfirmDialog(
            title = "删除员工",
            message = "确定要删除 ${deleteTarget!!.nameZh.ifBlank { deleteTarget!!.nameLo }} 吗？相关考勤记录不会被删除。",
            onConfirm = {
                Config.removeEmployee(context, deleteTarget!!.id)
                dwMap.remove(deleteTarget!!.id); mbMap.remove(deleteTarget!!.id)
                bnMap.remove(deleteTarget!!.id); adMap.remove(deleteTarget!!.id)
                base = Config.employees(context)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

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
                GlassButton(context.getString(R.string.delete), modifier = Modifier.width(48.dp)) {
                    deleteTarget = e
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = dwMap[e.id] ?: "", onValueChange = { dwMap[e.id] = it }, label = context.getString(R.string.field_daily_wage), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = mbMap[e.id] ?: "", onValueChange = { mbMap[e.id] = it }, label = context.getString(R.string.field_monthly_base), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = bnMap[e.id] ?: "", onValueChange = { bnMap[e.id] = it }, label = context.getString(R.string.field_bonus), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
                TextField(value = adMap[e.id] ?: "", onValueChange = { adMap[e.id] = it }, label = context.getString(R.string.field_advance), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    GlassButton(context.getString(R.string.save_employee_salary), modifier = Modifier.fillMaxWidth()) {
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
        Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }
    Spacer(Modifier.height(12.dp))
    var nl by remember { mutableStateOf("") }
    var nz by remember { mutableStateOf("") }
    var nd by remember { mutableStateOf("") }
    Text(context.getString(R.string.add_employee), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = nl, onValueChange = { nl = it }, label = context.getString(R.string.name_lo), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        TextField(value = nz, onValueChange = { nz = it }, label = context.getString(R.string.name_zh), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(value = nd, onValueChange = { nd = it }, label = context.getString(R.string.field_daily_wage), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        GlassButton(context.getString(R.string.add), primary = true, modifier = Modifier.width(80.dp)) {
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
