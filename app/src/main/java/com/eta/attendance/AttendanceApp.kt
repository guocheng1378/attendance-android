package com.eta.attendance

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceApp() {
    val controller = remember { ThemeController(ColorSchemeMode.MonetSystem, keyColor = Color(0xFF3482FF)) }
    MiuixTheme(controller = controller) {
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
    val picks = remember { mutableStateMapOf<Int, Status>() }
    LaunchedEffect(today) {
        AttendanceStore.forDate(context, today).forEach { picks[it.employeeId] = it.status }
    }
    val onSurface = MiuixTheme.colorScheme.onSurface

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
        Spacer(Modifier.height(8.dp))
        Config.EMPLOYEES.forEach { e ->
            val sel = picks[e.id]
            GlassCard(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(e.nameLo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onSurface)
                        Text(e.nameZh, fontSize = 13.sp, color = onSurface.copy(alpha = 0.6f))
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

@Composable
internal fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(if (selected) Color(0xFF3482FF) else Color.White.copy(alpha = 0.5f), label = "chipBg")
    val fg = if (selected) Color.White else Color(0xCC1A1D2B)
    Box(
        Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.6f), shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ===================== 统计页 =====================

@Composable
private fun StatsPanel() {
    val context = LocalContext.current
    val ym = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val summary = AttendanceStore.monthSummary(context, ym)
    val onSurface = MiuixTheme.colorScheme.onSurface

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp)
    ) {
        Text(context.getString(R.string.month_summary), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.employees), Modifier.weight(2f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onSurface)
                Text(context.getString(R.string.status_full), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onSurface)
                Text(context.getString(R.string.status_half), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onSurface)
                Text(context.getString(R.string.status_absent), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Config.EMPLOYEES.forEach { e ->
                val a = summary[e.id] ?: IntArray(3)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${e.nameLo} ${e.nameZh}", Modifier.weight(2f), fontSize = 14.sp, color = onSurface)
                    Text("${a[0]}", Modifier.weight(1f), fontSize = 14.sp, color = onSurface)
                    Text("${a[1]}", Modifier.weight(1f), fontSize = 14.sp, color = onSurface)
                    Text("${a[2]}", Modifier.weight(1f), fontSize = 14.sp, color = onSurface)
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

// ===================== 工资页 =====================

@Composable
private fun SalaryPanel() {
    val context = LocalContext.current
    val ym = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val summary = AttendanceStore.monthSummary(context, ym)
    val dailyWage = 100000 // 示例日薪（基普 LAK）
    val onSurface = MiuixTheme.colorScheme.onSurface

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp)
    ) {
        Text(context.getString(R.string.tab_salary), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Config.EMPLOYEES.forEach { e ->
            val a = summary[e.id] ?: IntArray(3)
            val days = a[0] + a[1] * 0.5
            val pay = (days * dailyWage).toInt()
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentPadding = PaddingValues(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.nameLo} ${e.nameZh}", fontSize = 16.sp, color = onSurface)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$days", fontSize = 13.sp, color = onSurface.copy(alpha = 0.6f))
                        Text("$pay LAK", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onSurface)
                    }
                }
            }
        }
    }
}

// ===================== 设置页 =====================

@Composable
private fun SettingsPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(Config.workStart(context)) }
    var end by remember { mutableStateOf(Config.workEnd(context)) }
    var url by remember { mutableStateOf(Config.supabaseUrl(context)) }
    var key by remember { mutableStateOf(Config.supabaseKey(context)) }
    val lang = Config.locale(context)
    val onSurface = MiuixTheme.colorScheme.onSurface

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).padding(bottom = 110.dp)
    ) {
        Text(context.getString(R.string.tab_settings), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.language), fontSize = 14.sp, color = onSurface)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("中文", primary = lang == "zh", modifier = Modifier.weight(1f)) {
                    Config.saveLocale(context, "zh"); (context as? Activity)?.recreate()
                }
                GlassButton("ລາວ", primary = lang == "lo", modifier = Modifier.weight(1f)) {
                    Config.saveLocale(context, "lo"); (context as? Activity)?.recreate()
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.work_start), fontSize = 14.sp, color = onSurface)
            TextField(value = start, onValueChange = { start = it }, label = context.getString(R.string.work_start), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(context.getString(R.string.work_end), fontSize = 14.sp, color = onSurface)
            TextField(value = end, onValueChange = { end = it }, label = context.getString(R.string.work_end), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassButton(context.getString(R.string.save), modifier = Modifier.fillMaxWidth()) {
                Config.saveWorkTime(context, start, end)
                Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.cloud), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onSurface)
            if (!Config.cloudEnabled(context)) {
                Text(context.getString(R.string.sync_off), fontSize = 12.sp, color = onSurface.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(8.dp))
            TextField(value = url, onValueChange = { url = it }, label = context.getString(R.string.supabase_url), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(value = key, onValueChange = { key = it }, label = context.getString(R.string.supabase_key), useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

// ===================== 液态玻璃组件（复用 laotran） =====================

@Composable
private fun GlassBackground() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(0xFF3D6BFF),
                0.5f to Color(0xFF7B5CFF),
                1f to Color(0xFF2BB8FF)
            )
        ))
        Box(
            Modifier.size(300.dp).align(Alignment.TopStart)
                .offset(x = (-100).dp, y = (-60).dp)
                .background(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent)))
        )
        Box(
            Modifier.size(280.dp).align(Alignment.CenterEnd)
                .offset(x = (-60).dp, y = (-140).dp)
                .background(Brush.radialGradient(listOf(Color(0x55FFD166), Color.Transparent)))
        )
        Box(
            Modifier.size(340.dp).align(Alignment.BottomStart)
                .offset(x = (-140).dp, y = 80.dp)
                .background(Brush.radialGradient(listOf(Color(0x5590E8FF), Color.Transparent)))
        )
        Box(
            Modifier.size(180.dp).align(Alignment.CenterStart)
                .background(Brush.radialGradient(listOf(Color(0x44FFFFFF), Color.Transparent)))
        )
    }
}

@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = shape
    Column(
        modifier = modifier
            .shadow(10.dp, s, spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(s)
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.72f), s)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
internal fun GlassButton(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(15.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "btnScale")
    val bg = if (primary) {
        MiuixTheme.colorScheme.primary.copy(alpha = if (pressed) 1f else 0.92f)
    } else {
        Color.White.copy(alpha = if (pressed) 0.75f else 0.55f)
    }
    val fg = if (primary) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(6.dp, shape, spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(shape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "btnScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(Color.White.copy(alpha = if (pressed) 0.7f else 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BottomNavBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xEEFFFFFF))
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        BottomNavItem(context.getString(R.string.tab_checkin), selectedTab == 0, { onSelect(0) }, Modifier.weight(1f))
        BottomNavItem(context.getString(R.string.tab_stats), selectedTab == 1, { onSelect(1) }, Modifier.weight(1f))
        BottomNavItem(context.getString(R.string.tab_salary), selectedTab == 2, { onSelect(2) }, Modifier.weight(1f))
        BottomNavItem(context.getString(R.string.tab_settings), selectedTab == 3, { onSelect(3) }, Modifier.weight(1f))
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, label = "navScale")
    val bg by animateColorAsState(if (selected) Color(0xFFE7EEFF) else Color.Transparent, label = "navBg")
    val fg = if (selected) Color(0xFF3482FF) else Color(0x99000000)
    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 14.sp, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
