package com.nullscroll.morninglock.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakRulesTest {
    @Test
    fun onlyAProductionDayWithEveryTaskConfirmedCountsAsComplete() {
        val summary = StreakRules.summarize(
            listOf(
                record("2026-10-23", planned = 2, completed = 2),
                record("2026-10-24", planned = 2, completed = 1),
                record("2026-10-25", planned = 2, completed = 0, session = false)
            ),
            asOf = LocalDate.parse("2026-10-25")
        )

        assertEquals(DayCompletion.COMPLETE, summary.days.first { it.date == "2026-10-23" }.completion)
        assertEquals(DayCompletion.INCOMPLETE, summary.days.first { it.date == "2026-10-24" }.completion)
        assertEquals(DayCompletion.PLANNED, summary.days.first { it.date == "2026-10-25" }.completion)
        assertEquals(0, summary.currentStreak)
    }

    @Test
    fun missedDayBreaksCurrentRunWhileBestStreakIsPreserved() {
        val summary = StreakRules.summarize(
            listOf(
                record("2026-10-20", planned = 1, completed = 1),
                record("2026-10-21", planned = 1, completed = 1),
                record("2026-10-22", planned = 1, completed = 1),
                record("2026-10-24", planned = 1, completed = 1),
                record("2026-10-25", planned = 1, completed = 0)
            ),
            asOf = LocalDate.parse("2026-10-25")
        )

        assertEquals(3, summary.bestStreak)
        assertEquals(1, summary.currentStreak)
    }

    @Test
    fun temporaryPlansNeverCountAndDuplicateSessionsDoNotDoubleCountADay() {
        val summary = StreakRules.summarize(
            listOf(
                record("2026-10-24", planned = 2, completed = 1),
                record("2026-10-24", planned = 2, completed = 2, calls = 1),
                record("2026-10-25", planned = 2, completed = 2, temporary = true)
            ),
            asOf = LocalDate.parse("2026-10-25")
        )

        assertEquals(1, summary.days.size)
        assertEquals(DayCompletion.COMPLETE, summary.days.single().completion)
        assertEquals(1, summary.days.single().callLaunches)
        assertEquals(1, summary.currentStreak)
    }

    private fun record(
        date: String,
        planned: Int,
        completed: Int,
        session: Boolean = true,
        temporary: Boolean = false,
        calls: Int = 0,
        snoozes: Int = 0
    ) = StreakRecord(date, temporary, session, planned, completed, calls, snoozes)
}
