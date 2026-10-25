package com.nullscroll.morninglock.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanIsolationRulesTest {
    @Test
    fun oneProductionPlanAndOneTemporaryTestPlanCanShareADate() {
        val existing = listOf(PlanSlot("2026-10-25", isTemporaryTest = false))

        assertTrue(PlanIsolationRules.canCreate("2026-10-25", isTemporaryTest = true, existing))
        assertFalse(PlanIsolationRules.canCreate("2026-10-25", isTemporaryTest = false, existing))
    }

    @Test
    fun onlyOnePlanOfEachKindCanShareTheSameDate() {
        val existing = listOf(PlanSlot("2026-10-25", isTemporaryTest = true))

        assertFalse(PlanIsolationRules.canCreate("2026-10-25", isTemporaryTest = true, existing))
        assertTrue(PlanIsolationRules.canCreate("2026-10-26", isTemporaryTest = true, existing))
    }

    @Test
    fun onlyTemporaryPlansAreIncludedInTestCleanup() {
        assertFalse(PlanIsolationRules.canClearWithTestData(isTemporaryTest = false))
        assertTrue(PlanIsolationRules.canClearWithTestData(isTemporaryTest = true))
    }

    @Test
    fun clearingTestsStopsOnlyATestSessionMonitor() {
        assertFalse(PlanIsolationRules.shouldStopMonitorForCleanup(activeSessionIds = setOf(7), deletedSessionIds = setOf(8)))
        assertTrue(PlanIsolationRules.shouldStopMonitorForCleanup(activeSessionIds = setOf(7, 8), deletedSessionIds = setOf(8)))
        assertFalse(PlanIsolationRules.shouldStopMonitorForCleanup(activeSessionIds = emptySet(), deletedSessionIds = setOf(8)))
    }
}
