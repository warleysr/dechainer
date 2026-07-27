package io.github.warleysr.dechainer.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Exercises [LocaleUtils] on both sides of its `Build.VERSION.SDK_INT` fork by running under API 30 (the
 * AppCompatDelegate branch) and API 36 (the framework LocaleManager branch): a language set through
 * `setLocale` reads back identically through `getLocale`, and with no application locale set `getLocale`
 * falls back to the system default language instead of returning an empty tag.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30, 36])
class LocaleUtilsTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a language set through setLocale reads back through getLocale`() {
        LocaleUtils.setLocale(context, "es")

        LocaleUtils.getLocale(context) shouldBe "es"
    }

    @Test
    fun `getLocale falls back to the system language when no application locale is set`() {
        LocaleUtils.setLocale(context, "")

        LocaleUtils.getLocale(context) shouldBe Locale.getDefault().language
    }
}
