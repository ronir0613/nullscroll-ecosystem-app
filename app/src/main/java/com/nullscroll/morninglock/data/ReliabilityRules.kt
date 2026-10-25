package com.nullscroll.morninglock.data

internal data class ReliabilityAccess(
    val usageAccess: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenAlarm: Boolean,
    val overlay: Boolean,
    val screenPinning: Boolean,
    val batteryOptimizationExempt: Boolean
)

internal enum class AccessNeed { REQUIRED, OPTIONAL, DEVICE_SETTING }

internal data class AccessSummary(val label: String, val granted: Boolean, val need: AccessNeed)

internal object ReliabilityRules {
    fun summarize(access: ReliabilityAccess): List<AccessSummary> = listOf(
        AccessSummary("Usage access", access.usageAccess, AccessNeed.REQUIRED),
        AccessSummary("Notifications", access.notifications, AccessNeed.REQUIRED),
        AccessSummary("Exact alarms", access.exactAlarms, AccessNeed.REQUIRED),
        AccessSummary("Full-screen alarms", access.fullScreenAlarm, AccessNeed.OPTIONAL),
        AccessSummary("Overlay backstop", access.overlay, AccessNeed.OPTIONAL),
        AccessSummary("Screen pinning", access.screenPinning, AccessNeed.DEVICE_SETTING),
        AccessSummary("Battery optimization exemption", access.batteryOptimizationExempt, AccessNeed.OPTIONAL)
    )

    fun sessionCeilingUsesPermissionGrants(): Boolean = false
}
