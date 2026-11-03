package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechFormatsSpellTest {

    @Test
    fun decomposedVietnamese_isComposedBeforeSpelling() {
        assertEquals("V, i, \u1EB9, t", SpeechFormats.spell("Vie\u0323t"))
    }

    @Test
    fun emoji_isSpelledAsOneSymbol() {
        assertEquals("a, 😀", SpeechFormats.spell("a😀"))
    }

    @Test
    fun crLf_isSpokenAsOneNewline() {
        assertEquals("a, ${t(S.SP_NEWLINE)}, b", SpeechFormats.spell("a\r\nb"))
    }

    @Test
    fun truncation_countsVisibleCharacters_andNeverSplitsASurrogatePair() {
        val result = SpeechFormats.spell("😀".repeat(150))
        assertTrue(result.endsWith(t(S.SP_SPELL_MORE)))
        assertEquals(SpeechFormats.MAX_SPELL_CHARS, result.count { it == '\uD83D' })
        assertEquals(SpeechFormats.MAX_SPELL_CHARS, result.count { it == '\uDE00' })
    }
}
