package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitHubUpdaterPrefsTest {

    private lateinit var context: Context

    private fun release(version: String) = GitHubUpdater.GitHubRelease(
        version = version,
        tagName = "v$version",
        releaseNotes = "",
        apkUrl = "https://github.com/warleysr/dechainer/releases/download/v$version/app.apk",
        apkName = "app.apk",
        sizeBytes = 1024L,
        htmlUrl = "https://github.com/warleysr/dechainer/releases/tag/v$version"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("github_updater_prefs", Context.MODE_PRIVATE).edit { clear() }
    }

    @Test
    fun `shouldPromptForRelease respects ignored version and prompts again on newer release`() {
        val v15 = release("1.15.0")
        val v16 = release("1.16.0")

        assertTrue(GitHubUpdater.shouldPromptForRelease(context, v15))

        GitHubUpdater.ignoreVersion(context, "v1.15.0")
        assertFalse(GitHubUpdater.shouldPromptForRelease(context, v15))
        assertTrue(GitHubUpdater.shouldPromptForRelease(context, v16))
    }

    @Test
    fun `shouldPromptForRelease stops prompting when auto check is disabled`() {
        val v15 = release("1.15.0")

        GitHubUpdater.setAutoCheckEnabled(context, false)
        assertFalse(GitHubUpdater.shouldPromptForRelease(context, v15))

        GitHubUpdater.setAutoCheckEnabled(context, true)
        assertTrue(GitHubUpdater.shouldPromptForRelease(context, v15))
    }
}
