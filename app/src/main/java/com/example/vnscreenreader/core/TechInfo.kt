package com.example.vnscreenreader.core

/**
 * Câu mô tả KỸ THUẬT của một phần tử (lớp view, bấm/tích được, có nằm trong cây trợ năng "quan trọng" không, mã).
 * Dùng để chẩn đoán vì sao một nút không tới được/không bấm được (ví dụ ô tích bị ứng dụng giấu khỏi trợ năng).
 * Thuần hàm, không phụ thuộc Android.
 */
object TechInfo {

    fun describe(
        className: CharSequence?,
        clickable: Boolean,
        checkable: Boolean,
        important: Boolean,
        viewId: String?,
    ): String = t(
        S.MSG_TECH_INFO,
        shortClass(className),
        yesNo(clickable),
        yesNo(checkable),
        yesNo(important),
        viewId?.takeIf { it.isNotBlank() }?.substringAfter(":id/") ?: t(S.TECH_NONE),
    )

    /** "android.widget.CheckBox" → "CheckBox"; rỗng → "không có". */
    fun shortClass(className: CharSequence?): String =
        className?.toString()?.trim()?.substringAfterLast('.')?.takeIf { it.isNotEmpty() } ?: t(S.TECH_NONE)

    private fun yesNo(value: Boolean) = if (value) t(S.TECH_YES) else t(S.TECH_NO)
}

/**
 * Chọn điều khiển nằm CẠNH một phần tử (ví dụ ô tích bên trái dòng "Tôi đồng ý với điều khoản") khi chính phần tử
 * đó không bấm được. Thuần hàm trên hình chữ nhật để kiểm thử offline.
 */
object NearbyControlPicker {

    class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val height: Int get() = bottom - top
    }

    class Candidate(val id: Int, val box: Box, val checkable: Boolean, val clickable: Boolean)

    /** Khoảng cách ngang tối đa (px) giữa phần tử và điều khiển cạnh nó. */
    const val MAX_GAP_PX = 300

    /**
     * Ứng viên cùng hàng (chồng lấn dọc ít nhất nửa chiều cao của hộp thấp hơn) và gần nhất theo chiều ngang;
     * ô tích được ưu tiên hơn nút bấm thường. null nếu không có.
     */
    fun pick(anchor: Box, candidates: List<Candidate>, maxGap: Int = MAX_GAP_PX): Int? =
        candidates.asSequence()
            .filter { it.checkable || it.clickable }
            .filter { sameRow(anchor, it.box) }
            .map { it to horizontalGap(anchor, it.box) }
            .filter { (_, gap) -> gap <= maxGap }
            .sortedWith(compareBy({ !it.first.checkable }, { it.second }, { it.first.id }))
            .firstOrNull()?.first?.id

    private fun sameRow(a: Box, b: Box): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val smaller = minOf(a.height, b.height)
        return smaller > 0 && overlap * 2 >= smaller
    }

    private fun horizontalGap(a: Box, b: Box): Int = when {
        b.right <= a.left -> a.left - b.right
        b.left >= a.right -> b.left - a.right
        else -> 0
    }
}
