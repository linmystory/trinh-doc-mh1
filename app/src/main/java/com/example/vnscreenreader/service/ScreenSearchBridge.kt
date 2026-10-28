package com.example.vnscreenreader.service

/**
 * Cầu nối trong cùng tiến trình giữa màn hình nhập từ khóa ([com.example.vnscreenreader.ScreenSearchActivity]) và
 * phiên đọc đang chạy. Phiên đăng ký bộ xử lý khi bắt đầu và PHẢI gỡ khi đóng để không giữ tham chiếu tới
 * dịch vụ đã hủy (rò rỉ bộ nhớ).
 */
object ScreenSearchBridge {

    @Volatile private var handler: ((String) -> Unit)? = null

    fun register(newHandler: (String) -> Unit) {
        handler = newHandler
    }

    fun clear() {
        handler = null
    }

    /** @return false nếu dịch vụ không chạy (không có ai nhận). */
    fun submit(query: String): Boolean {
        val current = handler ?: return false
        current(query)
        return true
    }
}
