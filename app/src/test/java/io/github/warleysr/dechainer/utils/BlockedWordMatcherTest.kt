package io.github.warleysr.dechainer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedWordMatcherTest {

    @Test
    fun `matches exact word`() {
        val regex = BlockedWordMatcher.buildRegex("buceta")
        assertTrue(regex.containsMatchIn("buceta"))
        assertTrue(regex.containsMatchIn("olha essa buceta"))
    }

    @Test
    fun `matches word with spaces between characters`() {
        val regex = BlockedWordMatcher.buildRegex("buceta")
        assertTrue(regex.containsMatchIn("b uceta"))
        assertTrue(regex.containsMatchIn("b u c e t a"))
        assertTrue(regex.containsMatchIn("bu ceta"))
        assertTrue(regex.containsMatchIn("olha essa b uceta aqui"))
    }

    @Test
    fun `matches word with punctuation or symbols between characters`() {
        val regex = BlockedWordMatcher.buildRegex("buceta")
        assertTrue(regex.containsMatchIn("b.u.c.e.t.a"))
        assertTrue(regex.containsMatchIn("b-u-c-e-t-a"))
        assertTrue(regex.containsMatchIn("b_uceta"))
        assertTrue(regex.containsMatchIn("b*uceta"))
    }

    @Test
    fun `matches leetspeak and number substitutions`() {
        val regexCuzinho = BlockedWordMatcher.buildRegex("cuzinho")
        assertTrue(regexCuzinho.containsMatchIn("cuz1nho"))
        assertTrue(regexCuzinho.containsMatchIn("c u z 1 n h o"))
        assertTrue(regexCuzinho.containsMatchIn("cuz!nho"))
        assertTrue(regexCuzinho.containsMatchIn("cus1nho"))
        assertTrue(regexCuzinho.containsMatchIn("olha esse cuz1nho agora"))

        val regexBuceta = BlockedWordMatcher.buildRegex("buceta")
        assertTrue(regexBuceta.containsMatchIn("buc3t4"))
        assertTrue(regexBuceta.containsMatchIn("b u c 3 t @"))
    }

    @Test
    fun `matches repeated characters`() {
        val regex = BlockedWordMatcher.buildRegex("buceta")
        assertTrue(regex.containsMatchIn("buuuuceta"))
        assertTrue(regex.containsMatchIn("buceeeeta"))
    }

    @Test
    fun `respects word boundaries and avoids false positives on safe compound words`() {
        val regexCu = BlockedWordMatcher.buildRegex("cu")
        assertTrue(regexCu.containsMatchIn("cu"))
        assertTrue(regexCu.containsMatchIn("c u"))
        assertTrue(regexCu.containsMatchIn("c.u"))
        assertTrue(regexCu.containsMatchIn("vai tomar no c u"))

        // False positives that MUST NOT match:
        assertFalse(regexCu.containsMatchIn("escudo"))
        assertFalse(regexCu.containsMatchIn("documento"))
        assertFalse(regexCu.containsMatchIn("curioso"))
        assertFalse(regexCu.containsMatchIn("acumulador"))
        assertFalse(regexCu.containsMatchIn("circuito"))
    }

    @Test
    fun `matches multi-word phrases with obfuscation`() {
        val regex = BlockedWordMatcher.buildRegex("fotos intimas")
        assertTrue(regex.containsMatchIn("fotos intimas"))
        assertTrue(regex.containsMatchIn("f0t0s 1nt1m4s"))
        assertTrue(regex.containsMatchIn("f o t o s   i n t i m a s"))
    }
}
