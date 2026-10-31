package com.example.vnscreenreader.core

/**
 * Quy tắc hàng đợi giọng nói: ngắt ngay khi cần, và không để hàng đợi cũ chất đống.
 * Đơn vị đếm là CÂU (một lần gọi speak, có thể gồm nhiều đoạn và khoảng lặng), xem [SpeechPendingTracker].
 */
object SpeechQueuePolicy {

    const val MAX_PENDING = 3

    /** FLUSH: ngắt câu đang đọc. ADD: xếp hàng. DROP: bỏ câu mới (ưu tiên thấp mà hàng đã đầy). */
    enum class Mode { FLUSH, ADD, DROP }

    fun modeFor(interrupt: Boolean, pendingMessages: Int): Mode =
        when {
            interrupt -> Mode.FLUSH
            // Lời ưu tiên thấp (thông báo, "đã chạm") KHÔNG được xóa câu đang đọc: hàng đầy thì bỏ câu mới.
            pendingMessages >= MAX_PENDING -> Mode.DROP
            else -> Mode.ADD
        }
}
