package com.example.vnscreenreader.input

import com.example.vnscreenreader.gesture.Gesture

/**
 * Máy trạng thái nút tai nghe (KEYCODE_HEADSETHOOK): bấm một lần, bấm hai lần (trong [DOUBLE_GAP_MS]) và nhấn giữ
 * ([LONG_PRESS_MS]). Thuần logic, không hẹn giờ: lớp gọi hẹn giờ rồi gọi [onLongPress] / [onSingleTimeout].
 *
 * Bấm một lần chỉ được xác nhận sau [DOUBLE_GAP_MS] để biết có phải bấm đôi hay không.
 */
class HeadsetKeyNav {
    private var held = false
    private var longFired = false
    private var singlePending = false
    private var lastUpMs = 0L

    fun isHeld(): Boolean = held

    /** Gọi khi nút xuống (lần đầu, không phải lặp). */
    fun onDown() {
        held = true
        longFired = false
    }

    /** Hết thời gian giữ. Trả về cử chỉ giữ, một lần cho mỗi lần giữ. */
    fun onLongPress(): Gesture? {
        if (!held || longFired) return null
        longFired = true
        singlePending = false
        return Gesture.HEADSET_LONG
    }

    /** Gọi khi nút nhả. Trả về bấm đôi nếu đây là lần bấm thứ hai; bấm một lần đợi [onSingleTimeout]. */
    fun onUp(nowMs: Long): Gesture? {
        if (!held) return null
        held = false
        if (longFired) {
            longFired = false
            return null
        }
        if (singlePending && nowMs - lastUpMs <= DOUBLE_GAP_MS) {
            singlePending = false
            return Gesture.HEADSET_DOUBLE
        }
        singlePending = true
        lastUpMs = nowMs
        return null
    }

    /** Hết thời gian chờ lần bấm thứ hai. Trả về bấm một lần nếu còn đang chờ. */
    fun onSingleTimeout(): Gesture? {
        if (!singlePending) return null
        singlePending = false
        return Gesture.HEADSET_SHORT
    }

    fun reset() {
        held = false
        longFired = false
        singlePending = false
    }

    companion object {
        const val LONG_PRESS_MS = 500L
        const val DOUBLE_GAP_MS = 350L
    }
}
