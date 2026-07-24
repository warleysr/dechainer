package io.github.warleysr.dechainer.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.activities.BlockedWordActivity
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.awaitUntil
import io.github.warleysr.dechainer.support.startBlockingService
import io.github.warleysr.dechainer.support.startedActivity
import io.github.warleysr.dechainer.support.textChangedEvent
import io.github.warleysr.dechainer.support.windowStateEvent
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vertical-slice tests for active (typed) word blocking — invariants R2 (blocking always happens)
 * and R1 (the persistence contract).
 *
 * Each test writes through the real [io.github.warleysr.dechainer.viewmodels.BlockedWordsViewModel]
 * and reads through the real [io.github.warleysr.dechainer.DechainerAccessibilityService], so the
 * only thing under test is the end-to-end behaviour.
 * ViewModel persists the words and target packages, and the service — driven by real accessibility
 * events — launches [BlockedWordActivity] with the offending word. Nothing here reaches into class
 * internals.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWordBlockingPipelineTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"

    @Test
    fun `a forbidden word typed in a target app opens the block screen with that word`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updateWords("kumar\nbahis")
        viewModel.toggleAppSelection(targetPackage)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, "android.widget.FrameLayout"))
        service.onAccessibilityEvent(textChangedEvent("bugün kumar oynadım"))

        awaitUntil { service.startedActivity() != null }

        val intent = service.startedActivity().shouldNotBeNull()
        intent.component?.className shouldBe BlockedWordActivity::class.java.name
        intent.getStringExtra("word") shouldBe "kumar"
    }

    @Test
    fun `the same word typed in a non-target app is not blocked`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updateWords("kumar")
        viewModel.toggleAppSelection(targetPackage)

        val service = startBlockingService()
        val untargetedPackage = "com.example.other"
        service.onAccessibilityEvent(
            windowStateEvent(untargetedPackage, "android.widget.FrameLayout")
        )
        service.onAccessibilityEvent(textChangedEvent("bugün kumar oynadım"))

        service.startedActivity().shouldBeNull()
    }

    @Test
    fun `a forbidden word embedded in a longer word is not blocked`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updateWords("kumar")
        viewModel.toggleAppSelection(targetPackage)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, "android.widget.FrameLayout"))
        service.onAccessibilityEvent(textChangedEvent("bugün kumarhane açıldı"))

        service.startedActivity().shouldBeNull()
    }

    @Test
    fun `an uppercase occurrence of a lowercase forbidden word is blocked`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updateWords("kumar")
        viewModel.toggleAppSelection(targetPackage)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, "android.widget.FrameLayout"))
        service.onAccessibilityEvent(textChangedEvent("BUGÜN KUMAR OYNADIM"))

        awaitUntil { service.startedActivity() != null }

        val intent = service.startedActivity().shouldNotBeNull()
        intent.getStringExtra("word") shouldBe "kumar"
    }

    @Test
    fun `a word containing regex metacharacters matches literally without crashing`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updateWords("c++")
        viewModel.toggleAppSelection(targetPackage)

        val service = startBlockingService()
        service.onAccessibilityEvent(windowStateEvent(targetPackage, "android.widget.FrameLayout"))
        service.onAccessibilityEvent(textChangedEvent("i love c++ a lot"))

        awaitUntil { service.startedActivity() != null }

        val intent = service.startedActivity().shouldNotBeNull()
        intent.getStringExtra("word") shouldBe "c++"
    }
}
