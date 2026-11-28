package com.example.vnscreenreader.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Bản sao nhẹ của AccessibilityEvent. Dữ liệu được trích ngay trên luồng chính
 * (vì hệ thống có thể tái sử dụng event gốc), rồi chuyển sang luồng nền xử lý.
 *
 * Cố ý KHÔNG phải `data class`: `toString()` tự sinh sẽ in cả nội dung văn bản người dùng và
 * AccessibilityNodeInfo (rò rỉ dữ liệu cá nhân nếu ai đó lỡ ghi log - CWE-532).
 */
class ReaderEvent(
    val type: Int,
    val packageName: String?,
    val text: String,
    val fromIndex: Int,
    val addedCount: Int,
    val removedCount: Int,
    val isPassword: Boolean,
    val source: AccessibilityNodeInfo?,
    val timeMs: Long,
    /** Từng đoạn chữ riêng của sự kiện (thông báo: tiêu đề, nội dung...). */
    val texts: List<String> = emptyList(),
    /** Tên lớp của nguồn sự kiện; dùng để nhận ra Toast ("android.widget.Toast"). */
    val className: String? = null,
    /** Cuối vùng chọn (TYPE_VIEW_TEXT_SELECTION_CHANGED); trùng [fromIndex] khi chỉ là con trỏ. */
    val toIndex: Int = -1,
    /** Độ dài toàn bộ văn bản của ô nhập (TYPE_VIEW_TEXT_SELECTION_CHANGED). */
    val itemCount: Int = -1,
    /** Cờ CONTENT_CHANGE_TYPE_* của TYPE_WINDOW_CONTENT_CHANGED. */
    val contentChangeTypes: Int = 0,
    /** Văn bản của ô nhập TRƯỚC khi đổi (chỉ TYPE_VIEW_TEXT_CHANGED); null nếu ứng dụng không gửi. Dùng để biết đã xóa/chèn gì. */
    val beforeText: String? = null,
) {
    fun dedupKey(): String =
        "$type|${source?.viewIdResourceName}|$text|$fromIndex|$toIndex|$addedCount|$removedCount"

    /** Chỉ lộ metadata không nhạy cảm. */
    override fun toString(): String = "ReaderEvent(type=$type, package=$packageName)"

    companion object {
        fun from(event: AccessibilityEvent): ReaderEvent {
            val separator = if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) "" else " "
            return ReaderEvent(
                type = event.eventType,
                packageName = event.packageName?.toString(),
                text = event.text.joinToString(separator),
                fromIndex = event.fromIndex,
                addedCount = event.addedCount,
                removedCount = event.removedCount,
                isPassword = event.isPassword,
                source = event.source,
                timeMs = event.eventTime,
                texts = event.text.map { it.toString() },
                className = event.className?.toString(),
                toIndex = event.toIndex,
                itemCount = event.itemCount,
                contentChangeTypes = event.contentChangeTypes,
                beforeText = if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) event.beforeText?.toString() else null,
            )
        }
    }
}
