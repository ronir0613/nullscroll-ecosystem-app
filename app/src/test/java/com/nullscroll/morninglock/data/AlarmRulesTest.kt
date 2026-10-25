package com.nullscroll.morninglock.data

import java.time.ZoneId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRulesTest {
    @Test
    fun alarmTimeUsesSelectedDateAndDeviceZone() {
        val trigger = AlarmRules.alarmAtEpochMillis(
            date = "2026-10-25",
            time = "07:05",
            zoneId = ZoneId.of("Asia/Kolkata")
        )

        assertEquals(Instant.parse("2026-10-25T01:35:00Z").toEpochMilli(), trigger)
    }

    @Test
    fun snoozeCanBeUsedTwiceAndAdvancesFiveMinutes() {
        assertTrue(AlarmRules.canSnooze(0))
        assertTrue(AlarmRules.canSnooze(1))
        assertFalse(AlarmRules.canSnooze(2))
        assertEquals(1_700_000_300_000L, AlarmRules.snoozeAtEpochMillis(1_700_000_000_000L))
    }

    @Test
    fun pendingPlanCanChangeAlarmTimeAndTriggerTogether() {
        val plan = PlanEntity(id = 7, date = "2026-10-25", alarmTime = "07:00", locked = true)

        val updated = AlarmRules.withUpdatedTime(plan, "08:15", ZoneId.of("Asia/Kolkata"))

        assertEquals("08:15", updated?.alarmTime)
        assertEquals(
            Instant.parse("2026-10-25T02:45:00Z").toEpochMilli(),
            updated?.nextAlarmAtEpochMillis
        )
    }

    @Test
    fun alarmTimeCannotBeChangedAfterSnoozeOrDismissal() {
        val snoozed = PlanEntity(id = 7, date = "2026-10-25", alarmTime = "07:00", locked = true, snoozesUsed = 1)
        val dismissed = PlanEntity(id = 8, date = "2026-10-25", alarmTime = "07:00", locked = true, alarmDismissedAtEpochMillis = 1L)

        assertNull(AlarmRules.withUpdatedTime(snoozed, "08:15", ZoneId.of("Asia/Kolkata")))
        assertNull(AlarmRules.withUpdatedTime(dismissed, "08:15", ZoneId.of("Asia/Kolkata")))
    }
}
