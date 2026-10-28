package com.example.vnscreenreader.feedback

/**
 * Giới hạn tần suất phản hồi: khi ngón tay lướt nhanh qua nhiều phần tử, rung/âm báo liên tục gây khó chịu và
 * tốn pin. Chỉ cho phép một phản hồi mỗi [minIntervalMs]. Không thread-safe (dùng trên luồng xử lý duy nhất).
 */
class FeedbackThrottle(private val minIntervalMs: Long = DEFAULT_INTERVAL_MS) {

    private var lastMs = Long.MIN_VALUE

    fun tryAcquire(nowMs: Long): Boolean {
        val allowed = lastMs == Long.MIN_VALUE || nowMs - lastMs !in 0 until minIntervalMs
        if (allowed) lastMs = nowMs
        return allowed
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 45L
    }
}
