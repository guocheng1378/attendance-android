# 考勤签到 (Attendance)

中老双语员工考勤 Android App —— Kotlin + Jetpack Compose + Miuix 液态玻璃 UI。
由网页版 `attendance-tracker` 重写而来（路线 C）。

## 功能
- 中老双语一键切换（`values` / `values-lo`，老挝语下配色名也有本地化标签）
- 实时时钟，按「上班时间」自动判定迟到
- 点选员工标记「全天 / 半天 / 缺勤」，支持全选；清空当天会报实际删除条数
- 月度统计 + 柱状图 + 导出 CSV
- 工资估算（口径见下节），支持奖金、扣减、预支；预支超出显示为负数（欠款）
- 本地存储（SharedPreferences + JSON），不配任何云端也能离线用
- 备份通道四选一：本地文件导入导出 / WebDAV（坚果云等）/ GitHub Tracker JSON / Supabase
- 定时自动备份到 WebDAV（WorkManager；失败自动 retry，连续 3 次才发通知，避免刷屏）
- 每日签到提醒（通知，Android 13+ 需授权；未授权时设置页常驻红字提示）
- 液态玻璃悬浮底栏 + 玻璃卡片 + 陀螺仪高光跟随（复用 laotran 视觉体系）

四个底栏页：签到 / 统计 / 工资 / 设置。设置页内再分外观、考勤、薪资、数据四个子页。

## 工资口径
```
日薪       = 月薪 ÷ 应出勤天数
应出勤天数 = 当月总天数 − 全员没来天数 − 2   （最少 1 天）
出勤折算   = 全天×1 + 半天×0.5
扣减天数   = 出勤 < 应出勤÷2 → 2 天；出勤 < 应出勤 → 1 天；否则 0
gross      = round千( min(出勤折算, 应出勤) × 日薪 + 奖金 )   // 奖金固定发放，只在总和上取整一次
实发 net   = max(gross − 日薪×扣减天数, 0)                    // 当月总收款
应发 payable = net − 预支                                     // 可为负 = 欠款
```
例：月薪 4,000,000 ₭、31 天月、无停工 → 应出勤 29 天、日薪 137,931 ₭。
满勤 + 奖金 200,000 ₭ → 实发 4,200,000 ₭；缺 1 天 → 扣 1 天，实发 3,924,000 ₭。

金额一律按千位四舍五入（₭ 最小流通面额）。非法年月直接标红提示，绝不按 30 天兜底。

## 构建
### 云端（推荐，不占手机流量）
push 到 `main` 或提 PR 触发 GitHub Actions（`.github/workflows/build.yml`）：
- 先跑 `compileDebugKotlin` 与单元测试，再产出 artifact **attendance-debug-apk**，下载即装。
- 打 `v*` tag 时额外构建 release 并把 APK 挂到 Release 页面。

要出**签名** release，需在仓库 Settings → Secrets and variables → Actions 配 4 个：
`KEYSTORE_BASE64`（keystore 的 base64）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
不配则跳过签名步骤，只出 debug 包。

生成 keystore：
```bash
keytool -genkey -v -keystore att.jks -alias att -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 att.jks   # 这串就是 KEYSTORE_BASE64
```

### 本地
```bash
./gradlew :app:assembleDebug     # 需要 JDK 21 + Android SDK 37
```

## 测试
```bash
./gradlew :app:testDebugUnitTest
```
`app/src/test/` 下是工资引擎的口径回归测试（`SalaryEngineTest`，16 个用例）：
日历与闰年、非法年月抛异常、应出勤兜底、满勤/超勤/半天折算、扣减阈值边界（14/15 天）、
千位取整只算一次、预支与欠款、零月薪不炸。断言与上节公式逐条对齐，改口径时必须同步改这里。

UI 层暂无自动化测试（Compose 界面未在真机上验证过，见「已知限制」）。

## 云端同步怎么配
按省事程度排序，任选其一即可；一个都不配也能正常用（数据只留在本机）。

### 1. WebDAV / 坚果云（推荐主通道）
设置页填 服务器地址、账号、应用密码、远端路径。
- 地址必须带 `http://` 或 `https://`，否则直接拦下并提示，不会白跑一次请求。
- 坚果云不允许在共享根目录放文件，路径没写父目录时会自动归到 `/attendance/` 下。
- 「上传备份」= 本机 → 云端；「从云端恢复」= 云端 → 本机（同员工同天覆盖）。
- 云端备份比本机旧时，恢复会被拒绝并说明原因；确认后需再点一次才会强制覆盖 ——
  防止用一份旧备份悄悄盖掉新数据。
- 401 / 403 / 404 / 409 / 507 / 网络异常分别给出可读原因，不再笼统报「失败」。

### 2. GitHub Tracker JSON
填 tracker 导出的 JSON 直链，按**姓名**匹配现有员工（先中文名、再老挝文名）。
匹配不上的跳过，并在提示里列出名单，**不会自动新建员工**（旧版会悄悄建人且新人日薪为 0，
既污染名单又算错工资）。所以请先在「设置 → 员工薪资」里把人加齐再导入。

### 3. Supabase（可选，只做单向推送）
```sql
create table attendance (
  employee_id int not null,
  date text not null,
  status text,
  check_in_time text,
  late boolean
);
alter table attendance add constraint attendance_uniq unique (employee_id, date);
```
**那个唯一约束必须建**：App 用 `Prefer: resolution=merge-duplicates` 做 upsert，
没有约束时每点一次「立即同步」都会往表里追加一批重复行。
设置页填 URL + Anon Key（也可用 CI Secrets 注入 `BuildConfig`）。

云端凭据存在 EncryptedSharedPreferences 里。若设备端加密存储不可用，保存会返回失败并
明确提示「密码/密钥未保存」，不会静默降级成明文存储。

## 技术栈
AGP 9.3.2 · Kotlin 2.4.10 · Compose 1.12.0-rc01 · Miuix 0.9.4-rc01（ui / icons / nav / blur / preference）·
OkHttp 4.12.0 · WorkManager 2.9.1 · minSdk 33 · targetSdk 34 · compileSdk 37 · JDK 21 · Gradle 9.6.1

权限只有 4 个：`INTERNET`、`ACCESS_NETWORK_STATE`、`POST_NOTIFICATIONS`、`VIBRATE`。
本 App 不做定位打卡，因此没有定位权限；`allowBackup=false`，考勤数据与云端凭据不参与系统云备份。
备份文件写在应用私有外部目录（`Android/data/com.eta.attendance/files/`），不需要存储权限。

## 目录
- `MainActivity.kt` 入口 Activity
- `AttendanceApp.kt` 四页 UI（签到 / 统计 / 工资 / 设置）+ 对话框
- `Glass.kt` 液态玻璃组件（卡片、按钮、悬浮底栏、陀螺仪高光）
- `Theme.kt` 5 套配色 × 明暗双主题、`Palette.label()` 本地化配色名
- `Charts.kt` 柱状图
- `Store.kt` 考勤仓库 + CSV + 备份导入导出 + WebDAV / Supabase / Tracker 网络层
- `Config.kt` 员工名单 / 上下班时间 / 语言 / 主题 / 云端凭据（加密存储）
- `SalaryEngine.kt` 工资计算（纯函数，可单测）
- `SalaryScreen.kt` 工资页 + 工资 CSV 导出
- `Reminder.kt` 通知与权限
- `AutoBackupWorker.kt` 定时备份
- `LocaleUtils.kt` 中老双语切换

界面文案全部走 `R.string`，`values/` 与 `values-lo/` 两份 key 严格对齐（153 条，
两边 key 集合完全相同、占位符个数一致，且不存在未被引用的孤儿 key）。

## 已知限制
- 数据存 SharedPreferences 的单个 JSON 串，全量读写。十几人 × 每天一条的规模没问题；
  上千人或多设备并发写不合适（合并策略是「同员工同天覆盖」，没有真正的冲突解决）。
- 导入备份只做合并、从不删除：换设备时想要「和本机完全一致」，请先清空再恢复。
- 迟到判定只比较时间字符串与上班时间，不处理跨天班次。
- 员工名单解析失败时退回内置默认名单并显示警告，此时保存会被拒绝，防止覆盖真实名单。
- 历史版本导入的员工可能 `monthlyBase = 0`，新版本不会自动回填默认月薪
  （涉及薪资数据，需人工在「员工薪资」页逐个确认）。
- 陀螺仪高光监听没有随 Activity ON_STOP 暂停（缺 `lifecycle-runtime-compose` 依赖）；
  已做静止去抖与频率上限，实际功耗影响很小但不为零。
- 界面渲染未在真机上验证过，视觉问题请开 issue。

## 支持
Issues 开在本仓库。请附上：App 版本、系统版本、复现步骤；涉及数据异常的，
脱敏后附上「设置 → 导出备份」的 JSON。
