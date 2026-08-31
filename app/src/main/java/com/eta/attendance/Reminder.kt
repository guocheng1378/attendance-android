package com.eta.attendance

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Reminder {
    private const val CHANNEL_ID = "checkin_reminder"
    private const val WORK_NAME = "checkin_reminder_daily"
    private const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "未打卡提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "当天还没有签到记录时提醒" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context) {
        ensureChannel(context)
        val hour = Config.reminderHour(context)
        val minute = Config.reminderMinute(context)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        val delay = target.timeInMillis - now.timeInMillis
        val req = PeriodicWorkRequestBuilder<CheckInReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun notifyNow(context: Context, body: String) {
        ensureChannel(context)
        if (!hasPermission(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("考勤提醒")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
    }
}

class CheckInReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Config.reminderEnabled(context)) return Result.success()
        val employees = Config.employees(context)
        if (employees.isEmpty()) return Result.success()
        val today = AttendanceStore.today()
        val records = AttendanceStore.forDate(context, today)
        if (records.isEmpty()) {
            Reminder.notifyNow(context, "$today 还没有任何签到记录，记得打卡")
        } else if (records.size < employees.size) {
            val done = records.map { it.employeeId }.toSet()
            val missing = employees.filter { it.id !in done }
            val names = missing.take(6).joinToString("、") { it.nameZh.ifBlank { it.nameLo } }
            Reminder.notifyNow(context, "$today 还有 ${missing.size} 人未签到：$names")
        }
        return Result.success()
    }
}
