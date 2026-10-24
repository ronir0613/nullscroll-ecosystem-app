package com.nullscroll.morninglock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRulesTest {
    @Test
    fun plannedBudgetStartsAtThirtyMinutesAndAddsTaskDurations() {
        assertEquals(30, PlanRules.plannedBudgetMinutes(emptyList()))
        assertEquals(47, PlanRules.plannedBudgetMinutes(listOf(5, 12)))
        assertEquals(30, PlanRules.plannedBudgetMinutes(listOf(-5, 0)))
    }

    @Test
    fun planCanBeLockedOnlyWhenEveryTaskHasPositiveDuration() {
        assertFalse(PlanRules.canLock(emptyList()))
        assertFalse(PlanRules.canLock(listOf(5, 0)))
        assertFalse(PlanRules.canLock(listOf(5, -1)))
        assertTrue(PlanRules.canLock(listOf(5, 12)))
    }

    @Test
    fun lockedPlanCanOnlyBeExtendedByPositiveMinutes() {
        assertEquals(10, PlanRules.extendedMinutes(locked = true, currentExtension = 5, addedMinutes = 5))
        assertNull(PlanRules.extendedMinutes(locked = true, currentExtension = 5, addedMinutes = 0))
        assertNull(PlanRules.extendedMinutes(locked = true, currentExtension = 5, addedMinutes = -5))
        assertNull(PlanRules.extendedMinutes(locked = false, currentExtension = 0, addedMinutes = 5))
    }
}
