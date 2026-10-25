package com.nullscroll.morninglock.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object AlarmRules {
    const val MAX_SNOOZES = 2
    const val SNOOZE_MILLIS = 5 * 60 * 1000L

    fun alarmAtEpochMillis(date: String, time: String, zoneId: ZoneId): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zoneId).toInstant().toEpochMilli()

    fun canSnooze(snoozesUsed: Int): Boolean = snoozesUsed in 0 until MAX_SNOOZES

    fun snoozeAtEpochMillis(nowEpochMillis: Long): Long = Math.addExact(nowEpochMillis, SNOOZE_MILLIS)

    fun withUpdatedTime(plan: PlanEntity, time: String, zoneId: ZoneId): PlanEntity? {
        if (!plan.locked || plan.snoozesUsed != 0 || plan.alarmDismissedAtEpochMillis != null) return null
        return plan.copy(
            alarmTime = time,
            nextAlarmAtEpochMillis = alarmAtEpochMillis(plan.date, time, zoneId)
        )
    }
}
