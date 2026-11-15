package com.example.vnscreenreader.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagLogTest {
    private fun tmp(): File = File.createTempFile("diag", ".log").also { it.delete() }

    @Test
    fun format_singleLine_andCapped() {
        val l = DiagFormat.line(0L, "MOVE", "a\nb\r" + "x".repeat(1000))
        assertFalse(l.contains('\n'))
        assertTrue(l.contains(" MOVE a b"))
        assertTrue(l.length < 450)
    }

    @Test
    fun file_appendsReadsCountsAndClears() {
        val f = DiagFile(tmp())
        f.append("one"); f.append("two")
        assertEquals("one\ntwo\n", f.readAll())
        assertEquals(2, f.lineCount())
        f.clear()
        assertEquals("", f.readAll())
        assertEquals(0, f.lineCount())
    }

    @Test
    fun file_trimsToLastHalfOnLineBoundary() {
        val f = DiagFile(tmp(), maxBytes = 200)
        repeat(50) { f.append("line-$it-xxxxxxxx") }
        val text = f.readAll()
        assertTrue(text.length <= 200)
        assertTrue(text.trimEnd().endsWith("line-49-xxxxxxxx"))
        assertTrue(text.lines().filter { it.isNotEmpty() }.all { it.startsWith("line-") && it.endsWith("xxxxxxxx") })
    }

    @Test
    fun diag_disabledOrUninitialised_doesNotEvaluateMessage() {
        Diag.enabled = false
        var evaluated = false
        Diag.log("T") { evaluated = true; "x" }
        assertFalse(evaluated)
        Diag.enabled = true
    }

    @Test
    fun tail_keepsWholeTextWhenShortEnough() {
        assertEquals("a\nb\n", DiagFormat.tail("a\nb\n", 4))
        assertEquals("a\nb\n", DiagFormat.tail("a\nb\n", 100))
        assertEquals("", DiagFormat.tail("", 0))
    }

    @Test
    fun tail_dropsTheHalfCutFirstLine() {
        // 12 ký tự, giữ 6 cuối = "b\nccc\n": dòng "b" bị cụt nên bỏ, còn lại dòng đầy đủ.
        assertEquals("ccc\n", DiagFormat.tail("aaa\nbbb\nccc\n", 6))
    }

    @Test
    fun tail_keepsFirstLineWhenCutLandsOnLineBoundary() {
        assertEquals("ccc\n", DiagFormat.tail("aaa\nbbb\nccc\n", 4))
    }

    @Test
    fun tail_neverReturnsMoreThanRequestedAndToleratesNonPositive() {
        val text = (1..50).joinToString("") { "line-$it\n" }
        val cut = DiagFormat.tail(text, 40)
        assertTrue(cut.length <= 40)
        assertTrue(text.endsWith(cut))
        assertTrue(cut.startsWith("line-"))
        assertEquals("", DiagFormat.tail(text, 0))
        assertEquals("", DiagFormat.tail(text, -5))
    }

    @Test
    fun tail_withoutAnyLineBreakReturnsRawTail() {
        assertEquals("cdef", DiagFormat.tail("abcdef", 4))
    }

    @Test
    fun diag_readersNeverThrowWhateverTheSinkState() {
        // Không có sink (hoặc sink hỏng) thì các hàm đọc phải trả giá trị rỗng, không ném lỗi ra giao diện.
        Diag.text()
        Diag.text(10)
        assertTrue(Diag.lineCount() >= 0)
        Diag.clear()
    }
}
