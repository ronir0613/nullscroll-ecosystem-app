package com.nullscroll.morninglock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReliabilityRulesTest {
    @Test fun optionalSettingsAreNotMarkedRequired() {
        val rows = ReliabilityRules.summarize(ReliabilityAccess(false, false, false, false, false, false, false))
        assertEquals(3, rows.count { it.need == AccessNeed.REQUIRED })
        assertEquals(3, rows.count { it.need == AccessNeed.OPTIONAL })
        assertEquals(1, rows.count { it.need == AccessNeed.DEVICE_SETTING })
    }

    @Test fun missingUsageAccessDoesNotDisableSessionCeiling() {
        assertFalse(ReliabilityRules.sessionCeilingUsesPermissionGrants())
    }
}
