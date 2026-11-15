package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Điều khiển đọc của vuốt một ngón xuống/lên: thứ tự vòng tròn, đồng bộ với đơn vị chữ, hướng thanh trượt, qua biên phần tử. */
class ReadingControlTest {

    @Test
    fun order_isTheDocumentedCircle() {
        // Thứ tự của bản mới nhất Bộ trợ năng Android (ký tự, từ, đoạn, tiêu đề, điều khiển, liên kết, tốc độ đọc),
        // cộng "phần tử" ở đầu và "câu" giữa từ và đoạn.
        assertEquals(
            listOf("ELEMENT", "CHARACTER", "WORD", "SENTENCE", "PARAGRAPH", "HEADING", "CONTROL", "LINK", "SPEECH_RATE"),
            ReadingControl.entries.map { it.name },
        )
    }

    @Test
    fun next_andPrevious_wrapAround() {
        assertEquals(ReadingControl.CHARACTER, ReadingControl.ELEMENT.next())
        assertEquals(ReadingControl.CONTROL, ReadingControl.HEADING.next())
        assertEquals(ReadingControl.SPEECH_RATE, ReadingControl.LINK.next())
        assertEquals(ReadingControl.ELEMENT, ReadingControl.SPEECH_RATE.next())
        assertEquals(ReadingControl.SPEECH_RATE, ReadingControl.ELEMENT.previous())
        assertEquals(ReadingControl.ELEMENT, ReadingControl.CHARACTER.previous())
        assertEquals(ReadingControl.WORD, ReadingControl.SENTENCE.previous())
    }

    @Test
    fun nextThenPrevious_isIdentity_andAFullTurnComesBack() {
        for (control in ReadingControl.entries) {
            assertEquals(control, control.next().previous())
            assertEquals(control, control.previous().next())
            var walked = control
            repeat(ReadingControl.entries.size) { walked = walked.next() }
            assertEquals(control, walked)
        }
    }

    @Test
    fun onlyTheFourTextControlsCarryAUnit() {
        assertNull(ReadingControl.ELEMENT.unit)
        assertNull(ReadingControl.HEADING.unit)
        assertNull(ReadingControl.CONTROL.unit)
        assertNull(ReadingControl.LINK.unit)
        assertNull(ReadingControl.SPEECH_RATE.unit)
        assertEquals(TextGranularity.PARAGRAPH, ReadingControl.PARAGRAPH.unit)
        assertEquals(TextGranularity.SENTENCE, ReadingControl.SENTENCE.unit)
        assertEquals(TextGranularity.WORD, ReadingControl.WORD.unit)
        assertEquals(TextGranularity.CHARACTER, ReadingControl.CHARACTER.unit)
    }

    @Test
    fun caretUnit_isWhatTheRealCaretMovesBy_inAnEditField() {
        // "Phần tử" (mặc định) duyệt từng ký tự trong ô nhập, như bản mới của Bộ trợ năng Android.
        assertEquals(TextGranularity.CHARACTER, ReadingControl.ELEMENT.caretUnit)
        assertEquals(TextGranularity.CHARACTER, ReadingControl.CHARACTER.caretUnit)
        assertEquals(TextGranularity.WORD, ReadingControl.WORD.caretUnit)
        assertEquals(TextGranularity.SENTENCE, ReadingControl.SENTENCE.caretUnit)
        assertEquals(TextGranularity.PARAGRAPH, ReadingControl.PARAGRAPH.caretUnit)
        // Các điều khiển nhảy giữa phần tử hoặc chỉnh giá trị thì không dời con trỏ.
        assertNull(ReadingControl.HEADING.caretUnit)
        assertNull(ReadingControl.CONTROL.caretUnit)
        assertNull(ReadingControl.LINK.caretUnit)
        assertNull(ReadingControl.SPEECH_RATE.caretUnit)
    }

    @Test
    fun forUnit_isTheInverseOfUnit_forEveryGranularity() {
        for (unit in TextGranularity.entries) assertEquals(unit, ReadingControl.forUnit(unit).unit)
    }

    @Test
    fun labels_areTheSpokenNames() {
        assertEquals("Phần tử", ReadingControl.ELEMENT.label)
        assertEquals("Tiêu đề", ReadingControl.HEADING.label)
        assertEquals("Điều khiển", ReadingControl.CONTROL.label)
        assertEquals("Liên kết", ReadingControl.LINK.label)
        assertEquals("Tốc độ đọc", ReadingControl.SPEECH_RATE.label)
        assertEquals("đoạn", ReadingControl.PARAGRAPH.label)
        assertEquals("câu", ReadingControl.SENTENCE.label)
        assertEquals("từ", ReadingControl.WORD.label)
        assertEquals("ký tự", ReadingControl.CHARACTER.label)
        assertEquals("Điều khiển đọc: Tiêu đề", t(S.MSG_CONTROL_SET, ReadingControl.HEADING.label))
    }

    @Test
    fun sliderDirection_swipeDownDecreases_swipeUpIncreases() {
        assertEquals(-1, ReadingControl.sliderDirection(forward = true))
        assertEquals(1, ReadingControl.sliderDirection(forward = false))
    }

    @Test
    fun crossesElement_onlyWhenTextRunsOutOrIsMissing() {
        assertTrue(ReadingControl.crossesElement(null)) // chưa chọn phần tử, hoặc phần tử không có chữ
        assertTrue(ReadingControl.crossesElement(UnitMove.End))
        assertTrue(ReadingControl.crossesElement(UnitMove.Start))
        assertFalse(ReadingControl.crossesElement(UnitMove.At("từ")))
        assertFalse(ReadingControl.crossesElement(UnitMove.Changed)) // phần tử vừa đổi: đã báo, ở lại
    }
}

/** Con trỏ chữ dùng cho điều khiển đọc: chọn đơn vị rõ ràng, quên vị trí, đặt sau đơn vị cuối. */
class TextCursorControlTest {

    @Test
    fun select_keepsThePositionLikeCycle() {
        val c = TextCursor(TextGranularity.SENTENCE)
        c.bind("Một hai. Ba bốn năm.")
        c.move(true); c.move(true) // đang ở câu "Ba bốn năm."
        assertEquals(TextGranularity.WORD, c.select(TextGranularity.WORD))
        assertEquals("Ba", c.current())
        assertEquals(TextGranularity.WORD, c.select(TextGranularity.WORD)) // chọn lại đúng đơn vị: không đổi gì
        assertEquals("Ba", c.current())
        assertEquals(TextGranularity.SENTENCE, c.select(TextGranularity.SENTENCE))
        assertEquals("Ba bốn năm.", c.current())
    }

    @Test
    fun select_beforeAnyMove_staysAtTheStart() {
        val c = TextCursor(TextGranularity.SENTENCE)
        c.bind("a b")
        assertEquals(TextGranularity.WORD, c.select(TextGranularity.WORD))
        assertNull(c.current())
        assertEquals(UnitMove.At("a"), c.move(true))
    }

    @Test
    fun cycle_stillWalksTheFourUnitsInOrder() {
        val c = TextCursor(TextGranularity.PARAGRAPH)
        assertEquals(TextGranularity.SENTENCE, c.cycle())
        assertEquals(TextGranularity.WORD, c.cycle())
        assertEquals(TextGranularity.CHARACTER, c.cycle())
        assertEquals(TextGranularity.PARAGRAPH, c.cycle())
    }

    @Test
    fun reset_forcesAFreshStart_evenForIdenticalText() {
        val c = TextCursor(TextGranularity.WORD)
        c.bind("xóa xóa")
        c.move(true); c.move(true)
        assertEquals("xóa", c.current())
        c.bind("xóa xóa") // cùng chữ: giữ vị trí (đang ở từ thứ hai)
        assertEquals(UnitMove.End, c.move(true))
        c.reset()
        c.bind("xóa xóa") // phần tử khác có chữ y hệt: phải bắt đầu lại từ đầu
        assertNull(c.current())
        assertEquals(UnitMove.At("xóa"), c.move(true))
    }

    @Test
    fun parkAtEnd_thenBackwardReadsTheLastUnit_andForwardReportsTheEnd() {
        val c = TextCursor(TextGranularity.WORD)
        c.bind("một hai ba")
        c.parkAtEnd()
        assertNull(c.current())
        assertEquals(UnitMove.At("ba"), c.move(false))
        assertEquals(UnitMove.At("hai"), c.move(false))
        c.parkAtEnd()
        assertEquals(UnitMove.End, c.move(true))
    }

    @Test
    fun parkAtEnd_onEmptyText_isHarmless_andIsEmptyTellsEmptyFromParked() {
        val c = TextCursor(TextGranularity.WORD)
        c.bind("   ")
        assertTrue(c.isEmpty)
        c.parkAtEnd()
        assertEquals(UnitMove.End, c.move(true))
        assertEquals(UnitMove.Start, c.move(false))
        c.bind("một")
        c.parkAtEnd()
        assertFalse(c.isEmpty) // đang đặt sau đơn vị cuối nhưng văn bản có chữ
        assertEquals(UnitMove.End, c.move(true))
    }
}
