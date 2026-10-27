package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.models.AppGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocusModeTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        FocusMode.setApps(context, emptySet())
        FocusMode.setGroupIds(context, emptySet())
    }

    @Test
    fun `getAllTargetApps returns union of individual apps and groups`() {
        // Create groups
        val group1 = AppGroupRepository.createGroup("Social")
        AppGroupRepository.setGroupPackages(group1.id, setOf("com.instagram.android", "com.twitter.android"))

        val group2 = AppGroupRepository.createGroup("Games")
        AppGroupRepository.setGroupPackages(group2.id, setOf("com.supercell.clashroyale"))

        // Set individual apps
        FocusMode.setApps(context, setOf("com.reddit.frontpage", "com.instagram.android"))

        // Set selected group: only group 1
        FocusMode.setGroupIds(context, setOf(group1.id))

        val targets = FocusMode.getAllTargetApps(context)

        // Should contain individual apps + group 1 apps, with deduplication
        assertEquals(3, targets.size)
        assertTrue(targets.contains("com.instagram.android"))
        assertTrue(targets.contains("com.twitter.android"))
        assertTrue(targets.contains("com.reddit.frontpage"))

        // Add group 2
        FocusMode.setGroupIds(context, setOf(group1.id, group2.id))
        val targetsWithGroup2 = FocusMode.getAllTargetApps(context)
        assertEquals(4, targetsWithGroup2.size)
        assertTrue(targetsWithGroup2.contains("com.supercell.clashroyale"))

        // Remove group 1
        FocusMode.setGroupIds(context, setOf(group2.id))
        val targetsWithoutGroup1 = FocusMode.getAllTargetApps(context)
        assertEquals(3, targetsWithoutGroup1.size)
        assertTrue(targetsWithoutGroup1.contains("com.instagram.android")) // still in individual apps
        assertTrue(targetsWithoutGroup1.contains("com.reddit.frontpage"))
        assertTrue(targetsWithoutGroup1.contains("com.supercell.clashroyale"))
    }
}
