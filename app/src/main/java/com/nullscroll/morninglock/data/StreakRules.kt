package com.nullscroll.morninglock.data

import java.time.LocalDate

internal data class StreakRecord(
    val date: String,
    val isTemporaryTest: Boolean,
    val hasSession: Boolean,
    val plannedTasks: Int,
    val completedTasks: Int,
    val callLaunches: Int,
    val snoozes: Int
)

internal enum class DayCompletion { PLANNED, INCOMPLETE, COMPLETE }

internal data class StreakDay(
    val date: String,
    val completion: DayCompletion,
    val plannedTasks: Int,
    val completedTasks: Int,
    val callLaunches: Int,
    val snoozes: Int
)

internal data class StreakSummary(
    val days: List<StreakDay>,
    val currentStreak: Int,
    val bestStreak: Int
)

internal object StreakRules {
    fun summarize(records: List<StreakRecord>, asOf: LocalDate): StreakSummary {
        val days = records
            .filterNot { it.isTemporaryTest }
            .groupBy { LocalDate.parse(it.date) }
            .map { (date, sessions) ->
                val planned = sessions.maxOfOrNull { it.plannedTasks.coerceAtLeast(0) } ?: 0
                val completed = sessions.maxOfOrNull { it.completedTasks.coerceAtLeast(0) } ?: 0
                val fullSession = sessions.any { it.plannedTasks > 0 && it.completedTasks >= it.plannedTasks }
                val hasSession = sessions.any { it.hasSession }
                StreakDay(
                    date = date.toString(),
                    completion = when {
                        fullSession -> DayCompletion.COMPLETE
                        hasSession -> DayCompletion.INCOMPLETE
                        else -> DayCompletion.PLANNED
                    },
                    plannedTasks = planned,
                    completedTasks = completed.coerceAtMost(planned),
                    callLaunches = sessions.sumOf { it.callLaunches.coerceAtLeast(0) },
                    snoozes = sessions.maxOfOrNull { it.snoozes.coerceAtLeast(0) } ?: 0
                )
            }
            .sortedBy { it.date }

        val completeDates = days.asSequence()
            .filter { it.completion == DayCompletion.COMPLETE }
            .map { LocalDate.parse(it.date) }
            .filterNot { it.isAfter(asOf) }
            .toSet()
        var best = 0
        var running = 0
        var previous: LocalDate? = null
        completeDates.sorted().forEach { date ->
            running = if (previous?.plusDays(1) == date) running + 1 else 1
            best = maxOf(best, running)
            previous = date
        }

        var cursor = if (asOf in completeDates) asOf else asOf.minusDays(1)
        var current = 0
        while (cursor in completeDates) {
            current++
            cursor = cursor.minusDays(1)
        }
        return StreakSummary(days, current, best)
    }
}
