package com.nullscroll.morninglock.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CallCountingRulesTest {
    @Test
    fun successfulCloseOneDialerLaunchAddsOneToAnActiveSession() {
        assertEquals(
            4,
            CallCountingRules.nextCount(3, CallLaunchKind.CLOSE_ONE, sessionActive = true, launchSucceeded = true)
        )
    }

    @Test
    fun emergencyIncomingFailedOrEndedLaunchesDoNotIncrement() {
        assertEquals(3, CallCountingRules.nextCount(3, CallLaunchKind.EMERGENCY, true, true))
        assertEquals(3, CallCountingRules.nextCount(3, CallLaunchKind.INCOMING, true, true))
        assertEquals(3, CallCountingRules.nextCount(3, CallLaunchKind.CLOSE_ONE, true, false))
        assertEquals(3, CallCountingRules.nextCount(3, CallLaunchKind.CLOSE_ONE, false, true))
    }
}
