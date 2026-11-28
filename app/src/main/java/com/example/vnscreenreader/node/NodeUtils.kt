package com.example.vnscreenreader.node

import android.graphics.Rect
import com.example.vnscreenreader.core.PagerHints
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.vnscreenreader.core.CheckedState
import com.example.vnscreenreader.core.ClickProbe
import com.example.vnscreenreader.core.ClickTargetPolicy
import com.example.vnscreenreader.core.FocusTrace
import com.example.vnscreenreader.core.ListPosition
import com.example.vnscreenreader.core.ListPositions
import com.example.vnscreenreader.core.HoverPolicy
import com.example.vnscreenreader.core.NodeSnapshot
import com.example.vnscreenreader.core.ScrollCandidate
import com.example.vnscreenreader.core.ScrollEdge
import com.example.vnscreenreader.core.ScrollOrdering
import com.example.vnscreenreader.core.StopFacts
import com.example.vnscreenreader.core.StopSelection
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
    hasRange = rangeInfo != null,
    isScreenReaderFocusable = isScreenReaderFocusable,
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

/**
 * Phím của bàn phím nhập liệu (chữ, số, xóa...): được đánh dấu là phím nhập, hoặc nằm trong cửa sổ bàn phím (IME). Nhấc tay là gõ
 * ([com.example.vnscreenreader.core.LiftToTypePolicy]) và nhấn phím thất bại thì không nhấn phím bên cạnh ([ClickTargetPolicy.mayClickNearby]).
 */
fun AccessibilityNodeInfo.isKeyboardKey(): Boolean =
    isTextEntryKey || window?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD

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

    /** Số cấp tối đa đi ngược lên để tìm khung cuộn bao quanh một phần tử: cây trang web rất sâu nên rộng hơn [MAX_ANCESTOR_DEPTH]. */
    private const val MAX_SCROLL_ANCESTOR_DEPTH = 40
    private const val MAX_HOVER_ANCESTOR_DEPTH = 4

    /**
     * Số nút tối đa khi dựng cây con để gộp chữ ([mergedLabel]) — chỉ để chặn thời gian trên cây khổng lồ; số MẢNH chữ gộp được
     * do [StopSelection.MERGE_MAX_PARTS] quyết định (khung nhiều chữ hơn không còn là khối gộp). [MERGE_READ_MAX_PARTS] là trần an toàn
     * khi gộp cho nút bất kỳ (không phải điểm dừng do [collect] chọn), cao hơn hẳn để không cắt chữ.
     */
    private const val MERGE_NODE_BUDGET = 300
    private const val MERGE_READ_MAX_PARTS = 30
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
     * Kết quả [collect]: các điểm dừng theo thứ tự đọc, và [complete] = false khi có nút con không lấy được (cây đang đổi hoặc hệ thống
     * không trả nút) nên danh sách CÓ THỂ thiếu phần tử — nơi gọi không nên giữ nó trong bộ đệm.
     */
    class Collected(val nodes: List<AccessibilityNodeInfo>, val complete: Boolean)

    /**
     * Các phần tử đáng dừng lại khi vuốt, theo thứ tự đọc. Mục tiêu: KHÔNG bỏ sót phần tử nào người dùng cần nghe hay nhấn, và không
     * đọc một chữ hai lần. Quy tắc chi tiết (thuần, có kiểm thử) nằm ở [StopSelection]; ở đây chỉ dựng cây phẳng các nút đang hiển thị
     * (tiền thứ tự, trần [maxNodes] nút hiển thị) kèm sự thật cục bộ của từng nút rồi gọi nó. Tóm tắt:
     *  - Nút nhấn/tích/gõ/chỉnh được: luôn là điểm dừng riêng (không nhãn thì đọc gộp chữ con hoặc "không có nhãn"); chữ KHÔNG thao tác
     *    bên dưới nó được gộp vào nhãn của nó.
     *  - Nút chỉ có chữ: điểm dừng, KHÔNG nuốt con (khung có `contentDescription` không làm mất chữ bên dưới nữa).
     *  - Khung bố cục, khung bọc quanh các nút khác, khung bấm được chứa quá nhiều chữ: không phải điểm dừng, các con tự là điểm dừng.
     * Thời gian O(số nút duyệt), bộ nhớ O(số nút hiển thị).
     */
    fun collect(root: AccessibilityNodeInfo, maxNodes: Int = MAX_NODES): Collected {
        val tree = buildTree(root, TraversalBudget(maxNodes), rootMustBeVisible = true)
        val stops = StopSelection.select(tree.parent, tree.facts)
        val nodes = ArrayList<AccessibilityNodeInfo>(stops.size)
        for (index in stops) nodes += tree.nodes[index]
        return Collected(nodes, tree.complete)
    }

    /** Cây phẳng các nút hiển thị theo tiền thứ tự: [parent] là chỉ số cha (-1 ở gốc), [facts] là sự thật cục bộ của từng nút. */
    private class FlatTree(
        val nodes: List<AccessibilityNodeInfo>,
        val parent: IntArray,
        val facts: List<StopFacts>,
        val complete: Boolean,
    )

    /**
     * Duyệt sâu trước (ngăn xếp tường minh, không đệ quy) trong ngân sách [budget]. Nút ẨN bị bỏ cùng cả nhánh của nó. [rootMustBeVisible] = false
     * thì gốc luôn được giữ dù ẩn (gộp chữ cho một nút đã biết).
     */
    private fun buildTree(root: AccessibilityNodeInfo, budget: TraversalBudget, rootMustBeVisible: Boolean): FlatTree {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val parents = ArrayList<Int>()
        val facts = ArrayList<StopFacts>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>() // (nút, chỉ số cha trong cây phẳng)
        var complete = true

        fun visit(node: AccessibilityNodeInfo, parentIndex: Int) {
            val index = nodes.size
            nodes += node
            parents += parentIndex
            facts += factsOf(node)
            for (i in node.childCount - 1 downTo 0) {
                val child = node.getChild(i)
                if (child == null) complete = false else stack.addLast(child to index)
            }
        }

        val rootVisible = root.isVisibleToUser
        budget.record(rootVisible)
        if (rootVisible || !rootMustBeVisible) visit(root, -1)
        while (stack.isNotEmpty() && budget.canContinue) {
            val (node, parentIndex) = stack.removeLast()
            val visible = node.isVisibleToUser
            budget.record(visible)
            if (visible) visit(node, parentIndex)
        }
        return FlatTree(nodes, parents.toIntArray(), facts, complete)
    }

    private fun factsOf(node: AccessibilityNodeInfo): StopFacts {
        val range = node.rangeInfo != null
        return StopFacts(
            hasLabel = hasOwnLabel(node),
            hasAuxLabel = !node.hintText.isNullOrBlank() || !node.tooltipText.isNullOrBlank(),
            clickable = node.isClickable,
            screenReaderFocusable = node.isScreenReaderFocusable,
            checkable = node.isCheckable,
            editable = node.isEditable,
            range = range,
            // Chỉ tra danh sách hành động khi có RangeInfo (thanh trượt/tiến trình): với hầu hết nút việc này được bỏ qua.
            adjustable = range && node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id },
            structural = node.isScrollable || node.collectionInfo != null,
        )
    }

    private fun hasOwnLabel(node: AccessibilityNodeInfo): Boolean =
        !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()

    /** Chữ riêng của nút để đọc: `contentDescription` trước rồi `text` (cùng thứ tự như lời đọc phần tử); ô mật khẩu không bao giờ lộ chữ. */
    private fun labelText(node: AccessibilityNodeInfo): String? {
        if (node.isPassword) return null
        val description = node.contentDescription?.toString()?.trim()
        if (!description.isNullOrEmpty()) return description
        return node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Nhãn gộp của một khung không có nhãn riêng: chữ của các nút bên dưới theo thứ tự đọc, nối bằng ", " (xem [StopSelection.mergeParts]).
     * Mặc định chỉ gồm phần chữ mà điểm dừng này đọc THAY cho mình: nút thao tác bên trong (nhấn/tích/gõ/chỉnh) là điểm dừng riêng nên
     * không bị đọc lặp, và khung cuộn/danh sách bên trong là ranh giới. [everything] = true gộp mọi chữ hiển thị bên dưới (vùng thông báo
     * trực tiếp, kiểm tra "bên dưới có chữ không"). Rỗng nếu không có chữ nào.
     */
    fun mergedLabel(node: AccessibilityNodeInfo, everything: Boolean = false): String {
        if (node.childCount == 0) return ""
        val tree = buildTree(node, TraversalBudget(MERGE_NODE_BUDGET, MERGE_NODE_BUDGET * 4), rootMustBeVisible = false)
        val picks = StopSelection.mergeParts(tree.parent, tree.facts, everything)
        if (picks.isEmpty()) return ""
        // Gộp "mọi chữ" (vùng thông báo trực tiếp): giữ ngắn như một khối gộp; một danh sách dài đánh dấu trực tiếp không được đọc cả loạt.
        val limit = if (everything) StopSelection.MERGE_MAX_PARTS else MERGE_READ_MAX_PARTS
        val parts = ArrayList<String>(minOf(picks.size, limit))
        for (index in picks) {
            if (parts.size >= limit) break
            labelText(tree.nodes[index])?.let { parts += it }
        }
        return parts.joinToString(", ")
    }

    /**
     * Phần tử cần đọc khi ngón tay chạm vào [source] (sự kiện HOVER_ENTER của chế độ khám phá bằng cảm ứng).
     * - Chính [source] nếu nó có nội dung/có thể tương tác/có khoảng giá trị ([HoverPolicy.isReadable]).
     * - Khối đọc tự khai báo không nhãn ([HoverPolicy.needsMergedText]): chính [source] khi bên dưới có chữ để gộp.
     * - Nếu không (ví dụ ảnh con không nhãn nằm trong một nút bấm): nút bấm cha gần nhất ([HoverPolicy.isReadableAncestor]).
     * - Trả về null khi chạm vào khoảng trống: không đọc gì.
     */
    fun findHoverTarget(source: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!source.isVisibleToUser) return null
        val snapshot = source.toSnapshot()
        if (HoverPolicy.isReadable(snapshot)) return source
        if (HoverPolicy.needsMergedText(snapshot) && mergedLabel(source).isNotEmpty()) return source
        var current = source.parent
        var depth = 0
        while (current != null && depth < MAX_HOVER_ANCESTOR_DEPTH) {
            // Chỉ cần cờ bấm được: dựng ảnh chụp tối giản thay vì toSnapshot() đầy đủ (không tốn lượt gọi hệ thống cho mỗi tổ tiên).
            if (current.isVisibleToUser && HoverPolicy.isReadableAncestor(NodeSnapshot(isClickable = current.isClickable))) return current
            current = current.parent
            depth++
        }
        return null
    }

    /** Nút có hành động nhấn dù cờ `isClickable` là false (nút web, view tự khai báo hành động). */
    fun hasClickAction(node: AccessibilityNodeInfo): Boolean =
        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }

    /**
     * Nút thuộc nội dung web: có hành động duyệt phần tử HTML (ACTION_NEXT/PREVIOUS_HTML_ELEMENT). Dựa vào khả năng chứ
     * không dựa vào tên lớp nên đúng cả với trình duyệt không dùng WebView (Gecko và các trình duyệt khác).
     */
    fun hasHtmlNavigation(node: AccessibilityNodeInfo): Boolean = node.actionList.any {
        it.id == AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT || it.id == AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT
    }

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
     * Như [scrollTargets] nhưng chỉ giữ khung NỘI DUNG cuộn DỌC (danh sách, trang): bỏ bộ chọn giá trị (NumberPicker, thanh trượt...)
     * vì với chúng "cuộn" là ĐỔI GIÁ TRỊ đang chỉnh, và bỏ băng chuyền/bộ chuyển trang chỉ cuộn ngang (vuốt hết màn hình không được
     * lật trang ngang). Dùng cho việc app tự cuộn hộ khi vuốt hết màn hình; các lệnh cuộn người dùng chủ động vẫn dùng [scrollTargets].
     */
    fun contentScrollTargets(from: AccessibilityNodeInfo?, root: AccessibilityNodeInfo, forward: Boolean): List<AccessibilityNodeInfo> {
        val wanted = scrollActions(forward)
        return scrollTargetsBy(from, root) { node -> isVerticalContentScroller(node, wanted) }
    }

    /**
     * Khung cuộn DỌC gần nhất BAO QUANH [node] (không tính chính nó) còn cuộn được theo hướng yêu cầu ([isVerticalContentScroller]);
     * null nếu không có. Đi ngược lên tối đa [MAX_SCROLL_ANCESTOR_DEPTH] cấp.
     */
    fun verticalScrollAncestor(node: AccessibilityNodeInfo, forward: Boolean): AccessibilityNodeInfo? {
        val wanted = scrollActions(forward)
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 1
        while (current != null && depth <= MAX_SCROLL_ANCESTOR_DEPTH) {
            if (current.isScrollable && isVerticalContentScroller(current, wanted)) return current
            current = current.parent
            depth++
        }
        return null
    }

    /** [node] còn hành động cuộn trong [wanted], không phải khung chỉ cuộn ngang, không phải bộ chọn giá trị. Chỉ đọc trường cục bộ (không IPC). */
    private fun isVerticalContentScroller(node: AccessibilityNodeInfo, wanted: IntArray): Boolean {
        val ids = node.actionList.map { it.id }
        return ids.any { it in wanted } &&
            !ScrollEdge.isHorizontalOnly(
                ids,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id,
            ) &&
            ScrollEdge.isContentScroller(node.className, node.rangeInfo != null)
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
