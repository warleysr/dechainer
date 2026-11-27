package io.github.warleysr.dechainer.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletListItem(val text: String, val indentLevel: Int = 0) : MarkdownBlock
    data class OrderedListItem(val number: String, val text: String, val indentLevel: Int = 0) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}

private val HTML_COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+?)\\s*#*$")
private val BULLET_REGEX = Regex("^(\\s*)[-*+]\\s+(.+)$")
private val ORDERED_REGEX = Regex("^(\\s*)(\\d+)[.)]\\s+(.+)$")
private val HR_REGEX = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val FENCE_REGEX = Regex("^\\s*(```|~~~).*$")
private val BARE_URL_REGEX = Regex("^https?://[^\\s<>()]+")

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val cleaned = markdown
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(HTML_COMMENT_REGEX, "")

    val lines = cleaned.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        if (FENCE_REGEX.matches(line)) {
            val fence = trimmed.take(3)
            i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n")))
            continue
        }

        if (HR_REGEX.matches(line)) {
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        HEADING_REGEX.matchEntire(trimmed)?.let { match ->
            blocks.add(
                MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    text = match.groupValues[2].trim()
                )
            )
            i++
            continue
        }

        BULLET_REGEX.matchEntire(line)?.let { match ->
            val spaces = match.groupValues[1].replace("\t", "  ").length
            blocks.add(
                MarkdownBlock.BulletListItem(
                    text = match.groupValues[2].trim(),
                    indentLevel = (spaces / 2).coerceIn(0, 3)
                )
            )
            i++
            continue
        }

        ORDERED_REGEX.matchEntire(line)?.let { match ->
            val spaces = match.groupValues[1].replace("\t", "  ").length
            blocks.add(
                MarkdownBlock.OrderedListItem(
                    number = match.groupValues[2],
                    text = match.groupValues[3].trim(),
                    indentLevel = (spaces / 2).coerceIn(0, 3)
                )
            )
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").removePrefix(" "))
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n").trim()))
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (i < lines.size) {
            val current = lines[i]
            val currentTrimmed = current.trim()
            if (currentTrimmed.isEmpty() ||
                FENCE_REGEX.matches(current) ||
                HR_REGEX.matches(current) ||
                HEADING_REGEX.matches(currentTrimmed) ||
                BULLET_REGEX.matches(current) ||
                ORDERED_REGEX.matches(current) ||
                currentTrimmed.startsWith(">")
            ) {
                break
            }
            paragraphLines.add(currentTrimmed)
            i++
        }
        if (paragraphLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
        }
    }

    return blocks
}

internal fun buildInlineMarkdownAnnotatedString(
    text: String,
    linkColor: Color,
    codeBackgroundColor: Color
): AnnotatedString = buildAnnotatedString {
    appendInlineMarkdown(
        text = text,
        start = 0,
        end = text.length,
        linkColor = linkColor,
        codeBackgroundColor = codeBackgroundColor
    )
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    start: Int,
    end: Int,
    linkColor: Color,
    codeBackgroundColor: Color
) {
    var i = start
    while (i < end) {
        val ch = text[i]

        // Backslash escape for markdown punctuation
        if (ch == '\\' && i + 1 < end && text[i + 1] in "\\`*_{}[]()#+-.!~>") {
            append(text[i + 1])
            i += 2
            continue
        }

        // Inline code: `code`
        if (ch == '`') {
            val closeIdx = text.indexOf('`', i + 1)
            if (closeIdx in (i + 1) until end) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackgroundColor
                    )
                ) {
                    append(text.substring(i + 1, closeIdx))
                }
                i = closeIdx + 1
                continue
            }
        }

        // Markdown link: [label](url)
        if (ch == '[') {
            val linkMatch = matchMarkdownLink(text, i, end)
            if (linkMatch != null) {
                val (labelStart, labelEnd, url, nextIndex) = linkMatch
                val linkStyle = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                )
                withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle))) {
                    appendInlineMarkdown(text, labelStart, labelEnd, linkColor, codeBackgroundColor)
                }
                i = nextIndex
                continue
            }
        }

        // Bare URL: https://... or http://...
        if (ch == 'h' && (text.startsWith("https://", i) || text.startsWith("http://", i))) {
            val urlMatch = BARE_URL_REGEX.find(text.substring(i, end))
            if (urlMatch != null) {
                var url = urlMatch.value
                while (url.isNotEmpty() && url.last() in ".,;:!?)") {
                    url = url.dropLast(1)
                }
                if (url.length > "https://".length) {
                    val linkStyle = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle))) {
                        append(url)
                    }
                    i += url.length
                    continue
                }
            }
        }

        // Bold + Italic: ***text*** or ___text___
        if ((text.startsWith("***", i) || text.startsWith("___", i)) && i + 3 < end) {
            val delimiter = text.substring(i, i + 3)
            val closeIdx = findClosingDelimiter(text, delimiter, i + 3, end)
            if (closeIdx != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    appendInlineMarkdown(text, i + 3, closeIdx, linkColor, codeBackgroundColor)
                }
                i = closeIdx + 3
                continue
            }
        }

        // Bold: **text** or __text__
        if ((text.startsWith("**", i) || text.startsWith("__", i)) && i + 2 < end) {
            val delimiter = text.substring(i, i + 2)
            val closeIdx = findClosingDelimiter(text, delimiter, i + 2, end)
            if (closeIdx != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineMarkdown(text, i + 2, closeIdx, linkColor, codeBackgroundColor)
                }
                i = closeIdx + 2
                continue
            }
        }

        // Strikethrough: ~~text~~
        if (text.startsWith("~~", i) && i + 2 < end) {
            val closeIdx = findClosingDelimiter(text, "~~", i + 2, end)
            if (closeIdx != -1) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInlineMarkdown(text, i + 2, closeIdx, linkColor, codeBackgroundColor)
                }
                i = closeIdx + 2
                continue
            }
        }

        // Italic: *text* or _text_
        if ((ch == '*' || ch == '_') && i + 1 < end && !text[i + 1].isWhitespace()) {
            val canOpen = ch == '*' || i == start || !text[i - 1].isLetterOrDigit()
            if (canOpen) {
                val closeIdx = findClosingSingleItalic(text, ch, i + 1, end)
                if (closeIdx != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(text, i + 1, closeIdx, linkColor, codeBackgroundColor)
                    }
                    i = closeIdx + 1
                    continue
                }
            }
        }

        append(ch)
        i++
    }
}

private data class LinkMatch(
    val labelStart: Int,
    val labelEnd: Int,
    val url: String,
    val nextIndex: Int
)

private fun matchMarkdownLink(text: String, start: Int, end: Int): LinkMatch? {
    val closeBracket = text.indexOf(']', start + 1)
    if (closeBracket == -1 || closeBracket + 1 >= end || text[closeBracket + 1] != '(') {
        return null
    }
    val closeParen = text.indexOf(')', closeBracket + 2)
    if (closeParen == -1 || closeParen >= end) return null
    val url = text.substring(closeBracket + 2, closeParen).trim()
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    return LinkMatch(
        labelStart = start + 1,
        labelEnd = closeBracket,
        url = url,
        nextIndex = closeParen + 1
    )
}

private fun findClosingDelimiter(text: String, delimiter: String, from: Int, end: Int): Int {
    var idx = text.indexOf(delimiter, from)
    while (idx != -1 && idx + delimiter.length <= end) {
        if (idx > from && text[idx - 1] != '\\') {
            return idx
        }
        idx = text.indexOf(delimiter, idx + 1)
    }
    return -1
}

private fun findClosingSingleItalic(text: String, delimiter: Char, from: Int, end: Int): Int {
    var idx = from
    while (idx < end) {
        if (text[idx] == '\\') {
            idx += 2
            continue
        }
        if (text[idx] == delimiter && !text[idx - 1].isWhitespace()) {
            val isDouble = idx + 1 < end && text[idx + 1] == delimiter
            val canClose = delimiter == '*' || idx + 1 == end || !text[idx + 1].isLetterOrDigit()
            if (!isDouble && canClose) {
                return idx
            }
        }
        idx++
    }
    return -1
}

/** Renders GitHub-flavored Markdown text (headings, lists, links, inline styles, code, quotes). */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    }
                    if (index > 0) Spacer(Modifier.height(2.dp))
                    Text(
                        text = remember(block.text, linkColor, codeBackgroundColor) {
                            buildInlineMarkdownAnnotatedString(block.text, linkColor, codeBackgroundColor)
                        },
                        style = headingStyle
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = remember(block.text, linkColor, codeBackgroundColor) {
                            buildInlineMarkdownAnnotatedString(block.text, linkColor, codeBackgroundColor)
                        },
                        style = baseStyle
                    )
                }

                is MarkdownBlock.BulletListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 12).dp)
                    ) {
                        Text(
                            text = if (block.indentLevel == 0) "•" else "◦",
                            style = baseStyle
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = remember(block.text, linkColor, codeBackgroundColor) {
                                buildInlineMarkdownAnnotatedString(block.text, linkColor, codeBackgroundColor)
                            },
                            style = baseStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.OrderedListItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 12).dp)
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = baseStyle
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = remember(block.text, linkColor, codeBackgroundColor) {
                                buildInlineMarkdownAnnotatedString(block.text, linkColor, codeBackgroundColor)
                            },
                            style = baseStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = remember(block.text, linkColor, codeBackgroundColor) {
                                buildInlineMarkdownAnnotatedString(block.text, linkColor, codeBackgroundColor)
                            },
                            style = baseStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        color = codeBackgroundColor,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.code,
                            style = baseStyle.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
