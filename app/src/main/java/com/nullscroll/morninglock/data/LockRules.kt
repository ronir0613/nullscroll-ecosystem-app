package com.nullscroll.morninglock.data

internal object LockRules {
    fun isAllowed(
        foregroundPackage: String,
        ownPackage: String,
        safetyPackages: Set<String>,
        callPackages: Set<String>
    ): Boolean = foregroundPackage == ownPackage ||
        foregroundPackage in safetyPackages ||
        foregroundPackage in callPackages
}
