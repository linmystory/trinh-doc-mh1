package com.example.vnscreenreader.core

/**
 * Vết của MỘT phần tử đã chọn, đủ để tìm lại nó sau khi giao diện vẽ lại (nút bị tái chế, danh sách nạp thêm):
 * mã duy nhất (nếu ứng dụng đặt), `viewId`, nhãn, hộp tọa độ và chỉ số trong danh sách (nếu có).
 * Cố ý không phải `data class` vì chứa mảng.
 */
class FocusTrace(
    val windowId: Int,
    val uniqueId: String?,
    val viewId: String?,
    val label: String,
    val box: IntArray,
    val itemIndex: Int = NO_INDEX,
) {
    /** Cùng một phần tử logic (dù đã dịch chuyển do cuộn): cùng uniqueId, hoặc cùng nhãn + viewId. */
    fun sameElementAs(other: FocusTrace): Boolean {
        if (windowId != other.windowId) return false
        if (!uniqueId.isNullOrBlank()) return uniqueId == other.uniqueId
        return label.isNotBlank() && label == other.label && viewId == other.viewId
    }

    companion object {
        const val NO_INDEX = -1
    }
}

/**
 * Bộ đệm vòng giữ vài vết gần nhất (mới nhất trước). Phần tử đang chọn biến mất thì vẫn còn vết của những phần tử
 * trước đó để bám, thay vì quay về mốc 0. Chỉ dùng trên luồng xử lý duy nhất của phiên đọc.
 */
class FocusTraceBuffer(private val capacity: Int = DEFAULT_CAPACITY) {
    private val items = ArrayDeque<FocusTrace>()

    init {
        require(capacity >= 1) { "capacity phải >= 1" }
    }

    val size: Int get() = items.size

    /** Thêm vết; nếu là cùng phần tử với vết mới nhất thì thay thế (cập nhật hộp) chứ không nhân đôi. */
    fun push(trace: FocusTrace) {
        val head = items.firstOrNull()
        if (head != null && head.sameElementAs(trace)) items.removeFirst()
        items.addFirst(trace)
        while (items.size > capacity) items.removeLast()
    }

    fun latest(): FocusTrace? = items.firstOrNull()

    fun newestFirst(): List<FocusTrace> = items.toList()

    /** Bỏ vết thuộc cửa sổ khác (sang cửa sổ mới thì vết cũ vô nghĩa). */
    fun keepWindow(windowId: Int) {
        items.removeAll { it.windowId != windowId }
    }

    fun clear() = items.clear()

    companion object {
        const val DEFAULT_CAPACITY = 4
    }
}

/** Tìm lại phần tử theo vết. Thuần hàm (không phụ thuộc Android) để kiểm thử. */
object TraceLocator {

    /**
     * Chỉ số ứng viên khớp MẠNH với một trong các vết (mới nhất trước); -1 nếu không có.
     * "Mạnh" = cùng uniqueId, hoặc cùng nhãn (hoặc, khi nhãn rỗng, cùng viewId). Không dùng vị trí thuần túy:
     * khe theo tọa độ do [FocusTracker.virtualCurrent] đảm nhiệm khi đây trả -1.
     * Nhiều ứng viên cùng nhãn: ưu tiên cùng chỉ số danh sách, rồi cùng viewId, rồi gần hộp cũ nhất.
     */
    fun locate(
        traces: List<FocusTrace>,
        count: Int,
        uniqueIdOf: (Int) -> String?,
        viewIdOf: (Int) -> String?,
        labelOf: (Int) -> String,
        boxOf: (Int) -> IntArray,
        itemIndexOf: (Int) -> Int,
    ): Int {
        for (trace in traces) {
            val hit = locateOne(trace, count, uniqueIdOf, viewIdOf, labelOf, boxOf, itemIndexOf)
            if (hit >= 0) return hit
        }
        return -1
    }

    private fun locateOne(
        trace: FocusTrace,
        count: Int,
        uniqueIdOf: (Int) -> String?,
        viewIdOf: (Int) -> String?,
        labelOf: (Int) -> String,
        boxOf: (Int) -> IntArray,
        itemIndexOf: (Int) -> Int,
    ): Int {
        if (!trace.uniqueId.isNullOrBlank()) {
            for (i in 0 until count) if (uniqueIdOf(i) == trace.uniqueId) return i
        }
        val pool: List<Int> = when {
            trace.label.isNotBlank() -> (0 until count).filter { labelOf(it) == trace.label }
            !trace.viewId.isNullOrBlank() -> (0 until count).filter { viewIdOf(it) == trace.viewId }
            else -> emptyList()
        }
        if (pool.isEmpty()) return -1
        if (pool.size == 1) return pool[0]

        var narrowed = pool
        if (trace.itemIndex != FocusTrace.NO_INDEX) {
            narrowed.filter { itemIndexOf(it) == trace.itemIndex }.takeIf { it.isNotEmpty() }?.let { narrowed = it }
        }
        if (!trace.viewId.isNullOrBlank() && narrowed.size > 1) {
            narrowed.filter { viewIdOf(it) == trace.viewId }.takeIf { it.isNotEmpty() }?.let { narrowed = it }
        }
        return narrowed.minByOrNull { distanceSquared(boxOf(it), trace.box) } ?: -1
    }

    private fun distanceSquared(a: IntArray, b: IntArray): Long {
        val dx = ((a[0] + a[2]) - (b[0] + b[2])).toLong() / 2
        val dy = ((a[1] + a[3]) - (b[1] + b[3])).toLong() / 2
        return dx * dx + dy * dy
    }
}
