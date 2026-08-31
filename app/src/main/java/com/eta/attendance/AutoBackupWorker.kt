package com.eta.attendance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 自动备份 Worker：每天将考勤数据推送到 WebDAV（坚果云）。
 * 需在设置页开启 WebDAV 后才生效。
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Config.davEnabled(context)) return Result.success()
        return try {
            val ok = AttendanceStore.pushToDav(context).ok
            if (ok) Reminder.notifyNow(context, "考勤数据已自动备份到云端")
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object AutoBackup {
    private const val WORK_NAME = "auto_backup_daily"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS) // 首次延迟1小时，避免启动时立即执行
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
