package com.example.vnscreenreader.core

import android.view.accessibility.AccessibilityEvent

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

    /**
     * Cổng vào của luồng xử lý sự kiện: loại sự kiện được miễn ([EventDedupPolicy.exempt]) luôn được qua và không đụng tới bộ nhớ trùng;
     * các loại còn lại theo [shouldProcess].
     */
    fun admit(eventType: Int, key: String, nowMs: Long): Boolean = EventDedupPolicy.exempt(eventType) || shouldProcess(key, nowMs)

    companion object {
        const val DEFAULT_WINDOW_MS = 120L
        const val DEFAULT_CAPACITY = 16
    }
}

/**
 * Loại sự kiện KHÔNG được qua [EventDeduplicator].
 *
 * Khóa trùng chỉ gồm loại sự kiện, id view và chữ sự kiện. Với chạm vào phần tử (HOVER_ENTER) khóa đó KHÔNG phân biệt được các phần tử:
 * Compose, web và ứng dụng tự vẽ thường không có id và chữ sự kiện rỗng; danh sách lặp lại cùng một chữ ("Xóa", "Trả lời") cũng cùng
 * khóa. Vì mốc thời gian bị làm mới cả khi sự kiện bị bỏ, lướt ngón tay nhanh (mỗi phần tử dưới 120 ms) chỉ đọc phần tử ĐẦU và bỏ hết
 * các phần tử sau, kể cả phần tử ngón tay dừng lại. Chạm là thao tác chọn của người dùng nên không được rơi; việc chặn tiếng vọng
 * của chính phần tử vừa đọc do [HoverEchoGuard] đảm nhiệm.
 */
object EventDedupPolicy {

    fun exempt(eventType: Int): Boolean = eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
}
