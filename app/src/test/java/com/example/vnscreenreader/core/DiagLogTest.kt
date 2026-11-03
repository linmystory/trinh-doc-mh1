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
}
