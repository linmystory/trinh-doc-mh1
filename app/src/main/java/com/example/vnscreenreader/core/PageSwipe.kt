package com.example.vnscreenreader.core

/**
 * Hình học cú vuốt NGANG giả lập để chuyển trang khi không nút nào nhận hành động cuộn/chuyển trang (ví dụ màn hình
 * chính của một số launcher dùng khung tự vẽ). Thuần hàm, không phụ thuộc Android, để kiểm thử.
 */
object SwipeGeometry {

    class Line(val startX: Int, val startY: Int, val endX: Int, val endY: Int)

    private const val MIN_WIDTH_PX = 200
    private const val MIN_HEIGHT_PX = 100

    /** Vuốt DỌC qua giữa vùng; tiến (nội dung đi lên) = ngón tay đi từ dưới lên, 75% → 25% chiều cao. */
    fun vertical(left: Int, top: Int, right: Int, bottom: Int, forward: Boolean): Line? {
        val width = right - left
        val height = bottom - top
        if (width < 100 || height < MIN_WIDTH_PX) return null
        val x = left + width / 2
        val near = top + height * 25 / 100
        val far = top + height * 75 / 100
        return if (forward) Line(x, far, x, near) else Line(x, near, x, far)
    }

    /**
     * Đường vuốt nằm ngang qua GIỮA vùng [left, top, right, bottom], từ 80% tới 20% chiều rộng (tránh mép màn hình vì
     * cú vuốt từ mép bị hệ thống hiểu là "Quay lại"). Chuyển trang kế (forward) = nội dung trượt sang trái = ngón
     * tay đi từ phải sang trái; bố cục phải-sang-trái ([rtl]) thì ngược lại. null nếu vùng quá nhỏ.
     */
    fun horizontal(left: Int, top: Int, right: Int, bottom: Int, forward: Boolean, rtl: Boolean): Line? {
        val width = right - left
        val height = bottom - top
        if (width < MIN_WIDTH_PX || height < MIN_HEIGHT_PX) return null
        val y = top + height / 2
        val near = left + width * 2 / 10
        val far = left + width * 8 / 10
        val fingerGoesLeft = forward != rtl
        return if (fingerGoesLeft) Line(far, y, near, y) else Line(near, y, far, y)
    }
}

/**
 * Đoán một khung có phải bộ chuyển trang NGANG theo tên lớp. Chỉ dùng khi khung đó chỉ báo FORWARD/BACKWARD chung
 * (không báo trái/phải): tránh nhầm danh sách DỌC, vì cuộn "kế" của danh sách dọc là cuộn xuống chứ không sang trang.
 */
object PagerHints {

    private val MARKERS = listOf("pager", "horizontal", "workspace", "pagedview", "carousel", "viewflipper")

    fun looksHorizontalPager(className: CharSequence?): Boolean {
        val name = className?.toString()?.lowercase() ?: return false
        return MARKERS.any { it in name }
    }
}
