package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageSetTest {
    @Test fun toggleAddsThenRemoves() {
        val (a, inA) = PackageSet.toggle("", "com.cam")
        assertTrue(inA)
        assertEquals("com.cam", a)
        val (b, inB) = PackageSet.toggle(a, "com.game")
        assertTrue(inB)
        assertEquals(listOf("com.cam", "com.game"), PackageSet.parse(b))
        val (c, inC) = PackageSet.toggle(b, "com.cam")
        assertFalse(inC)
        assertEquals("com.game", c)
    }

    @Test fun containsIsExactAndNullSafe() {
        assertTrue(PackageSet.contains("a.b,c.d", "c.d"))
        assertFalse(PackageSet.contains("a.b,c.d", "c"))
        assertFalse(PackageSet.contains("a.b", null))
        assertFalse(PackageSet.contains("", "a.b"))
    }

    @Test fun parseIgnoresBlanksAndDuplicates() {
        assertEquals(listOf("a", "b"), PackageSet.parse(" a, ,b,a ,"))
    }

    @Test fun listIsCappedKeepingTheNewest() {
        var raw = ""
        for (i in 1..PackageSet.MAX + 5) raw = PackageSet.toggle(raw, "p$i").first
        val list = PackageSet.parse(raw)
        assertEquals(PackageSet.MAX, list.size)
        assertEquals("p${PackageSet.MAX + 5}", list.last())
        assertFalse("p1" in list)
    }
}

class WindowFocusMemoryTest {
    private fun trace(label: String) = FocusTrace(1, null, null, label, intArrayOf(0, 0, 1, 1))

    @Test fun rememberAndRecall() {
        val m = WindowFocusMemory()
        assertNull(m.recall("a"))
        m.remember("a", trace("Gửi"))
        assertEquals("Gửi", m.recall("a")!!.label)
        m.remember("a", trace("Hủy"))
        assertEquals("Hủy", m.recall("a")!!.label)
    }

    @Test fun oldestWindowIsForgotten() {
        val m = WindowFocusMemory(capacity = 2)
        m.remember("a", trace("1"))
        m.remember("b", trace("2"))
        m.remember("c", trace("3"))
        assertNull(m.recall("a"))
        assertNotNull(m.recall("b"))
        assertNotNull(m.recall("c"))
    }

    @Test fun clearForgetsAll() {
        val m = WindowFocusMemory()
        m.remember("a", trace("1"))
        m.clear()
        assertNull(m.recall("a"))
    }
}

class TypingEchoTest {
    @Test fun wordCompletedBySpace() {
        // Đã gõ "xin chào " : ký tự vừa thêm là dấu cách ở vị trí 8.
        assertEquals("chào", TypingEcho.completedWord("xin chào ", 8, 1))
    }

    @Test fun wordCompletedByPunctuationAndNewline() {
        assertEquals("ok", TypingEcho.completedWord("ok.", 2, 1))
        assertEquals("ok", TypingEcho.completedWord("ok\n", 2, 1))
    }

    @Test fun typingALetterOrNothingBeforeGivesNull() {
        assertNull(TypingEcho.completedWord("abc", 2, 1)) // vừa thêm chữ cái
        assertNull(TypingEcho.completedWord(" ", 0, 1)) // chưa có từ nào trước đó
        assertNull(TypingEcho.completedWord("a  ", 2, 1)) // hai dấu cách liền nhau: từ đã đọc ở lần trước
    }

    @Test fun pastesAndDeletionsAreIgnored() {
        assertNull(TypingEcho.completedWord("abc def", 3, 4)) // dán nhiều ký tự
        assertNull(TypingEcho.completedWord("abc", 3, 1)) // chỉ số ngoài chuỗi
        assertNull(TypingEcho.completedWord("abc ", 3, 0))
    }

    @Test fun apostropheAndHyphenInsideAWordStayInIt() {
        assertEquals("don't", TypingEcho.completedWord("don't ", 5, 1))
        assertEquals("co-op", TypingEcho.completedWord("co-op ", 5, 1))
        // Dấu nháy ở cuối từ (không có chữ sau nó) không thuộc từ: từ vừa xong là "dogs" nhưng ký tự thêm là dấu cách ở 5.
        assertEquals("dogs", TypingEcho.completedWord("dogs' ", 5, 1))
    }

    @Test fun veryLongWordIsCut() {
        val w = "a".repeat(200)
        assertEquals(60, TypingEcho.completedWord("$w ", 200, 1)!!.length)
    }
}

class ListRangeTest {
    @Test fun convertsToOneBased() {
        val r = ListRange.from(4, 8, 30)!!
        assertEquals(5, r.first)
        assertEquals(9, r.last)
        assertEquals(30, r.total)
    }

    @Test fun rejectsUntrustworthyOrFullyVisibleLists() {
        assertNull(ListRange.from(-1, 3, 10))
        assertNull(ListRange.from(5, 3, 10))
        assertNull(ListRange.from(0, 3, 0))
        assertNull(ListRange.from(0, 10, 10)) // vượt tổng
        assertNull(ListRange.from(0, 9, 10)) // đã hiện hết
        assertNotNull(ListRange.from(0, 8, 10))
    }
}

class NewStep2To5SettingsTest {
    @Test fun backupSchemaKnowsTheNewSettings() {
        for (key in listOf("focus_dialogs", "remember_focus", "echo_chars", "echo_words", "speak_capitals", "speak_list_range")) {
            assertEquals(key, 'b', BackupSchema.typeOf(BackupSchema.SETTINGS, key))
        }
        assertEquals('s', BackupSchema.typeOf(BackupSchema.SETTINGS, "volume_excluded_apps"))
        assertEquals('b', BackupSchema.typeOf(BackupSchema.SETTINGS, "headset_key"))
        for (key in listOf("announce_calls", "location_enabled")) {
            assertEquals(key, 'b', BackupSchema.typeOf(BackupSchema.SETTINGS, key))
        }
        assertEquals('b', BackupSchema.typeOf(BackupSchema.SETTINGS, "scroll_adjust_sliders"))
        for (key in listOf("voice_mode", "voice_split", "first_voice_language", "second_voice_language")) {
            assertEquals(key, 's', BackupSchema.typeOf(BackupSchema.SETTINGS, key))
        }
        // Tên gói công cụ đọc phụ thuộc từng máy nên không đi theo tệp sao lưu; khóa người gọi cũ cũng đã bỏ.
        assertEquals(null, BackupSchema.typeOf(BackupSchema.SETTINGS, "tts_engine"))
        assertEquals(null, BackupSchema.typeOf(BackupSchema.SETTINGS, "second_tts_engine"))
        assertEquals(null, BackupSchema.typeOf(BackupSchema.SETTINGS, "call_names_contacts"))
    }
}
