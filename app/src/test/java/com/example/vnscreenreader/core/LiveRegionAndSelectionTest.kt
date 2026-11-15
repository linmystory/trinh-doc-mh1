package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRegionGateTest {
    @Test fun sameTextFromSameSourceIsReadOnceThenAgainAfterTheDelay() {
        val gate = LiveRegionGate(repeatAfterMs = 4_000)
        assertTrue(gate.allow("a", "Đã lưu", 0))
        assertFalse(gate.allow("a", "Đã lưu", 1_000))
        assertTrue(gate.allow("a", "Đã lưu", 4_500))
    }

    @Test fun changedTextOrOtherSourceIsReadImmediately() {
        val gate = LiveRegionGate()
        assertTrue(gate.allow("a", "1 phút", 0))
        assertTrue(gate.allow("a", "2 phút", 10))
        assertTrue(gate.allow("b", "2 phút", 20))
    }

    @Test fun oldestSourcesAreForgottenBeyondCapacity() {
        val gate = LiveRegionGate(repeatAfterMs = 1_000_000, capacity = 2)
        assertTrue(gate.allow("a", "x", 0))
        assertTrue(gate.allow("b", "x", 1))
        assertTrue(gate.allow("c", "x", 2)) // đẩy "a" ra
        assertTrue(gate.allow("a", "x", 3)) // đã quên "a" nên đọc lại
        assertFalse(gate.allow("c", "x", 4))
    }

    @Test fun clearForgetsEverything() {
        val gate = LiveRegionGate()
        gate.allow("a", "x", 0)
        gate.clear()
        assertTrue(gate.allow("a", "x", 1))
    }
}

class SelectionSpeechTest {
    private fun d(text: String, from: Int, to: Int = from, prev: Int = -1, prevLen: Int = text.length) =
        SelectionSpeech.describe(text, from, to, prev, prevLen)

    @Test fun movingRightReadsTheCharacterJustPassed() {
        val r = d("xin chào", 2, prev = 1)
        assertEquals("i", r.speech)
        assertEquals(2, r.caret)
    }

    @Test fun movingLeftReadsTheCharacterNowAfterTheCaret() {
        assertEquals("c", d("abc", 2, prev = 3).speech)
    }

    @Test fun firstEventHasNoMarkSoItStaysSilentButRemembersTheCaret() {
        val r = d("abc", 3)
        assertNull(r.speech)
        assertEquals(3, r.caret)
        assertEquals(3, r.length)
    }

    @Test fun typingChangesLengthSoSelectionEventIsSilent() {
        assertNull(d("abcd", 4, prev = 3, prevLen = 3).speech)
    }

    @Test fun selectionRangeReadsTheSelectedText() {
        assertEquals("bc", d("abcd", 1, 3).speech)
        assertEquals("bc", d("abcd", 3, 1).speech)
    }

    @Test fun longJumpReadsTheLineNotTheWholeSpan() {
        val text = "dòng một\n" + "x".repeat(100) + "\ndòng ba"
        val r = d(text, text.length, prev = 0)
        assertEquals("dòng ba", r.speech)
    }

    @Test fun invalidIndicesAreIgnored() {
        assertNull(d("ab", 5).speech)
        assertNull(d("ab", -1).speech)
        assertEquals(-1, d("ab", 9).caret)
    }

    @Test fun lineAtHandlesEdges() {
        assertEquals("ab", SelectionSpeech.lineAt("ab\ncd", 1))
        assertEquals("cd", SelectionSpeech.lineAt("ab\ncd", 3))
        assertEquals("cd", SelectionSpeech.lineAt("ab\ncd", 5))
        assertEquals("", SelectionSpeech.lineAt("", 0))
        assertEquals("", SelectionSpeech.lineAt("ab\n", 3))
    }

    @Test fun spokenTextIsCapped() {
        val long = "a".repeat(500)
        assertEquals(SelectionSpeech.MAX_SPOKEN_CHARS, d(long, 0, 500).speech!!.length)
    }
}

class NewSettingsKeysTest {
    @Test fun backupSchemaKnowsTheNewBooleanSettings() {
        for (key in listOf("read_toasts", "keyboard_shortcuts", "volume_key_nav")) {
            assertEquals(key, 'b', BackupSchema.typeOf(BackupSchema.SETTINGS, key))
        }
    }
}
