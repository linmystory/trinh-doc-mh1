package com.example.vnscreenreader.core

/**
 * Bỏ qua sự kiện trùng lặp trong một khoảng thời gian ngắn (debounce), nhớ [capacity] khóa gần nhất (LRU)
 * nên cả kiểu bão sự kiện xen kẽ A,B,A,B cũng bị lọc (bản cũ chỉ nhớ một khóa).
 *
 * Mỗi lần gặp lại khóa, mốc thời gian được làm mới => bão sự kiện liên tục vẫn bị chặn.
 * Bộ nhớ bị chặn trên bởi [capacity]. Không thread-safe: chỉ dùng trên một luồng xử lý duy nhất.
 *
 * Độ phức tạp: O(1) thời gian mỗi lần gọi, O(capacity) không gian.
 */
class EventDeduplicator(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity >= 1) { "capacity phải >= 1" }
    }

    private val lastSeen = object : LinkedHashMap<String, Long>(capacity * 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean = size > capacity
    }

    fun shouldProcess(key: String, nowMs: Long): Boolean {
        val previous = lastSeen.put(key, nowMs)
        // Thời gian đi lùi (đồng hồ chỉnh lại) không bị coi là trùng.
        return previous == null || nowMs - previous !in 0..windowMs
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 120L
        const val DEFAULT_CAPACITY = 16
    }
}
