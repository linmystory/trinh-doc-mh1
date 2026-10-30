package com.example.vnscreenreader.core

/** Một vùng cuộn ứng viên: [ancestorRank] = 0 là cha gần phần tử đang chọn nhất, null nếu không phải tổ tiên. */
data class ScrollCandidate(val id: Int, val ancestorRank: Int?, val areaPx: Long)

/**
 * Thứ tự thử cuộn. Lỗi cũ: chỉ thử MỘT vùng (vùng cuộn con gần nhất hoặc vùng đầu tiên trong cây, thường là
 * danh sách ngang nhỏ) nên cuộn được một đoạn rồi báo "không cuộn được nữa" dù trang còn dài.
 * Quy tắc: tổ tiên của phần tử đang chọn trước (gần nhất trước), rồi các vùng khác theo diện tích LỚN → NHỎ
 * (vùng nội dung chính thường lớn nhất). Cùng diện tích giữ nguyên thứ tự gốc (ổn định). O(n log n).
 */
object ScrollOrdering {
    fun order(candidates: List<ScrollCandidate>): List<ScrollCandidate> {
        val ancestors = candidates.filter { it.ancestorRank != null }.sortedBy { it.ancestorRank }
        val others = candidates.filter { it.ancestorRank == null }.sortedByDescending { it.areaPx }
        return ancestors + others
    }
}
