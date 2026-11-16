package com.example.vnscreenreader.core

import kotlin.math.abs

/**
 * Một phần tử nằm trong vùng cuộn: nhãn (rỗng nếu không có) và hộp theo toạ độ màn hình. Chỉ giữ số liệu thô để so sánh
 * trước/sau khi cuộn, không phụ thuộc Android.
 */
data class ScrollItem(val label: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/**
 * Kết quả đối chiếu vùng cuộn trước/sau một lần cuộn. [index]: chỉ số (trong ảnh SAU) của phần tử cần chọn, null nếu không có
 * phần tử mới. [shift]: quãng nội dung đã trượt (px, dương = lên) nếu đo được. [old]/[pinned]: số phần tử đã thấy từ trước /
 * đứng yên tại chỗ (thanh dính, tiêu đề cố định). Hai số cuối chỉ để ghi nhật ký chẩn đoán.
 */
data class ScrollMatch(val index: Int?, val shift: Int?, val old: Int, val pinned: Int)

/**
 * Sau khi cuộn vùng chứa một lần, chọn phần tử kế tiếp (tiến: phần tử MỚI đầu tiên; lùi: phần tử mới CUỐI cùng).
 *
 * Cách cũ chỉ so NHÃN chữ ("đã thấy thì bỏ"): hụt khi nhiều mục trùng chữ ("Thích", "Chia sẻ", giá tiền), và có thể bỏ sót
 * mục mới vì trùng chữ với mục cũ. Ở đây phần tử được coi là "cũ" chỉ khi trùng nhãn VÀ nằm đúng chỗ dự kiến sau khi nội dung
 * trượt một quãng đo được ([ScrollMatch.shift]); quãng trượt lấy từ các cặp trước/sau cùng nhãn, cùng cột, cùng chiều cao
 * (cặp nguyên vẹn, không bị cắt mép), nhóm theo giá trị và chọn nhóm nhiều phiếu nhất. Ngoài ra:
 *  - phần tử đứng yên (cùng nhãn, đúng hộp cũ) là thanh/tiêu đề dính: không bao giờ được chọn (trừ khi nhãn đó cũng có bản đã trượt
 *    theo nội dung: hàng đều nhau, nút "Thích" lặp ở mỗi bài, thì bản trùng chỗ là nội dung mới);
 *  - phần tử bị cắt ở mép (đoạn văn dài nửa trên đã đọc, nửa dưới vừa lộ ra) vẫn là phần tử CŨ nên không đọc lại.
 * Giới hạn đã biết: danh sách toàn mục GIỐNG HỆT nhau (cùng nhãn, cùng hộp) không đo được quãng trượt, và tiêu đề dính ĐỔI chữ khi cuộn
 * (tên mục hiện hành) vẫn bị coi là phần tử mới.
 * Thuần hàm, O(n + m) trung bình (n, m = số phần tử trước/sau; so khớp theo bảng băm nhãn).
 */
object ScrollContinuation {
    private const val TOL_Y = 3
    private const val TOL_X = 4

    /** Phần tử nằm trong 1/4 chiều cao trên cùng hoặc dưới cùng của vùng là ứng viên của thanh dính. */
    private const val EDGE_DIVISOR = 4

    /** Chỉ số (trong [after]) của phần tử cần chọn sau khi cuộn, hoặc null nếu cuộn xong mà không có gì mới. */
    fun pick(before: List<ScrollItem>, after: List<ScrollItem>, container: IntArray, forward: Boolean): Int? =
        analyze(before, after, container, forward).index

    /**
     * [container] = hộp (trái, trên, phải, dưới) của vùng cuộn. [before]/[after] = các phần tử nằm trong vùng đó, theo thứ tự
     * đọc, trước và sau khi cuộn.
     */
    fun analyze(before: List<ScrollItem>, after: List<ScrollItem>, container: IntArray, forward: Boolean): ScrollMatch {
        if (after.isEmpty() || container.size < 4) return ScrollMatch(null, null, 0, 0)

        val labeledBefore = HashMap<String, MutableList<ScrollItem>>()
        for (item in before) {
            if (item.label.isNotEmpty()) labeledBefore.getOrPut(item.label) { ArrayList() }.add(item)
        }
        val afterCounts = HashMap<String, Int>()
        for (item in after) {
            if (item.label.isNotEmpty()) afterCounts[item.label] = (afterCounts[item.label] ?: 0) + 1
        }

        val shift = measureShift(labeledBefore, after, afterCounts, forward)

        // "Đứng yên": cùng nhãn và đúng hộp cũ. Đã đo được quãng trượt mà vẫn đứng yên thì là thanh dính (trừ nhãn đi theo nội dung, xem
        // dưới). Chưa đo được: nếu quá nửa vùng đứng yên thì cả trang đứng yên (không cuộn); còn lại chỉ coi gần mép là thanh dính, ở
        // giữa là trùng hợp (trang mới vô tình có mục giống hệt chỗ cũ).
        val still = BooleanArray(after.size) { isStill(after[it], labeledBefore, before) }
        val stillIsPinned = shift != null || still.count { it } * 2 >= after.size
        val band = (container[3] - container[1]) / EDGE_DIVISOR

        // Phần tử có bản "trước" nằm đúng chỗ sau khi trượt [shift]: chắc chắn là nội dung ĐÃ THẤY nay trượt lên/xuống. Nhãn của chúng là
        // nhãn "đi theo nội dung": mục khác cùng nhãn mà vô tình trùng đúng chỗ cũ (hàng đều nhau, nút "Thích" lặp ở mỗi bài) là nội dung
        // MỚI chứ không phải thanh dính.
        val shifted = BooleanArray(after.size)
        val scrollingLabels = HashSet<String>()
        if (shift != null) {
            for ((i, item) in after.withIndex()) {
                val sameLabel = labeledBefore[item.label] ?: continue
                if (sameLabel.any { b -> sameColumn(item, b) && abs(b.top - shift - item.top) <= TOL_Y }) {
                    shifted[i] = true
                    scrollingLabels += item.label
                }
            }
        }

        val old = BooleanArray(after.size)
        val pinned = BooleanArray(after.size)
        for ((i, item) in after.withIndex()) {
            if (shifted[i]) {
                old[i] = true
                continue
            }
            if (still[i] && item.label !in scrollingLabels && (stillIsPinned || nearEdge(item, container, band))) {
                pinned[i] = true
                continue
            }
            val sameLabel = labeledBefore[item.label] ?: continue
            if (sameLabel.any { b -> sameColumn(item, b) && continuesAtEdge(b, item, container, shift, forward) }) old[i] = true
        }

        val pinnedCount = pinned.count { it }
        val oldCount = old.count { it }
        val candidates = after.indices.filter { !old[it] && !pinned[it] }
        if (candidates.isEmpty()) return ScrollMatch(null, shift, oldCount, pinnedCount)
        // Gần như cả vùng đứng yên, chỉ vài chữ đổi (đồng hồ, bộ đếm): không phải là đã cuộn.
        if (shift == null && candidates.size < pinnedCount) return ScrollMatch(null, null, oldCount, pinnedCount)
        val index = if (forward) candidates.first() else candidates.last()
        return ScrollMatch(index, shift, oldCount, pinnedCount)
    }

    private class Vote(val delta: Int, val unique: Boolean)

    /**
     * Quãng nội dung trượt (px). Mỗi cặp (trước, sau) cùng nhãn, cùng cột, cùng chiều cao cho một phiếu `trước.top - sau.top`
     * đúng hướng (tiến: dương). Nhóm phiếu cách nhau ≤ [TOL_Y]; nhóm hợp lệ khi có ≥ 2 phiếu hoặc có một phiếu của nhãn duy nhất
     * (một cặp nhãn trùng lặp có thể chỉ là ngẫu nhiên). Chọn nhóm nhiều phiếu nhất, rồi nhiều phiếu "nhãn duy nhất", rồi
     * quãng xa hơn. null nếu không đo được (cuộn trọn trang không còn phần tử nào giữ lại).
     */
    private fun measureShift(
        labeledBefore: Map<String, List<ScrollItem>>,
        after: List<ScrollItem>,
        afterCounts: Map<String, Int>,
        forward: Boolean,
    ): Int? {
        val votes = ArrayList<Vote>()
        for (a in after) {
            val candidates = labeledBefore[a.label] ?: continue
            val unique = candidates.size == 1 && afterCounts[a.label] == 1
            for (b in candidates) {
                if (!sameColumn(a, b) || abs(a.height - b.height) > TOL_Y) continue
                val delta = b.top - a.top
                if (if (forward) delta <= TOL_Y else delta >= -TOL_Y) continue
                votes += Vote(delta, unique)
            }
        }
        if (votes.isEmpty()) return null
        votes.sortBy { it.delta }

        var bestDelta: Int? = null
        var bestCount = 0
        var bestUnique = 0
        var i = 0
        while (i < votes.size) {
            var j = i
            var unique = 0
            while (j < votes.size && votes[j].delta - votes[i].delta <= TOL_Y) {
                if (votes[j].unique) unique++
                j++
            }
            val count = j - i
            val delta = votes[i + count / 2].delta
            if (count >= 2 || unique >= 1) {
                val best = bestDelta
                val better = best == null || count > bestCount ||
                    (count == bestCount && (unique > bestUnique || (unique == bestUnique && abs(delta) > abs(best))))
                if (better) {
                    bestDelta = delta
                    bestCount = count
                    bestUnique = unique
                }
            }
            i = j
        }
        return bestDelta
    }

    /** Phần tử [a] đúng hộp cũ của một phần tử trước đó cùng nhãn (không nhãn thì chỉ xét hộp giữa các phần tử không nhãn). */
    private fun isStill(a: ScrollItem, labeledBefore: Map<String, List<ScrollItem>>, before: List<ScrollItem>): Boolean {
        if (a.label.isNotEmpty()) {
            return labeledBefore[a.label]?.any { sameColumn(a, it) && sameRows(a, it) } == true
        }
        return before.any { it.label.isEmpty() && sameColumn(a, it) && sameRows(a, it) }
    }

    /**
     * [a] (sau) là phần còn lại của [b] (trước) bị cắt ở mép: tiến thì nửa trên của b đã cuộn khỏi mép trên và [a] dính mép trên,
     * trong khi b trước đó chạm mép dưới (hoặc, nếu đã đo được quãng trượt, đã bị đẩy qua mép trên). Lùi thì đối xứng.
     */
    private fun continuesAtEdge(b: ScrollItem, a: ScrollItem, container: IntArray, shift: Int?, forward: Boolean): Boolean =
        if (forward) {
            a.top <= container[1] + TOL_Y &&
                (if (shift != null) b.top - shift < container[1] + TOL_Y else b.bottom >= container[3] - TOL_Y)
        } else {
            a.bottom >= container[3] - TOL_Y &&
                (if (shift != null) b.bottom - shift > container[3] - TOL_Y else b.top <= container[1] + TOL_Y)
        }

    private fun nearEdge(a: ScrollItem, container: IntArray, band: Int): Boolean =
        a.top <= container[1] + band || a.bottom >= container[3] - band

    private fun sameColumn(a: ScrollItem, b: ScrollItem): Boolean =
        abs(a.left - b.left) <= TOL_X && abs(a.right - b.right) <= TOL_X

    private fun sameRows(a: ScrollItem, b: ScrollItem): Boolean =
        abs(a.top - b.top) <= TOL_Y && abs(a.bottom - b.bottom) <= TOL_Y
}

/**
 * Quyết định ở biên vùng cuộn khi vuốt phần tử kế/trước. Thuần hàm, không phụ thuộc Android.
 */
object ScrollEdge {

    /** Điểm giữa của [box] (trái, trên, phải, dưới) có nằm trong [area] không; trái/trên tính, phải/dưới không (như `Rect.contains`). */
    fun containsCenter(area: IntArray, box: IntArray): Boolean {
        val x = (box[0] + box[2]) / 2
        val y = (box[1] + box[3]) / 2
        return x >= area[0] && x < area[2] && y >= area[1] && y < area[3]
    }

    /** Khung chỉ cuộn NGANG (có hành động trái/phải mà không có lên/xuống): băng chuyền, bộ chuyển trang. */
    fun isHorizontalOnly(actionIds: Collection<Int>, left: Int, right: Int, up: Int, down: Int): Boolean =
        (left in actionIds || right in actionIds) && up !in actionIds && down !in actionIds

    /** Tên lớp của bộ chọn giá trị: cuộn chúng là ĐỔI GIÁ TRỊ (giờ, số), không phải đọc thêm nội dung. */
    private val VALUE_PICKERS = listOf("picker", "seekbar", "slider", "wheel")

    /**
     * Khung cuộn có phải khung nội dung (danh sách, trang, cuộn dọc) để app tự cuộn hộ khi vuốt hết màn hình không. Khung có
     * khoảng giá trị ([hasRange]) hoặc là bộ chọn (NumberPicker, TimePicker, thanh trượt) thì KHÔNG: cuộn là đổi giá trị người dùng
     * đang chỉnh. Không biết tên lớp thì coi là khung nội dung.
     */
    fun isContentScroller(className: CharSequence?, hasRange: Boolean): Boolean {
        if (hasRange) return false
        val name = className?.toString()?.lowercase() ?: return true
        return VALUE_PICKERS.none { it in name }
    }
}

/**
 * Quyết định khi nào vùng cuộn đã "đổi rồi dừng" sau một lần cuộn, dựa trên các ảnh chụp thăm dò liên tiếp. Thuần hàm, không đồng hồ,
 * không Android: nơi gọi tự chờ giữa các lần [offer].
 *  - ảnh còn GIỐNG trước khi cuộn: chưa thấy gì đổi (cuộn chưa chạy, hoặc ứng dụng cập nhật cây giao diện chậm);
 *  - ảnh KHÁC trước khi cuộn nhưng khác lần thăm dò trước: đang trượt hoặc đang nạp thêm;
 *  - ảnh KHÁC trước khi cuộn và giống lần thăm dò trước: đã dừng hẳn, dùng được.
 * Cách cũ chờ cố định 300 + 500 ms nên chọn nhầm lúc danh sách còn đang trượt (RecyclerView trượt ~600 ms) hoặc trang web cập nhật chậm.
 */
class ScrollSettle(private val before: List<ScrollItem>) {
    private var last: List<ScrollItem>? = null

    /** Lần thăm dò gần nhất có khác ảnh trước khi cuộn không. */
    val changed: Boolean get() = last != null

    /** Đưa ảnh vừa thăm dò vào; true khi nội dung đã ĐỔI so với trước khi cuộn và DỪNG (hai lần thăm dò liên tiếp giống nhau). */
    fun offer(now: List<ScrollItem>): Boolean {
        if (now == before) {
            last = null // quay về như cũ: coi như chưa đổi
            return false
        }
        val stable = last == now
        last = now
        return stable
    }
}

/**
 * Nhớ vài vùng cuộn vừa được cuộn mà KHÔNG lộ ra phần tử mới (khung báo còn cuộn được nhưng thực ra đã hết, hoặc nội dung nạp chậm), để
 * các lần vuốt liền sau đi thẳng sang phần tử kế thay vì lại chờ vô ích. Mỗi mục hết hạn sau [ttlMs]; nhớ tối đa [MAX_ENTRIES] mục
 * (mục cũ nhất bị bỏ). Đồng hồ do nơi gọi đưa vào nên kiểm thử được. Có khóa vì [clear] có thể gọi từ luồng khác; mỗi lần gọi O([MAX_ENTRIES]).
 */
class ScrollExhaustion(private val ttlMs: Long) {
    private val until = LinkedHashMap<Long, Long>()

    @Synchronized
    fun mark(key: Long, nowMs: Long) {
        until.remove(key) // đặt lại thành mục mới nhất
        until[key] = nowMs + ttlMs
        while (until.size > MAX_ENTRIES) until.remove(until.keys.first())
    }

    @Synchronized
    fun isExhausted(key: Long, nowMs: Long): Boolean {
        val end = until[key] ?: return false
        if (nowMs >= end) {
            until.remove(key)
            return false
        }
        return true
    }

    @Synchronized
    fun clear() = until.clear()

    private companion object {
        const val MAX_ENTRIES = 8
    }
}
