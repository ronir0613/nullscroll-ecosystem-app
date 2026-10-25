package com.nullscroll.morninglock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nullscroll.morninglock.data.MorningLockDatabase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = MorningLockDatabase.open(context).dao()
                dao.pendingAlarmPlans(LocalDate.now().toString()).forEach { plan ->
                    runCatching { AlarmScheduler.schedule(context, plan) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
