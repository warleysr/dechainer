package io.github.warleysr.dechainer.screens.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingChangeTest {

    @Test
    fun `isLooserSet returns false when adding new elements or keeping same`() {
        val oldSet = setOf("app1", "app2")
        val newSetWithMore = setOf("app1", "app2", "app3")
        val sameSet = setOf("app1", "app2")

        assertFalse(isLooserSet(oldSet, newSetWithMore))
        assertFalse(isLooserSet(oldSet, sameSet))
    }

    @Test
    fun `isLooserSet returns true when any element is removed`() {
        val oldSet = setOf("app1", "app2", "app3")
        val newSetWithFewer = setOf("app1", "app2")
        val completelyDifferent = setOf("app4", "app5")

        assertTrue(isLooserSet(oldSet, newSetWithFewer))
        assertTrue(isLooserSet(oldSet, completelyDifferent))
    }
}
