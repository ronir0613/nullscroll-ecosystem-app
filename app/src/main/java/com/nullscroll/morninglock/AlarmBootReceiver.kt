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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = MorningLockDatabase.open(context).dao()
                dao.pendingAlarmPlans(LocalDate.now().toString()).forEach { plan ->
                    runCatching { AlarmScheduler.schedule(context, plan) }
                }
                val now = System.currentTimeMillis()
                dao.loadActiveSessions().forEach { session ->
                    val deadline = session.ceilingAtEpochMillis
                    if (deadline == null || now >= deadline) {
                        dao.endSession(session.id, now)
                    } else {
                        val deadlineArmed = runCatching { SessionDeadline.schedule(context, session.id, deadline) }.isSuccess
                        if (deadlineArmed) runCatching { SessionMonitor.start(context, session.id) }
                        else dao.endSession(session.id, now)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
