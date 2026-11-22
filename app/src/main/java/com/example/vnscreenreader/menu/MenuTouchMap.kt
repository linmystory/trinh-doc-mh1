package com.example.vnscreenreader.menu

/**
 * Hình học của trình đơn khi được vẽ thành danh sách phủ màn hình để người dùng CHẠM/LƯỚT ngón tay khám phá (như mọi màn hình khác).
 * Thuần hàm, không phụ thuộc Android, để kiểm thử: lớp vẽ (service/MenuOverlay) chỉ gọi lại.
 *
 * Quy tắc: các hàng chia ĐỀU toàn bộ chiều cao [heightPx], nên chạm ở đâu trên màn hình cũng rơi vào một hàng, mục đầu ở trên cùng và
 * mục cuối ở dưới cùng (dễ đoán vị trí khi không nhìn). Hàng không thấp hơn [minRowPx] (đủ lớn để chạm): trình đơn dài quá thì chỉ hiện
 * một CỬA SỔ [visible] hàng, cửa sổ tự dịch để mục đang chọn luôn nằm trong đó (vuốt vẫn đi được mọi mục).
 *
 * Độ phức tạp: mọi thao tác O(1) thời gian và không gian.
 */
class MenuTouchMap(val count: Int, val heightPx: Int, minRowPx: Int) {

    /** Số hàng hiện cùng lúc: 0 khi không có mục hoặc không có chỗ vẽ. */
    val visible: Int =
        if (count <= 0 || heightPx <= 0) 0 else minOf(count, maxOf(1, heightPx / maxOf(1, minRowPx)))

    /** Chiều cao một hàng, px (phần dư của phép chia thuộc về hàng cuối). */
    val rowPx: Int = if (visible == 0) 0 else heightPx / visible

    /** Hàng đầu tiên lớn nhất có thể (cửa sổ không trượt quá cuối danh sách). */
    private val maxFirst: Int = (count - visible).coerceAtLeast(0)

    /**
     * Mục đầu tiên đang hiện sao cho [selected] nằm trong cửa sổ, dịch ÍT NHẤT có thể so với [currentFirst] (danh sách không nhảy khi
     * vuốt trong phạm vi đang thấy). Kết quả luôn trong 0..(count - visible).
     */
    fun firstFor(selected: Int, currentFirst: Int): Int {
        if (visible == 0) return 0
        var first = currentFirst.coerceIn(0, maxFirst)
        if (selected < first) {
            first = selected
        } else if (selected >= first + visible) {
            first = selected - visible + 1
        }
        return first.coerceIn(0, maxFirst)
    }

    /** Vị trí (trong toàn danh sách) của hàng nằm ở độ cao [y] khi hàng đầu đang hiện là [first]; -1 nếu [y] ngoài vùng vẽ. */
    fun rowAt(y: Float, first: Int): Int {
        if (visible == 0 || y.isNaN() || y < 0f || y >= heightPx.toFloat()) return -1
        val local = minOf((y / rowPx).toInt(), visible - 1)
        return first.coerceIn(0, maxFirst) + local
    }

    /** Cạnh trên của hàng thứ [local] (0 = hàng đầu đang hiện), px. */
    fun rowTop(local: Int): Int = local * rowPx

    /** Cạnh dưới của hàng thứ [local]; hàng cuối kéo dài tới hết [heightPx] để không để hở phần dư. */
    fun rowBottom(local: Int): Int = if (local >= visible - 1) heightPx else (local + 1) * rowPx
}
