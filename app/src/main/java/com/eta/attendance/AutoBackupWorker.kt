package com.eta.attendance

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 自动备份 Worker：每天将考勤数据推送到 WebDAV（坚果云）。
 * 需在设置页开启 WebDAV 后才生效。
 * 失败时返回 [Result.retry]，由 WorkManager 按指数退避重试（见 [AutoBackup.schedule]）；
 * 连续失败到 [FAIL_NOTIFY_THRESHOLD] 次才弹一次通知，成功一次即清零计数。
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Config.davEnabled(context)) return Result.success()
        val res = runCatching { AttendanceStore.pushToDav(context) }.getOrNull()
        if (res?.ok == true) {
            Config.resetAutoBackupFailures(context)
            Reminder.notifyNow(context, context.getString(R.string.auto_backup_ok_notif))
            return Result.success()
        }
        val fails = Config.incrAutoBackupFailures(context)
        if (fails >= FAIL_NOTIFY_THRESHOLD) {
            Reminder.notifyNow(context, context.getString(R.string.auto_backup_fail_notif))
        }
        // 失败原因（res.message）只进日志，通知文案走资源，避免刷屏与硬编码
        android.util.Log.w("AutoBackupWorker", "自动备份失败（连续 $fails 次）：${res?.message}")
        return Result.retry()
    }

    companion object {
        /** 连续失败达到此次数才提示一次 */
        private const val FAIL_NOTIFY_THRESHOLD = 3
    }
}

object AutoBackup {
    private const val WORK_NAME = "auto_backup_daily"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS) // 首次延迟1小时，避免启动时立即执行
            // 失败后指数退避重试：30s 起步（WorkManager 允许的最小值），最多重试到系统上限
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
