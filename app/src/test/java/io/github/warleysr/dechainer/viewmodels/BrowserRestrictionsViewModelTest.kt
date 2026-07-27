package io.github.warleysr.dechainer.viewmodels

import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.prefs
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Exercises [BrowserRestrictionsViewModel]'s blocked-list persistence through [Fixtures]: saved lists
 * survive a round trip through the `blocked_lists_json` prefs into a freshly built view model with their
 * sites trimmed and blank lines dropped, a malformed json leaves the list empty instead of crashing on
 * construction, saving with an existing id updates that list in place, and saving with a null id appends
 * a new list rather than replacing one.
 */
@RunWith(AndroidJUnit4::class)
class BrowserRestrictionsViewModelTest {

    @get:Rule
    val rule = DechainerTestRule()

    @Test
    fun `saved lists round-trip through the JSON into a fresh view model`() {
        val viewModel = Fixtures.browserRestrictionsViewModel()
        viewModel.saveOrUpdateList("list-1", "Adult", "a.com\n  b.com  \n\n")
        viewModel.saveOrUpdateList("list-2", "Gambling", "c.com")

        prefs("browser_prefs").getString("blocked_lists_json", null) shouldNotBe null

        Fixtures.browserRestrictionsViewModel().blockedLists shouldContainExactly listOf(
            BlockedList("list-1", "Adult", listOf("a.com", "b.com")),
            BlockedList("list-2", "Gambling", listOf("c.com"))
        )
    }

    @Test
    fun `a malformed blocked_lists_json leaves the list empty without crashing`() {
        prefs("browser_prefs").edit().putString("blocked_lists_json", "{ not valid json ]").commit()

        lateinit var viewModel: BrowserRestrictionsViewModel
        shouldNotThrowAny { viewModel = Fixtures.browserRestrictionsViewModel() }

        viewModel.blockedLists.shouldBeEmpty()
    }

    @Test
    fun `saveOrUpdateList with an existing id updates the list in place`() {
        val viewModel = Fixtures.browserRestrictionsViewModel()
        viewModel.saveOrUpdateList("list-1", "Adult", "a.com")

        viewModel.saveOrUpdateList("list-1", "Adult v2", "a.com\nb.com")

        viewModel.blockedLists shouldContainExactly listOf(
            BlockedList("list-1", "Adult v2", listOf("a.com", "b.com"))
        )
    }

    @Test
    fun `saveOrUpdateList with a null id appends a new list`() {
        val viewModel = Fixtures.browserRestrictionsViewModel()
        viewModel.saveOrUpdateList("list-1", "Adult", "a.com")

        viewModel.saveOrUpdateList(null, "Gambling", "c.com")

        viewModel.blockedLists.size shouldBe 2
        viewModel.blockedLists.map { it.id } shouldContainExactly
            viewModel.blockedLists.map { it.id }.distinct()
        viewModel.blockedLists[1].id shouldNotBe "list-1"
    }
}
