package com.eta.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && Config.reminderEnabled(this)) Reminder.schedule(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 37 起系统强制 edge-to-edge，顶部 inset 由 NavDisplay 的调用方（AttendanceScreen）消费
        enableEdgeToEdge()
        LocaleUtils.apply(this)
        Reminder.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Config.reminderEnabled(this)) Reminder.schedule(this)
        if (Config.autoBackupEnabled(this) && Config.davEnabled(this)) AutoBackup.schedule(this)
        setContent {
            AttendanceApp()
        }
    }
}
