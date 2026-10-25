package com.nullscroll.morninglock.data

internal enum class CallLaunchKind { CLOSE_ONE, EMERGENCY, INCOMING }

internal object CallCountingRules {
    fun nextCount(
        currentCount: Int,
        kind: CallLaunchKind,
        sessionActive: Boolean,
        launchSucceeded: Boolean
    ): Int = if (
        currentCount >= 0 &&
        kind == CallLaunchKind.CLOSE_ONE &&
        sessionActive &&
        launchSucceeded
    ) Math.addExact(currentCount, 1) else currentCount
}
