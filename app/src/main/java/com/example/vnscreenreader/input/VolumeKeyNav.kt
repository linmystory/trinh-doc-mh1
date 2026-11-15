package com.example.vnscreenreader.input

import com.example.vnscreenreader.gesture.Gesture

/**
 * Máy trạng thái phím âm lượng. Thuần logic (không đụng Android, không hẹn giờ) để kiểm thử; lớp gọi tự hẹn giờ nhấn giữ
 * rồi gọi [onLongPress]. Trả về "cử chỉ" phím ([Gesture] có `fingers = 0`) để tra hành động trong bảng gán, nên người dùng
 * đổi được từng kiểu bấm.
 *
 * - Bấm nhanh: thực hiện khi NHẢ phím ([Gesture.VOLUME_UP_SHORT] / [Gesture.VOLUME_DOWN_SHORT]).
 * - Giữ một phím [LONG_PRESS_MS]: [Gesture.VOLUME_UP_LONG] / [Gesture.VOLUME_DOWN_LONG], một lần cho mỗi lần giữ.
 * - Nhấn cả hai: [Gesture.VOLUME_BOTH] ngay khi phím thứ hai xuống, một lần cho đến khi nhả hết; không sinh thêm bấm nhanh.
 *
 * Tự dọn trạng thái "kẹt" (mất sự kiện nhả phím) bằng [expireIfIdle].
 */
class VolumeKeyNav(private val minChordGapMs: Long = 0L) {
    enum class Key { UP, DOWN }

    private var upHeld = false
    private var downHeld = false
    private var upLong = false
    private var downLong = false
    private var chordFired = false
    private var lastEventMs = 0L
    private var upDownAtMs = 0L
    private var downDownAtMs = 0L

    fun isHeld(key: Key): Boolean = if (key == Key.UP) upHeld else downHeld

    /** Gọi khi phím xuống. [repeat] = KeyEvent.repeatCount. Trả về cử chỉ cần chạy ngay (chỉ khi nhấn cả hai). */
    fun onDown(key: Key, repeat: Int, nowMs: Long): Gesture? {
        lastEventMs = nowMs
        if (repeat > 0) return null
        if (isHeld(key)) reset() // đã giữ mà lại có "xuống" mới = đã lỡ mất sự kiện nhả
        setHeld(key, true)
        setLong(key, false)
        if (key == Key.UP) upDownAtMs = nowMs else downDownAtMs = nowMs
        if (isHeld(other(key)) && !chordFired) {
            chordFired = true
            // Hai phím xuống gần như cùng lúc (dưới ngưỡng): nuốt cả hai, không làm gì. Dùng cho Android mới mà hệ thống tự
            // xử lý tổ hợp hai phím âm lượng (phím tắt trợ năng, chụp màn hình), để không giành quyền với hệ thống.
            val gap = nowMs - (if (key == Key.UP) downDownAtMs else upDownAtMs)
            return if (gap < minChordGapMs) null else Gesture.VOLUME_BOTH
        }
        return null
    }

    /** Hết thời gian nhấn giữ của [key]. Trả về cử chỉ giữ nếu phím vẫn đang giữ một mình. */
    fun onLongPress(key: Key): Gesture? {
        if (!isHeld(key) || isHeld(other(key)) || chordFired || isLong(key)) return null
        setLong(key, true)
        return if (key == Key.DOWN) Gesture.VOLUME_DOWN_LONG else Gesture.VOLUME_UP_LONG
    }

    /** Gọi khi phím nhả. Trả về cử chỉ bấm nhanh nếu có. Nếu phím không được giữ thì trả về null. */
    fun onUp(key: Key, nowMs: Long): Gesture? {
        lastEventMs = nowMs
        if (!isHeld(key)) return null
        val wasLong = isLong(key)
        setHeld(key, false)
        setLong(key, false)
        if (chordFired) {
            if (!isHeld(other(key))) chordFired = false
            return null
        }
        if (wasLong) return null
        return if (key == Key.DOWN) Gesture.VOLUME_DOWN_SHORT else Gesture.VOLUME_UP_SHORT
    }

    /** Xóa trạng thái nếu đã quá [maxIdleMs] không có sự kiện phím nào mà vẫn tưởng đang giữ. */
    fun expireIfIdle(nowMs: Long, maxIdleMs: Long = MAX_IDLE_MS) {
        if ((upHeld || downHeld) && nowMs - lastEventMs > maxIdleMs) reset()
    }

    fun reset() {
        upHeld = false
        downHeld = false
        upLong = false
        downLong = false
        chordFired = false
    }

    private fun other(key: Key) = if (key == Key.UP) Key.DOWN else Key.UP
    private fun setHeld(key: Key, v: Boolean) { if (key == Key.UP) upHeld = v else downHeld = v }
    private fun isLong(key: Key) = if (key == Key.UP) upLong else downLong
    private fun setLong(key: Key, v: Boolean) { if (key == Key.UP) upLong = v else downLong = v }

    companion object {
        const val LONG_PRESS_MS = 500L
        const val MAX_IDLE_MS = 3_000L
    }
}
