package com.example.vnscreenreader.core

/**
 * Ngân sách duyệt cây giao diện. Trước đây giới hạn 1.000 tính cả nút ẨN nên trang dài nhiều nút ẩn (ViewPager,
 * RecyclerView giữ view cũ) bị cắt mất phần nội dung đang hiển thị. Nay:
 *  - [maxVisible] chỉ đếm nút đang hiển thị (đó mới là thứ người dùng nghe được);
 *  - [maxTotal] là trần cứng cho mọi nút đã lấy ra, vì mỗi lần `getChild` là một lần IPC tốn kém.
 */
class TraversalBudget(private val maxVisible: Int = DEFAULT_MAX_VISIBLE, private val maxTotal: Int = DEFAULT_MAX_TOTAL) {
    private var visible = 0
    private var total = 0

    val canContinue: Boolean get() = visible < maxVisible && total < maxTotal

    fun record(isVisible: Boolean) {
        total++
        if (isVisible) visible++
    }

    companion object {
        const val DEFAULT_MAX_VISIBLE = 1500
        const val DEFAULT_MAX_TOTAL = 6000
    }
}
