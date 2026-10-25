package com.nullscroll.morninglock.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanReviewRulesTest {
    @Test
    fun savedPlanWithNoPendingAlarmShowsOnlyOneHomeAction() {
        assertEquals(
            PlanReviewActions(primary = "BACK TO HOME", secondary = null),
            PlanReviewRules.actions(existing = true, pending = false, alarmScheduled = false, alarmTimeChanged = false)
        )
        assertEquals(
            PlanReviewActions(primary = "BACK TO HOME", secondary = null),
            PlanReviewRules.actions(existing = true, pending = true, alarmScheduled = true, alarmTimeChanged = false)
        )
    }

    @Test
    fun savedPlanWithPendingAlarmKeepsScheduleAndReturnActionsDistinct() {
        assertEquals(
            PlanReviewActions(primary = "SCHEDULE ALARM", secondary = "BACK TO HOME"),
            PlanReviewRules.actions(existing = true, pending = true, alarmScheduled = false, alarmTimeChanged = false)
        )
        assertEquals(
            PlanReviewActions(primary = "UPDATE ALARM TIME", secondary = "BACK TO HOME"),
            PlanReviewRules.actions(existing = true, pending = true, alarmScheduled = true, alarmTimeChanged = true)
        )
    }
}
