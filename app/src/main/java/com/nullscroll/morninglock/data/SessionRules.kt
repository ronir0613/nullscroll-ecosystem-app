package com.nullscroll.morninglock.data

internal object SessionRules {
    const val CEILING_BUFFER_MINUTES = 20

    fun ceilingAtEpochMillis(
        dismissedAtEpochMillis: Long,
        baseBudgetMinutes: Int,
        taskMinutes: Int,
        extensionMinutes: Int
    ): Long {
        val plannedMinutes = Math.addExact(Math.addExact(baseBudgetMinutes, taskMinutes), extensionMinutes)
        val totalMillis = Math.multiplyExact((plannedMinutes + CEILING_BUFFER_MINUTES).toLong(), 60_000L)
        return Math.addExact(dismissedAtEpochMillis, totalMillis)
    }

    fun taskEndAtEpochMillis(startedAtEpochMillis: Long, durationMinutes: Int, ceilingAtEpochMillis: Long): Long =
        minOf(Math.addExact(startedAtEpochMillis, Math.multiplyExact(durationMinutes.toLong(), 60_000L)), ceilingAtEpochMillis)

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
