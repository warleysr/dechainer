package io.github.warleysr.dechainer.viewmodels

import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.DechainerAccessibilityService.Companion.ActivityLog
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.installApp
import io.github.warleysr.dechainer.support.prefs
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Exercises [ActivityBlockerViewModel]'s own logic through [Fixtures]: a class name is added only once
 * and never when blank, a removal is persisted so a freshly built view model no longer loads it, and
 * getGroupedAccessedActivities groups the static access log by package, orders the groups by app label
 * rather than package name, and falls back to the raw package name when the package is not installed.
 */
@RunWith(AndroidJUnit4::class)
class ActivityBlockerViewModelTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val blockedClass = "com.example.socialapp.FeedActivity"
    private val otherClass = "com.example.socialapp.ProfileActivity"

    @Test
    fun `addBlockedActivity ignores a duplicate class name`() {
        val viewModel = Fixtures.activityBlockerViewModel()

        viewModel.addBlockedActivity(blockedClass)
        viewModel.addBlockedActivity(blockedClass)

        viewModel.blockedActivities shouldContainExactly listOf(blockedClass)
        prefs("activity_blocker_prefs").getStringSet("blocked_activities", emptySet()) shouldBe
            setOf(blockedClass)
    }

    @Test
    fun `addBlockedActivity ignores a blank class name`() {
        val viewModel = Fixtures.activityBlockerViewModel()

        viewModel.addBlockedActivity("")
        viewModel.addBlockedActivity("   ")

        viewModel.blockedActivities.shouldBeEmpty()
        prefs("activity_blocker_prefs").getStringSet("blocked_activities", emptySet()) shouldBe
            emptySet<String>()
    }

    @Test
    fun `removing a blocked activity persists across a reload`() {
        val viewModel = Fixtures.activityBlockerViewModel()
        viewModel.addBlockedActivity(blockedClass)
        viewModel.addBlockedActivity(otherClass)

        viewModel.removeBlockedActivity(blockedClass)

        prefs("activity_blocker_prefs").getStringSet("blocked_activities", emptySet()) shouldBe
            setOf(otherClass)
        Fixtures.activityBlockerViewModel().blockedActivities shouldContainExactly listOf(otherClass)
    }

    @Test
    fun `getGroupedAccessedActivities groups by package and sorts by app label`() {
        installApp("com.example.zeta", "Alpha App")
        installApp("com.example.alpha", "Zeta App")
        DechainerAccessibilityService.accessedActivities.add(ActivityLog("com.example.zeta", "zeta.Home"))
        DechainerAccessibilityService.accessedActivities.add(ActivityLog("com.example.zeta", "zeta.Detail"))
        DechainerAccessibilityService.accessedActivities.add(ActivityLog("com.example.alpha", "alpha.Main"))

        val grouped = Fixtures.activityBlockerViewModel().getGroupedAccessedActivities()

        grouped.map { it.appName } shouldContainExactly listOf("Alpha App", "Zeta App")
        grouped.first { it.packageName == "com.example.zeta" }.activities.size shouldBe 2
    }

    @Test
    fun `getGroupedAccessedActivities falls back to the package name for an uninstalled package`() {
        val ghost = "com.example.ghost"
        DechainerAccessibilityService.accessedActivities.add(ActivityLog(ghost, "ghost.Main"))

        val grouped = Fixtures.activityBlockerViewModel().getGroupedAccessedActivities()

        grouped.first { it.packageName == ghost }.appName shouldBe ghost
    }
}
