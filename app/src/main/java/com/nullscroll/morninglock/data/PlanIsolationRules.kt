package com.nullscroll.morninglock.data

internal data class PlanSlot(val date: String, val isTemporaryTest: Boolean)

internal object PlanIsolationRules {
    fun canCreate(date: String, isTemporaryTest: Boolean, existing: Iterable<PlanSlot>): Boolean =
        existing.none { it.date == date && it.isTemporaryTest == isTemporaryTest }

    fun canClearWithTestData(isTemporaryTest: Boolean): Boolean = isTemporaryTest

    fun shouldStopMonitorForCleanup(activeSessionIds: Set<Long>, deletedSessionIds: Set<Long>): Boolean =
        activeSessionIds.any { it in deletedSessionIds }

    fun packagesForPlan(planId: Long, selections: Iterable<AllowedAppEntity>): Set<String> =
        selections.asSequence().filter { it.planId == planId }.map { it.packageName }.toSet()
}

internal object SafetyAppRules {
    fun canLaunch(packageName: String, hasLaunchActivity: Boolean): Boolean =
        packageName.isNotBlank() && hasLaunchActivity
}
