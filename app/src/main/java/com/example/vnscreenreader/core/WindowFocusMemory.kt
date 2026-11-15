package com.example.vnscreenreader.core

/**
 * Nhớ phần tử đã chọn cuối cùng của từng cửa sổ (khóa = gói + tiêu đề) để quay lại đúng chỗ. Giữ [capacity] cửa sổ gần
 * nhất. Chỉ dùng trên luồng xử lý duy nhất của phiên đọc.
 */
class WindowFocusMemory(private val capacity: Int = DEFAULT_CAPACITY) {
    private val map = object : LinkedHashMap<String, FocusTrace>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FocusTrace>?): Boolean = size > capacity
    }

    fun remember(key: String, trace: FocusTrace) {
        map[key] = trace
    }

    fun recall(key: String): FocusTrace? = map[key]

    fun clear() = map.clear()

    companion object {
        const val DEFAULT_CAPACITY = 8
    }
}
