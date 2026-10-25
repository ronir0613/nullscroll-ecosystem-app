package com.nullscroll.morninglock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {
    @Test
    fun ceilingIncludesPlanTasksExtensionAndTwentyMinuteBuffer() {
        assertEquals(
            1_700_000_000_000L + 80 * 60_000L,
            SessionRules.ceilingAtEpochMillis(
                dismissedAtEpochMillis = 1_700_000_000_000L,
                baseBudgetMinutes = 30,
                taskMinutes = 20,
                extensionMinutes = 10
            )
        )
    }

    @Test
    fun taskEndDoesNotExtendPastTheSessionCeiling() {
        assertEquals(1_700_003_000_000L, SessionRules.taskEndAtEpochMillis(1_700_000_000_000L, 60, 1_700_003_000_000L))
        assertEquals(1_700_002_000_000L, SessionRules.taskEndAtEpochMillis(1_700_000_000_000L, 60, 1_700_002_000_000L))
    }

    @Test
    fun taskCannotBeConfirmedBeforeItsPersistedEnd() {
        assertFalse(SessionRules.canConfirmTask(1_700_000_060_000L, 1_700_000_059_999L))
        assertTrue(SessionRules.canConfirmTask(1_700_000_060_000L, 1_700_000_060_000L))
    }

    @Test
    fun onlyCurrentUnstartedTaskCanStartBeforeTheDeadline() {
        assertTrue(SessionRules.canStartTask(0, 2, null, 1_700_000_060_000L, 1_700_000_000_000L))
        assertTrue(SessionRules.canStartTask(1, 2, null, 1_700_000_060_000L, 1_700_000_000_000L))
        assertFalse(SessionRules.canStartTask(2, 2, null, 1_700_000_060_000L, 1_700_000_000_000L))
        assertFalse(SessionRules.canStartTask(0, 2, 1_700_000_001_000L, 1_700_000_060_000L, 1_700_000_000_000L))
        assertFalse(SessionRules.canStartTask(0, 2, null, 1_700_000_000_000L, 1_700_000_000_000L))
    }

    @Test
    fun temporaryTestModeUsesTwentyFiveSecondSessionCeiling() {
        val dismissedAt = 1_700_000_000_000L
        assertEquals(
            dismissedAt + 25_000L,
            SessionRules.ceilingAtEpochMillis(
                dismissedAtEpochMillis = dismissedAt,
                baseBudgetMinutes = 30,
                taskMinutes = 20,
                extensionMinutes = 10,
                testMode = true
            )
        )
    }

    @Test
    fun temporaryTestModeUsesFiveSecondTaskTimerAndStillHonorsSessionCeiling() {
        val startedAt = 1_700_000_000_000L
        val ceiling = startedAt + 25_000L
        assertEquals(
            startedAt + 5_000L,
            SessionRules.taskEndAtEpochMillis(startedAt, 5, ceiling, testMode = true)
        )
        assertEquals(
            startedAt + 3_000L,
            SessionRules.taskEndAtEpochMillis(startedAt, 5, startedAt + 3_000L, testMode = true)
        )
    }
}
