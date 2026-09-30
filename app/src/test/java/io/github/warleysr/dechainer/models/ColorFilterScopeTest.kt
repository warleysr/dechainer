package io.github.warleysr.dechainer.models

import io.github.warleysr.dechainer.models.ColorFilterScope.Coverage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorFilterScopeTest {
    private val apps = setOf("a", "b")

    @Test
    fun deviceAppliesEverywhere() {
        assertTrue(ColorFilterScope.DEVICE.appliesTo("x", apps, apps))
        assertTrue(ColorFilterScope.DEVICE.appliesTo(null, apps, apps))
    }

    @Test
    fun exceptAppsTurnsOffOnlyInExcludedApps() {
        assertFalse(ColorFilterScope.EXCEPT_APPS.appliesTo("a", apps, emptySet()))
        assertTrue(ColorFilterScope.EXCEPT_APPS.appliesTo("x", apps, emptySet()))
        assertTrue(ColorFilterScope.EXCEPT_APPS.appliesTo(null, apps, emptySet()))
    }

    @Test
    fun onlyAppsTurnsOnOnlyInListedApps() {
        assertTrue(ColorFilterScope.ONLY_APPS.appliesTo("a", emptySet(), apps))
        assertFalse(ColorFilterScope.ONLY_APPS.appliesTo("x", emptySet(), apps))
        assertFalse(ColorFilterScope.ONLY_APPS.appliesTo(null, emptySet(), apps))
    }

    @Test
    fun movingToDeviceNeverLoosens() {
        assertFalse(loosening(Coverage(ColorFilterScope.ONLY_APPS, emptySet(), apps), device))
        assertFalse(loosening(Coverage(ColorFilterScope.EXCEPT_APPS, apps, emptySet()), device))
    }

    @Test
    fun excludingAppsLoosensOnlyWhenSomethingNewIsExcluded() {
        assertFalse(loosening(device, Coverage(ColorFilterScope.EXCEPT_APPS, emptySet(), emptySet())))
        assertTrue(loosening(device, Coverage(ColorFilterScope.EXCEPT_APPS, apps, emptySet())))
        val excludingA = Coverage(ColorFilterScope.EXCEPT_APPS, setOf("a"), emptySet())
        assertFalse(loosening(Coverage(ColorFilterScope.EXCEPT_APPS, apps, emptySet()), excludingA))
        assertTrue(loosening(excludingA, Coverage(ColorFilterScope.EXCEPT_APPS, apps, emptySet())))
    }

    @Test
    fun onlyAppsLoosensWhenAnAppIsDropped() {
        assertTrue(loosening(device, Coverage(ColorFilterScope.ONLY_APPS, emptySet(), apps)))
        val onlyA = Coverage(ColorFilterScope.ONLY_APPS, emptySet(), setOf("a"))
        assertFalse(loosening(onlyA, Coverage(ColorFilterScope.ONLY_APPS, emptySet(), apps)))
        assertTrue(loosening(Coverage(ColorFilterScope.ONLY_APPS, emptySet(), apps), onlyA))
    }

    @Test
    fun onlyAppsToExceptAppsLoosensWhenAListedAppGetsExcluded() {
        val onlyA = Coverage(ColorFilterScope.ONLY_APPS, emptySet(), setOf("a"))
        assertFalse(loosening(onlyA, Coverage(ColorFilterScope.EXCEPT_APPS, setOf("b"), setOf("a"))))
        assertTrue(loosening(onlyA, Coverage(ColorFilterScope.EXCEPT_APPS, setOf("a"), setOf("a"))))
    }

    private val device = Coverage(ColorFilterScope.DEVICE, emptySet(), emptySet())

    private fun loosening(from: Coverage, to: Coverage) = ColorFilterScope.isLoosening(from, to)
}
