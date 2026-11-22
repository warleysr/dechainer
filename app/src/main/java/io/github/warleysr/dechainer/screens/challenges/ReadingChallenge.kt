package io.github.warleysr.dechainer.screens.challenges

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.ReadingLibrary
import io.github.warleysr.dechainer.security.SecurityManager

// Pace of a slow, attentive reading (~110 words per minute for an average word): a fixed cost per
// word plus a bit per letter, so long words take longer, and pauses where the punctuation asks for one.
private const val WORD_BASE_MS = 300L
private const val LETTER_MS = 45L
private const val SHORT_PAUSE_MS = 250L
private const val LONG_PAUSE_MS = 500L
private const val LEAD_IN_MS = 600L

/** How long a word takes to fade from "being read" to "read" after its turn ends. */
private const val SETTLE_MS = 350f

private class TimedWord(val start: Int, val end: Int, val startMs: Long, val endMs: Long)

private class TimedText(val words: List<TimedWord>, val totalMs: Long)

private fun timeWords(text: String): TimedText {
    var cursor = LEAD_IN_MS
    val words = Regex("\\S+").findAll(text).map { match ->
        val token = match.value
        val letters = token.count { it.isLetterOrDigit() }
        val startMs = cursor
        val endMs = startMs + WORD_BASE_MS + letters * LETTER_MS
        cursor = endMs + when (token.last()) {
            '.', '!', '?' -> LONG_PAUSE_MS
            ',', ';', ':', '—', '-' -> SHORT_PAUSE_MS
            else -> 0L
        }
        TimedWord(match.range.first, match.range.last + 1, startMs, endMs)
    }.toList()
    return TimedText(words, cursor)
}

@Composable
fun ReadingChallenge(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val language = remember { SecurityManager.getReadingLanguage(context) }
    val rtl = language == SecurityManager.ReadingLanguage.ARABIC
    val passages = remember { ReadingLibrary.pick(context, language) }

    if (passages.isEmpty()) {
        // Only possible with "my phrases" and none written: nothing to read, so nothing to hold back.
        LaunchedEffect(Unit) { onSuccess() }
        return
    }

    var index by remember { mutableIntStateOf(0) }
    val passage = passages[index]
    val timed = remember(passage) { timeWords(passage.text) }
    val elapsed = remember(index) { Animatable(0f) }

    LaunchedEffect(index) {
        elapsed.animateTo(
            targetValue = timed.totalMs.toFloat(),
            animationSpec = tween(durationMillis = timed.totalMs.toInt(), easing = LinearEasing)
        )
    }

    val now = elapsed.value
    val finished = now >= timed.totalMs
    val unreadColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val readingColor = MaterialTheme.colorScheme.primary
    val readColor = MaterialTheme.colorScheme.onSurface

    val annotated = buildAnnotatedString {
        var last = 0
        timed.words.forEach { word ->
            append(passage.text.substring(last, word.start))
            val color = when {
                now <= word.startMs -> unreadColor
                now < word.endMs -> lerp(
                    unreadColor,
                    readingColor,
                    (now - word.startMs) / (word.endMs - word.startMs)
                )
                else -> lerp(readingColor, readColor, ((now - word.endMs) / SETTLE_MS).coerceIn(0f, 1f))
            }
            withStyle(SpanStyle(color = color)) {
                append(passage.text.substring(word.start, word.end))
            }
            last = word.end
        }
        append(passage.text.substring(last))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.reading_challenge_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${index + 1} / ${passages.size}", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    annotated,
                    style = MaterialTheme.typography.titleLarge.copy(
                        textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr
                    ),
                    fontFamily = FontFamily.Serif,
                    textAlign = if (rtl) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                if (passage.source.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        passage.source,
                        style = MaterialTheme.typography.labelLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { (now / timed.totalMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = finished,
            onClick = { if (index + 1 >= passages.size) onSuccess() else index++ },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                stringResource(
                    if (index + 1 >= passages.size) R.string.reading_finish else R.string.reading_next
                )
            )
        }
    }
}
