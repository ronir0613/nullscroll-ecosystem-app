package com.nullscroll.morninglock.data

internal object SessionRules {
    const val CEILING_BUFFER_MINUTES = 20
    const val TEST_MODE_SESSION_DURATION_MILLIS = 25_000L
    const val TEST_MODE_TASK_DURATION_MILLIS = 5_000L

    fun ceilingAtEpochMillis(
        dismissedAtEpochMillis: Long,
        baseBudgetMinutes: Int,
        taskMinutes: Int,
        extensionMinutes: Int,
        testMode: Boolean = false
    ): Long {
        if (testMode) return Math.addExact(dismissedAtEpochMillis, TEST_MODE_SESSION_DURATION_MILLIS)
        val plannedMinutes = Math.addExact(Math.addExact(baseBudgetMinutes, taskMinutes), extensionMinutes)
        val totalMillis = Math.multiplyExact((plannedMinutes + CEILING_BUFFER_MINUTES).toLong(), 60_000L)
        return Math.addExact(dismissedAtEpochMillis, totalMillis)
    }

    fun taskEndAtEpochMillis(
        startedAtEpochMillis: Long,
        durationMinutes: Int,
        ceilingAtEpochMillis: Long,
        testMode: Boolean = false
    ): Long {
        val durationMillis = if (testMode) TEST_MODE_TASK_DURATION_MILLIS else Math.multiplyExact(durationMinutes.toLong(), 60_000L)
        return minOf(Math.addExact(startedAtEpochMillis, durationMillis), ceilingAtEpochMillis)
    }

    fun canConfirmTask(taskEndsAtEpochMillis: Long?, nowEpochMillis: Long): Boolean =
        taskEndsAtEpochMillis != null && nowEpochMillis >= taskEndsAtEpochMillis

    fun canStartTask(
        activeTaskIndex: Int,
        taskCount: Int,
        taskStartedAtEpochMillis: Long?,
        ceilingAtEpochMillis: Long,
        nowEpochMillis: Long
    ): Boolean = activeTaskIndex in 0 until taskCount &&
        taskStartedAtEpochMillis == null &&
        nowEpochMillis < ceilingAtEpochMillis

}
