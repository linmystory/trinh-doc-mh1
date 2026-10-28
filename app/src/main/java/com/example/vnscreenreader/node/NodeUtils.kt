package com.example.vnscreenreader.node

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.CheckedState
import com.example.vnscreenreader.core.HoverPolicy
import com.example.vnscreenreader.core.NodeSnapshot

fun AccessibilityNodeInfo.toSnapshot(): NodeSnapshot = NodeSnapshot(
    text = text?.toString(),
    contentDescription = contentDescription?.toString(),
    hintText = hintText?.toString(),
    className = className?.toString(),
    isPassword = isPassword,
    isEditable = isEditable,
    isCheckable = isCheckable,
    checkedState = readCheckedState(),
    isClickable = isClickable,
    isEnabled = isEnabled,
    isSelected = isSelected,
    stateDescription = stateDescription?.toString(),
    roleDescription = extras?.getCharSequence(ROLE_DESCRIPTION_KEY)?.toString(),
    isHeading = isHeading,
    error = error?.toString(),
    rangePercent = rangeInfo?.toPercent(),
    labeledByText = firstLabeledBy()?.let { (it.text ?: it.contentDescription)?.toString() },
    tooltip = tooltipText?.toString(),
    viewId = viewIdResourceName,
    packageName = packageName?.toString(),
)

/** API 36 thay `getLabeledBy()` (một nhãn, deprecated) bằng `getLabeledByList()`; máy cũ dùng API cũ. */
private fun AccessibilityNodeInfo.firstLabeledBy(): AccessibilityNodeInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        labeledByList.firstOrNull()
    } else {
        @Suppress("DEPRECATION")
        labeledBy
    }

/** Khóa extras mà framework dùng cho `android:accessibilityRoleDescription`/`setRoleDescription`. */
private const val ROLE_DESCRIPTION_KEY = "AccessibilityNodeInfo.roleDescription"

private fun AccessibilityNodeInfo.RangeInfo.toPercent(): Int? {
    val span = max - min
    if (span <= 0f || span.isNaN()) return null
    return (((current - min) / span) * 100f).toInt().coerceIn(0, 100)
}

/**
 * API 36 thay `isChecked()` (boolean, đã deprecated) bằng `getChecked()` ba trạng thái
 * (có thêm "chọn một phần"). Máy cũ hơn vẫn dùng API cũ.
 */
private fun AccessibilityNodeInfo.readCheckedState(): CheckedState {
    if (!isCheckable) return CheckedState.UNCHECKED
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        when (getChecked()) {
            AccessibilityNodeInfo.CHECKED_STATE_TRUE -> CheckedState.CHECKED
            AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> CheckedState.PARTIAL
            else -> CheckedState.UNCHECKED
        }
    } else {
        @Suppress("DEPRECATION")
        if (isChecked) CheckedState.CHECKED else CheckedState.UNCHECKED
    }
}

/** Duyệt cây giao diện (có giới hạn) và các tiện ích tìm phần tử. Chỉ gọi từ luồng nền. */
object NodeCollector {

    const val MAX_NODES = 1000
    const val MAX_ANCESTOR_DEPTH = 12
    private const val MAX_HOVER_ANCESTOR_DEPTH = 4
    private const val MERGE_MAX_PARTS = 6
    private const val MERGE_MAX_DEPTH = 4

    /** Tất cả phần tử đang hiển thị, theo thứ tự đọc (tiền thứ tự). */
    fun traverse(root: AccessibilityNodeInfo, maxNodes: Int = MAX_NODES): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue
            result += node
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return result
    }

    /**
     * Các phần tử đáng dừng lại khi vuốt, theo thứ tự đọc. Mục tiêu: KHÔNG bao giờ dừng ở phần tử "rỗng".
     *  - Phần tử có nhãn riêng (text/contentDescription): giữ.
     *  - Khung chứa nhấn được/screen-reader-focusable KHÔNG có nhãn riêng: giữ làm MỘT mục. Có chữ con thì đọc gộp
     *    (xem [mergedLabel]); không có thì đọc "không có nhãn" để người dùng vẫn nhấn được nút chưa gắn nhãn.
     *    Các con chỉ-để-đọc của nó bị bỏ để không đọc lặp hai lần.
     *  - Ô nhập/công tắc chưa gắn nhãn: giữ (quan trọng, phải tới được).
     *  - Còn lại (khung bố cục thuần túy, ảnh trang trí không nhấn được) bị bỏ qua nhưng vẫn duyệt các con.
     * Các con có thể tương tác (nút bấm lồng trong thẻ) luôn được giữ.
     * Thời gian O(số nút duyệt) với trần [MAX_NODES].
     */
    fun collect(root: AccessibilityNodeInfo, maxNodes: Int = MAX_NODES): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>() // (nút, đã được cha gộp nhãn)
        stack.addLast(root to false)
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val (node, consumed) = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue

            val own = hasOwnLabel(node)
            val interactive = node.isClickable || node.isCheckable || node.isEditable
            var childrenConsumed = consumed
            when {
                own && (!consumed || interactive) -> {
                    result += node
                    childrenConsumed = true
                }
                // Khung nhấn được không nhãn riêng: luôn là một điểm dừng (có chữ con thì đọc gộp, không thì
                // đọc "không có nhãn") để người dùng vẫn tới và nhấn được nút chưa gắn nhãn.
                !own && (node.isClickable || node.isScreenReaderFocusable) && !consumed -> {
                    result += node
                    childrenConsumed = true
                }
                !own && (node.isEditable || node.isCheckable) -> result += node
            }
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it to childrenConsumed) }
            }
        }
        return result
    }

    private fun hasOwnLabel(node: AccessibilityNodeInfo): Boolean =
        !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()

    /**
     * Nhãn gộp của một khung chứa không có nhãn riêng: chữ của các con hiển thị (tối đa [MERGE_MAX_PARTS] mảnh,
     * sâu tối đa [MERGE_MAX_DEPTH] cấp), nối bằng ", ". Rỗng nếu không có chữ nào.
     */
    fun mergedLabel(node: AccessibilityNodeInfo): String {
        val parts = ArrayList<String>(MERGE_MAX_PARTS)
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it to 1) }
        while (stack.isNotEmpty() && parts.size < MERGE_MAX_PARTS) {
            val (child, depth) = stack.removeLast()
            if (!child.isVisibleToUser) continue
            val label = (child.text ?: child.contentDescription)?.toString()?.trim()
            if (!label.isNullOrEmpty()) {
                parts += label
                continue // đã có nhãn: không đọc thêm các con của nó
            }
            if (depth < MERGE_MAX_DEPTH) {
                for (i in child.childCount - 1 downTo 0) child.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
        return parts.joinToString(", ")
    }

    /**
     * Phần tử cần đọc khi ngón tay chạm vào [source] (sự kiện HOVER_ENTER của chế độ khám phá bằng cảm ứng).
     * - Chính [source] nếu nó có nội dung/có thể tương tác ([HoverPolicy]).
     * - Nếu không (ví dụ ảnh con không nhãn nằm trong một nút bấm): nút bấm cha gần nhất.
     * - Trả về null khi chạm vào khoảng trống: không đọc gì.
     */
    fun findHoverTarget(source: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!source.isVisibleToUser) return null
        if (HoverPolicy.isReadable(source.toSnapshot())) return source
        var current = source.parent
        var depth = 0
        while (current != null && depth < MAX_HOVER_ANCESTOR_DEPTH) {
            if (current.isVisibleToUser && current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAncestorOrSelf(node) { it.isClickable }

    /** Phần tử đầu tiên thỏa [predicate] khi đi từ [node] ngược lên tối đa [MAX_ANCESTOR_DEPTH] cấp. */
    inline fun findAncestorOrSelf(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (predicate(current)) return current
            current = current.parent
            depth++
        }
        return null
    }

    /** Tìm vùng cuộn gần phần tử đang chọn nhất, nếu không có thì lấy vùng cuộn đầu tiên trên màn hình. */
    fun findScrollable(from: AccessibilityNodeInfo?, root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = from
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.isScrollable) return current
            current = current.parent
            depth++
        }
        return traverse(root).firstOrNull { it.isScrollable }
    }
}
