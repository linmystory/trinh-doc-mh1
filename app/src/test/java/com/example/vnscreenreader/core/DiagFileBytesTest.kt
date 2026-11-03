package com.example.vnscreenreader.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tệp nhật ký là UTF-8: giới hạn phải tính theo BYTE (tiếng Việt 2-3 byte mỗi ký tự), không theo số ký tự. */
class DiagFileBytesTest {
    private fun tmp(): File = File.createTempFile("diag", ".log").also { it.delete() }

    @Test
    fun trim_keepsAboutHalfTheByteBudget_forMultiByteText() {
        val file = tmp()
        val diag = DiagFile(file, maxBytes = 400)
        var previous = 0L
        var trimmed = false
        for (i in 10..99) {
            diag.append("dòng-$i-" + "ệ".repeat(8))
            val length = file.length()
            if (length < previous) {
                assertTrue("Sau khi cắt còn $length byte", length <= 200L + 64L)
                trimmed = true
                break
            }
            previous = length
        }
        assertTrue("Chưa bao giờ cắt", trimmed)
        file.delete()
    }

    @Test
    fun trim_neverBreaksAMultiByteCharacter_orALine() {
        val file = tmp()
        val diag = DiagFile(file, maxBytes = 400)
        for (i in 10..99) diag.append("dòng-$i-" + "ệ".repeat(8))
        val lines = diag.readAll().lines().filter { it.isNotEmpty() }
        assertTrue(lines.isNotEmpty())
        val pattern = Regex("dòng-\\d{2}-ệ{8}")
        lines.forEach { assertTrue("Dòng hỏng: $it", pattern.matches(it)) }
        assertEquals("dòng-99-" + "ệ".repeat(8), lines.last())
        file.delete()
    }
}
