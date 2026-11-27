package io.github.warleysr.dechainer.screens.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `parseMarkdownBlocks parses headings, lists, quotes, code blocks, rules, and paragraphs`() {
        val md = """
            <!-- generated comment -->
            ## What's Changed
            
            * Added **updater** support
              - Nested item
            1. First step
            2. Second step
            
            > Important note
            
            ```kotlin
            val x = 1
            ```
            
            ---
            
            Final paragraph line 1
            Final paragraph line 2
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        blocks shouldBe listOf(
            MarkdownBlock.Heading(2, "What's Changed"),
            MarkdownBlock.BulletListItem("Added **updater** support", indentLevel = 0),
            MarkdownBlock.BulletListItem("Nested item", indentLevel = 1),
            MarkdownBlock.OrderedListItem("1", "First step", indentLevel = 0),
            MarkdownBlock.OrderedListItem("2", "Second step", indentLevel = 0),
            MarkdownBlock.Blockquote("Important note"),
            MarkdownBlock.CodeBlock("val x = 1"),
            MarkdownBlock.HorizontalRule,
            MarkdownBlock.Paragraph("Final paragraph line 1\nFinal paragraph line 2")
        )
    }

    @Test
    fun `parseMarkdownBlocks normalizes CRLF line endings`() {
        val md = "### Title\r\n- Item A\r\n- Item B"
        parseMarkdownBlocks(md) shouldBe listOf(
            MarkdownBlock.Heading(3, "Title"),
            MarkdownBlock.BulletListItem("Item A", indentLevel = 0),
            MarkdownBlock.BulletListItem("Item B", indentLevel = 0)
        )
    }

    @Test
    fun `buildInlineMarkdownAnnotatedString formats bold, italic, code, strikethrough, and links`() {
        val annotated = buildInlineMarkdownAnnotatedString(
            text = "Fix **crash** in *some_var_name* using `adb_shell` ~~old~~ [PR #1](https://github.com/a/b/pull/1) and https://example.com.",
            linkColor = Color.Blue,
            codeBackgroundColor = Color.LightGray
        )

        annotated.text shouldBe "Fix crash in some_var_name using adb_shell old PR #1 and https://example.com."

        val spans = annotated.spanStyles
        spans.any {
            it.item.fontWeight == FontWeight.Bold &&
                annotated.text.substring(it.start, it.end) == "crash"
        } shouldBe true

        spans.any {
            it.item.fontStyle == FontStyle.Italic &&
                annotated.text.substring(it.start, it.end) == "some_var_name"
        } shouldBe true

        spans.any {
            it.item.fontFamily == FontFamily.Monospace &&
                annotated.text.substring(it.start, it.end) == "adb_shell"
        } shouldBe true

        spans.any {
            it.item.textDecoration == TextDecoration.LineThrough &&
                annotated.text.substring(it.start, it.end) == "old"
        } shouldBe true

        val links = annotated.getLinkAnnotations(0, annotated.text.length)
        links shouldHaveSize 2
        (links[0].item as LinkAnnotation.Url).url shouldBe "https://github.com/a/b/pull/1"
        annotated.text.substring(links[0].start, links[0].end) shouldBe "PR #1"
        (links[1].item as LinkAnnotation.Url).url shouldBe "https://example.com"
        annotated.text.substring(links[1].start, links[1].end) shouldBe "https://example.com"
    }

    @Test
    fun `buildInlineMarkdownAnnotatedString does not italicize underscores inside identifiers`() {
        val annotated = buildInlineMarkdownAnnotatedString(
            text = "Call my_helper_func() now",
            linkColor = Color.Blue,
            codeBackgroundColor = Color.LightGray
        )

        annotated.text shouldBe "Call my_helper_func() now"
        annotated.spanStyles.none { it.item.fontStyle == FontStyle.Italic } shouldBe true
    }
}
