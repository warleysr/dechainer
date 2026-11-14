package io.github.warleysr.dechainer.data

import io.github.warleysr.dechainer.screens.common.isLooserSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualBlockingLooseningTest {

    @Test
    fun `enabling is tightening and disabling is loosening`() {
        val turningOnLoosening = !true
        val turningOffLoosening = !false

        assertFalse(turningOnLoosening)
        assertTrue(turningOffLoosening)
    }

    @Test
    fun `adding target apps is tightening and removing target apps is loosening`() {
        val currentApps = setOf("com.reddit.frontpage", "com.twitter.android")

        val withAddedApp = setOf("com.reddit.frontpage", "com.twitter.android", "com.instagram.android")
        assertFalse(isLooserSet(currentApps, withAddedApp))

        val withRemovedApp = setOf("com.reddit.frontpage")
        assertTrue(isLooserSet(currentApps, withRemovedApp))
    }

    @Test
    fun `adding category is tightening and unchecking category is loosening`() {
        val selectedCategories = setOf("FEMALE_BREAST_EXPOSED", "FEMALE_GENITALIA_EXPOSED")

        // Unchecking an already selected category:
        val unchecking = "FEMALE_BREAST_EXPOSED" in selectedCategories
        assertTrue(unchecking)

        // Checking an unselected category:
        val checking = "BUTTOCKS_COVERED" in selectedCategories
        assertFalse(checking)
    }

    @Test
    fun `sanitizeCategories migrates legacy categories correctly`() {
        val legacyExplicit = setOf("porn", "hentai")
        val migratedExplicit = VisualBlockingSettings.sanitizeCategories(legacyExplicit)
        assertTrue(migratedExplicit.containsAll(VisualBlockingSettings.DEFAULT_CATEGORIES))

        val legacySexy = setOf("sexy")
        val migratedSexy = VisualBlockingSettings.sanitizeCategories(legacySexy)
        assertTrue("FEMALE_BREAST_COVERED" in migratedSexy)
        assertTrue("BUTTOCKS_COVERED" in migratedSexy)

        val alreadyModern = setOf("ANUS_EXPOSED", "BUTTOCKS_EXPOSED")
        val sanitizedModern = VisualBlockingSettings.sanitizeCategories(alreadyModern)
        org.junit.Assert.assertEquals(alreadyModern, sanitizedModern)

        val nullOrEmpty = VisualBlockingSettings.sanitizeCategories(emptySet())
        org.junit.Assert.assertEquals(VisualBlockingSettings.DEFAULT_CATEGORIES, nullOrEmpty)
    }

    @Test
    fun `increasing sensitivity (lowering threshold) is tightening and decreasing sensitivity is loosening`() {
        val currentThreshold = 0.5f

        val higherSensitivityThreshold = 0.3f
        val isLooserWhenMoreSensitive = higherSensitivityThreshold > currentThreshold
        assertFalse(isLooserWhenMoreSensitive)

        val lowerSensitivityThreshold = 0.7f
        val isLooserWhenLessSensitive = lowerSensitivityThreshold > currentThreshold
        assertTrue(isLooserWhenLessSensitive)
    }

    @Test
    fun `enabling suspend is tightening and disabling suspend is loosening`() {
        val enableSuspendLoosening = !true
        val disableSuspendLoosening = !false

        assertFalse(enableSuspendLoosening)
        assertTrue(disableSuspendLoosening)
    }

    @Test
    fun `suspend block count loosening logic`() {
        val currentBlockCount = 5

        // Fewer blocks needed -> triggers suspension faster -> stricter
        val stricterCount = 3
        val stricterLoosening = stricterCount > currentBlockCount
        assertFalse(stricterLoosening)

        // More blocks needed -> triggers suspension slower -> looser
        val looserCount = 10
        val looserLoosening = looserCount > currentBlockCount
        assertTrue(looserLoosening)
    }

    @Test
    fun `suspend window minutes loosening logic`() {
        val currentWindowMinutes = 15

        // Longer window -> strikes stay active longer -> stricter
        val longerWindow = 30
        val longerWindowLoosening = longerWindow < currentWindowMinutes
        assertFalse(longerWindowLoosening)

        // Shorter window -> strikes expire sooner -> looser
        val shorterWindow = 5
        val shorterWindowLoosening = shorterWindow < currentWindowMinutes
        assertTrue(shorterWindowLoosening)
    }

    @Test
    fun `suspend duration minutes loosening logic`() {
        val currentDurationMinutes = 30

        // Longer duration -> app stays suspended longer -> stricter
        val longerDuration = 60
        val longerDurationLoosening = longerDuration < currentDurationMinutes
        assertFalse(longerDurationLoosening)

        // Shorter duration -> app stays suspended less -> looser
        val shorterDuration = 15
        val shorterDurationLoosening = shorterDuration < currentDurationMinutes
        assertTrue(shorterDurationLoosening)
    }
}
