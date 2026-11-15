package com.example.vnscreenreader.core

/**
 * Nhập phím bằng cách nhấc tay: rê ngón tay tới phím (được đọc), nhấc tay thì phím được nhấn.
 * Máy trạng thái thuần: chỉ nhớ "phím đang chạm" và thời điểm, KHÔNG gọi hệ thống; phần nhấn phím do phiên đọc làm.
 *
 * Quy tắc an toàn để không gõ nhầm:
 * - Chỉ phím (cuối cùng ngón tay lướt tới) mới được nhấn; lướt sang phần tử khác phím thì hủy.
 * - Cử chỉ (vuốt, nhiều ngón) bắt đầu thì hủy.
 * - Mỗi lần chạm chỉ nhấn tối đa một lần (hệ thống báo hai sự kiện kết thúc liên tiếp).
 * - Chạm quá lâu ([maxAgeMs]) rồi mới nhấc: coi như người dùng đang nghe/tìm phím, không nhấn.
 */
class LiftToTypePolicy(private val clock: () -> Long, private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
    private var onKey = false
    private var since = 0L

    /** Ngón tay vừa lướt tới một phần tử; [isKey] cho biết đó có phải phím bàn phím không. */
    fun onHover(isKey: Boolean) {
        onKey = isKey
        since = clock()
    }

    /** Hệ thống bắt đầu nhận diện một cử chỉ: không phải thao tác gõ. */
    fun onGestureStart() {
        onKey = false
    }

    /** Ngón tay nhấc lên. true = cần nhấn phím đang chạm. */
    fun onTouchEnd(enabled: Boolean): Boolean {
        val fire = enabled && onKey && clock() - since <= maxAgeMs
        onKey = false
        return fire
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 5_000L
    }
}
