package com.example.vnscreenreader.node

import android.graphics.Rect
import com.example.vnscreenreader.core.PagerHints
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.CheckedState
import com.example.vnscreenreader.core.ClickProbe
import com.example.vnscreenreader.core.ClickTargetPolicy
import com.example.vnscreenreader.core.FocusTrace
import com.example.vnscreenreader.core.ListPosition
import com.example.vnscreenreader.core.ListPositions
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
    isLink = isLinkLike(),
)

/**
 * Thẻ liên kết trong nội dung web: Chrome/WebView gắn vai trò và địa chỉ đích vào `extras`. Khóa là chuỗi nội bộ của
 * Chromium (không thuộc API công khai) nên chỉ dùng để NHẬN DIỆN thêm; không có thì phần tử vẫn bấm được qua
 * ACTION_CLICK như mọi nút khác.
 */
fun AccessibilityNodeInfo.isLinkLike(): Boolean {
    val ex = extras ?: return false
    if (ex.getString(CHROME_ROLE_KEY) == "link") return true
    return !ex.getString(TARGET_URL_KEY).isNullOrBlank()
}

private const val CHROME_ROLE_KEY = "AccessibilityNodeInfo.chromeRole"
private const val TARGET_URL_KEY = "AccessibilityNodeInfo.targetUrl"

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
    private const val MAX_LIST_LOOKUP = 8
    private const val MAX_ITEM_INDEX_LOOKUP = 4
    private const val ITEM_INDEX_ROW_STEP = 10_000

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
     * [detailed] = tiêu điểm chi tiết: thêm các nút bấm được không nhãn bị khung cha "nuốt" (ô tích bị giấu).
     * Thời gian O(số nút duyệt) với trần [MAX_NODES].
     */
    fun collect(root: AccessibilityNodeInfo, maxNodes: Int = MAX_NODES, detailed: Boolean = false): List<AccessibilityNodeInfo> {
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
                // Tiêu điểm chi tiết: ô tích/nút không nhãn nằm trong khung bấm được vẫn là một điểm dừng riêng.
                detailed && !own && node.isClickable -> result += node
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

    /** Nút có hành động nhấn dù cờ `isClickable` là false (nút web, view tự khai báo hành động). */
    fun hasClickAction(node: AccessibilityNodeInfo): Boolean =
        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }

    /** Gốc nội dung web (WebView/GeckoView). */
    fun isWebRoot(node: AccessibilityNodeInfo): Boolean = ClickTargetPolicy.isWebContainerClass(node.className?.toString())

    fun probe(node: AccessibilityNodeInfo): ClickProbe = ClickProbe(
        clickable = node.isClickable,
        clickAction = hasClickAction(node),
        scrollable = node.isScrollable,
        webRoot = isWebRoot(node),
        link = node.isLinkLike(),
    )

    /**
     * Vị trí trong danh sách ("mục 3 trên 12") theo CollectionItemInfo của phần tử (hoặc tổ tiên gần nhất có) và
     * CollectionInfo của khung chứa nó. null nếu ứng dụng không cung cấp.
     */
    fun listPosition(node: AccessibilityNodeInfo): ListPosition? {
        var item: AccessibilityNodeInfo.CollectionItemInfo? = null
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_LIST_LOOKUP) {
            val found = item
            if (found == null) {
                item = current.collectionItemInfo
            } else {
                current.collectionInfo?.let { return ListPositions.of(found.rowIndex, it.rowCount, found.columnIndex, it.columnCount) }
            }
            current = current.parent
            depth++
        }
        return null
    }

    /** Chỉ số trong danh sách của phần tử (hàng * 10000 + cột), -1 nếu không rõ. Dùng làm tiêu chí phân định khi bám vết. */
    fun itemIndex(node: AccessibilityNodeInfo): Int {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ITEM_INDEX_LOOKUP) {
            current.collectionItemInfo?.let { info ->
                val row = info.rowIndex.coerceAtLeast(0)
                val col = info.columnIndex.coerceAtLeast(0)
                return if (info.rowIndex < 0 && info.columnIndex < 0) FocusTrace.NO_INDEX else row * ITEM_INDEX_ROW_STEP + col
            }
            current = current.parent
            depth++
        }
        return FocusTrace.NO_INDEX
    }

    fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findAncestorOrSelf(node) { it.isClickable }

    /** Có thể kích hoạt: đánh dấu clickable HOẶC có hành động CLICK (nội dung web thường chỉ có hành động). */
    fun isActivatable(node: AccessibilityNodeInfo): Boolean =
        node.isEnabled && (node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })

    /**
     * Đích để nhấn cho [node]: chính nó hoặc tổ tiên kích hoạt được; nếu không có, hậu duệ kích hoạt được đầu tiên đang
     * hiện (duyệt rộng, tối đa 3 cấp) — ví dụ dòng chữ tiêu đề nằm trong khung liên kết hoặc ngược lại.
     */
    fun findClickTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        findAncestorOrSelf(node) { isActivatable(it) }?.let { return it }
        // Chỉ nhấn hậu duệ khi nó là DUY NHẤT: dòng có nhiều nút (ví dụ "Xóa", "Gửi") thì đoán sai là thao tác không hoàn tác được.
        var found: AccessibilityNodeInfo? = null
        var level = listOf(node)
        repeat(MAX_CLICK_DESCENT) {
            val next = ArrayList<AccessibilityNodeInfo>()
            for (parent in level) {
                for (i in 0 until minOf(parent.childCount, MAX_CLICK_CHILDREN)) {
                    val child = parent.getChild(i) ?: continue
                    if (!child.isVisibleToUser) continue
                    if (isActivatable(child)) {
                        if (found != null) return null
                        found = child
                    } else {
                        next += child
                    }
                }
            }
            level = next
        }
        return found
    }

    /** [node] nằm trong một WebView (một trong các tổ tiên có tên lớp chứa "WebView"). */
    fun insideWebView(node: AccessibilityNodeInfo): Boolean =
        findAncestorOrSelf(node) { it.className?.contains("WebView") == true } != null

    private const val MAX_CLICK_DESCENT = 3
    private const val MAX_CLICK_CHILDREN = 20

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
        val wanted = scrollActions(forward)
        return scrollTargetsBy(from, root) { node -> node.actionList.any { a -> a.id in wanted } }
    }

    /**
     * Vùng cuộn/chuyển trang NGANG (ViewPager, tab vuốt ngang, danh sách ngang): nút có bất kỳ hành động ngang nào
     * (trái/phải/trang trái/trang phải), cùng thứ tự ưu tiên như [scrollTargets].
     */
    fun pageTargets(from: AccessibilityNodeInfo?, root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        scrollTargetsBy(from, root) { node -> node.actionList.any { a -> a.id in HORIZONTAL_ACTION_IDS } }

    /**
     * Vùng của khung tự nhận "cuộn được" nhưng KHÔNG báo bất kỳ hành động cuộn nào (view tự vẽ): cuộn bằng hành động
     * không thể, phải giả lập cú vuốt. Lấy khung lớn nhất đang hiện; null nếu không có.
     */
    fun stuckScrollableBounds(root: AccessibilityNodeInfo): Rect? {
        val ids = HORIZONTAL_ACTION_IDS + setOf(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id,
        )
        var best: Rect? = null
        for (node in traverse(root)) {
            if (!node.isScrollable || !node.isVisibleToUser) continue
            if (node.actionList.any { it.id in ids }) continue
            val r = Rect().also { node.getBoundsInScreen(it) }
            if (r.isEmpty) continue
            val b = best
            if (b == null || r.width().toLong() * r.height() > b.width().toLong() * b.height()) best = r
        }
        return best
    }

    /**
     * Dự phòng khi không nút nào báo hành động ngang: khung cuộn được, có FORWARD/BACKWARD chung và TÊN LỚP giống bộ
     * chuyển trang ngang (ViewPager, Workspace, HorizontalScrollView...). Cùng thứ tự ưu tiên như [pageTargets].
     */
    fun pageTargetsByHint(from: AccessibilityNodeInfo?, root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        scrollTargetsBy(from, root) { node ->
            PagerHints.looksHorizontalPager(node.className) &&
                node.actionList.any { a -> a.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || a.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD }
        }

    /** Thứ tự thử hành động chuyển trang: FORWARD/BACKWARD (tự xử lý RTL) trước, rồi trang, rồi cuộn trái/phải. */
    fun pageActions(forward: Boolean): IntArray =
        if (forward) {
            intArrayOf(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
            )
        } else {
            intArrayOf(
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id,
            )
        }

    private val HORIZONTAL_ACTION_IDS: Set<Int> = setOf(
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id,
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
        AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id,
        AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id,
    )

    private fun scrollTargetsBy(
        from: AccessibilityNodeInfo?,
        root: AccessibilityNodeInfo,
        accepts: (AccessibilityNodeInfo) -> Boolean,
    ): List<AccessibilityNodeInfo> {
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

        return ScrollOrdering.order(candidates).map { nodes[it.id] }.filter(accepts)
    }

    /** Hai hành động cuộn tương đương theo hướng (ưu tiên FORWARD/BACKWARD rồi tới DOWN/UP). */
    fun scrollActions(forward: Boolean): IntArray =
        if (forward) {
            intArrayOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
        } else {
            intArrayOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)
        }
}
