package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunkerTest {

    private fun List<String>.compact() = joinToString("").filterNot { it.isWhitespace() }

    @Test
    fun blank_givesNoChunks() {
        assertTrue(SpeechChunker.split("", 10).isEmpty())
        assertTrue(SpeechChunker.split("   \n ", 10).isEmpty())
    }

    @Test
    fun shortText_isReturnedAsIs() {
        assertEquals(listOf("Xin chào"), SpeechChunker.split("  Xin chào ", 100))
    }

    @Test
    fun textExactlyAtLimit_isNotSplit() {
        val text = "a".repeat(4000)
        assertEquals(1, SpeechChunker.split(text, 4000).size)
    }

    @Test
    fun longText_neverProducesChunkAboveLimit_andLosesNothing() {
        val text = (1..800).joinToString(" ") { "từ$it" } // ~5000 ký tự, vượt 4000 của TTS
        val chunks = SpeechChunker.split(text, 4000)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 4000 && it.isNotBlank() })
        assertEquals(text.filterNot { it.isWhitespace() }, chunks.compact())
    }

    @Test
    fun prefersSentenceBoundary() {
        val text = "Câu thứ nhất khá dài. Câu thứ hai cũng dài. Câu thứ ba."
        val chunks = SpeechChunker.split(text, 30)
        assertEquals("Câu thứ nhất khá dài.", chunks.first())
        assertTrue(chunks.all { it.length <= 30 })
    }

    @Test
    fun doesNotSplitInsideDecimalNumber() {
        // "3.14" không được coi là hết câu: sau dấu chấm không phải khoảng trắng.
        val chunks = SpeechChunker.split("aaaaaaaa 3.14159 bbbbbbbb", 14)
        assertTrue(chunks.none { it.endsWith("3.") })
        assertEquals("aaaaaaaa3.14159bbbbbbbb", chunks.compact())
    }

    @Test
    fun fallsBackToWhitespace() {
        val chunks = SpeechChunker.split("alpha beta gamma delta epsilon", 12)
        assertTrue(chunks.all { it.length <= 12 })
        assertEquals("alphabetagammadeltaepsilon", chunks.compact())
        assertTrue(chunks.none { it.startsWith(" ") || it.endsWith(" ") })
    }

    @Test
    fun hardCut_whenNoSeparatorAtAll() {
        val chunks = SpeechChunker.split("x".repeat(25), 10)
        assertEquals(listOf(10, 10, 5), chunks.map { it.length })
    }

    @Test
    fun hardCut_neverSplitsSurrogatePair() {
        val emoji = "😀" // 😀 = 2 đơn vị UTF-16
        val chunks = SpeechChunker.split(emoji.repeat(20), 5)
        chunks.forEach { chunk ->
            assertFalse("chunk kết thúc bằng high surrogate lẻ", Character.isHighSurrogate(chunk.last()))
            assertFalse("chunk bắt đầu bằng low surrogate lẻ", Character.isLowSurrogate(chunk.first()))
            assertTrue(chunk.length <= 5)
        }
        assertEquals(emoji.repeat(20), chunks.joinToString(""))
    }

    @Test
    fun alwaysTerminates_withMinimalLimit() {
        val chunks = SpeechChunker.split("abcdefghij", 2)
        assertEquals("abcdefghij", chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 2 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun limitBelowTwo_isRejected() {
        SpeechChunker.split("abc", 1)
    }
}

class SpeechRetryPolicyTest {

    @Test
    fun delays_growExponentially() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), (1..5).map { SpeechRetryPolicy.delayMs(it) })
    }

    @Test
    fun givesUpAfterMaxRetries() {
        assertEquals(null, SpeechRetryPolicy.delayMs(SpeechRetryPolicy.MAX_RETRIES + 1))
        assertEquals(null, SpeechRetryPolicy.delayMs(100))
    }

    @Test
    fun invalidFailureCount_isNotRetried() {
        assertEquals(null, SpeechRetryPolicy.delayMs(0))
        assertEquals(null, SpeechRetryPolicy.delayMs(-1))
    }
}
