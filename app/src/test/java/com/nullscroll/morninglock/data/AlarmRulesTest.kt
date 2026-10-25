package com.nullscroll.morninglock.data

import java.time.ZoneId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
