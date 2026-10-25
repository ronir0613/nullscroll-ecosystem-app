package com.nullscroll.morninglock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.nullscroll.morninglock.data.MorningLockDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val planId = intent.getLongExtra(AlarmScheduler.EXTRA_PLAN_ID, -1L)
        if (planId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val plan = MorningLockDatabase.open(context).dao().planById(planId)
                if (plan == null || !plan.locked || plan.alarmDismissedAtEpochMillis != null) {
                    AlarmScheduler.cancel(context, planId)
                } else if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    AlarmScheduler.showAlarmNotification(context, plan)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
