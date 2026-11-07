package io.github.warleysr.dechainer.screens.common

import android.os.Bundle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `isLooserRestrictions detects boolean loosening`() {
        val current = Bundle().apply {
            putBoolean("key1", true)
            putBoolean("key2", false)
        }
        val loosened = Bundle().apply {
            putBoolean("key1", false)
            putBoolean("key2", false)
        }
        val tightened = Bundle().apply {
            putBoolean("key1", true)
            putBoolean("key2", true)
        }
        val same = Bundle().apply {
            putBoolean("key1", true)
            putBoolean("key2", false)
        }

        assertTrue(isLooserRestrictions(current, loosened))
        assertFalse(isLooserRestrictions(current, tightened))
        assertFalse(isLooserRestrictions(current, same))
    }

    @Test
    fun `isLooserRestrictions detects string and array loosening`() {
        val current = Bundle().apply {
            putString("strKey", "some_value")
            putStringArray("arrKey", arrayOf("rule1", "rule2"))
        }
        val loosenedStr = Bundle().apply {
            putString("strKey", "")
            putStringArray("arrKey", arrayOf("rule1", "rule2"))
        }
        val loosenedArr = Bundle().apply {
            putString("strKey", "some_value")
            putStringArray("arrKey", arrayOf("rule1"))
        }
        val tightened = Bundle().apply {
            putString("strKey", "some_value")
            putStringArray("arrKey", arrayOf("rule1", "rule2", "rule3"))
        }

        assertTrue(isLooserRestrictions(current, loosenedStr))
        assertTrue(isLooserRestrictions(current, loosenedArr))
        assertFalse(isLooserRestrictions(current, tightened))
    }

    @Test
    fun `isLooserRestrictions detects URLBlocklist loosening`() {
        val current = Bundle().apply {
            putStringArray("URLBlocklist", arrayOf("pornhub.com", "xvideos.com"))
        }
        val loosened = Bundle().apply {
            putStringArray("URLBlocklist", arrayOf("pornhub.com"))
        }
        val tightened = Bundle().apply {
            putStringArray("URLBlocklist", arrayOf("pornhub.com", "xvideos.com", "gambling.com"))
        }

        assertTrue(isLooserRestrictions(current, loosened))
        assertFalse(isLooserRestrictions(current, tightened))
    }
}
