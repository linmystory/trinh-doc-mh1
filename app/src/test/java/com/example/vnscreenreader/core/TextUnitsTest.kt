package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextUnitsTest {

    @Test
    fun sentencesSplitOnTerminalPunctuation() {
        val t = "Xin chào. Bạn khỏe không? Tôi khỏe!"
        assertEquals(listOf("Xin chào.", "Bạn khỏe không?", "Tôi khỏe!"), TextUnits.split(t, TextGranularity.SENTENCE))
    }

    @Test
    fun abbreviationsAndDecimalsDoNotSplit() {
        assertEquals(listOf("Tôi ở TP. Hồ Chí Minh."), TextUnits.split("Tôi ở TP. Hồ Chí Minh.", TextGranularity.SENTENCE))
        assertEquals(listOf("Tôi là An.", "Bạn là Bình."), TextUnits.split("Tôi là An. Bạn là Bình.", TextGranularity.SENTENCE))
        assertEquals(listOf("Gặp Dr. Smith nhé."), TextUnits.split("Gặp Dr. Smith nhé.", TextGranularity.SENTENCE))
        assertEquals(listOf("Giá 3.5 triệu."), TextUnits.split("Giá 3.5 triệu.", TextGranularity.SENTENCE))
        assertEquals(listOf("Có táo, v.v. và cam."), TextUnits.split("Có táo, v.v. và cam.", TextGranularity.SENTENCE))
    }

    @Test
    fun ellipsisQuotesAndNewlines() {
        assertEquals(listOf("Chờ đã... Được rồi."), TextUnits.split("Chờ đã... Được rồi.", TextGranularity.SENTENCE).let { listOf(it.joinToString(" ")) })
        assertEquals(listOf("\"Đi nào.\"", "Ok"), TextUnits.split("\"Đi nào.\" Ok", TextGranularity.SENTENCE))
        assertEquals(listOf("Dòng một", "Dòng hai."), TextUnits.split("Dòng một\nDòng hai.", TextGranularity.SENTENCE))
    }

    @Test
    fun paragraphsWordsCharacters() {
        assertEquals(listOf("a b", "c"), TextUnits.split("a b\n\n  c  \n", TextGranularity.PARAGRAPH))
        assertEquals(listOf("xin", "chào", "bạn"), TextUnits.split(" xin  chào\tbạn ", TextGranularity.WORD))
        assertEquals(listOf("a", "😀", "b"), TextUnits.split("a😀b", TextGranularity.CHARACTER))
        assertEquals(emptyList<String>(), TextUnits.split("   ", TextGranularity.WORD))
        assertEquals(emptyList<String>(), TextUnits.split("", TextGranularity.SENTENCE))
    }

    @Test
    fun cursorWalksAndStopsAtEnds() {
        val c = TextCursor(TextGranularity.WORD)
        c.bind("một hai ba")
        assertEquals(UnitMove.Start, c.move(false))
        assertEquals(UnitMove.At("một"), c.move(true))
        assertEquals(UnitMove.At("hai"), c.move(true))
        assertEquals(UnitMove.At("ba"), c.move(true))
        assertEquals(UnitMove.End, c.move(true))
        assertEquals("ba", c.current())
        assertEquals(UnitMove.At("hai"), c.move(false))
    }

    @Test
    fun cycleKeepsPositionAndRebindResetsOnlyOnNewText() {
        val c = TextCursor(TextGranularity.SENTENCE)
        c.bind("Một hai. Ba bốn năm.")
        c.move(true); c.move(true) // đang ở "Ba bốn năm."
        assertEquals(TextGranularity.WORD, c.cycle())
        assertEquals("Ba", c.current()) // từ đầu tiên của câu đang đứng
        c.bind("Một hai. Ba bốn năm.") // cùng văn bản: giữ vị trí
        assertEquals("Ba", c.current())
        c.bind("văn bản khác")
        assertNull(c.current())
    }

    @Test
    fun nonBreakingAndOtherUnicodeSpacesSeparateWords() {
        // Trang web hay nối từ bằng &nbsp; (U+00A0); các dấu cách Unicode khác cũng phải tách từ như dấu cách thường.
        assertEquals(listOf("Hà", "Nội"), TextUnits.split("Hà Nội", TextGranularity.WORD))
        assertEquals(listOf("10", "km"), TextUnits.split("10 km", TextGranularity.WORD)) // dấu cách không ngắt hẹp
        assertEquals(listOf("a", "b", "c"), TextUnits.split("a b　c", TextGranularity.WORD))
        // Cụm toàn dấu cách Unicode không phải là một "từ" (trước đây U+00A0 đứng riêng thành một từ rỗng khi duyệt).
        assertEquals(listOf("Trang", "chủ", "|", "Giới", "thiệu"), TextUnits.split("Trang chủ  |  Giới thiệu", TextGranularity.WORD))
        assertEquals(emptyList<String>(), TextUnits.split("   　", TextGranularity.WORD))
        // Vị trí (range) vẫn khớp với chuỗi gốc, kể cả sau dấu cách không ngắt.
        val text = "x yz"
        assertEquals(listOf(0..0, 2..3), TextUnits.ranges(text, TextGranularity.WORD))
        // Dấu cách ASCII, tab, xuống dòng không đổi hành vi.
        assertEquals(listOf("xin", "chào", "bạn"), TextUnits.split("xin\nchào \t bạn", TextGranularity.WORD))
    }

    @Test
    fun granularityCycleCoversAllAndWraps() {
        var g = TextGranularity.PARAGRAPH
        val seen = mutableListOf<TextGranularity>()
        repeat(4) { seen += g; g = g.next() }
        assertEquals(TextGranularity.entries.toList(), seen)
        assertEquals(TextGranularity.PARAGRAPH, g)
    }
}
