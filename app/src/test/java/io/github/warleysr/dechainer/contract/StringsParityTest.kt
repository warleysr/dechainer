package io.github.warleysr.dechainer.contract

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A pure-JVM test that parses the `values` and `values-pt` `strings.xml` files straight off disk and
 * checks the two locales stay in lockstep, so a translation that drops a key or garbles a format
 * argument is caught at build time instead of crashing only in the affected language. The key check
 * pins that both files declare the exact same set of names; the blank check pins that no value is
 * empty; the format check pins that every shared key carries the same multiset of `%` placeholders,
 * which is where a divergence would throw `IllegalFormatException` at runtime. Nothing here touches
 * the Android framework.
 */
class StringsParityTest {

    private val baseStrings = parseStrings("values")
    private val translatedStrings = parseStrings("values-pt")

    @Test
    fun `both locales declare the same string keys`() {
        translatedStrings.keys shouldBe baseStrings.keys
    }

    @Test
    fun `no string value is blank in either locale`() {
        baseStrings.filterValues { it.isBlank() }.keys shouldBe emptySet()
        translatedStrings.filterValues { it.isBlank() }.keys shouldBe emptySet()
    }

    @Test
    fun `format arguments match across locales for every shared key`() {
        val mismatches = baseStrings.keys.intersect(translatedStrings.keys)
            .mapNotNull { key ->
                val base = formatArgs(baseStrings.getValue(key))
                val translated = formatArgs(translatedStrings.getValue(key))
                if (base != translated) key to (base to translated) else null
            }
            .toMap()

        mismatches shouldBe emptyMap()
    }

    private fun formatArgs(value: String): List<String> =
        FORMAT_ARG.findAll(value).map { it.value }.sorted().toList()

    private fun parseStrings(qualifier: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resolveStringsFile(qualifier))
        val nodes = document.getElementsByTagName("string")

        val result = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            result[element.getAttribute("name")] = element.textContent
        }
        return result
    }

    private fun resolveStringsFile(qualifier: String): File {
        val relative = "src/main/res/$qualifier/strings.xml"
        val candidates = listOf(
            File(System.getProperty("user.dir"), relative),
            File(System.getProperty("user.dir"), "app/$relative"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("strings.xml not found for '$qualifier'; tried ${candidates.joinToString { it.absolutePath }}")
    }

    private companion object {
        private val FORMAT_ARG = Regex("%(\\d+\\$)?[a-zA-Z]")
    }
}
