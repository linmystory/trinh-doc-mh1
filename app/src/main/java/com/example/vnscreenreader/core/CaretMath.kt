package com.example.vnscreenreader.core

/** Kết quả một lần dời con trỏ trong ô nhập bằng vuốt xuống/lên (xem [CaretMath]). */
sealed interface CaretMove {
    /** Con trỏ đã dời; [text] là đoạn chữ vừa đi qua, để đọc ra. */
    data class Moved(val text: String) : CaretMove

    /** Hết chữ theo hướng này (hoặc ô trống): người gọi sang phần tử bên cạnh để không bị kẹt trong ô. */
    data object Edge : CaretMove

    /** Không dời được (không phải ô nhập, ô mật khẩu, hoặc hệ thống từ chối): đọc theo cách thường. */
    data object Unavailable : CaretMove
}

/**
 * Tính vị trí con trỏ mới khi vuốt xuống/lên trong ô nhập, theo đúng cách các trình đọc màn hình chuẩn duyệt chữ:
 *  - Tiến: con trỏ nhảy tới CUỐI đơn vị kế tiếp (đơn vị đầu tiên còn kết thúc sau con trỏ); từ giữa một từ thì tới cuối từ đó.
 *  - Lùi: con trỏ nhảy tới ĐẦU đơn vị liền trước (đơn vị cuối cùng bắt đầu trước con trỏ).
 * Đoạn chữ con trỏ vừa đi qua là phần được đọc ra. Thuần hàm, không đụng tới Android.
 */
object CaretMath {

    /**
     * Vị trí con trỏ mới khi dời từ [caret] theo [unit] về phía [forward]; null khi không còn đơn vị nào theo hướng đó (đã ở
     * cuối/đầu chữ, hoặc chữ rỗng). [caret] ngoài khoảng hợp lệ được kẹp vào [0, text.length].
     */
    fun target(text: String, caret: Int, unit: TextGranularity, forward: Boolean): Int? {
        if (text.isEmpty()) return null
        val position = caret.coerceIn(0, text.length)
        val ranges = TextUnits.ranges(text, unit)
        return if (forward) {
            ranges.firstOrNull { it.last + 1 > position }?.let { it.last + 1 }
        } else {
            ranges.lastOrNull { it.first < position }?.first
        }
    }

    /** Đoạn chữ nằm giữa hai vị trí con trỏ (không phân biệt thứ tự), để đọc ra sau khi dời. */
    fun traversed(text: String, from: Int, to: Int): String {
        val start = minOf(from, to).coerceIn(0, text.length)
        val end = maxOf(from, to).coerceIn(0, text.length)
        return text.substring(start, end)
    }
}
