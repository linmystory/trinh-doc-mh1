package com.example.vnscreenreader.input

import android.view.KeyEvent
import com.example.vnscreenreader.gesture.GestureAction

/**
 * Bảng phím tắt bàn phím ngoài. Thuần hàm (không phụ thuộc trạng thái) để kiểm thử.
 *
 * Quy tắc an toàn: chỉ tổ hợp có Alt hoặc Meta (phím Windows/Search) và KHÔNG có Ctrl mới được nhận; mọi phím khác
 * (chữ, số, mũi tên trơn, Ctrl+C...) trả về null để ứng dụng nhận bình thường, nên gõ văn bản không bị ảnh hưởng.
 */
object KeyShortcuts {

    private val MODIFIERS: Set<Int> = setOf(
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
        KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_FUNCTION,
    )

    /** Hành động cho phép lặp khi giữ phím (di chuyển, cuộn); các hành động khác chỉ chạy một lần mỗi lần nhấn. */
    private val REPEATABLE: Set<GestureAction> = setOf(
        GestureAction.NEXT_ELEMENT, GestureAction.PREVIOUS_ELEMENT,
        GestureAction.UNIT_NEXT, GestureAction.UNIT_PREVIOUS,
        GestureAction.SCROLL_FORWARD, GestureAction.SCROLL_BACKWARD,
    )

    fun isModifierKey(keyCode: Int): Boolean = keyCode in MODIFIERS

    fun isCtrlKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT

    fun isRepeatable(action: GestureAction): Boolean = action in REPEATABLE

    /** Hành động của tổ hợp, hoặc null nếu không phải phím tắt của trình đọc. */
    fun resolve(keyCode: Int, alt: Boolean, meta: Boolean, shift: Boolean, ctrl: Boolean): GestureAction? {
        if (ctrl || !(alt || meta) || isModifierKey(keyCode)) return null
        return if (shift) withShift(keyCode) else plain(keyCode)
    }

    private fun plain(keyCode: Int): GestureAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT -> GestureAction.NEXT_ELEMENT
        KeyEvent.KEYCODE_DPAD_LEFT -> GestureAction.PREVIOUS_ELEMENT
        KeyEvent.KEYCODE_DPAD_DOWN -> GestureAction.SCROLL_FORWARD
        KeyEvent.KEYCODE_DPAD_UP -> GestureAction.SCROLL_BACKWARD
        KeyEvent.KEYCODE_PAGE_DOWN -> GestureAction.PAGE_NEXT
        KeyEvent.KEYCODE_PAGE_UP -> GestureAction.PAGE_PREVIOUS
        KeyEvent.KEYCODE_MOVE_HOME -> GestureAction.JUMP_FIRST
        KeyEvent.KEYCODE_MOVE_END -> GestureAction.JUMP_LAST
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> GestureAction.ACTIVATE
        KeyEvent.KEYCODE_DEL -> GestureAction.BACK
        KeyEvent.KEYCODE_H -> GestureAction.NEXT_HEADING
        KeyEvent.KEYCODE_K -> GestureAction.NEXT_LINK
        KeyEvent.KEYCODE_R -> GestureAction.RECENTS
        KeyEvent.KEYCODE_N -> GestureAction.NOTIFICATIONS
        KeyEvent.KEYCODE_Q -> GestureAction.QUICK_SETTINGS
        KeyEvent.KEYCODE_M -> GestureAction.OPEN_GLOBAL_MENU
        KeyEvent.KEYCODE_L -> GestureAction.OPEN_LOCAL_MENU
        KeyEvent.KEYCODE_T -> GestureAction.READ_FROM_TOP
        KeyEvent.KEYCODE_Z -> GestureAction.REPEAT_LAST
        KeyEvent.KEYCODE_F -> GestureAction.SEARCH_SCREEN
        KeyEvent.KEYCODE_S -> GestureAction.SPEAK_STATUS
        KeyEvent.KEYCODE_O -> GestureAction.OCR_SCREEN
        KeyEvent.KEYCODE_D -> GestureAction.DESCRIBE_FOCUSED
        KeyEvent.KEYCODE_C -> GestureAction.COPY_FOCUSED
        KeyEvent.KEYCODE_P -> GestureAction.TOGGLE_KEYS_PAUSE
        KeyEvent.KEYCODE_G -> GestureAction.SAY_LOCATION
        KeyEvent.KEYCODE_X -> GestureAction.CLEAR_FIELD
        KeyEvent.KEYCODE_V -> GestureAction.VOLUME_EXCLUDE_APP
        else -> null
    }

    private fun withShift(keyCode: Int): GestureAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT -> GestureAction.UNIT_NEXT
        KeyEvent.KEYCODE_DPAD_LEFT -> GestureAction.UNIT_PREVIOUS
        KeyEvent.KEYCODE_DPAD_UP -> GestureAction.CYCLE_GRANULARITY
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> GestureAction.LONG_CLICK_FOCUSED
        KeyEvent.KEYCODE_DEL -> GestureAction.HOME
        KeyEvent.KEYCODE_H -> GestureAction.PREVIOUS_HEADING
        KeyEvent.KEYCODE_K -> GestureAction.PREVIOUS_LINK
        KeyEvent.KEYCODE_T -> GestureAction.READ_FROM_CURRENT
        KeyEvent.KEYCODE_X -> GestureAction.RESTORE_CLEARED
        else -> null
    }
}
