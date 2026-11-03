package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đơn vị đọc cho các ngôn ngữ không dùng khoảng trắng (Trung) và cho ký tự tổ hợp. */
class TextUnitsLanguageTest {

    private fun assertContiguousCover(text: String, ranges: List<IntRange>) {
        assertEquals(0, ranges.first().first)
        assertEquals(text.length - 1, ranges.last().last)
        ranges.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
    }

    @Test
    fun chineseSentences_splitWithoutWhitespace() {
        assertEquals(
            listOf("你好。", "我很好！", "再见？"),
            TextUnits.split("你好。我很好！再见？", TextGranularity.SENTENCE),
        )
    }

    @Test
    fun chineseClosingQuote_staysWithItsSentence() {
        assertEquals(
            listOf("他说：「你好！」", "然后走了。"),
            TextUnits.split("他说：「你好！」然后走了。", TextGranularity.SENTENCE),
        )
    }

    @Test
    fun latinStillNeedsWhitespaceAfterThePeriod() {
        assertEquals(listOf("a.b"), TextUnits.split("a.b", TextGranularity.SENTENCE))
    }

    @Test
    fun arabicQuestionMark_endsASentence() {
        assertEquals(
            listOf("كيف حالك؟", "أنا بخير."),
            TextUnits.split("كيف حالك؟ أنا بخير.", TextGranularity.SENTENCE),
        )
    }

    @Test
    fun spacedLanguages_keepOneWordPerWhitespaceSeparatedToken() {
        assertEquals(listOf("xin", "chào,", "bạn"), TextUnits.split("xin chào, bạn", TextGranularity.WORD))
        assertEquals(listOf("привет", "мир"), TextUnits.split("привет мир", TextGranularity.WORD))
    }

    @Test
    fun mixedChineseAndSpaces_keepsTheSpacedWordsIntact() {
        val text = "Hello 你好世界。 world"
        val words = TextUnits.split(text, TextGranularity.WORD)
        assertEquals("Hello", words.first())
        assertEquals("world", words.last())
        assertEquals("你好世界。", words.drop(1).dropLast(1).joinToString(""))
    }

    @Test
    fun unspacedChinese_isCoveredWithoutGapsAndPunctuationNeverStandsAlone() {
        val text = "“你好，世界。”"
        val ranges = TextUnits.ranges(text, TextGranularity.WORD)
        assertContiguousCover(text, ranges)
        ranges.forEach { r ->
            val word = text.substring(r.first, r.last + 1)
            assertTrue("\"$word\" chỉ gồm dấu câu", word.any { it.isLetterOrDigit() })
        }
    }

    @Test
    fun punctuationOnlyRun_isASingleWord() {
        assertEquals(listOf("。。。"), TextUnits.split("。。。", TextGranularity.WORD))
    }

    @Test
    fun decomposedVietnamese_isOneCharacterPerVisibleLetter() {
        assertEquals(listOf("a\u0323", "b"), TextUnits.split("a\u0323b", TextGranularity.CHARACTER))
    }

    @Test
    fun emoji_isNeverSplitIntoSurrogates() {
        assertEquals(listOf("a", "😀", "b"), TextUnits.split("a😀b", TextGranularity.CHARACTER))
    }
}
