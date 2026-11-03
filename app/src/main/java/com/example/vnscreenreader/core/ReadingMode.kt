package com.example.vnscreenreader.core

/**
 * Chế độ đọc: khi khác [ELEMENT], vuốt phần tử kế/trước nhảy tới phần tử CÙNG LOẠI
 * kế tiếp/trước đó thay vì từng phần tử một. Thuần Kotlin để kiểm thử.
 */
enum class ReadingMode(private val labelKey: S) {
    ELEMENT(S.MODE_ELEMENT),
    HEADING(S.MODE_HEADING),
    LINK(S.MODE_LINK),
    CONTROL(S.MODE_CONTROL),
    EDIT(S.MODE_EDIT),
    CHECKABLE(S.MODE_CHECKABLE),
    IMAGE(S.MODE_IMAGE);

    val label: String get() = t(labelKey)

    fun next(): ReadingMode = entries[(ordinal + 1) % entries.size]
    fun previous(): ReadingMode = entries[(ordinal + entries.size - 1) % entries.size]

    /** Phần tử [node] có thuộc loại của chế độ này không. [ELEMENT] khớp mọi phần tử. */
    fun matches(node: NodeSnapshot): Boolean = when (this) {
        ELEMENT -> true
        HEADING -> node.isHeading
        LINK -> isLink(node)
        CONTROL -> node.isEnabled && (node.isClickable || node.isCheckable || node.isEditable || hasControlClass(node.className))
        EDIT -> node.isEditable
        CHECKABLE -> node.isCheckable
        IMAGE -> AutoOcrPolicy.isAnyImage(node.className)
    }

    private fun hasControlClass(cls: String?): Boolean =
        cls != null && listOf("Button", "Switch", "CheckBox", "RadioButton", "SeekBar", "Spinner", "EditText").any { it in cls }

    /**
     * Liên kết: ứng dụng tự khai báo vai trò "link/liên kết" hoặc tên lớp có "Link". Nội dung web trong Chrome thường khai
     * báo theo cách này, nhưng không phải trang nào cũng vậy (đây là suy đoán, không đảm bảo tìm hết liên kết).
     */
    private fun isLink(node: NodeSnapshot): Boolean {
        val role = node.roleDescription?.lowercase().orEmpty()
        return LinkRoles.matches(role) || node.className?.contains("Link") == true
    }
}

/** Tìm phần tử kế tiếp thỏa chế độ đọc. */
object ReadingModeSearch {
    /**
     * [matches] song song với danh sách phần tử; [current] = chỉ số đang chọn (-1 nếu chưa chọn). Trả về chỉ số phần
     * tử khớp kế tiếp theo hướng, null nếu không còn trong danh sách này (khi đó thử cuộn).
     */
    fun next(matches: List<Boolean>, current: Int, forward: Boolean): Int? {
        if (matches.isEmpty()) return null
        return if (forward) {
            val from = if (current < 0) 0 else current + 1
            (from until matches.size).firstOrNull { matches[it] }
        } else {
            val from = if (current < 0) matches.size - 1 else minOf(current, matches.size) - 1
            (from downTo 0).firstOrNull { matches[it] }
        }
    }
}

/**
 * Nhảy giữa các vùng chứa. [keys] song song với danh sách phần tử: phần tử cùng vùng chứa có cùng khóa. Tiến: phần tử đầu
 * của vùng kế tiếp. Lùi: đầu vùng hiện tại nếu đang ở giữa vùng, ngược lại đầu vùng trước đó.
 */
object ContainerNav {
    fun target(keys: List<Int>, current: Int, forward: Boolean): Int? {
        if (keys.isEmpty()) return null
        if (current < 0) return if (forward) 0 else runStart(keys, keys.size - 1)
        val cur = minOf(current, keys.size - 1)
        if (forward) return (cur + 1 until keys.size).firstOrNull { keys[it] != keys[cur] }
        val start = runStart(keys, cur)
        if (start < cur) return start
        return if (start == 0) null else runStart(keys, start - 1)
    }

    private fun runStart(keys: List<Int>, index: Int): Int {
        var i = index
        while (i > 0 && keys[i - 1] == keys[index]) i--
        return i
    }
}

/** Chuyển vòng tròn giữa các cửa sổ. */
object WindowCycle {
    /** [ids] theo thứ tự cửa sổ; trả về id kế/trước, quay vòng. Không có cửa sổ hiện tại trong danh sách: đầu (tiến) hoặc cuối (lùi). Null nếu không có cửa sổ khác. */
    fun next(ids: List<Int>, currentId: Int?, forward: Boolean): Int? {
        if (ids.isEmpty()) return null
        val at = ids.indexOf(currentId)
        if (at < 0) return if (forward) ids.first() else ids.last()
        if (ids.size == 1) return null
        val step = if (forward) 1 else -1
        return ids[(at + step + ids.size) % ids.size]
    }
}
