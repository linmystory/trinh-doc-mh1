package com.example.vnscreenreader.core

/**
 * Quyết định một phần tử có nên được đọc khi người dùng đặt ngón tay lên (khám phá bằng cảm ứng).
 *
 * Nguyên tắc: thứ gì VUỐT tới được ([StopSelection]) thì CHẠM vào cũng phải nghe được. Chỉ đọc phần tử có nội dung để nói hoặc có thể
 * tương tác; các khung bố cục trống (LinearLayout, RecyclerView, ...) bị bỏ qua: nếu không, ngón tay lướt qua khoảng trống sẽ đọc ra
 * "phần tử không có nhãn". Tách khỏi AccessibilityNodeInfo (chỉ dùng [NodeSnapshot]) để kiểm thử bằng JVM thuần.
 */
object HoverPolicy {

    /**
     * Phần tử bị chạm TRỰC TIẾP có đáng đọc ngay không: có chữ (kể cả gợi ý/chú thích/nhãn trỏ tới), có thể thao tác (nhấn, nhập, tích),
     * hoặc có khoảng giá trị (thanh trượt, thanh tiến trình, đánh giá sao). Trước đây thiếu vế cuối nên thanh trượt không nhãn
     * vuốt tới được mà chạm vào thì im lặng.
     */
    fun isReadable(node: NodeSnapshot): Boolean =
        hasLabel(node) || node.isClickable || node.isEditable || node.isCheckable || node.hasRange

    /**
     * Khung không nhãn nhưng ứng dụng tự khai báo là MỘT khối đọc ([NodeSnapshot.isScreenReaderFocusable]; vuốt dừng ở khối này và đọc gộp
     * chữ các con). Chỉ đọc khi bên dưới có chữ để gộp: nơi gọi tra điều đó (tốn vài lần gọi hệ thống) và chỉ khi hàm này trả true.
     */
    fun needsMergedText(node: NodeSnapshot): Boolean = node.isScreenReaderFocusable && !isReadable(node)

    /** Tổ tiên có nên được đọc THAY cho phần tử con không nhãn, không thao tác đang bị chạm (ví dụ biểu tượng trang trí trong một nút bấm). */
    fun isReadableAncestor(node: NodeSnapshot): Boolean = node.isClickable

    private fun hasLabel(node: NodeSnapshot): Boolean =
        !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() || !node.hintText.isNullOrBlank() ||
            !node.labeledByText.isNullOrBlank() || !node.tooltip.isNullOrBlank()
}
