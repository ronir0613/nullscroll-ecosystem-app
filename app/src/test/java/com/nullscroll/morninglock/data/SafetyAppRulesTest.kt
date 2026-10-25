package com.nullscroll.morninglock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyAppRulesTest {
    @Test
    fun sessionGetsOnlyTheSelectedAppsForItsPlan() {
        val selections = listOf(
            AllowedAppEntity(planId = 1, packageName = "health.one", displayName = "Health One"),
            AllowedAppEntity(planId = 2, packageName = "safety.two", displayName = "Safety Two")
        )

        assertEquals(setOf("health.one"), PlanIsolationRules.packagesForPlan(1, selections))
        assertEquals(setOf("safety.two"), PlanIsolationRules.packagesForPlan(2, selections))
    }

    @Test
    fun selectedAppCanOpenOnlyWhenItHasALaunchActivity() {
        assertTrue(SafetyAppRules.canLaunch("health.one", hasLaunchActivity = true))
        assertFalse(SafetyAppRules.canLaunch("health.one", hasLaunchActivity = false))
        assertFalse(SafetyAppRules.canLaunch("", hasLaunchActivity = true))
    }
}
