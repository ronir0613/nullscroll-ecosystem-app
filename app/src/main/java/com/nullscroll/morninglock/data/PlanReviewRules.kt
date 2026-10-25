package com.nullscroll.morninglock.data

internal data class PlanReviewActions(val primary: String, val secondary: String?)

internal object PlanReviewRules {
    fun actions(existing: Boolean, pending: Boolean, alarmScheduled: Boolean, alarmTimeChanged: Boolean): PlanReviewActions {
        if (!existing) return PlanReviewActions("LOCK PLAN & SET ALARM", "BACK")
        if (alarmTimeChanged) return PlanReviewActions("UPDATE ALARM TIME", "BACK TO HOME")
        if (!pending || alarmScheduled) return PlanReviewActions("BACK TO HOME", null)
        return PlanReviewActions("SCHEDULE ALARM", "BACK TO HOME")
    }
}
