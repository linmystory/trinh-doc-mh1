package com.example.vnscreenreader.core

/**
 * Nhận biết một thao tác có thật sự tác dụng hay không, để KHÔNG thử lại khi thao tác đã chạy nhưng hệ thống trả
 * "thất bại" (nhiều WebView làm vậy) — thử lại sẽ nhấn hai lần, ví dụ bật rồi tắt lại một công tắc.
 */
object ClickEffect {
    /** [after] = null nghĩa là nút đã biến mất (thường là đã chuyển màn hình) và cũng tính là có tác dụng. */
    fun changed(before: String, after: String?): Boolean = after == null || after != before
}

/** Chữ ký của màn hình (danh sách nhãn các phần tử đang hiện): so sánh trước/sau một cú vuốt giả lập. */
object ScreenSignature {
    fun of(labels: List<String>): Int = labels.hashCode()
    fun changed(before: Int, after: Int): Boolean = before != after
}
