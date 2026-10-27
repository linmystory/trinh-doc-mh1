package com.example.vnscreenreader.core

/**
 * Bỏ qua sự kiện trùng lặp liên tiếp trong một khoảng thời gian ngắn (debounce).
 * Không thread-safe: chỉ dùng trên một luồng xử lý duy nhất.
 */
class EventDeduplicator(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private var lastKey: String? = null
    private var lastTimeMs = 0L

    fun shouldProcess(key: String, nowMs: Long): Boolean {
        val isDuplicate = key == lastKey && nowMs - lastTimeMs in 0..windowMs
        lastKey = key
        lastTimeMs = nowMs
        return !isDuplicate
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 120L
    }
}
