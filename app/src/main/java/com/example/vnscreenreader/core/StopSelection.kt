package com.example.vnscreenreader.core

/**
 * Sự thật CỤC BỘ của một nút giao diện (chỉ đọc trường có sẵn, không gọi hệ thống) mà việc chọn điểm dừng khi vuốt cần.
 * Tách khỏi `AccessibilityNodeInfo` để toàn bộ quy tắc chọn được kiểm thử bằng JVM thuần ([StopSelection]).
 */
data class StopFacts(
    /** Có chữ riêng: `text` hoặc `contentDescription` không rỗng. */
    val hasLabel: Boolean = false,
    /** Không có chữ riêng nhưng có gợi ý (`hintText`) hoặc chú thích (`tooltipText`) để đọc. */
    val hasAuxLabel: Boolean = false,
    val clickable: Boolean = false,
    /** `isScreenReaderFocusable`: ứng dụng khai báo khung này là MỘT khối khi đọc (gồm cả chữ của các con). */
    val screenReaderFocusable: Boolean = false,
    val checkable: Boolean = false,
    val editable: Boolean = false,
    /** Có `RangeInfo` (thanh trượt, thanh tiến trình, đánh giá sao). */
    val range: Boolean = false,
    /** Có `RangeInfo` VÀ chỉnh được giá trị (hành động đặt tiến trình): thanh trượt thật, không phải thanh tiến trình. */
    val adjustable: Boolean = false,
    /** Khung cuộn hoặc danh sách/lưới (`isScrollable`/`collectionInfo`): ranh giới, không bao giờ gộp chữ của con. */
    val structural: Boolean = false,
) {
    /** Có thể thao tác (nhấn/tích/gõ/chỉnh giá trị): luôn là điểm dừng RIÊNG, không bao giờ bị khung cha gộp mất. */
    val interactive: Boolean get() = clickable || checkable || editable || adjustable
}

/**
 * Chọn các "điểm dừng" khi vuốt từng phần tử, trên cây phẳng theo TIỀN THỨ TỰ (thứ tự đọc): `parent[i] < i`, `parent[0] = -1`.
 *
 * Mục tiêu hàng đầu: KHÔNG làm mất phần tử người dùng cần nghe/nhấn, và không đọc một chữ hai lần.
 *  - Nút CÓ THAO TÁC (nhấn/tích/gõ/chỉnh) có nhãn: luôn là một điểm dừng riêng và là "khối" gộp các chữ không thao tác bên dưới.
 *  - Nút có thao tác KHÔNG nhãn: là một điểm dừng đọc gộp chữ các con ([mergeParts]) khi gộp được (1..[MERGE_MAX_PARTS] mảnh);
 *    không có chữ nào bên dưới và cũng không có điểm dừng nào bên dưới thì là nút chưa gắn nhãn (vẫn phải tới được để nhấn);
 *    còn là khung bọc quanh các điểm dừng khác, hoặc quá nhiều chữ (khung gốc "bấm được" cả màn hình) thì chỉ là khung bố cục:
 *    KHÔNG làm điểm dừng và KHÔNG nuốt con, các con tự là điểm dừng. Nhấn một con vẫn kích hoạt khung cha (tổ tiên bấm được).
 *  - Nút KHÔNG thao tác có nhãn: điểm dừng nhưng KHÔNG nuốt con. (Trước đây khung có `contentDescription` — danh sách, WebView,
 *    nhóm bố cục — làm mất hết chữ bên dưới.) Ngoại lệ duy nhất: khung tự khai báo là một khối đọc ([StopFacts.screenReaderFocusable]).
 *  - Nút nhập liệu/công tắc/thanh trượt chỉnh được: điểm dừng dù có nhãn hay không, dù nằm trong khối nào.
 *  - Thanh tiến trình, ô có gợi ý/chú thích mà không có chữ: điểm dừng khi KHÔNG nằm trong khối đã gộp.
 *  - Khung cuộn/danh sách là ranh giới: chữ của nó không bị khối cha gộp, và con của nó không bị khối cha nuốt.
 *
 * Mọi hàm chạy O(n) thời gian và bộ nhớ.
 */
object StopSelection {

    /**
     * Số mảnh chữ tối đa để một khung bấm được không nhãn còn được đọc gộp thành MỘT điểm dừng. Nhiều hơn là khung bố cục
     * (thẻ rất dài, khung gốc bấm được cả màn hình): tách ra từng chữ còn hơn đọc một câu dài mà vẫn thiếu.
     */
    const val MERGE_MAX_PARTS = 12

    /** Kết quả phân tích từ dưới lên: số mảnh chữ gộp được, có nội dung bên dưới không, và nút có phải điểm dừng thao tác không. */
    private class Analysis(val parts: IntArray, val below: BooleanArray, val control: BooleanArray)

    private fun analyze(parent: IntArray, facts: List<StopFacts>): Analysis {
        val n = facts.size
        val parts = IntArray(n)
        val below = BooleanArray(n)
        val control = BooleanArray(n)
        // Cha luôn đứng trước con (tiền thứ tự) nên duyệt NGƯỢC thì mọi con đã xong trước cha.
        for (i in n - 1 downTo 0) {
            val f = facts[i]
            control[i] = isControlStop(f, parts[i], below[i])
            val p = parent[i]
            if (p >= 0) {
                parts[p] += when {
                    f.structural || control[i] -> 0 // ranh giới / điểm dừng riêng: không gộp vào cha
                    f.hasLabel -> 1 // có chữ: một mảnh, các con của nó không đọc thêm
                    else -> parts[i] // khung trống: trong suốt, chữ của con tính cho cha
                }
                if (f.hasLabel || control[i] || below[i]) below[p] = true
            }
        }
        return Analysis(parts, below, control)
    }

    private fun isControlStop(f: StopFacts, parts: Int, below: Boolean): Boolean = when {
        !f.interactive -> false
        f.hasLabel -> true
        f.editable || f.checkable || f.adjustable -> true // ô nhập/công tắc/thanh trượt chưa gắn nhãn vẫn phải tới được
        f.structural -> false
        else -> unit(parts, below)
    }

    /** Khung không nhãn (bấm được hoặc tự khai báo là khối) có nên là một điểm dừng, xét theo chữ và điểm dừng bên dưới. */
    private fun unit(parts: Int, below: Boolean): Boolean = when {
        parts > MERGE_MAX_PARTS -> false
        parts > 0 -> true
        else -> !below // không có gì bên dưới: nút chưa gắn nhãn; có điểm dừng bên dưới: chỉ là khung bọc
    }

    /**
     * Chỉ số các điểm dừng (tăng dần). [parent] và [facts] cùng kích thước n; cây rỗng cho kết quả rỗng.
     */
    fun select(parent: IntArray, facts: List<StopFacts>): IntArray {
        val n = facts.size
        if (n == 0) return IntArray(0)
        val a = analyze(parent, facts)
        // consumedBelow[i]: các con của nút i đã được một khối cha gộp nhãn (nên chữ không thao tác của chúng không phải điểm dừng).
        val consumedBelow = BooleanArray(n)
        val out = IntArray(n)
        var count = 0
        for (i in 0 until n) {
            val f = facts[i]
            val p = parent[i]
            val consumed = p >= 0 && !f.structural && consumedBelow[p]
            val stop = when {
                a.control[i] -> true
                f.interactive -> false // khung bọc / quá nhiều chữ: chỉ là bố cục
                consumed -> false // chữ này đã nằm trong nhãn gộp của khối cha
                f.hasLabel -> true
                f.structural -> false
                f.screenReaderFocusable -> unit(a.parts[i], a.below[i])
                else -> f.hasAuxLabel || f.range
            }
            if (stop) out[count++] = i
            val consumer = stop && !f.structural && (f.interactive || f.screenReaderFocusable)
            consumedBelow[i] = if (f.structural) false else consumer || consumed
        }
        return out.copyOf(count)
    }

    /**
     * Chỉ số các nút có chữ được gộp vào nhãn của nút gốc (chỉ số 0), theo thứ tự đọc. Cây gồm gốc và các hậu duệ hiển thị của nó.
     *  - [everything] = false: đúng phần chữ mà điểm dừng gốc đọc thay cho mình (bỏ điểm dừng thao tác riêng và ranh giới cuộn), nhờ đó
     *    chữ không bị đọc hai lần.
     *  - [everything] = true: mọi chữ hiển thị bên dưới (vùng thông báo trực tiếp, kiểm tra "bên dưới có chữ không").
     * Nút đã có chữ thì dừng ở nó (không đọc thêm các con).
     */
    fun mergeParts(parent: IntArray, facts: List<StopFacts>, everything: Boolean = false): IntArray {
        val n = facts.size
        if (n <= 1) return IntArray(0)
        val control = if (everything) null else analyze(parent, facts).control
        val open = BooleanArray(n) // open[i]: chữ của con nút i còn được gộp vào nhãn của gốc
        open[0] = true
        val out = IntArray(n)
        var count = 0
        for (i in 1 until n) {
            if (!open[parent[i]]) continue
            val f = facts[i]
            when {
                control != null && (f.structural || control[i]) -> Unit // điểm dừng/ranh giới riêng
                f.hasLabel -> out[count++] = i
                else -> open[i] = true
            }
        }
        return out.copyOf(count)
    }
}
