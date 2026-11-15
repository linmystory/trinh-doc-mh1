package com.example.vnscreenreader.core

/**
 * Chống đọc lặp cho vùng cập nhật trực tiếp (live region), tiêu đề khung và Toast: một nguồn chỉ được đọc lại khi nội dung
 * đổi, hoặc khi đã quá [repeatAfterMs] (cho phép báo lại cùng một thông điệp sau một lúc). Thuần logic để kiểm thử.
 * Chỉ dùng trên một luồng (luồng xử lý sự kiện).
 */
class LiveRegionGate(
    private val repeatAfterMs: Long = DEFAULT_REPEAT_AFTER_MS,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private class Seen(val text: String, val atMs: Long)

    private val seen = object : LinkedHashMap<String, Seen>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Seen>?): Boolean = size > capacity
    }

    /** true = được đọc (và được ghi nhớ); false = trùng lặp gần đây. */
    fun allow(key: String, text: String, nowMs: Long): Boolean {
        val last = seen[key]
        if (last != null && last.text == text && nowMs - last.atMs < repeatAfterMs) return false
        seen[key] = Seen(text, nowMs)
        return true
    }

    fun clear() = seen.clear()

    companion object {
        const val DEFAULT_REPEAT_AFTER_MS = 4_000L
        const val DEFAULT_CAPACITY = 32
    }
}
