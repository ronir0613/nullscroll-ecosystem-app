package com.nullscroll.morninglock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.nullscroll.morninglock.data.AlarmRules
import com.nullscroll.morninglock.data.PlanEntity
import java.time.ZoneId

internal object AlarmScheduler {
    const val EXTRA_PLAN_ID = "com.nullscroll.morninglock.extra.PLAN_ID"
    const val EXTRA_SNOOZES_USED = "com.nullscroll.morninglock.extra.SNOOZES_USED"
    const val ACTION_FIRE = "com.nullscroll.morninglock.action.FIRE_ALARM"
    const val ACTION_SNOOZE = "com.nullscroll.morninglock.action.SNOOZE_ALARM"
    const val ACTION_DISMISS = "com.nullscroll.morninglock.action.DISMISS_ALARM"

    private const val CHANNEL_ID = "morning_alarm"
    private const val CHANNEL_NAME = "Morning alarm"

    fun schedule(context: Context, plan: PlanEntity) {
        require(plan.locked) { "Only locked plans can schedule an alarm." }
        require(plan.alarmDismissedAtEpochMillis == null) { "A dismissed plan cannot be scheduled again." }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            throw ExactAlarmAccessRequired()
        }
        val triggerAt = plan.nextAlarmAtEpochMillis
            ?: AlarmRules.alarmAtEpochMillis(plan.date, plan.alarmTime, ZoneId.systemDefault())
        val showIntent = alarmActivityPendingIntent(context, plan.id, plan.snoozesUsed, requestCode(plan.id, 1))
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
        alarmManager.setAlarmClock(info, firePendingIntent(context, plan.id))
    }

    fun cancel(context: Context, planId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(firePendingIntent(context, planId))
        cancelNotification(context, planId)
    }

    fun cancelNotification(context: Context, planId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(planId))
    }

    fun showAlarmNotification(context: Context, plan: PlanEntity) {
        ensureNotificationChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmActivity = alarmActivityPendingIntent(context, plan.id, plan.snoozesUsed, requestCode(plan.id, 1))
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Morning Lock alarm")
            .setContentText("Dismiss to begin your morning plan.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(plan.nextAlarmAtEpochMillis ?: System.currentTimeMillis())
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(alarmActivity)
            .setFullScreenIntent(alarmActivity, true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                    "SNOOZE · 5 MIN",
                    snoozePendingIntent(context, plan.id)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    "DISMISS",
                    dismissPendingIntent(context, plan.id, plan.snoozesUsed)
                ).build()
            )
            .build()
        manager.notify(notificationId(plan.id), notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "User-scheduled Morning Lock alarms"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 400, 700)
            setSound(sound, audio)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun firePendingIntent(context: Context, planId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_PLAN_ID, planId)
        return PendingIntent.getBroadcast(
            context,
            requestCode(planId, 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun snoozePendingIntent(context: Context, planId: Long): PendingIntent =
        alarmActivityPendingIntent(context, planId, 0, requestCode(planId, 2), ACTION_SNOOZE)

    private fun dismissPendingIntent(context: Context, planId: Long, snoozesUsed: Int): PendingIntent =
        alarmActivityPendingIntent(
            context,
            planId,
            snoozesUsed,
            requestCode(planId, 3),
            ACTION_DISMISS
        )

    private fun alarmActivityPendingIntent(
        context: Context,
        planId: Long,
        snoozesUsed: Int,
        requestCode: Int,
        action: String? = null
    ): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_PLAN_ID, planId)
            .putExtra(EXTRA_SNOOZES_USED, snoozesUsed)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action != null) intent.action = action
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(planId: Long, action: Int): Int = 31 * planId.hashCode() + action
    private fun notificationId(planId: Long): Int = 31 * planId.hashCode() + 7
}

internal class ExactAlarmAccessRequired : IllegalStateException("Allow exact alarms in Android settings to schedule this alarm.")
