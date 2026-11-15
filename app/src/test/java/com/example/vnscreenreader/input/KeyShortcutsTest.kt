package com.example.vnscreenreader.input

import android.view.KeyEvent
import com.example.vnscreenreader.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyShortcutsTest {
    private fun alt(code: Int, shift: Boolean = false) =
        KeyShortcuts.resolve(code, alt = true, meta = false, shift = shift, ctrl = false)

    @Test fun navigationWithAlt() {
        assertEquals(GestureAction.NEXT_ELEMENT, alt(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(GestureAction.PREVIOUS_ELEMENT, alt(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(GestureAction.SCROLL_FORWARD, alt(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(GestureAction.SCROLL_BACKWARD, alt(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(GestureAction.ACTIVATE, alt(KeyEvent.KEYCODE_ENTER))
        assertEquals(GestureAction.ACTIVATE, alt(KeyEvent.KEYCODE_SPACE))
        assertEquals(GestureAction.BACK, alt(KeyEvent.KEYCODE_DEL))
    }

    @Test fun metaWorksLikeAlt() {
        val action = KeyShortcuts.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, alt = false, meta = true, shift = false, ctrl = false)
        assertEquals(GestureAction.NEXT_ELEMENT, action)
    }

    @Test fun pauseAndPerAppVolumeShortcuts() {
        assertEquals(GestureAction.TOGGLE_KEYS_PAUSE, alt(KeyEvent.KEYCODE_P))
        assertEquals(GestureAction.VOLUME_EXCLUDE_APP, alt(KeyEvent.KEYCODE_V))
        assertNull(alt(KeyEvent.KEYCODE_P, shift = true))
        assertNull(alt(KeyEvent.KEYCODE_E)) // lối thoát khẩn cấp đã bỏ ở 2.18.1: Alt+E không còn gán gì
        assertEquals(GestureAction.SAY_LOCATION, alt(KeyEvent.KEYCODE_G))
        assertEquals(GestureAction.CLEAR_FIELD, alt(KeyEvent.KEYCODE_X))
        assertEquals(GestureAction.RESTORE_CLEARED, alt(KeyEvent.KEYCODE_X, shift = true))
    }

    @Test fun shiftVariants() {
        assertEquals(GestureAction.UNIT_NEXT, alt(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true))
        assertEquals(GestureAction.UNIT_PREVIOUS, alt(KeyEvent.KEYCODE_DPAD_LEFT, shift = true))
        assertEquals(GestureAction.LONG_CLICK_FOCUSED, alt(KeyEvent.KEYCODE_ENTER, shift = true))
        assertEquals(GestureAction.HOME, alt(KeyEvent.KEYCODE_DEL, shift = true))
        assertEquals(GestureAction.PREVIOUS_HEADING, alt(KeyEvent.KEYCODE_H, shift = true))
        assertEquals(GestureAction.NEXT_HEADING, alt(KeyEvent.KEYCODE_H))
    }

    /** Gõ văn bản bình thường không bao giờ bị chặn: không có Alt/Meta thì không có phím tắt nào. */
    @Test fun plainTypingIsNeverCaptured() {
        for (code in listOf(
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE,
        )) {
            assertNull(KeyShortcuts.resolve(code, alt = false, meta = false, shift = false, ctrl = false))
            assertNull(KeyShortcuts.resolve(code, alt = false, meta = false, shift = true, ctrl = false))
        }
    }

    @Test fun ctrlCombinationsAreLeftToTheApp() {
        assertNull(KeyShortcuts.resolve(KeyEvent.KEYCODE_C, alt = true, meta = false, shift = false, ctrl = true))
        assertNull(KeyShortcuts.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, alt = false, meta = true, shift = false, ctrl = true))
    }

    @Test fun unmappedKeysAndModifierKeysPassThrough() {
        assertNull(alt(KeyEvent.KEYCODE_TAB)) // Alt+Tab là của hệ thống
        assertNull(alt(KeyEvent.KEYCODE_ALT_LEFT))
        assertNull(alt(KeyEvent.KEYCODE_SHIFT_LEFT, shift = true))
        assertTrue(KeyShortcuts.isModifierKey(KeyEvent.KEYCODE_META_LEFT))
        assertFalse(KeyShortcuts.isModifierKey(KeyEvent.KEYCODE_A))
    }

    @Test fun onlyMovementRepeatsWhenHeld() {
        assertTrue(KeyShortcuts.isRepeatable(GestureAction.NEXT_ELEMENT))
        assertTrue(KeyShortcuts.isRepeatable(GestureAction.SCROLL_FORWARD))
        assertFalse(KeyShortcuts.isRepeatable(GestureAction.ACTIVATE))
        assertFalse(KeyShortcuts.isRepeatable(GestureAction.BACK))
        assertFalse(KeyShortcuts.isRepeatable(GestureAction.OCR_SCREEN))
    }

    @Test fun ctrlKeyDetection() {
        assertTrue(KeyShortcuts.isCtrlKey(KeyEvent.KEYCODE_CTRL_LEFT))
        assertTrue(KeyShortcuts.isCtrlKey(KeyEvent.KEYCODE_CTRL_RIGHT))
        assertFalse(KeyShortcuts.isCtrlKey(KeyEvent.KEYCODE_ALT_LEFT))
    }

    /** Mỗi hành động trong bảng phím tắt phải là hành động thật (không NONE) và không trùng tổ hợp. */
    @Test fun noShortcutMapsToNone() {
        val seen = mutableMapOf<Pair<Int, Boolean>, GestureAction>()
        for (code in KeyEvent.KEYCODE_UNKNOWN..KeyEvent.KEYCODE_PAGE_DOWN + 50) {
            for (shift in listOf(false, true)) {
                val action = alt(code, shift) ?: continue
                assertTrue(action != GestureAction.NONE)
                assertNull(seen.put(code to shift, action))
            }
        }
        assertTrue(seen.size >= 30)
    }
}
