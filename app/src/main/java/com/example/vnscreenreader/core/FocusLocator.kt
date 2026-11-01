package com.example.vnscreenreader.core

/** Tính toán thuần cho việc điều hướng phần tử. */
object FocusLocator {

    /**
     * Chỉ số hộp NHỎ NHẤT trong [boxes] (left, top, right, bottom) chứa điểm ([x], [y]); -1 nếu không hộp nào chứa.
     * Dùng khi phần tử đang chọn không phải một điểm dừng (ví dụ con của khối đã gộp nhãn): vuốt kế tiếp phải bắt
     * đầu từ điểm dừng chứa nó chứ không nhảy về đầu danh sách.
     */
    fun nearestContaining(boxes: List<IntArray>, x: Int, y: Int): Int {
        var best = -1
        var bestArea = Long.MAX_VALUE
        for ((i, b) in boxes.withIndex()) {
            if (x < b[0] || x > b[2] || y < b[1] || y > b[3]) continue
            val area = (b[2] - b[0]).toLong() * (b[3] - b[1]).toLong()
            if (area < bestArea) {
                best = i
                bestArea = area
            }
        }
        return best
    }
}

/**
 * Bám vết tiêu điểm khi giao diện vẽ lại: phần tử đang chọn biến mất khỏi danh sách (bị tái chế/đổi cây) thì KHÔNG
 * đặt lại về 0 mà coi như đang đứng ở "khe" nơi nó từng nằm, để vuốt tiếp ra đúng phần tử lân cận theo hướng vuốt.
 */
object FocusTracker {

    /** Hộp (left, top, right, bottom). Thứ tự đọc: trên → dưới, rồi trái → phải. */
    private fun before(a: IntArray, b: IntArray): Boolean = a[1] < b[1] || (a[1] == b[1] && a[0] < b[0])

    /**
     * Chỉ số "đang đứng" ảo trong [boxes] cho phần tử cũ có hộp [last] (và nhãn [lastLabel]), sao cho
     * FocusIndex.next/previous cho đúng lân cận. Ưu tiên: cùng nhãn và gần vị trí cũ nhất; nếu không, khe theo vị trí.
     * Trả về -1 khi không có gì để bám (danh sách rỗng).
     */
    fun virtualCurrent(boxes: List<IntArray>, labels: List<String>, last: IntArray, lastLabel: String, forward: Boolean): Int {
        if (boxes.isEmpty()) return -1
        if (lastLabel.isNotBlank()) {
            var best = -1
            var bestDist = Long.MAX_VALUE
            for (i in boxes.indices) {
                if (labels.getOrNull(i) != lastLabel) continue
                val dx = ((boxes[i][0] + boxes[i][2]) - (last[0] + last[2])).toLong() / 2
                val dy = ((boxes[i][1] + boxes[i][3]) - (last[1] + last[3])).toLong() / 2
                val d = dx * dx + dy * dy
                if (d < bestDist) { best = i; bestDist = d }
            }
            if (best >= 0) return best
        }
        return if (forward) {
            val firstAfter = boxes.indexOfFirst { before(last, it) }
            if (firstAfter < 0) boxes.size - 1 else firstAfter - 1
        } else {
            val lastBefore = boxes.indexOfLast { before(it, last) }
            if (lastBefore < 0) 0 else lastBefore + 1
        }
    }
}

/** Thời lượng nhấn giữ giả lập: dài hơn ngưỡng nhấn giữ của hệ thống một chút để chắc chắn được tính là nhấn giữ. */
object LongPressTiming {
    private const val MARGIN_MS = 200L
    private const val MIN_MS = 500L
    private const val MAX_MS = 3000L
    fun durationMs(systemTimeoutMs: Int): Long = (systemTimeoutMs + MARGIN_MS).coerceIn(MIN_MS, MAX_MS)
}

/** Chọn phần tử sau khi tự cuộn: bỏ qua những phần tử đã thấy trước khi cuộn. */
object AutoScrollPicker {

    /**
     * [old]/[new]: nhãn các phần tử trước/sau khi cuộn. Tiến: phần tử MỚI đầu tiên; lùi: phần tử mới CUỐI cùng.
     * Phần tử không nhãn luôn coi là mới. null nếu cuộn xong mà không có phần tử mới nào.
     */
    /**
     * Như [pick] nhưng chỉ xét phần tử nằm TRONG vùng vừa cuộn ([inside]) và bám theo [anchor] (nhãn phần tử đang chọn
     * trước khi cuộn): tiến = phần tử kế sau anchor; không thấy anchor (cuộn trọn trang) = phần tử mới đầu tiên trong vùng.
     * Nhờ đó thanh tiêu đề/nút cố định ở đầu màn hình (luôn "mới") không bị chọn nhầm, gây cảm giác quay về đầu.
     */
    fun pickWithin(old: List<String>, new: List<String>, inside: List<Boolean>, anchor: String, forward: Boolean): Int? {
        val candidates = new.indices.filter { inside.getOrElse(it) { false } }
        if (candidates.isEmpty()) return null
        if (anchor.isNotBlank()) {
            val at = candidates.indexOfFirst { new[it] == anchor }
            if (at >= 0) {
                val step = if (forward) at + 1 else at - 1
                candidates.getOrNull(step)?.let { return it }
            }
        }
        val seen = old.filter { it.isNotBlank() }.toSet()
        val fresh = candidates.filter { new[it].isBlank() || new[it] !in seen }
        return if (forward) fresh.firstOrNull() else fresh.lastOrNull()
    }

    fun pick(old: List<String>, new: List<String>, forward: Boolean): Int? {
        val seen = old.filter { it.isNotBlank() }.toSet()
        val fresh = new.indices.filter { new[it].isBlank() || new[it] !in seen }
        return if (forward) fresh.firstOrNull() else fresh.lastOrNull()
    }
}
