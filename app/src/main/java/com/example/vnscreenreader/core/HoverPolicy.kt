package com.example.vnscreenreader.core

/**
 * Quyết định một phần tử có nên được đọc khi người dùng đặt ngón tay lên (khám phá bằng cảm ứng).
 *
 * Chỉ đọc phần tử có nội dung để nói hoặc có thể tương tác. Các khung bố cục trống (LinearLayout,
 * RecyclerView, ...) bị bỏ qua: nếu không, ngón tay lướt qua khoảng trống sẽ đọc ra "phần tử không có nhãn".
 * Tách khỏi AccessibilityNodeInfo (chỉ dùng [NodeSnapshot]) để kiểm thử bằng JVM thuần.
 */
object HoverPolicy {

    fun isReadable(node: NodeSnapshot): Boolean =
        hasLabel(node) || node.isClickable || node.isEditable || node.isCheckable

    private fun hasLabel(node: NodeSnapshot): Boolean =
        !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() || !node.hintText.isNullOrBlank() ||
            !node.labeledByText.isNullOrBlank() || !node.tooltip.isNullOrBlank()
}
