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

/** Chọn phần tử sau khi tự cuộn: bỏ qua những phần tử đã thấy trước khi cuộn. */
object AutoScrollPicker {

    /**
     * [old]/[new]: nhãn các phần tử trước/sau khi cuộn. Tiến: phần tử MỚI đầu tiên; lùi: phần tử mới CUỐI cùng.
     * Phần tử không nhãn luôn coi là mới. null nếu cuộn xong mà không có phần tử mới nào.
     */
    fun pick(old: List<String>, new: List<String>, forward: Boolean): Int? {
        val seen = old.filter { it.isNotBlank() }.toSet()
        val fresh = new.indices.filter { new[it].isBlank() || new[it] !in seen }
        return if (forward) fresh.firstOrNull() else fresh.lastOrNull()
    }
}
