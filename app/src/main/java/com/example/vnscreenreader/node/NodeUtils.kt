package com.example.vnscreenreader.node

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.CheckedState
import com.example.vnscreenreader.core.HoverPolicy
import com.example.vnscreenreader.core.NodeSnapshot
import com.example.vnscreenreader.core.ScrollCandidate
import com.example.vnscreenreader.core.ScrollOrdering
import com.example.vnscreenreader.core.TraversalBudget

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

    /**
     * Số nút HIỂN THỊ tối đa mỗi lần duyệt (xem [TraversalBudget]). Chọn 1.500: đủ cho màn hình dài thực tế
     * (danh sách vài trăm dòng, mỗi dòng 3–5 nút) mà vẫn giới hạn thời gian duyệt trên máy yếu.
     */
    const val MAX_NODES = TraversalBudget.DEFAULT_MAX_VISIBLE
    const val MAX_ANCESTOR_DEPTH = 12
    private const val MAX_HOVER_ANCESTOR_DEPTH = 4
    private const val MERGE_MAX_PARTS = 6
    private const val MERGE_MAX_DEPTH = 4

    /** Tất cả phần tử đang hiển thị, theo thứ tự đọc (tiền thứ tự). */
    fun traverse(root: AccessibilityNodeInfo, maxNodes: Int = MAX_NODES): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        val budget = TraversalBudget(maxNodes)
        while (stack.isNotEmpty() && budget.canContinue) {
            val node = stack.removeLast()
            budget.record(node.isVisibleToUser)
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
        val budget = TraversalBudget(maxNodes)
        while (stack.isNotEmpty() && budget.canContinue) {
            val (node, consumed) = stack.removeLast()
            budget.record(node.isVisibleToUser)
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

    /**
     * Mọi vùng cuộn đang hiển thị, đã sắp theo [ScrollOrdering] và chỉ giữ vùng còn hỗ trợ hướng cuộn yêu cầu
     * (vùng đã ở cuối không còn hành động tương ứng trong actionList).
     */
    fun scrollTargets(from: AccessibilityNodeInfo?, root: AccessibilityNodeInfo, forward: Boolean): List<AccessibilityNodeInfo> {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val candidates = ArrayList<ScrollCandidate>()
        fun add(node: AccessibilityNodeInfo, rank: Int?) {
            if (nodes.any { it == node }) return
            node.refresh()
            if (!node.isScrollable || !node.isVisibleToUser) return
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            nodes += node
            candidates += ScrollCandidate(nodes.lastIndex, rank, bounds.width().toLong() * bounds.height())
        }
        var current = from
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.isScrollable) add(current, depth)
            current = current.parent
            depth++
        }
        traverse(root).filter { it.isScrollable }.forEach { add(it, null) }

        val wanted = scrollActions(forward)
        return ScrollOrdering.order(candidates).map { nodes[it.id] }
            .filter { node -> node.actionList.any { a -> a.id in wanted } }
    }

    /** Hai hành động cuộn tương đương theo hướng (ưu tiên FORWARD/BACKWARD rồi tới DOWN/UP). */
    fun scrollActions(forward: Boolean): IntArray =
        if (forward) {
            intArrayOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
        } else {
            intArrayOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)
        }
}
