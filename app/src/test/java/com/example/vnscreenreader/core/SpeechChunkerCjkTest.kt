package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunkerCjkTest {

    @Test
    fun chineseText_isCutRightAfterTheFullWidthPeriod_withoutNeedingWhitespace() {
        val text = "一二三四五。".repeat(10)
        val chunks = SpeechChunker.split(text, 20)
        assertTrue(chunks.size > 1)
        chunks.forEach {
            assertTrue("Đoạn dài ${it.length}", it.length <= 20)
            assertTrue("Đoạn không kết thúc ở dấu câu: $it", it.endsWith("。"))
        }
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun arabicQuestionMark_isASentenceTerminator() {
        val sentence = "كيف حالك؟ "
        val text = sentence.repeat(8).trim()
        val chunks = SpeechChunker.split(text, 30)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue("Đoạn không kết thúc ở dấu hỏi: $it", it.endsWith("؟")) }
    }
}
