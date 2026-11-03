package com.example.vnscreenreader.core

/**
 * Áp thứ tự duyệt do ứng dụng khai báo (`accessibilityTraversalAfter` / `accessibilityTraversalBefore`) lên danh sách điểm
 * dừng đã duyệt theo cây. Thuần hàm để kiểm thử.
 */
object TraversalOrder {

    /**
     * [afterOf]\[i\] = chỉ số điểm dừng mà phần tử i phải đứng NGAY SAU (-1 nếu không khai báo); [beforeOf]\[i\] = điểm dừng mà i
     * phải đứng NGAY TRƯỚC. Trả danh sách chỉ số theo thứ tự mới. Mỗi phần tử được dời tối đa một lần cho mỗi loại ràng buộc
     * nên luôn dừng (khai báo mâu thuẫn hay vòng tròn không gây lặp vô hạn, kết quả chỉ là một thứ tự hợp lệ nào đó).
     * Thời gian O(k * n) với k là số phần tử có khai báo; k thường bằng 0.
     */
    fun apply(count: Int, afterOf: IntArray, beforeOf: IntArray): List<Int> {
        val order = (0 until count).toMutableList()
        fun move(item: Int, target: Int, after: Boolean) {
            if (item == target || target !in 0 until count) return
            order.remove(item)
            val at = order.indexOf(target)
            if (at < 0) {
                order.add(item) // không xảy ra khi đầu vào hợp lệ; giữ nguyên phần tử thay vì làm mất
                return
            }
            order.add(if (after) at + 1 else at, item)
        }
        for (i in 0 until count) if (afterOf.getOrElse(i) { -1 } >= 0) move(i, afterOf[i], after = true)
        for (i in 0 until count) if (beforeOf.getOrElse(i) { -1 } >= 0) move(i, beforeOf[i], after = false)
        return order
    }
}
