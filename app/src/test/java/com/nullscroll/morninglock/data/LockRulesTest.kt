package com.nullscroll.morninglock.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {
    @Test
    fun ownSafetyAndInCallPackagesAreAllowed() {
        assertTrue(LockRules.isAllowed("app", "app", setOf("health"), setOf("dialer")))
        assertTrue(LockRules.isAllowed("health", "app", setOf("health"), setOf("dialer")))
        assertTrue(LockRules.isAllowed("dialer", "app", emptySet(), setOf("dialer")))
    }

    @Test
    fun unknownForegroundPackageIsReturnedToLock() {
        assertFalse(LockRules.isAllowed("social", "app", setOf("health"), setOf("dialer")))
    }
}
