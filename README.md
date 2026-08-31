# 考勤签到 (Attendance)

中老双语员工考勤 Android App —— Kotlin + Jetpack Compose + Miuix 液态玻璃 UI。
由网页版 `attendance-tracker` 重写而来（路线 C）。

## 功能
- 中老双语一键切换（`values` / `values-lo`）
- 实时时钟，自动判断迟到
- 点选员工标记「全天 / 半天 / 缺勤」
- 月度统计 + 导出 CSV
- 工资估算（日薪 × 出勤天数）
- 本地存储（SharedPreferences，JSON），可选 Supabase 云端同步
- 液态玻璃悬浮底栏 + 玻璃卡片（复用 laotran 视觉体系）

## 构建（云端，不占手机流量）
push 到 `main` 触发 GitHub Actions（`.github/workflows/build.yml`），
产出 artifact **attendance-debug-apk**，下载后安装即可。

## 云端同步（可选）
1. 注册 supabase.com，建表 `attendance(employee_id int, date text, status text, check_in_time text, late bool)`
2. 在 App「设置」页填入 Supabase URL + Anon Key（或通过 CI Secrets 注入 BuildConfig）
3. 点「立即同步」把本地记录 upsert 到云端

## 技术栈
AGP 9.3.2 · Kotlin 2.4.10 · Compose 1.11.2 · Miuix 0.9.3 · minSdk 24 · compileSdk 37 · JDK 17 · Gradle 9.6.1

## 目录
- `Config.kt` 员工名单 / 上下班时间 / 语言 / Supabase
- `Store.kt` 数据仓库 + CSV + Supabase 推送
- `AttendanceApp.kt` 四页 UI + 液态玻璃组件
- `LocaleUtils.kt` 中老双语切换
