package com.example.vnscreenreader.core

/** Duyệt nhanh theo loại phần tử (tiêu đề, liên kết). Thuần hàm. */
object QuickNav {

    /**
     * Chỉ số kế tiếp (tiến) hoặc trước đó (lùi) thỏa [matches], tính từ [current] và KHÔNG tính chính nó.
     * [current] < 0 = chưa chọn gì: tiến bắt đầu từ đầu danh sách, lùi bắt đầu từ cuối. null nếu không còn.
     * Thời gian O(count) trong trường hợp xấu nhất, bộ nhớ O(1).
     */
    fun find(count: Int, current: Int, forward: Boolean, matches: (Int) -> Boolean): Int? {
        if (count <= 0) return null
        if (forward) {
            val from = if (current < 0) 0 else current + 1
            for (i in from until count) if (matches(i)) return i
        } else {
            val from = if (current < 0) count - 1 else minOf(current, count) - 1
            for (i in from downTo 0) if (matches(i)) return i
        }
        return null
    }
}

/** Vị trí trong danh sách để đọc "Mục 3 trên 12". */
class ListPosition(val index: Int, val total: Int)

object ListPositions {

    /**
     * Ưu tiên theo hàng (danh sách dọc), không có thì theo cột (danh sách ngang). Chỉ số/số lượng không xác định
     * (ứng dụng báo -1 cho danh sách nạp dần) hoặc chỉ có một mục thì trả null: không đọc số liệu có thể sai.
     */
    fun of(rowIndex: Int, rowCount: Int, columnIndex: Int, columnCount: Int): ListPosition? {
        if (rowCount > 1 && rowIndex in 0 until rowCount) return ListPosition(rowIndex + 1, rowCount)
        if (columnCount > 1 && columnIndex in 0 until columnCount) return ListPosition(columnIndex + 1, columnCount)
        return null
    }
}
