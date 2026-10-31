package com.example.vnscreenreader.ai

import com.example.vnscreenreader.core.VietnameseDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextFormatterTest {
    private fun b(t: String, l: Int, top: Int, r: Int = l + 50, bottom: Int = top + 20) = OcrBlock(t, l, top, r, bottom)

    @Test
    fun readsTopToBottom_leftToRight_andJoinsSameLine() {
        val text = OcrTextFormatter.format(listOf(b("hai", 100, 10), b("Dòng", 10, 12), b("Cuối", 10, 80)))
        assertEquals("Dòng hai\nCuối", text)
    }

    @Test
    fun dropsNoiseAndBlank() {
        assertEquals("Xin chào", OcrTextFormatter.format(listOf(b("|", 0, 0), b("  ", 0, 30), b("Xin chào", 0, 60))))
        assertEquals("", OcrTextFormatter.format(emptyList()))
    }

    @Test
    fun keepsSingleDigit_andCapsLength() {
        assertEquals("5", OcrTextFormatter.format(listOf(b("5", 0, 0))))
        val long = OcrTextFormatter.format(listOf(b("a".repeat(100), 0, 0)), maxChars = 10)
        assertEquals(10, long.length)
    }
}

class ImageDescriptionBuilderTest {
    @Test
    fun translatesAndOrdersByConfidence() {
        val d = ImageDescriptionBuilder.build(listOf(LabelScore("Dog", 0.7f), LabelScore("Grass", 0.9f), LabelScore("Sky", 0.2f)))
        assertEquals("Hình ảnh có thể là: cỏ, chó", d)
    }

    @Test
    fun lowConfidence_isNotClaimed() {
        assertEquals("Không nhận diện được nội dung hình ảnh", ImageDescriptionBuilder.build(listOf(LabelScore("Dog", 0.3f))))
        assertEquals("Không nhận diện được nội dung hình ảnh", ImageDescriptionBuilder.build(emptyList()))
    }

    @Test
    fun untranslatedLabels_areNotReadRaw() {
        // Đọc nhãn tiếng Anh thô bằng giọng tiếng Việt sẽ sai âm: coi như không nhận ra.
        assertEquals("Không nhận diện được nội dung hình ảnh", ImageDescriptionBuilder.build(listOf(LabelScore("Zzyzx", 0.9f))))
    }

    @Test
    fun limitsLabelCount_andDeduplicates() {
        val labels = listOf("Dog", "Cat", "Bird", "Fish", "Horse").map { LabelScore(it, 0.9f) }
        assertEquals(4, ImageDescriptionBuilder.build(labels).removePrefix("Hình ảnh có thể là: ").split(", ").size)
        assertNotNull(VietnameseDictionary.imageLabel(" PERSON "))
        assertNull(VietnameseDictionary.imageLabel("nope"))
    }
}

class NotificationSummarizerTest {
    private fun n(vararg t: String, pkg: String = "p") = NotificationInfo(pkg, "Zalo", t.toList())

    @Test
    fun formatsSimpleNotification() {
        assertEquals("Thông báo từ Zalo: An. Hẹn 8h nhé", NotificationSummarizer().summarize(n("An", "Hẹn 8h nhé"), 0))
    }

    @Test
    fun emptyOrBlank_isSkipped() {
        assertNull(NotificationSummarizer().summarize(n("  ", ""), 0))
    }

    @Test
    fun identicalWithinWindow_isSkipped_thenAllowedAfter() {
        val s = NotificationSummarizer()
        assertNotNull(s.summarize(n("A"), 0))
        assertNull(s.summarize(n("A"), 1_000))
        assertNotNull(s.summarize(n("A"), 10_000))
    }

    @Test
    fun burst_isMergedIntoCount() {
        val s = NotificationSummarizer()
        s.summarize(n("1"), 0)
        s.summarize(n("2"), 100)
        assertEquals("3 thông báo mới từ Zalo, mới nhất: 3", s.summarize(n("3"), 200))
    }

    @Test
    fun linksAreShortened_andLongTextTruncatedAtWord() {
        val s = NotificationSummarizer(maxChars = 30)
        val out = s.summarize(n("Xem ngay https://example.com/rat/dai/dai/dai tại đây nhé các bạn ơi"), 0)!!
        assertTrue(out.contains("liên kết"))
        assertTrue(out.endsWith("..."))
        assertTrue(!out.contains("http"))
    }
}


class AuditFixesTest {
    private fun b(text: String, left: Int, top: Int, w: Int = 50) = OcrBlock(text, left, top, left + w, top + 20)

    @Test
    fun farApartColumns_areNotJoinedIntoOneSentence() {
        val text = OcrTextFormatter.format(listOf(b("Tên", 0, 0), b("Giá", 1000, 0)))
        assertEquals("Tên\nGiá", text)
    }

    @Test
    fun nearbyWords_stillJoinOnOneLine() {
        assertEquals("Xin chào", OcrTextFormatter.format(listOf(b("Xin", 0, 0), b("chào", 60, 0))))
    }

    @Test
    fun meaningfulSingleSymbols_areKept_noiseDropped() {
        assertEquals("%", OcrTextFormatter.format(listOf(b("%", 0, 0))))
        assertEquals("?", OcrTextFormatter.format(listOf(b("?", 0, 0))))
        assertEquals("", OcrTextFormatter.format(listOf(b("-", 0, 0), b(".", 0, 30))))
    }

    @Test
    fun truncation_neverSplitsSurrogatePair() {
        val text = OcrTextFormatter.format(listOf(b("ab\uD83D\uDE00cd", 0, 0)), maxChars = 3)
        assertEquals("ab", text)
    }

    @Test
    fun notificationCache_evictsLeastRecentlyUsed() {
        val s = NotificationSummarizer()
        fun n(pkg: String, t: String) = NotificationInfo(pkg, pkg, listOf(t))
        s.summarize(n("active", "một"), 0)
        for (i in 1..40) { s.summarize(n("p$i", "x"), i * 10L); s.summarize(n("active", "tin $i"), i * 10L + 5) }
        // "active" liên tục hoạt động nên vẫn nhớ tin cuối: gửi lại y hệt trong cửa sổ trùng => bị bỏ.
        assertNull(s.summarize(n("active", "tin 40"), 40 * 10L + 6))
    }

    @Test
    fun longNotification_keepsUpTo240Chars() {
        val s = NotificationSummarizer()
        val body = "từ ".repeat(70).trim() // 209 ký tự
        assertTrue(!s.summarize(NotificationInfo("a", "A", listOf(body)), 0)!!.endsWith("..."))
    }
}
