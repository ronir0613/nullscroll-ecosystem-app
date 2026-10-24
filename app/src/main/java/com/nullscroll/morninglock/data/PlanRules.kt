package com.nullscroll.morninglock.data

object PlanRules {
    const val DEFAULT_BUDGET_MINUTES = 30

    fun plannedBudgetMinutes(taskDurations: List<Int>): Int =
        DEFAULT_BUDGET_MINUTES + taskDurations.filter { it > 0 }.sum()

    fun canLock(taskDurations: List<Int>): Boolean =
        taskDurations.isNotEmpty() && taskDurations.all { it > 0 }

    fun extendedMinutes(locked: Boolean, currentExtension: Int, addedMinutes: Int): Int? {
        if (!locked || currentExtension < 0 || addedMinutes <= 0) return null
        return runCatching { Math.addExact(currentExtension, addedMinutes) }.getOrNull()
    }
}
