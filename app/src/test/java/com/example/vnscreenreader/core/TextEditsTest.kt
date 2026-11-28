package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lỗi người dùng báo: xóa ký tự trong văn bản đã nhập thì không đọc gì. Nguyên nhân: đọc theo số đếm (fromIndex/addedCount/removedCount) mà
 * nhiều ứng dụng và bàn phím báo sai hoặc bằng 0. Nay so văn bản trước/sau để biết ĐÃ SỬA GÌ.
 */
class TextEditsTest {

    // ---- TextEdits.diff ----

    @Test
    fun diff_withoutBeforeOrWithoutChange_isNull() {
        assertNull(TextEdits.diff(null, "abc"))
        assertNull(TextEdits.diff("abc", "abc"))
    }

    @Test
    fun diff_insertAtEnd() {
        val e = TextEdits.diff("abc", "abcd")!!
        assertEquals(3, e.start)
        assertEquals("", e.removed)
        assertEquals("d", e.added)
    }

    @Test
    fun diff_deleteLast() {
        val e = TextEdits.diff("abcd", "abc")!!
        assertEquals(3, e.start)
        assertEquals("d", e.removed)
        assertEquals("", e.added)
    }

    @Test
    fun diff_deleteInMiddle() {
        val e = TextEdits.diff("abcd", "abd")!!
        assertEquals(2, e.start)
        assertEquals("c", e.removed)
        assertEquals("", e.added)
    }

    @Test
    fun diff_composingToneMark_isReplacement() {
        val e = TextEdits.diff("chao", "chào")!!
        assertEquals(2, e.start)
        assertEquals("a", e.removed)
        assertEquals("à", e.added)
    }

    @Test
    fun diff_composingBackspace_removesOnlyLastLetter() {
        val e = TextEdits.diff("chào", "chà")!!
        assertEquals("o", e.removed)
        assertEquals("", e.added)
    }

    @Test
    fun diff_replaceAll() {
        val e = TextEdits.diff("hello", "x")!!
        assertEquals(0, e.start)
        assertEquals("hello", e.removed)
        assertEquals("x", e.added)
    }

    @Test
    fun diff_repeatedLetters_removeOne() {
        val e = TextEdits.diff("aab", "ab")!!
        assertEquals("a", e.removed)
        assertEquals("", e.added)
    }

    @Test
    fun diff_emptyEnds() {
        val all = TextEdits.diff("a", "")!!
        assertEquals("a", all.removed)
        assertEquals("", all.added)
        val first = TextEdits.diff("", "a")!!
        assertEquals("", first.removed)
        assertEquals("a", first.added)
    }

    @Test
    fun diff_neverSplitsSurrogatePair() {
        val smile = "😀"
        val grin = "😁"
        val deleted = TextEdits.diff("a$smile", "a")!!
        assertEquals(smile, deleted.removed)
        assertEquals("", deleted.added)
        val replaced = TextEdits.diff("x$smile", "x$grin")!!
        assertEquals(smile, replaced.removed)
        assertEquals(grin, replaced.added)
        // Cùng nửa sau, khác nửa đầu: phần cuối chung không được bắt đầu giữa cặp.
        val otherHigh = "🨀"
        val swapped = TextEdits.diff("${smile}z", "${otherHigh}z")!!
        assertEquals(smile, swapped.removed)
        assertEquals(otherHigh, swapped.added)
    }

    @Test
    fun diff_tooLong_isNull() {
        val big = "a".repeat(TextEdits.MAX_DIFF_CHARS + 1)
        assertNull(TextEdits.diff(big, big.dropLast(1)))
        assertNull(TextEdits.diff("a", big))
        assertNotNull(TextEdits.diff("a".repeat(TextEdits.MAX_DIFF_CHARS), "a".repeat(TextEdits.MAX_DIFF_CHARS - 1)))
    }

    @Test
    fun diff_resultIsAlwaysConsistent() {
        // Dựng lại văn bản sau từ văn bản trước + sửa đổi: phải khớp với mọi cặp chuỗi.
        val samples = listOf("", "a", "ab", "abc", "aab", "abab", "xin chào", "xin chà", "😀a😀", "a😀", "😀", "\n", " ", "  ")
        for (b in samples) for (a in samples) {
            val e = TextEdits.diff(b, a)
            if (b == a) {
                assertNull(e)
                continue
            }
            e!!
            assertEquals("$b -> $a", a, b.substring(0, e.start) + e.added + b.substring(e.start + e.removed.length))
            assertFalse(e.isEmpty)
        }
    }

    @Test
    fun edit_toString_doesNotLeakText() {
        val e = TextEdit(1, "bí mật", "mật khẩu")
        assertFalse(e.toString().contains("bí mật"))
        assertFalse(e.toString().contains("mật khẩu"))
    }

    // ---- TextMemory ----

    @Test
    fun memory_recallsOnlyTheSameField() {
        val m = TextMemory()
        assertNull(m.recall("a"))
        m.remember("a", "abc")
        assertEquals("abc", m.recall("a"))
        assertNull(m.recall("b"))
        assertNull(m.recall(null))
    }

    @Test
    fun memory_nullKeyForgets_andClearForgets() {
        val m = TextMemory()
        m.remember("a", "abc")
        m.remember(null, "xyz")
        assertNull(m.recall("a"))
        m.remember("a", "abc")
        m.clear()
        assertNull(m.recall("a"))
    }

    // ---- TextChangePlanner ----

    private fun plan(
        text: String,
        before: String?,
        from: Int = -1,
        added: Int = 0,
        removed: Int = 0,
        password: Boolean = false,
        speakPassword: Boolean = false,
        chars: Boolean = true,
        words: Boolean = true,
    ) = TextChangePlanner.plan(text, before, from, added, removed, password, speakPassword, chars, words)

    @Test
    fun plan_deleteWithUselessCounts_readsDeletedCharacter() {
        // Ứng dụng báo 0/0: trước đây im lặng hoàn toàn.
        assertEquals("Đã xóa c", plan("ab", "abc").chars)
    }

    @Test
    fun plan_deleteSpaceNewlineTab_readByName() {
        assertEquals("Đã xóa dấu cách", plan("ab", "ab ").chars)
        assertEquals("Đã xóa xuống dòng", plan("ab", "ab\n").chars)
        assertEquals("Đã xóa tab", plan("ab", "ab\t").chars)
        assertEquals("Đã xóa khoảng trắng", plan("ab", "ab ").chars)
    }

    @Test
    fun plan_deleteLongStretch_onlySaysDeleted() {
        val long = "x".repeat(SpeechTextBuilder.MAX_DELETED_SPOKEN_CHARS + 1)
        assertEquals("Đã xóa", plan("", long).chars)
        val limit = "y".repeat(SpeechTextBuilder.MAX_DELETED_SPOKEN_CHARS)
        assertEquals("Đã xóa $limit", plan("", limit).chars)
    }

    @Test
    fun plan_deleteWord_readsTheWord() {
        assertEquals("Đã xóa chào", plan("xin ", "xin chào").chars)
    }

    @Test
    fun plan_composingBackspace_readsDeletedLetterNotWholeWord() {
        // Số đếm theo kiểu "thay cả từ" (removed=4, added=3) trước đây khiến đọc lại "chà".
        assertEquals("Đã xóa o", plan("chà", "chào", from = 0, added = 3, removed = 4).chars)
    }

    @Test
    fun plan_composingToneMark_readsOnlyTheNewLetter() {
        val p = plan("chào", "chao", from = 0, added = 4, removed = 4)
        assertEquals("à", p.chars)
        assertEquals('à', p.typed)
        assertNull(p.word) // đang soạn lại từ, chưa phải gõ xong từ
    }

    @Test
    fun plan_typingLetter_readsLetter() {
        val p = plan("abc", "ab", from = 2, added = 1)
        assertEquals("c", p.chars)
        assertEquals('c', p.typed)
        assertNull(p.word)
    }

    @Test
    fun plan_spaceAfterWord_readsSpaceAndWord() {
        val p = plan("xin chào ", "xin chào", from = 8, added = 1)
        assertEquals("dấu cách", p.chars)
        assertEquals("chào", p.word)
    }

    @Test
    fun plan_spaceAfterWordWhenAppReportsWholeWordReplaced() {
        // IME thay cả từ đang soạn bằng "chào ": số đếm báo 5 ký tự chèn, nhưng thực chất chỉ thêm một dấu cách.
        val p = plan("xin chào ", "xin chào", from = 4, added = 5, removed = 4)
        assertEquals("dấu cách", p.chars)
        assertEquals("chào", p.word)
    }

    @Test
    fun plan_capitalLetter_isReportedAsTyped() {
        val p = plan("A", "", from = 0, added = 1)
        assertEquals("A", p.chars)
        assertEquals('A', p.typed)
    }

    @Test
    fun plan_pasteLongText_readsItAllAndHasNoTypedChar() {
        val p = plan("ab cd", "ab", from = 2, added = 3)
        assertEquals(" cd", p.chars)
        assertNull(p.typed)
    }

    @Test
    fun plan_passwordNeverLeaksCharacters() {
        val typed = plan("•••", "••", from = 2, added = 1, password = true)
        assertEquals("Đã nhập ký tự", typed.chars)
        assertNull(typed.typed)
        assertNull(typed.word)
        val deleted = plan("••", "•••", password = true)
        assertEquals("Đã xóa ký tự", deleted.chars)
        // Ngay cả khi ứng dụng gửi chữ thật trong ô mật khẩu.
        assertEquals("Đã xóa ký tự", plan("abc", "abcd", password = true).chars)
        assertEquals("Đã nhập ký tự", plan("abcd", "abc", password = true).chars)
    }

    @Test
    fun plan_passwordWhenUserAllowsSpeaking_readsRealCharsButMaskedStaysGeneric() {
        assertEquals("Đã xóa d", plan("abc", "abcd", password = true, speakPassword = true).chars)
        assertEquals("d", plan("abcd", "abc", password = true, speakPassword = true).chars)
        assertEquals("Đã xóa ký tự", plan("••", "•••", password = true, speakPassword = true).chars)
        assertEquals("Đã nhập ký tự", plan("•••", "••", password = true, speakPassword = true).chars)
    }

    @Test
    fun plan_replacingACharacterWithASpace_isNotAWordCompletion() {
        // Chọn "c" rồi gõ dấu cách: là thay thế (đang sửa chữ), không phải vừa gõ xong từ "ab".
        val p = plan("ab ", "abc", from = 2, added = 1, removed = 1)
        assertEquals("dấu cách", p.chars)
        assertNull(p.word)
        // Còn chèn thêm dấu cách sau từ thì vẫn là gõ xong từ.
        assertEquals("ab", plan("ab ", "ab", from = 2, added = 1).word)
    }

    @Test
    fun plan_passwordNeverEchoesAWord_evenWhenCharactersAreAllowedToBeSpoken() {
        val p = plan("mk ", "mk", from = 2, added = 1, password = true, speakPassword = true)
        assertNull(p.word)
        assertNull(p.typed)
    }

    @Test
    fun plan_echoCharsOff_isSilentButWordStillWorks() {
        assertNull(plan("ab", "abc", chars = false).chars)
        val p = plan("xin chào ", "xin chào", from = 8, added = 1, chars = false)
        assertNull(p.chars)
        assertEquals("chào", p.word)
    }

    @Test
    fun plan_echoWordsOff_hasNoWord() {
        assertNull(plan("xin chào ", "xin chào", from = 8, added = 1, words = false).word)
    }

    @Test
    fun plan_withoutBefore_fallsBackToCounts() {
        // Như trước: không có văn bản trước thì dùng số đếm của sự kiện.
        assertEquals("c", plan("abc", null, from = 2, added = 1).chars)
        assertEquals("Đã xóa", plan("ab", null, from = 2, removed = 1).chars)
        assertNull(plan("ab", null).chars)
    }

    @Test
    fun plan_beforeEqualsAfter_fallsBackToCounts() {
        assertEquals("c", plan("abc", "abc", from = 2, added = 1).chars)
        assertNull(plan("abc", "abc").chars)
    }

    @Test
    fun describeTextChange_usesBeforeText() {
        assertEquals("Đã xóa c", SpeechTextBuilder.describeTextChange("ab", -1, 0, 0, false, beforeText = "abc"))
        // Không có beforeText thì y như cũ.
        assertEquals("Đã xóa", SpeechTextBuilder.describeTextChange("ab", 2, 0, 1, false))
        assertTrue(SpeechTextBuilder.describeTextChange("ab", 0, 0, 0, false) == null)
    }

    @Test
    fun describeEdit_emptyEdit_isNull() {
        assertNull(SpeechTextBuilder.describeEdit(TextEdit(0, "", ""), isPassword = false))
    }
}
