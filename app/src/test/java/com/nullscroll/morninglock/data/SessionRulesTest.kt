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
}
