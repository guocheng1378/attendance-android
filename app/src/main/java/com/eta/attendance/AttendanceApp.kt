package com.eta.attendance

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
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
    // 底栏专用第二个背景层：必须与 backdropBg 是两个独立实例。
    // contentBg 只在下方的内容 Box 上注册一次，采样者是兄弟槽里的 BottomNavBar；
    // 同一实例若被祖先节点 layerBackdrop 注册、又被其后代 textureBlur 采样，
    // Android 上会形成 RenderNode 父子环，prepareTree 无限递归直接 native 崩溃。
    val contentBg = if (isRuntimeShaderSupported()) rememberLayerBackdrop() else null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdropBg)) {
            GlassBackground()
        }
        // 内容层用 fillMaxSize()：视口一直延伸到屏幕底，页面内容会从底栏背后穿过，
        // 底栏玻璃才采得到真实像素。contentBg 仍只注册在这一层 Box 上，采样者
        // BottomNavBar 是它的兄弟节点（理由见上面 contentBg 那段注释）。
        Box(
            Modifier.fillMaxSize()
                .then(contentBg?.let { Modifier.layerBackdrop(it) } ?: Modifier)
        ) {
            CompositionLocalProvider(LocalBackdrop provides backdropBg) {
                // 状态栏 inset 只能加在这里，不能加进各个页面内部：每个页面的根 Column 都是
                // Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)...)
                // （CheckInPanel / StatsPanel / SettingsPanel 与 SalaryScreen.kt 的 SalaryPanel2），那个 padding
                // 排在 verticalScroll 之后，页面内部再加的 inset 会跟着内容一起被滚走，所以状态栏
                // 这条 inset 必须加在滚动容器的外面。这里也只消费 TOP 一条边：底部 navigationBars
                // inset 已由导航条组件内部的 bottomPaddingValue 处理，整列再套 safeDrawingPadding()
                // 会把它算两遍。内容改为铺到屏幕底之后，各页面在滚动内容内部用
                // navBarBottomSpace() 补一段会跟着滚的底部留白，避免最后一屏永久压在玻璃底下。
                NavDisplay(
                    navController = navController,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()),
                ) {
                    entry<Route.CheckIn> {
                        // 只有栈顶（未被 Settings 等页面盖住）时才让签到页计时器继续刷新
                        val atTop = navController.backStack.lastOrNull() == Route.CheckIn
                        CheckInPanel(onOpenSettings = { navController.push(Route.Settings) }, foreground = atTop)
                    }
                    entry<Route.Stats> { StatsPanel() }
                    entry<Route.Salary> { SalaryPanel2() }
                    entry<Route.Settings> { SettingsPanel() }
                }
            }
        }
        // 底栏浮在内容之上：align(BottomCenter) 不占布局空间，胶囊背后就是内容层。
        // 左右 24dp 与底部安全区由 IosLiquidGlassNavigationBar 内部处理（组件自己读
        // navigationBars inset，见 LiquidGlassNavigationBar.kt 的 bottomPaddingValue：
        // 有 inset 时 8.dp + inset，inset 为 0 时组件兜底 36.dp）。这里的 16dp 是模板同款的悬浮
        // 间距，与组件内部那段是两笔相加的间距，各自独立生效，不存在把 inset 算两次的问题；
        // 组件内那段的具体数值由 navBarBottomSpace() 镜像同一套分支计算。
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            BottomNavBar(navController, contentBg, isBlurActive = contentBg != null)
        }
    }
}

/**
 * 底栏实际占用的底部高度：组件外 16dp 悬浮间距 + 组件内 64dp 胶囊 + 组件内底部安全区。
 *
 * 最后一段必须与 LiquidGlassNavigationBar.kt 的 bottomPaddingValue 逐分支一致：有系统
 * 导航栏 inset 时是 8.dp + inset，inset 为 0（三键导航、无手势条）时组件兜底用 36.dp。
 * 若这里固定按 8.dp + inset 估，inset 为 0 的设备会少留 28dp，最后一屏内容被胶囊压住。
 */
@Composable
internal fun navBarBottomSpace(): Dp {
    val inset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    return 16.dp + 64.dp + if (inset != 0.dp) 8.dp + inset else 36.dp
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
    GlassDialog(onDismiss) {
        val cc = LocalAppColors.current
        val ctx = LocalContext.current
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cc.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = cc.textSecondary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(ctx.getString(R.string.cancel), modifier = Modifier.weight(1f)) { onDismiss() }
            GlassButton(ctx.getString(R.string.confirm), primary = true, modifier = Modifier.weight(1f)) { onConfirm() }
        }
    }
}

/** 基于 androidx Dialog 的通用玻璃对话框（不依赖导航 dispatcher，避免 WindowDialog 崩溃） */
@Composable
private fun GlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { content() }
        }
    }
}

/** 月度日期选择对话框 */
@Composable
private fun MonthDatePicker(
    currentMonth: String,  // yyyy-MM
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val cal = Calendar.getInstance()
    val parts = currentMonth.split("-")
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
    val nDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
    val today = AttendanceStore.today()

    GlassDialog(onDismiss) {
        Text(currentMonth, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        // 星期标题
        Row(Modifier.fillMaxWidth()) {
            ctx.resources.getStringArray(R.array.weekdays_short).forEach {
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
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else c.textPrimary,
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

    Box(modifier = modifier) {
        GlassButton(
            text = if (saving) "  " else text,
            primary = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            onClick()
                            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            vib?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        )
        if (saving) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}


// ===================== 签到页 =====================

@Composable
private fun CheckInPanel(onOpenSettings: () -> Unit, foreground: Boolean = true) {
    val context = LocalContext.current
    val today = AttendanceStore.today()
    var selDate by remember { mutableStateOf(today) }
    var showDatePicker by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isToday = selDate == today
    // 被其它页面盖住时停掉计时器，避免每秒无意义的重组
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
    val lateNow = isToday && isLate(timeStr, Config.workStart(context))
    val employees = remember { Config.employees(context) }
    val picks = remember { mutableStateMapOf<Int, Status>() }
    // 本页内保存/清空后 +1，触发重新读取本地存储；foreground 变化同样触发（导入等在其它页写入的数据）
    var dataVersion by remember { mutableIntStateOf(0) }
    val dayRecords = remember(selDate, foreground, dataVersion) { AttendanceStore.forDate(context, selDate) }
    val totalCount = remember(foreground, dataVersion) { AttendanceStore.all(context).size }
    LaunchedEffect(dayRecords) {
        picks.clear()
        dayRecords.forEach { picks[it.employeeId] = it.status }
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
            .padding(bottom = 24.dp + navBarBottomSpace())
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(context.getString(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(context.getString(R.string.app_subtitle), fontSize = 13.sp, color = c.textPrimary.copy(alpha = 0.85f))
            }
            GlassIconButton(MiuixIcons.Settings, onOpenSettings)
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isToday) {
                Text(timeStr, fontSize = 46.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text("$selDate · " + context.getString(if (lateNow) R.string.late else R.string.on_time), fontSize = 14.sp, color = c.textPrimary.copy(alpha = 0.9f))
            } else {
                Text(selDate, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(context.getString(R.string.backfill_mode), fontSize = 14.sp, color = c.textPrimary.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.height(16.dp))
        // 日期导航：‹ 日期(可点击) ›
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassButton("‹", modifier = Modifier.width(56.dp)) { selDate = shiftDate(selDate, -1) }
            Text(
                if (isToday) context.getString(R.string.today) else selDate,
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.textPrimary,
                modifier = Modifier.clickable { showDatePicker = true }
            )
            GlassButton("›", modifier = Modifier.width(56.dp)) { if (selDate < today) selDate = shiftDate(selDate, 1) }
        }
        Spacer(Modifier.height(16.dp))
        Text(context.getString(R.string.tap_name_hint), fontSize = 14.sp, color = c.textPrimary)
        Text(context.getString(R.string.record_count_fmt, totalCount), fontSize = 12.sp, color = c.textPrimary.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        // 全选按钮横向滚动
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(context.getString(R.string.select_all_full), false) { employees.forEach { picks[it.id] = Status.FULL } }
            StatusChip(context.getString(R.string.select_all_half), false) { employees.forEach { picks[it.id] = Status.HALF } }
            StatusChip(context.getString(R.string.select_all_absent), false) { employees.forEach { picks[it.id] = Status.ABSENT } }
            StatusChip(context.getString(R.string.clear_all), false) {
                // 清空当天已保存的记录（不只是取消本页选中）
                val n = AttendanceStore.clearDay(context, selDate)
                picks.clear()
                dataVersion++
                Toast.makeText(context, context.getString(R.string.cleared_fmt, n), Toast.LENGTH_SHORT).show()
            }
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
            val prevById = dayRecords.associateBy { it.employeeId }
            // 只写入状态发生变化的员工，未变化者保留原签到时间与迟到标记
            val records = picks.mapNotNull { (id, st) ->
                val prev = prevById[id]
                if (prev != null && prev.status == st) return@mapNotNull null
                val isLateForRec = if (isToday) lateNow else isLate(hhmm, Config.workStart(context))
                AttendanceRecord(id, selDate, st, hhmm, isLateForRec && st == Status.FULL)
            }
            if (records.isNotEmpty()) AttendanceStore.upsertBatch(context, records)
            dataVersion++
            Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
    }
}

/** hhmm[:ss] 与上班时间（HH:mm）按字符串比较；长度不足视为不迟到 */
private fun isLate(hhmmss: String, workStart: String): Boolean =
    hhmmss.length >= 5 && hhmmss.substring(0, 5) > workStart

private fun shiftDate(date: String, delta: Int): String {
    val c = Calendar.getInstance()
    runCatching { c.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: c.time }
    c.add(Calendar.DAY_OF_MONTH, delta)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

// ===================== 统计页 =====================

private fun currentYm(): String {
    val cal = Calendar.getInstance()
    return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
}

private fun shiftYm(ym: String, delta: Int): String {
    val parts = ym.split("-")
    val cal = Calendar.getInstance()
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
    cal.add(Calendar.MONTH, delta)
    return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
}

/**
 * 考勤存储版本号：Store 每次落盘都会改写 attendance_data 里的记录，
 * 监听该 prefs 变化并自增，供统计页的 remember 作为失效 key（否则切回本页仍拿旧缓存）。
 */
@Composable
private fun rememberStoreVersion(context: Context): Int {
    var version by remember { mutableStateOf(0) }
    val sp = remember(context) { context.getSharedPreferences(AttendanceStore.PREFS_NAME, Context.MODE_PRIVATE) }
    val listener = remember { SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version += 1 } }
    DisposableEffect(sp) {
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return version
}

@Composable
private fun StatsPanel() {
    val context = LocalContext.current
    val c = LocalAppColors.current
    // 存储版本号：任何一次写库（签到/导入/恢复/同步）都会让 prefs 变化，据此让下面的读数失效重取
    val storeVersion = rememberStoreVersion(context)
    val employees = remember(storeVersion) { Config.employees(context) }
    // 月份导航
    var ym by remember { mutableStateOf(currentYm()) }
    val summary = remember(ym, storeVersion) { AttendanceStore.monthSummary(context, ym) }
    val allRecords = remember(ym, storeVersion) { AttendanceStore.all(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 24.dp + navBarBottomSpace())
    ) {
        Text(context.getString(R.string.month_summary), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
        Spacer(Modifier.height(16.dp))
        // 月份导航
        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton("‹", modifier = Modifier.width(56.dp)) {
                    ym = shiftYm(ym, -1)
                }
                Text(ym, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                GlassButton("›", modifier = Modifier.width(56.dp)) {
                    if (ym < currentYm()) ym = shiftYm(ym, 1)
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
    var remOn by remember { mutableStateOf(Config.reminderEnabled(context)) }
    var remH by remember { mutableStateOf(Config.reminderHour(context).toString()) }
    var remM by remember { mutableStateOf(Config.reminderMinute(context).toString()) }
    var sub by remember { mutableStateOf(0) }
    // 异步操作 loading 状态
    var syncLoading by remember { mutableStateOf(false) }
    var davUploadLoading by remember { mutableStateOf(false) }
    var davRestoreLoading by remember { mutableStateOf(false) }
    var ghImportLoading by remember { mutableStateOf(false) }
    // 「备份比本地旧」时 Store 只回一句冲突提示，这里存下原文与待导入内容，弹二次确认后带 force=true 重调
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var importConflictMsg by remember { mutableStateOf<String?>(null) }
    var restoreConflictMsg by remember { mutableStateOf<String?>(null) }

    /** 导入本地备份文件：force=false 时被冲突拦下则记下原文，交由确认对话框二次触发 */
    val doImport: (String, Boolean) -> Unit = { json, force ->
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { AttendanceStore.importBackupResult(context, json, force) }
                    .getOrElse { AttendanceStore.ImportResult(false, 0, context.getString(R.string.import_fail_fmt, it.message ?: it.toString())) }
            }
            if (!res.ok && res.conflict) {
                pendingImportJson = json
                importConflictMsg = res.message
            } else {
                val msg = if (res.ok) withSkippedNote(context, res.message) else res.message
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    /** 从 WebDAV 恢复：同样先按 force=false 请求，冲突时弹确认，不静默覆盖本地 */
    val doDavRestore: (Boolean) -> Unit = { force ->
        davRestoreLoading = true
        scope.launch {
            try {
                val res = AttendanceStore.pullFromDavResult(context, force)
                if (!res.ok && res.conflict) {
                    restoreConflictMsg = res.message
                } else {
                    val msg = if (res.ok) withSkippedNote(context, res.message) else res.message
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } finally { davRestoreLoading = false }
        }
    }

    if (importConflictMsg != null) {
        GlassConfirmDialog(
            title = context.getString(R.string.import_backup),
            message = importConflictMsg!!,
            onConfirm = {
                val json = pendingImportJson
                importConflictMsg = null
                pendingImportJson = null
                if (json != null) doImport(json, true)
            },
            onDismiss = {
                importConflictMsg = null
                pendingImportJson = null
            },
        )
    }
    if (restoreConflictMsg != null) {
        GlassConfirmDialog(
            title = context.getString(R.string.restore_from_cloud),
            message = restoreConflictMsg!!,
            onConfirm = {
                restoreConflictMsg = null
                doDavRestore(true)
            },
            onDismiss = { restoreConflictMsg = null },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 24.dp + navBarBottomSpace())
    ) {
        if (sub == 0) {
            Text(context.getString(R.string.tab_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(16.dp))
            SettingEntry(context.getString(R.string.settings_appearance), context.getString(R.string.settings_appearance_desc)) { sub = 1 }
            SettingEntry(context.getString(R.string.settings_attendance), context.getString(R.string.settings_attendance_desc)) { sub = 2 }
            SettingEntry(context.getString(R.string.settings_salary), context.getString(R.string.settings_salary_desc)) { sub = 3 }
            SettingEntry(context.getString(R.string.settings_data), context.getString(R.string.settings_data_desc)) { sub = 4 }
        } else {
            // 返回设置主菜单
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(context.getString(R.string.back), modifier = Modifier.width(100.dp)) { sub = 0 }
                Spacer(Modifier.width(12.dp))
                Text(when (sub) { 1 -> context.getString(R.string.settings_appearance); 2 -> context.getString(R.string.settings_attendance); 3 -> context.getString(R.string.settings_salary); else -> context.getString(R.string.settings_data) }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
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
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Palettes.ALL.forEach { p ->
                    val sel = p.id == palette
                    Column(
                        Modifier.width(56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(p.bgTop, p.bgBottom)))
                                .border(if (sel) 3.dp else 1.dp, if (sel) c.textPrimary else c.glassBorder, RoundedCornerShape(12.dp))
                                .clickable { Config.savePalette(context, p.id); (context as? Activity)?.recreate() }
                        )
                        Spacer(Modifier.height(4.dp))
                        // 色块下方标出配色名，横向可滚避免五个色块加文字撑破行宽
                        Text(p.label(lang), fontSize = 10.sp, color = c.textSecondary, textAlign = TextAlign.Center)
                    }
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
                // 开了提醒但系统没给通知权限时不能只报「已开启」，否则用户以为一切正常
                val msg = when {
                    !nv -> context.getString(R.string.reminder_off)
                    Reminder.notificationPermissionGranted(context) -> context.getString(R.string.reminder_on)
                    else -> context.getString(R.string.notif_perm_missing)
                }
                Toast.makeText(context, msg, if (nv) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }, title = context.getString(R.string.reminder_daily), summary = context.getString(R.string.reminder_desc))
            // 开关开着但权限缺失：常驻提示，避免只在 Toast 里一闪而过
            if (remOn && !Reminder.notificationPermissionGranted(context)) {
                Spacer(Modifier.height(6.dp))
                Text(context.getString(R.string.notif_perm_missing), fontSize = 11.sp, color = MiuixTheme.colorScheme.error)
            }
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

        // 工资规则（说明）
        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.pay_rule_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.prl_base), fontSize = 13.sp, color = c.textSecondary)
            Text(context.getString(R.string.prl_expect), fontSize = 13.sp, color = c.textSecondary)
            Text(context.getString(R.string.prl_attend), fontSize = 13.sp, color = c.textSecondary)
            Text(context.getString(R.string.prl_pen), fontSize = 13.sp, color = c.textSecondary)
            Text(context.getString(R.string.prl_rate), fontSize = 13.sp, color = c.textSecondary)
            Text(context.getString(R.string.prl_net), fontSize = 13.sp, color = c.textSecondary)
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
                    // 写到应用专属外部目录，无需存储权限（Download 在 Android 10+ 已受分区存储限制）
                    val txt = AttendanceStore.exportBackup(context)
                    val f = backupFile(context)
                    val err = runCatching { f.writeText(txt) }.exceptionOrNull()
                    if (err == null)
                        Toast.makeText(context, context.getString(R.string.sp_exported_fmt, f.absolutePath), Toast.LENGTH_LONG).show()
                    else
                        Toast.makeText(context, context.getString(R.string.export_dir_fail, err.message ?: err.toString()), Toast.LENGTH_LONG).show()
                }
                GlassButton(context.getString(R.string.import_backup), primary = true, modifier = Modifier.weight(1f)) {
                    scope.launch {
                        // 文件原文整份交给 Store：它自己统计坏条并拼进 message，
                        // 这里若先过滤重打包会丢掉 exportedAt，冲突检查就永远不生效
                        val json = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = backupFile(context)
                                if (f.exists()) f.readText() else null
                            }.getOrNull()
                        }
                        if (json.isNullOrBlank())
                            Toast.makeText(context, context.getString(R.string.backup_not_found), Toast.LENGTH_LONG).show()
                        else doImport(json, false)
                    }
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
                // 返回 false 表示系统加密区不可用，WebDAV 密码根本没写进去，不能只报「已保存」
                val ok = Config.saveDav(context, dUrl, dUser, dPass, dPath)
                Toast.makeText(context, context.getString(if (ok) R.string.saved else R.string.secure_store_fail), Toast.LENGTH_LONG).show()
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
                                    val res = AttendanceStore.pushToDav(context)
                                    // 成功/失败都直接展示 Store 给的文案（失败含 HTTP 码或 401/403 等具体原因）
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                } finally { davUploadLoading = false }
                            }
                        }
                    }
                    if (davUploadLoading) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassButton(
                        text = if (davRestoreLoading) "" else context.getString(R.string.restore_from_cloud),
                        primary = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!davRestoreLoading) doDavRestore(false)
                    }
                    if (davRestoreLoading) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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
                                val res = AttendanceStore.importFromTrackerResult(context, gUrl)
                                if (!res.ok) {
                                    Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_LONG).show()
                                } else {
                                    var msg = context.getString(R.string.imported_fmt, res.count)
                                    // 没匹配上的人不静默丢弃：列出来让用户先去设置里加人，否则数据悄悄少一截
                                    if (res.unmatched.isNotEmpty())
                                        msg += " · " + context.getString(R.string.unmatched_fmt, res.unmatched.joinToString(", "))
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            } finally { ghImportLoading = false }
                        }
                    }
                }
                if (ghImportLoading) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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
                    // 返回 false 表示加密区不可用，Key 没写进去，后续同步必然失败
                    val ok = Config.saveSupabase(context, url, key)
                    Toast.makeText(context, context.getString(if (ok) R.string.saved else R.string.secure_store_fail), Toast.LENGTH_LONG).show()
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
                                    val res = AttendanceStore.pushToSupabaseResult(context)
                                    // message 已区分「未配置」「HTTP xxx」「网络异常」，不再一律报未同步
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                } finally { syncLoading = false }
                            }
                        }
                    }
                    if (syncLoading) {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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

/** 备份文件位置：应用专属外部目录，与统计页导出的 CSV 同级 */
private fun backupFile(context: Context): File =
    File(context.getExternalFilesDir(null), "attendance_backup.json")

/** 导入/恢复成功后，若本地存量数据里有解析不了的坏条，追加一条提示（Store 只在 all() 时更新计数） */
private fun withSkippedNote(context: Context, msg: String): String {
    val skipped = AttendanceStore.lastSkipped
    return if (skipped > 0) "$msg · " + context.getString(R.string.bad_records_skipped_fmt, skipped) else msg
}

@Composable
private fun EmployeeEditor(context: Context) {
    val c = LocalAppColors.current
    var base by remember { mutableStateOf(Config.employees(context)) }
    // Config.employees 读不到已存名单时会退回内置默认值并置该标志，此时保存会覆盖真实名单
    val empFallback = remember(base) { Config.employeesFallback }
    val mbMap = remember { mutableStateMapOf<Int, String>() }
    val bnMap = remember { mutableStateMapOf<Int, String>() }
    var deleteTarget by remember { mutableStateOf<Employee?>(null) }
    if (deleteTarget != null) {
        val t = deleteTarget!!
        GlassConfirmDialog(
            title = context.getString(R.string.delete),
            message = context.getString(R.string.del_emp_confirm_fmt, t.nameZh.ifBlank { t.nameLo }),
            onConfirm = {
                Config.removeEmployee(context, t.id)
                mbMap.remove(t.id)
                bnMap.remove(t.id)
                base = Config.employees(context)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
    LaunchedEffect(base) {
        base.forEach { e ->
            if (!mbMap.containsKey(e.id)) mbMap[e.id] = if (e.monthlyBase > 0) e.monthlyBase.toInt().toString() else ""
            if (!bnMap.containsKey(e.id)) bnMap[e.id] = if (e.bonus > 0) e.bonus.toInt().toString() else ""
        }
    }
    if (empFallback) {
        Text(context.getString(R.string.emp_fallback_warn), fontSize = 11.sp, color = MiuixTheme.colorScheme.error)
        Spacer(Modifier.height(6.dp))
    }
    base.forEach { e ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.nameZh.ifBlank { e.nameLo }, Modifier.weight(1f), fontSize = 14.sp, color = c.textPrimary)
                GlassButton(context.getString(R.string.delete), modifier = Modifier.width(64.dp)) { deleteTarget = e }
            }
            TextField(value = mbMap[e.id] ?: "", onValueChange = { mbMap[e.id] = it }, label = context.getString(R.string.field_monthly_base), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            TextField(value = bnMap[e.id] ?: "", onValueChange = { bnMap[e.id] = it }, label = context.getString(R.string.field_bonus), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    GlassButton(context.getString(R.string.save_employee_salary), modifier = Modifier.fillMaxWidth()) {
        Config.saveEmployees(context, base.map { e -> e.copy(monthlyBase = mbMap[e.id]?.toDoubleOrNull() ?: e.monthlyBase, bonus = bnMap[e.id]?.toDoubleOrNull() ?: e.bonus) })
        Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
    }
    Spacer(Modifier.height(12.dp))
    var nl by remember { mutableStateOf("") }
    var nz by remember { mutableStateOf("") }
    var nm by remember { mutableStateOf("") }
    var nb by remember { mutableStateOf("") }
    Text(context.getString(R.string.add_employee), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(value = nl, onValueChange = { nl = it }, label = context.getString(R.string.name_lo), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        TextField(value = nz, onValueChange = { nz = it }, label = context.getString(R.string.name_zh), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(value = nm, onValueChange = { nm = it }, label = context.getString(R.string.field_monthly_base), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        TextField(value = nb, onValueChange = { nb = it }, label = context.getString(R.string.field_bonus), useLabelAsPlaceholder = true, modifier = Modifier.weight(1f))
        GlassButton(context.getString(R.string.add), primary = true, modifier = Modifier.width(80.dp)) {
            if (nl.isNotBlank() || nz.isNotBlank()) {
                Config.addEmployee(context, nl, nz, nm.toDoubleOrNull() ?: 0.0, nb.toDoubleOrNull() ?: 0.0)
                nl = ""; nz = ""; nm = ""; nb = ""; base = Config.employees(context)
            }
        }
    }
}

@Composable
private fun MonthGrid(employees: List<Employee>, recs: List<AttendanceRecord>, ym: String) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    val grid = HashMap<Int, HashMap<Int, AttendanceRecord>>()
    recs.forEach { r ->
        // 脏数据（长度不对或日期数字非法）直接跳过，不让整张表崩掉
        if (r.date.length == 10 && r.date.startsWith(ym)) {
            val d = r.date.substring(8).toIntOrNull() ?: return@forEach
            if (d !in 1..31) return@forEach
            grid.getOrPut(r.employeeId) { HashMap() }[d] = r
        }
    }
    val parts = ym.split("-")
    val cal = Calendar.getInstance()
    cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
    val nDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            Text(context.getString(R.string.employees), Modifier.width(72.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
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
                        Status.FULL -> { if (r.late) { sym = context.getString(R.string.late_tag); col = Color(0xFFFFB74D) } else { sym = "√"; col = Color(0xFF4CAF50) } }
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
