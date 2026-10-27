package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunkerViTest {

    private fun List<String>.compact() = joinToString("").filterNot { it.isWhitespace() }

    @Test
    fun doesNotCutAfterDanglingFunctionWord() {
        val text = "Hôm nay chúng tôi đến thăm trường của những em học sinh"
        SpeechChunker.split(text, 30).dropLast(1).forEach { chunk ->
            val last = chunk.trimEnd('.', ',').substringAfterLast(' ').lowercase()
            assertFalse("đoạn kết thúc bằng từ treo: $chunk", last in setOf("của", "và", "là", "những", "đến"))
        }
        assertEquals(text.filterNot { it.isWhitespace() }, SpeechChunker.split(text, 30).compact())
    }

    @Test
    fun doesNotStartChunkWithFinalParticle() {
        val text = "Cảm ơn bạn rất nhiều vì đã giúp đỡ tôi hôm qua nhé bạn"
        SpeechChunker.split(text, 36).drop(1).forEach {
            assertFalse("đoạn bắt đầu bằng tiểu từ: $it", it.startsWith("nhé"))
        }
    }

    @Test
    fun prefersCommaOverPlainSpace() {
        val chunks = SpeechChunker.split("Trời hôm nay rất đẹp, chúng ta đi dạo công viên", 30)
        assertEquals("Trời hôm nay rất đẹp,", chunks.first())
    }

    @Test
    fun doesNotSplitAfterAbbreviation() {
        val chunks = SpeechChunker.split("Ông ở TP. Hồ Chí Minh rất lâu rồi nhé bạn", 12)
        assertTrue(chunks.none { it.endsWith("TP.") })
    }

    @Test
    fun doesNotSplitThousandsOrTimes() {
        val chunks = SpeechChunker.split("Giá 1.000.000 đồng lúc 10:30 sáng nay", 16)
        assertTrue(chunks.none { it.endsWith("1.") || it.endsWith("1.000.") || it.endsWith("10:") })
    }

    @Test
    fun alwaysRespectsLimit_andLosesNothing() {
        val text = (1..300).joinToString(" ") { "của và ạ nhé từ$it," }
        val chunks = SpeechChunker.split(text, 50)
        assertTrue(chunks.all { it.length <= 50 && it.isNotBlank() })
        assertEquals(text.filterNot { it.isWhitespace() }, chunks.compact())
    }
}
