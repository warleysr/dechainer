package io.github.warleysr.dechainer.models

enum class ColorFilterScope {
    DEVICE,
    EXCEPT_APPS,
    ONLY_APPS;

    fun appliesTo(foregroundPackage: String?, excludedApps: Set<String>, onlyApps: Set<String>): Boolean =
        when (this) {
            DEVICE -> true
            EXCEPT_APPS -> foregroundPackage !in excludedApps
            ONLY_APPS -> foregroundPackage in onlyApps
        }

    companion object {
        /** Whether going from [from] to [to] leaves the mode off somewhere it used to apply. */
        fun isLoosening(from: Coverage, to: Coverage): Boolean = when (to.scope) {
            DEVICE -> false
            EXCEPT_APPS -> when (from.scope) {
                DEVICE -> to.excludedApps.isNotEmpty()
                EXCEPT_APPS -> !from.excludedApps.containsAll(to.excludedApps)
                ONLY_APPS -> from.onlyApps.any { it in to.excludedApps }
            }
            ONLY_APPS -> when (from.scope) {
                DEVICE, EXCEPT_APPS -> true
                ONLY_APPS -> !to.onlyApps.containsAll(from.onlyApps)
            }
        }
    }

    data class Coverage(val scope: ColorFilterScope, val excludedApps: Set<String>, val onlyApps: Set<String>)
}
