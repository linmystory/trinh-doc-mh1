package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.AutoOcrPolicy
import com.example.vnscreenreader.core.CustomLabelStore
import com.example.vnscreenreader.core.AutoScrollPicker
import com.example.vnscreenreader.core.ActionHintKind
import com.example.vnscreenreader.core.ClickTargetPolicy
import com.example.vnscreenreader.core.ActionHints
import com.example.vnscreenreader.core.FocusIndex
import com.example.vnscreenreader.core.FocusTrace
import com.example.vnscreenreader.core.FocusTraceBuffer
import com.example.vnscreenreader.core.QuickNav
import com.example.vnscreenreader.core.ReadingMode
import com.example.vnscreenreader.core.ReadingModeSearch
import com.example.vnscreenreader.core.TraversalOrder
import com.example.vnscreenreader.core.SpeechDetail
import com.example.vnscreenreader.core.Verbosity
import com.example.vnscreenreader.node.isLinkLike
import com.example.vnscreenreader.core.FocusLocator
import com.example.vnscreenreader.core.FocusTracker
import com.example.vnscreenreader.core.LongPressTiming
import com.example.vnscreenreader.core.LabelResolver
import com.example.vnscreenreader.core.ScreenSearch
import com.example.vnscreenreader.core.SpeechTextBuilder
import com.example.vnscreenreader.core.TextCursor
import com.example.vnscreenreader.core.TextGranularity
import com.example.vnscreenreader.core.EditOp
import com.example.vnscreenreader.core.EditPolicy
import com.example.vnscreenreader.core.SelectionMath
import com.example.vnscreenreader.core.NearbyControlPicker
import com.example.vnscreenreader.core.SwipeGeometry
import com.example.vnscreenreader.core.TechInfo
import com.example.vnscreenreader.core.UnitMove
import com.example.vnscreenreader.core.freshOrNull
import com.example.vnscreenreader.menu.CustomAction
import com.example.vnscreenreader.menu.Menus
import com.example.vnscreenreader.node.NodeCollector
import com.example.vnscreenreader.node.toSnapshot
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/**
 * Quản lý phần tử đang chọn và điều hướng. Chỉ được gọi từ một luồng xử lý duy nhất
 * (dispatcher 1 luồng của service) nên không cần khóa.
 */
class FocusNavigator(
    private val service: AccessibilityService,
    private val overlay: HighlightOverlay,
    private val customLabels: CustomLabelStore,
    private val feedback: Feedback = Feedback.None,
    private val speak: (text: String, interrupt: Boolean) -> Unit,
) {
    /** Tiêu điểm chi tiết đang bật: duyệt cả ô tích/nút không nhãn nằm trong khung bấm được. Do phiên đọc đặt. */
    @Volatile var detailedFocus: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateCache()
            }
        }

    /** Chế độ đọc hiện tại; ELEMENT = duyệt từng phần tử như cũ. Do người dùng đổi bằng cử chỉ. */
    @Volatile var readingMode: ReadingMode = ReadingMode.ELEMENT

    /** Cửa sổ người dùng đã chọn bằng "cửa sổ kế/trước"; null = cửa sổ đang hoạt động. Xóa khi cửa sổ đổi. */
    private var windowOverrideId: Int? = null

    private var currentNode: AccessibilityNodeInfo? = null

    /** Mức chi tiết khi đọc phần tử vừa chọn. Do phiên đọc đặt theo cài đặt (xem [Verbosity]). */
    @Volatile var verbosity: Verbosity = Verbosity.STANDARD

    /**
     * Bộ đệm vết các phần tử đã chọn (mã, nhãn, hộp, chỉ số danh sách) để bám lại khi giao diện vẽ lại hoặc nạp thêm dữ
     * liệu: nút cũ bị tái chế thì tìm lại theo vết thay vì quay về phần tử đầu tiên. Chỉ [reset] mới xóa vết.
     */
    private val traces = FocusTraceBuffer()
    private val clicker = ClickResolver(service)
    private var cachedNodes: List<AccessibilityNodeInfo> = emptyList()
    private var cachedAtMs = 0L
    @Volatile private var staleSinceMs = 0L
    @Volatile private var lastOwnFocusMs = 0L
    private val cursor = TextCursor()

    /** Gọi sau mỗi lần chọn phần tử mới (dùng cho tự đọc chữ trong ảnh). Đặt một lần khi khởi tạo phiên. */
    var onFocused: (AccessibilityNodeInfo) -> Unit = {}

    /** Phần tử đang chọn (có thể đã cũ); null nếu chưa chọn. */
    fun current(): AccessibilityNodeInfo? = currentNode

    /**
     * Phần tử đang chọn sau khi `refresh()`. Nếu nút đã cũ: đọc "Phần tử đã thay đổi", đặt lại lựa chọn và trả
     * null. Mọi thao tác đọc bounds/chữ/vùng chọn hoặc `performAction` phải đi qua đây.
     */
    fun currentFresh(): AccessibilityNodeInfo? {
        val node = currentNode ?: return null
        return freshOrNull(node, refresh = { it.refresh() }, onStale = {
            speak(t(S.MSG_ELEMENT_CHANGED), true)
            release() // bỏ nút cũ nhưng GIỮ vết: vuốt tiếp vẫn đi từ đúng chỗ đang đứng
        })
    }

    /** Đổi ngữ cảnh (màn hình/cửa sổ/trang mới, sau khi cuộn): bỏ cả phần tử đang chọn lẫn vết. */
    fun reset() {
        release()
        traces.clear()
    }

    /**
     * Gọi khi cửa sổ thật sự đổi (TYPE_WINDOW_STATE_CHANGED): bỏ cửa sổ đã chọn bằng "cửa sổ kế/trước" và đưa chế độ đọc
     * về mặc định. KHÔNG làm việc này trong [reset] vì reset còn được gọi sau khi cuộn hay chạy plugin.
     */
    fun onWindowChanged() {
        windowOverrideId = null
        readingMode = ReadingMode.ELEMENT
        reset()
    }

    /** Bỏ nút đang chọn nhưng giữ [traces]. */
    private fun release() {
        currentNode = null
        invalidateCache()
        overlay.clear()
    }

    fun invalidateCache() {
        cachedNodes = emptyList()
        cachedAtMs = 0L
    }

    fun focusOn(node: AccessibilityNodeInfo, prefix: String? = null) {
        selectQuietly(node)
        feedback.onFocusChanged()
        val spoken = describeFocus(node)
        speak(if (prefix.isNullOrBlank()) spoken else "$prefix. $spoken", true)
        onFocused(node)
    }

    /** Chọn phần tử (bỏ qua việc đọc): dùng khi còn đang tìm, chỉ đọc khi đã đến phần tử cuối cùng. */
    private fun selectQuietly(node: AccessibilityNodeInfo) {
        lastOwnFocusMs = SystemClock.uptimeMillis()
        currentNode = node
        rememberTrace(node)
        cursor.bind(nodeText(node))
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        highlight(node)
    }

    private fun rememberTrace(node: AccessibilityNodeInfo) {
        val r = Rect().also { node.getBoundsInScreen(it) }
        // Hộp rỗng (nút chưa vẽ): giữ hộp của vết trước thay vì ghi tọa độ 0 làm sai khe theo vị trí.
        val box = if (r.isEmpty) traces.latest()?.box ?: intArrayOf(0, 0, 0, 0) else intArrayOf(r.left, r.top, r.right, r.bottom)
        traces.push(
            FocusTrace(
                windowId = node.windowId,
                uniqueId = node.uniqueId,
                viewId = node.viewIdResourceName,
                label = labelKey(node),
                box = box,
                itemIndex = NodeCollector.itemIndex(node),
            ),
        )
    }

    fun highlight(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) overlay.clear() else overlay.show(bounds)
    }

    /**
     * Mô tả để đọc. Thứ tự nhãn (không chụp màn hình): nhãn của ứng dụng → nhãn người dùng tự gán →
     * chữ gộp của phần tử con → đoán theo tên view (báo t(S.SP_STATE_GUESS)). Xem [LabelResolver].
     */
    fun describe(node: AccessibilityNodeInfo, detail: SpeechDetail = SpeechDetail.DEFAULT): String {
        val snapshot = node.toSnapshot()
        val resolved = LabelResolver.resolve(
            node = snapshot,
            customLabel = { customLabels.find(snapshot.packageName, snapshot.viewId) },
            mergedChildren = { NodeCollector.mergedLabel(node) },
        )
        return SpeechTextBuilder.buildNodeDescription(resolved, detail)
    }

    /** Câu đọc khi vừa chọn phần tử, theo [verbosity]; vị trí trong danh sách chỉ tra khi cần (mức chi tiết). */
    private fun describeFocus(node: AccessibilityNodeInfo): String {
        val position = if (verbosity == Verbosity.DETAILED) NodeCollector.listPosition(node) else null
        return describe(node, SpeechDetail(verbosity, position))
    }

    /**
     * Gợi ý thao tác phù hợp với phần tử đang chọn ("... để kích hoạt", "... để mở liên kết"); null nếu không có.
     * Chỉ tra khi người dùng bật đọc gợi ý thao tác (duyệt cây tổ tiên/con tốn vài lần gọi hệ thống).
     */
    fun actionHintKind(node: AccessibilityNodeInfo): ActionHintKind? {
        val actionable = clicker.candidates(node, includeDescendants = false).isNotEmpty()
        return ActionHints.kindOf(
            editable = node.isEditable,
            link = node.isLinkLike(),
            checkable = node.isCheckable,
            actionable = actionable,
            enabled = node.isEnabled,
        )
    }

    fun move(forward: Boolean) {
        if (readingMode != ReadingMode.ELEMENT) {
            moveByMode(forward)
            return
        }
        val nodes = visibleNodes()
        if (nodes == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        if (nodes.isEmpty()) {
            speak(t(S.MSG_SCREEN_EMPTY), true)
            return
        }
        val current = locateCurrent(nodes, forward)
        com.example.vnscreenreader.core.Diag.log("MOVE") { "fwd=$forward size=${nodes.size} current=$current" }
        val target = if (forward) FocusIndex.next(current, nodes.size) else FocusIndex.previous(current, nodes.size)
        if (target == null) {
            // Hết phần tử trong màn hình: thử cuộn rồi chọn phần tử mới thay vì dừng.
            if (autoScrollAndSelect(nodes, forward)) return
            feedback.onBoundary()
            speak(if (forward) t(S.MSG_LAST_ELEMENT) else t(S.MSG_FIRST_ELEMENT), true)
            return
        }
        focusOn(nodes[target])
    }

    /**
     * Kích hoạt phần tử đang chọn. Thứ tự: ACTION_CLICK lên nút bấm được gần nhất (kể cả nút chỉ khai báo hành động
     * nhấn, như thẻ liên kết trong WebView) → nhấn con bấm được → nhấn điều khiển cạnh dòng chữ → (chỉ trong nội
     * dung web) giả lập chạm giữa phần tử. Câu báo lỗi do [ActionDispatcher] đọc theo kết quả trả về.
     */
    internal fun activate(): ActivateOutcome {
        val selected = currentNode ?: return ActivateOutcome.NOTHING_SELECTED
        val node = if (selected.refresh()) selected else relocateCurrent() ?: run {
            release()
            return ActivateOutcome.GONE
        }
        val candidates = clicker.candidates(node)
        if (candidates.any { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }) {
            feedback.onActivated()
            invalidateCache()
            return ActivateOutcome.CLICKED
        }
        // Dòng chữ không bấm được (hoặc bấm không có tác dụng): thường là nhãn của một ô tích/nút nằm ngay cạnh.
        if (activateNearby(node)) return ActivateOutcome.CLICKED_NEARBY
        if (ClickTargetPolicy.shouldTapFallback(clicker.isInsideWeb(node), clicker.isWebRoot(node)) && clicker.tapCenter(node)) {
            feedback.onActivated()
            invalidateCache()
            return ActivateOutcome.TAPPED
        }
        return if (candidates.isEmpty()) ActivateOutcome.CANNOT_CLICK else ActivateOutcome.CLICK_FAILED
    }

    /**
     * Nút đang chọn đã cũ: tìm lại phần tử theo vết (khớp mạnh: mã hoặc nhãn) trong cây hiện tại và chọn lại KHÔNG
     * đọc. null nếu không thấy.
     */
    private fun relocateCurrent(): AccessibilityNodeInfo? {
        invalidateCache()
        val nodes = visibleNodes() ?: return null
        val index = locateByTrace(nodes)
        if (index < 0) return null
        val node = nodes[index]
        currentNode = node
        rememberTrace(node)
        highlight(node)
        return node
    }

    /** Nhấn điều khiển (ô tích/nút) cùng hàng, nằm cạnh [node]; tìm trong các con của cha và ông của nó. */
    private fun activateNearby(node: AccessibilityNodeInfo): Boolean {
        val anchor = Rect().also { node.getBoundsInScreen(it) }
        val pool = ArrayList<AccessibilityNodeInfo>()
        var level: AccessibilityNodeInfo? = node.parent
        repeat(NEARBY_PARENT_LEVELS) {
            val parent = level ?: return@repeat
            for (i in 0 until minOf(parent.childCount, MAX_NEARBY_CHILDREN)) {
                val child = parent.getChild(i) ?: continue
                if (child != node && child.isVisibleToUser) pool += child
            }
            level = parent.parent
        }
        val candidates = pool.mapIndexed { index, n ->
            val r = Rect().also { n.getBoundsInScreen(it) }
            NearbyControlPicker.Candidate(index, NearbyControlPicker.Box(r.left, r.top, r.right, r.bottom), n.isCheckable, n.isClickable)
        }
        val pick = NearbyControlPicker.pick(NearbyControlPicker.Box(anchor.left, anchor.top, anchor.right, anchor.bottom), candidates)
            ?: return false
        val target = pool[pick]
        if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return false
        feedback.onActivated()
        invalidateCache()
        speak(t(S.MSG_NEARBY_CLICKED), true)
        return true
    }

    /** Thông tin kỹ thuật của phần tử đang chọn; null nếu chưa chọn hoặc nút đã cũ. */
    fun technicalInfo(): String? {
        if (currentNode == null) return null
        val node = currentFresh() ?: return null
        return TechInfo.describe(node.className, node.isClickable, node.isCheckable, node.isImportantForAccessibility, node.viewIdResourceName)
    }

    /** Loại phần tử để duyệt nhanh. */
    enum class QuickKind(internal val htmlElement: String) { HEADING("HEADING"), LINK("LINK") }

    /** Cử chỉ mới tới là dừng ngay vòng cuộn dài đang chạy (cuộn tới đầu/cuối, tìm tiêu đề/liên kết ngoài màn hình). */
    @Volatile private var longScrollCancelled = false

    /** Gọi từ luồng chính khi có cử chỉ mới; vòng cuộn dài kiểm tra cờ này sau mỗi bước. */
    fun cancelLongScroll() {
        longScrollCancelled = true
    }

    /**
     * Duyệt nhanh trong nội dung WEB bằng hành động chuẩn của nền tảng (ACTION_NEXT/PREVIOUS_HTML_ELEMENT với loại
     * "LINK"/"HEADING"): trình duyệt tự nhảy tới phần tử kế tiếp kể cả ngoài màn hình, không cần cuộn và quét như ứng
     * dụng thường. Trả null nếu nội dung không hỗ trợ hành động này (rơi về cách quét cũ); true/false = đã chuyển được hay
     * đã hết.
     */
    private fun webQuickMove(kind: QuickKind, forward: Boolean): Boolean? {
        val root = activeRoot() ?: return null
        val current = currentQuiet()?.takeIf { clicker.isInsideWeb(it) }
        val start = current
            ?: NodeCollector.traverse(root).firstOrNull { NodeCollector.isWebRoot(it) }
            ?: return null
        val actionId = if (forward) AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT else AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT
        if (start.actionList.none { it.id == actionId }) return null
        val args = Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_HTML_ELEMENT_STRING, kind.htmlElement) }
        // Xuất phát từ gốc WebView (chưa chọn gì trong trang): thất bại có thể chỉ là "chưa có điểm bắt đầu", không phải "hết"
        // nên rơi về cách quét thay vì báo sai là không còn.
        val fromRoot = current == null
        if (!start.performAction(actionId, args)) return if (fromRoot) null else false
        SystemClock.sleep(WEB_FOCUS_SETTLE_MS)
        val landed = service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return if (fromRoot) null else false
        if (landed == start) return if (fromRoot) null else false
        focusOn(landed)
        return true
    }

    /** Chọn phần tử ĐẦU hoặc CUỐI của màn hình hiện tại. */
    fun jumpEdge(first: Boolean) {
        val nodes = visibleNodes()
        if (nodes == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        if (nodes.isEmpty()) {
            speak(t(S.MSG_SCREEN_EMPTY), true)
            return
        }
        focusOn(if (first) nodes.first() else nodes.last())
    }

    /**
     * Nhảy tới tiêu đề/liên kết kế tiếp (hoặc trước) kể từ phần tử đang chọn. Hết trong màn hình thì cuộn tối đa
     * [MAX_QUICK_SCROLLS] lần để tìm tiếp (giống [move]); vẫn không có thì báo "không còn".
     */
    fun quickMove(kind: QuickKind, forward: Boolean) {
        longScrollCancelled = false
        webQuickMove(kind, forward)?.let { moved ->
            if (!moved) {
                feedback.onBoundary()
                speak(if (kind == QuickKind.HEADING) t(S.MSG_NO_HEADING) else t(S.MSG_NO_LINK), true)
            }
            return
        }
        val initial = visibleNodes()
        if (initial == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        if (initial.isEmpty()) {
            speak(t(S.MSG_SCREEN_EMPTY), true)
            return
        }
        val current = locateCurrent(initial, forward)
        val inScreen = QuickNav.find(initial.size, current, forward) { matchesKind(initial[it], kind) }
        if (inScreen != null) {
            focusOn(initial[inScreen])
            return
        }
        var seen: Set<String> = initial.map { labelKey(it) }.filter { it.isNotBlank() }.toSet()
        for (attempt in 0 until MAX_QUICK_SCROLLS) {
            if (longScrollCancelled || !scrollOnce(forward)) break
            SystemClock.sleep(AUTO_SCROLL_SETTLE_MS)
            if (longScrollCancelled) return
            invalidateCache()
            val fresh = visibleNodes() ?: break
            // Chỉ xét phần tử MỚI hiện ra sau khi cuộn, để thanh tiêu đề cố định ở đầu màn hình không bị chọn lại.
            val hits = fresh.indices.filter { i ->
                val label = labelKey(fresh[i])
                matchesKind(fresh[i], kind) && (label.isBlank() || label !in seen)
            }
            val pick = if (forward) hits.firstOrNull() else hits.lastOrNull()
            if (pick != null) {
                focusOn(fresh[pick])
                return
            }
            seen = seen + fresh.map { labelKey(it) }.filter { it.isNotBlank() }
        }
        feedback.onBoundary()
        speak(if (kind == QuickKind.HEADING) t(S.MSG_NO_HEADING) else t(S.MSG_NO_LINK), true)
    }

    private fun matchesKind(node: AccessibilityNodeInfo, kind: QuickKind): Boolean = when (kind) {
        QuickKind.HEADING -> node.isHeading
        QuickKind.LINK -> node.isLinkLike()
    }

    /** Nhảy tới hàng đầu/cuối bằng ACTION_SCROLL_TO_POSITION nếu khung cuộn hỗ trợ và biết số hàng. */
    private fun jumpByPosition(forward: Boolean): Boolean {
        val root = activeRoot() ?: return false
        val toPosition = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id
        for (target in NodeCollector.scrollTargets(currentQuiet(), root, forward)) {
            if (target.actionList.none { it.id == toPosition }) continue
            val rows = target.collectionInfo?.rowCount ?: continue
            if (rows < 2) continue
            val args = Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, if (forward) rows - 1 else 0) }
            if (target.performAction(toPosition, args)) return true
        }
        return false
    }

    /** Một lần cuộn bằng hành động của khung cuộn gần nhất; true nếu có khung cuộn được thật sự. */
    private fun scrollOnce(forward: Boolean): Boolean {
        val root = activeRoot() ?: return false
        val targets = NodeCollector.scrollTargets(currentQuiet(), root, forward)
        return targets.any { target -> NodeCollector.scrollActions(forward).any { target.performAction(it) } }
    }

    /**
     * Cuộn liên tục tới cuối/đầu trang (tối đa [MAX_EDGE_STEPS] lần, nghỉ [EDGE_STEP_MS] giữa các lần để nội dung kịp vẽ).
     * Dừng sớm khi khung không còn cuộn được. Danh sách vô tận chỉ cuộn được tới trần rồi báo "đã cuộn", không nói dối là
     * "tới cuối trang". Chạy trên luồng nền duy nhất của phiên đọc.
     */
    fun scrollToEdge(forward: Boolean) {
        val root = service.rootInActiveWindow
        if (root == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        if (NodeCollector.traverse(root).none { it.isScrollable }) {
            speak(t(S.MSG_NO_SCROLL_AREA), true)
            return
        }
        longScrollCancelled = false
        // Danh sách biết số hàng: nhảy thẳng tới hàng đầu/cuối (một bước) rồi vòng cuộn bên dưới chỉ để xác nhận đã tới biên.
        var steps = 0
        if (jumpByPosition(forward)) {
            steps = 1
            SystemClock.sleep(AUTO_SCROLL_SETTLE_MS)
        }
        while (steps < MAX_EDGE_STEPS && !longScrollCancelled && scrollOnce(forward)) {
            steps++
            SystemClock.sleep(EDGE_STEP_MS)
        }
        if (steps > 0) {
            invalidateCache()
            reset()
        }
        // Người dùng đã làm việc khác giữa chừng: không đọc câu kết quả đè lên thao tác mới của họ.
        if (longScrollCancelled) return
        val capped = steps >= MAX_EDGE_STEPS
        speak(
            when {
                capped && forward -> t(S.MSG_SCROLLED_DOWN)
                capped -> t(S.MSG_SCROLLED_UP)
                forward -> t(S.MSG_SCROLLED_BOTTOM)
                else -> t(S.MSG_SCROLLED_TOP)
            },
            true,
        )
    }

    fun scroll(forward: Boolean) {
        val root = service.rootInActiveWindow
        if (root == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        // Thử lần lượt từng vùng cuộn (tổ tiên trước, rồi vùng lớn nhất) và từng loại hành động cho tới khi một
        // vùng thật sự cuộn; chỉ khi không vùng nào cuộn được mới báo đã tới cuối.
        val targets = NodeCollector.scrollTargets(currentQuiet(), root, forward)
        if (targets.isEmpty()) {
            val any = NodeCollector.traverse(root).any { it.isScrollable }
            speak(if (any) t(S.MSG_CANNOT_SCROLL_MORE) else t(S.MSG_NO_SCROLL_AREA), true)
            return
        }
        val done = targets.any { target -> NodeCollector.scrollActions(forward).any { target.performAction(it) } }
        if (done) {
            invalidateCache()
            reset()
            speak(if (forward) t(S.MSG_SCROLLED_DOWN) else t(S.MSG_SCROLLED_UP), true)
        } else {
            speak(t(S.MSG_CANNOT_SCROLL_MORE), true)
        }
    }

    /**
     * Ghép mô tả các phần tử theo thứ tự đọc thành một văn bản (đọc liền mạch, TTS tự xếp hàng các đoạn).
     * Giới hạn [MAX_READ_ALL] phần tử để không đọc vô tận trên màn hình rất dài. null nếu không đọc được cây.
     */
    fun describeScreen(fromCurrent: Boolean): String? {
        invalidateCache()
        val nodes = visibleNodes() ?: return null
        val start = if (fromCurrent) locateCurrent(nodes, forward = true).coerceAtLeast(0) else 0
        return nodes.asSequence().drop(start).take(MAX_READ_ALL).map { describe(it) }.joinToString(". ")
    }

    /** Tìm phần tử theo chữ (không phân biệt hoa/thường/dấu), nhảy tới kết quả kế tiếp sau phần tử đang chọn. */
    fun search(query: String) {
        invalidateCache()
        val nodes = visibleNodes()
        if (nodes == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        val index = ScreenSearch.find(nodes.map { describe(it) }, query, locateCurrent(nodes, forward = true))
        if (index == null) speak(t(S.MSG_NOT_FOUND, query), true) else focusOn(nodes[index])
    }

    /** Hành động tùy chỉnh (do ứng dụng khai báo) của phần tử đang chọn. */
    fun customActions(): List<CustomAction> =
        currentNode?.actionList.orEmpty()
            .filter { Menus.isCustomAction(it.id, it.label) }
            .map { CustomAction(it.id, it.label.toString()) }

    fun performCustomAction(id: Int): Boolean {
        val node = currentNode ?: return false
        if (!node.refresh()) return false
        val done = node.performAction(id)
        if (done) invalidateCache()
        return done
    }

    fun longClick() {
        if (currentNode == null) {
            speak(t(S.MSG_NOTHING_SELECTED), true)
            return
        }
        val node = currentFresh() ?: return
        // 1) Hành động nhấn giữ của khung (hoặc cha). 2) Context click (menu ngữ cảnh chuột phải). 3) Giả lập chạm giữ.
        val target = NodeCollector.findAncestorOrSelf(node) { it.isLongClickable }
        val done = (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) ||
            NodeCollector.findAncestorOrSelf(node) { n ->
                n.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK.id }
            }?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK.id) == true
        if (done) {
            feedback.onActivated()
            invalidateCache()
            speak(t(S.MSG_LONG_CLICKED), true)
            return
        }
        if (!holdAt(node)) speak(t(S.MSG_CANNOT_LONG_CLICK), true)
    }

    /** Chạm giữ giả lập tại giữa phần tử, lâu hơn ngưỡng nhấn giữ của hệ thống. */
    private fun holdAt(node: AccessibilityNodeInfo): Boolean {
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.isEmpty) return false
        val path = android.graphics.Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val duration = LongPressTiming.durationMs(android.view.ViewConfiguration.getLongPressTimeout())
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        val message = t(S.MSG_LONG_CLICKED)
        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: android.accessibilityservice.GestureDescription?) {
                    invalidateCache()
                    speak(message, true)
                }

                override fun onCancelled(g: android.accessibilityservice.GestureDescription?) {
                    speak(t(S.MSG_CANNOT_LONG_CLICK), true)
                }
            },
            null,
        )
    }

    /** Ảnh (kể cả ảnh không nhãn) đang được chọn: quyết định vị trí các mục đọc ảnh trong trình đơn phần tử. */
    fun isCurrentImage(): Boolean {
        val node = currentNode ?: return false
        return AutoOcrPolicy.isAnyImage(node.className?.toString())
    }

    /** Chữ của phần tử đang chọn; null nếu chưa chọn, rỗng hoặc là ô mật khẩu (không bao giờ lộ mật khẩu). */
    fun focusedText(preferSelection: Boolean = false): Result<String> {
        if (currentNode == null) return Result.failure(IllegalStateException(t(S.MSG_NOTHING_SELECTED)))
        val node = currentFresh() ?: return Result.failure(IllegalStateException(t(S.MSG_ELEMENT_CHANGED)))
        if (node.isPassword) return Result.failure(IllegalStateException(t(S.MSG_PASSWORD_NO_ACTION)))
        val raw = (node.text ?: node.contentDescription)?.toString().orEmpty()
        // Có vùng chọn hợp lệ (kể cả chọn ngược) thì chỉ lấy đoạn đã chọn.
        val selected = if (preferSelection) {
            SelectionMath.selectedText(raw, node.textSelectionStart, node.textSelectionEnd)?.trim()
        } else null
        val text = if (!selected.isNullOrEmpty()) selected else raw.trim()
        return if (text.isEmpty()) Result.failure(IllegalStateException(t(S.MSG_NO_TEXT))) else Result.success(text)
    }

    private fun nodeText(node: AccessibilityNodeInfo): String =
        if (node.isPassword) "" else (node.text ?: node.contentDescription)?.toString().orEmpty()

    /** Đổi đơn vị đọc (đoạn → câu → từ → ký tự) và trả về đơn vị mới. */
    fun cycleGranularity(): TextGranularity = cursor.cycle()

    /** Di chuyển tới đơn vị kế/trước của phần tử đang chọn; null nếu chưa chọn phần tử hoặc không có chữ. */
    fun moveUnit(forward: Boolean): UnitMove? {
        if (currentNode == null) return null
        val node = currentFresh() ?: return UnitMove.Changed // đã đọc "phần tử đã thay đổi" và reset
        cursor.bind(nodeText(node))
        val move = cursor.move(forward)
        if (move is UnitMove.End && cursor.current() == null) return null
        return move
    }

    /** Đơn vị đang đọc, null nếu chưa di chuyển. Không bao giờ trả chữ của ô mật khẩu. */
    fun currentUnit(): String? = cursor.current()

    enum class EditResult { DONE, NO_SELECTION, NOT_EDITABLE, PASSWORD, FAILED }

    /**
     * Dán/cắt/chọn tất cả/di chuyển con trỏ trong ô nhập đang chọn. Không đụng tới clipboard (hệ thống tự xử lý
     * ACTION_CUT/ACTION_PASTE). Ô mật khẩu chỉ cho phép dán ([EditPolicy]).
     */
    fun editText(op: EditOp): EditResult {
        if (currentNode == null) return EditResult.NO_SELECTION
        val node = currentFresh() ?: return EditResult.FAILED
        val target = NodeCollector.findAncestorOrSelf(node) { it.isEditable } ?: return EditResult.NOT_EDITABLE
        if (target.isPassword && !EditPolicy.allowedOnPassword(op)) return EditResult.PASSWORD
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val ok = when (op) {
            EditOp.PASTE -> target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            EditOp.CUT -> target.performAction(AccessibilityNodeInfo.ACTION_CUT)
            else -> {
                val length = target.text?.length ?: 0
                val range = SelectionMath.target(op, length, target.textSelectionStart, target.textSelectionEnd)
                range != null && setSelection(target, range.first, range.second)
            }
        }
        if (ok) invalidateCache()
        return if (ok) EditResult.DONE else EditResult.FAILED
    }

    private fun setSelection(node: AccessibilityNodeInfo, start: Int, end: Int): Boolean {
        val args = android.os.Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    /** Phần tử đang chọn có phải ô nhập liệu không (quyết định có hiện trình đơn con "Chỉnh sửa văn bản"). */
    fun isCurrentEditable(): Boolean {
        val node = currentNode ?: return false
        return NodeCollector.findAncestorOrSelf(node) { EditPolicy.isEditField(it.isEditable, it.className) } != null
    }

    /**
     * Chỉ số điểm dừng ứng với phần tử đang chọn, theo thứ tự tin cậy: chính nó → tổ tiên là điểm dừng → khớp vết
     * (mã/nhãn, chịu được việc nút bị tái chế hoặc danh sách nạp thêm) → hộp nhỏ nhất chứa nó theo tọa độ → khe theo vị
     * trí cũ. -1 chỉ khi thật sự không xác định được (chưa chọn gì hoặc đã sang cửa sổ khác): khi đó vuốt bắt đầu từ đầu.
     */
    private fun locateCurrent(nodes: List<AccessibilityNodeInfo>, forward: Boolean): Int {
        nodes.firstOrNull()?.windowId?.let { traces.keepWindow(it) }
        val direct = currentIndexIn(nodes)
        if (direct >= 0) return direct
        val byTrace = locateByTrace(nodes)
        if (byTrace >= 0) return byTrace
        return trackedIndex(nodes, forward) ?: -1
    }

    private fun currentIndexIn(nodes: List<AccessibilityNodeInfo>): Int {
        val cur = currentNode ?: return -1
        nodes.indexOfFirst { it == cur }.let { if (it >= 0) return it }
        var parent = cur.parent
        var depth = 0
        while (parent != null && depth < MAX_PARENT_LOOKUP) {
            val p = parent
            nodes.indexOfFirst { it == p }.let { if (it >= 0) return it }
            parent = p.parent
            depth++
        }
        // Nút cũ thuộc cửa sổ khác: tọa độ của nó vô nghĩa trong cửa sổ hiện tại.
        if (nodes.isNotEmpty() && cur.windowId != nodes[0].windowId) return -1
        // Nút cũ đã bị tái chế (không còn làm mới được): tọa độ cũ có thể đã sai, nhường cho vết (mã/nhãn) trước.
        if (!cur.refresh()) return -1
        val bounds = Rect().also { cur.getBoundsInScreen(it) }
        if (bounds.isEmpty) return -1
        val boxes = nodes.map { n -> Rect().also { n.getBoundsInScreen(it) }.let { intArrayOf(it.left, it.top, it.right, it.bottom) } }
        return FocusLocator.nearestContaining(boxes, bounds.centerX(), bounds.centerY())
    }

    /** Khớp mạnh theo bộ đệm vết (xem [TraceLocator]); -1 nếu không khớp. */
    private fun locateByTrace(nodes: List<AccessibilityNodeInfo>): Int {
        val history = traces.newestFirst()
        if (history.isEmpty() || nodes.isEmpty()) return -1
        val labels = HashMap<Int, String>()
        return FocusLocator.relocate(
            traces = history,
            count = nodes.size,
            uniqueIdOf = { nodes[it].uniqueId },
            viewIdOf = { nodes[it].viewIdResourceName },
            labelOf = { i -> labels.getOrPut(i) { labelKey(nodes[i]) } },
            boxOf = { i -> Rect().also { nodes[i].getBoundsInScreen(it) }.let { intArrayOf(it.left, it.top, it.right, it.bottom) } },
            itemIndexOf = { NodeCollector.itemIndex(nodes[it]) },
        )
    }

    /** Không khớp được phần tử nào: coi như đang đứng ở "khe" của vết mới nhất để vuốt tiếp ra đúng lân cận. */
    private fun trackedIndex(nodes: List<AccessibilityNodeInfo>, forward: Boolean): Int? {
        val last = traces.latest() ?: return null
        val boxes = nodes.map { n -> Rect().also { n.getBoundsInScreen(it) }.let { intArrayOf(it.left, it.top, it.right, it.bottom) } }
        val i = FocusTracker.virtualCurrent(boxes, nodes.map { labelKey(it) }, last.box, last.label, forward)
        return i.takeIf { it >= 0 }
    }

    private fun labelKey(node: AccessibilityNodeInfo): String =
        if (node.isPassword) "" else (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()

    /** Phần tử đang chọn sau `refresh()` nhưng KHÔNG báo gì nếu đã cũ (dùng cho cuộn, không muốn ngắt người dùng). */
    private fun currentQuiet(): AccessibilityNodeInfo? = currentNode?.takeIf { it.refresh() }

    /**
     * Đã tới phần tử cuối/đầu của màn hình: cuộn một lần rồi chọn phần tử mới đầu tiên (tiến) hoặc cuối cùng (lùi).
     * Trả về true nếu đã chọn được; false nếu không có vùng cuộn, cuộn không được hoặc không có phần tử mới.
     * Chờ ngắn để giao diện ổn định (chạy trên luồng xử lý nền duy nhất).
     */
    private fun autoScrollAndSelect(oldNodes: List<AccessibilityNodeInfo>, forward: Boolean, quiet: Boolean = false): Boolean {
        val root = activeRoot() ?: return false
        val targets = NodeCollector.scrollTargets(currentQuiet(), root, forward)
        val oldLabels = oldNodes.map { labelKey(it) }
        val anchor = currentQuiet()?.let { labelKey(it) }.orEmpty()
        val area = Rect()
        // Chiến lược 1: hành động cuộn của chính khung.
        val scrolledTarget = targets.firstOrNull { target ->
            NodeCollector.scrollActions(forward).any { target.performAction(it) }
        }
        if (scrolledTarget != null) {
            scrolledTarget.getBoundsInScreen(area)
            if (pickAfterScroll(area, oldLabels, anchor, forward, attempts = 2, quiet = quiet)) return true
        }
        // Chiến lược 2 (dự phòng): hành động báo thành công nhưng không đổi gì (view tự vẽ, lúc được lúc không), hoặc khung
        // "cuộn được" mà không có hành động nào: giả lập cú vuốt dọc. Ở cuối danh sách thật, khung không còn hành động
        // tiến nên không rơi vào đây.
        val stuck = if (scrolledTarget != null) area else NodeCollector.stuckScrollableBounds(root)
        if (stuck == null || stuck.isEmpty) return false
        val line = SwipeGeometry.vertical(stuck.left, stuck.top, stuck.right, stuck.bottom, forward) ?: return false
        if (!swipeBlocking(line)) return false
        return pickAfterScroll(stuck, oldLabels, anchor, forward, attempts = 2, quiet = quiet)
    }

    /** Chờ giao diện ổn định rồi chọn phần tử kế trong vùng vừa cuộn; thử lại nếu danh sách tải dần. */
    private fun pickAfterScroll(area: Rect, oldLabels: List<String>, anchor: String, forward: Boolean, attempts: Int, quiet: Boolean = false): Boolean {
        repeat(attempts) { attempt ->
            SystemClock.sleep(if (attempt == 0) AUTO_SCROLL_SETTLE_MS else AUTO_SCROLL_RETRY_MS)
            invalidateCache()
            val fresh = visibleNodes() ?: return false
            val inside = fresh.map { n ->
                val r = Rect().also { n.getBoundsInScreen(it) }
                !area.isEmpty && Rect.intersects(area, r) && area.contains(r.centerX(), r.centerY())
            }
            val index = AutoScrollPicker.pickWithin(oldLabels, fresh.map { labelKey(it) }, inside, anchor, forward)
            if (index != null) {
                if (quiet) selectQuietly(fresh[index]) else focusOn(fresh[index])
                return true
            }
        }
        return false
    }

    /** Phát cú vuốt và CHỜ nó kết thúc (chỉ gọi từ luồng xử lý nền, không phải luồng chính). */
    private fun swipeBlocking(line: SwipeGeometry.Line): Boolean {
        val path = android.graphics.Path().apply {
            moveTo(line.startX.toFloat(), line.startY.toFloat())
            lineTo(line.endX.toFloat(), line.endY.toFloat())
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()
        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: android.accessibilityservice.GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(g: android.accessibilityservice.GestureDescription?) { latch.countDown() }
            },
            null,
        )
        if (!dispatched) return false
        latch.await(GESTURE_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        return ok
    }

    /** Chuyển chế độ đọc kế/trước và đọc tên chế độ mới. */
    fun cycleReadingMode(forward: Boolean): ReadingMode {
        val mode = if (forward) readingMode.next() else readingMode.previous()
        readingMode = mode
        com.example.vnscreenreader.core.Diag.log("MODE") { "mode=$mode" }
        speak(t(S.MSG_MODE_SET, mode.label), true)
        return mode
    }

    /**
     * Nhảy tới phần tử cùng loại với [readingMode] kế/trước. Hết màn hình thì cuộn và tìm tiếp (tối đa
     * [MAX_MODE_SCROLLS] lần), cuộn trong im lặng để chỉ đọc phần tử cuối cùng tìm được.
     */
    private fun moveByMode(forward: Boolean) {
        val mode = readingMode
        var nodes: List<AccessibilityNodeInfo> = visibleNodes() ?: run {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        val origin = currentNode // để trả lại nếu tìm không ra (không để lựa chọn trôi đi âm thầm)
        val deadline = SystemClock.uptimeMillis() + MODE_SEARCH_BUDGET_MS
        var scrolls = 0
        while (true) {
            val snapshot = nodes
            val matches = snapshot.map { mode.matches(it.toSnapshot()) }
            val cur = locateCurrent(snapshot, forward)
            val found = ReadingModeSearch.next(matches, cur, forward)
            com.example.vnscreenreader.core.Diag.log("MODE") { "move mode=$mode fwd=$forward size=${snapshot.size} cur=$cur found=$found scrolls=$scrolls" }
            if (found != null) {
                focusOn(snapshot[found])
                return
            }
            val outOfBudget = scrolls >= MAX_MODE_SCROLLS || SystemClock.uptimeMillis() > deadline
            if (outOfBudget || !autoScrollAndSelect(snapshot, forward, quiet = true)) {
                origin?.takeIf { it.refresh() }?.let { selectQuietly(it) }
                feedback.onBoundary()
                speak(t(S.MSG_MODE_NONE, mode.label), true)
                return
            }
            scrolls++
            // Phần tử vừa được chọn sau khi cuộn có thể đã khớp.
            val landed = currentNode
            if (landed != null && mode.matches(landed.toSnapshot())) {
                focusOn(landed)
                return
            }
            nodes = visibleNodes() ?: return
        }
    }

    /** Nhảy tới phần tử đầu của vùng chứa kế/trước (danh sách cuộn, thanh công cụ, hộp thoại, vùng có tiêu đề...). */
    fun moveContainer(forward: Boolean) {
        val nodes = visibleNodes()
        if (nodes == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        if (nodes.isEmpty()) {
            speak(t(S.MSG_SCREEN_EMPTY), true)
            return
        }
        val cache = HashMap<AccessibilityNodeInfo, Int>()
        val keys = nodes.map { containerKey(it, cache) }
        val cur = locateCurrent(nodes, forward)
        val target = com.example.vnscreenreader.core.ContainerNav.target(keys, cur, forward)
        com.example.vnscreenreader.core.Diag.log("CONTAINER") { "fwd=$forward size=${nodes.size} containers=${keys.toSet().size} cur=$cur target=$target" }
        if (target == null) {
            feedback.onBoundary()
            speak(t(S.MSG_CONTAINER_NONE), true)
            return
        }
        focusOn(nodes[target])
    }

    /**
     * Khóa vùng chứa của [node]: tổ tiên gần nhất là khung cuộn/danh sách/vùng có tiêu đề/thanh công cụ/hộp thoại; 0 =
     * ngoài cùng. [cache] dùng chung cho cả lượt duyệt nên mỗi nút cha chỉ bị hỏi một lần (số lần gọi liên tiến trình
     * ~ số nút + số tổ tiên khác nhau, thay vì số nút x độ sâu).
     */
    private fun containerKey(node: AccessibilityNodeInfo, cache: HashMap<AccessibilityNodeInfo, Int>, depth: Int = 0): Int {
        cache[node]?.let { return it }
        val parent = if (depth >= MAX_PARENT_LOOKUP) null else node.parent
        val key = when {
            parent == null -> 0
            isContainer(parent) -> parent.hashCode()
            else -> containerKey(parent, cache, depth + 1)
        }
        cache[node] = key
        return key
    }

    private fun isContainer(n: AccessibilityNodeInfo): Boolean {
        if (n.isScrollable || n.collectionInfo != null || !n.paneTitle.isNullOrEmpty()) return true
        val cls = n.className?.toString().orEmpty()
        return listOf("Toolbar", "ActionBar", "TabLayout", "BottomNavigation", "NavigationBar", "Dialog").any { it in cls }
    }

    /** Chuyển sang cửa sổ kế/trước (ví dụ từ hộp thoại hoặc bàn phím về màn hình phía sau) và đọc phần tử đầu của nó. */
    fun moveWindow(forward: Boolean) {
        val windows = service.windows.filter { w ->
            w.root != null && w.type in WINDOW_TYPES
        }
        val currentId = currentNode?.windowId ?: windowOverrideId ?: service.rootInActiveWindow?.windowId
        val targetId = com.example.vnscreenreader.core.WindowCycle.next(windows.map { it.id }, currentId, forward)
        com.example.vnscreenreader.core.Diag.log("WINDOW") { "fwd=$forward windows=${windows.size} current=$currentId target=$targetId" }
        if (targetId == null) {
            speak(t(S.MSG_WINDOW_NONE), true)
            return
        }
        val window = windows.first { it.id == targetId }
        windowOverrideId = targetId
        reset()
        val title = window.title?.toString()?.takeIf { it.isNotBlank() } ?: t(S.MSG_WINDOW_UNNAMED)
        val nodes = visibleNodes().orEmpty()
        val first = nodes.firstOrNull()
        if (first == null) speak(t(S.MSG_WINDOW_SET, title), true) else focusOn(first, t(S.MSG_WINDOW_SET, title))
    }

    /** Chuyển trang NGANG (ViewPager, tab vuốt ngang). */
    fun scrollPage(forward: Boolean) {
        val root = service.rootInActiveWindow
        if (root == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        val from = currentQuiet()
        var targets = NodeCollector.pageTargets(from, root)
        if (targets.isEmpty()) targets = NodeCollector.pageTargetsByHint(from, root)
        if (targets.isEmpty()) {
            // Không khung nào nhận hành động chuyển trang (ví dụ màn hình chính của launcher): giả lập cú vuốt ngang.
            if (!swipePage(root, forward)) speak(t(S.MSG_NO_PAGE_AREA), true)
            return
        }
        val actions = NodeCollector.pageActions(forward)
        val done = targets.any { target -> actions.any { target.performAction(it) } }
        if (done) {
            invalidateCache()
            reset()
            speak(if (forward) t(S.MSG_PAGE_NEXT) else t(S.MSG_PAGE_PREV), true)
        } else {
            speak(t(S.MSG_NO_MORE_PAGES), true)
        }
    }

    /**
     * Giả lập cú vuốt ngang bằng dispatchGesture (cần canPerformGestures). Không xác nhận được trang có đổi thật hay
     * không, nên câu báo chỉ nói "đã vuốt". @return false nếu không thể phát cử chỉ.
     */
    private fun swipePage(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val bounds = Rect().also { root.getBoundsInScreen(it) }
        val rtl = service.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        val line = SwipeGeometry.horizontal(bounds.left, bounds.top, bounds.right, bounds.bottom, forward, rtl) ?: return false
        invalidateCache()
        val before = screenSignature()
        val ok = swipeBlocking(line)
        com.example.vnscreenreader.core.Diag.log("PAGE") { "swipe dispatched ok=$ok" }
        if (!ok) return false
        SystemClock.sleep(AUTO_SCROLL_SETTLE_MS)
        invalidateCache()
        val changed = com.example.vnscreenreader.core.ScreenSignature.changed(before, screenSignature())
        com.example.vnscreenreader.core.Diag.log("PAGE") { "swipe changedScreen=$changed" }
        reset() // trang mới có phần tử khác
        // Chỉ báo "đã vuốt" khi màn hình thật sự đổi; nếu không (cuối trang, hoặc hệ thống không nhận cú vuốt giả lập) thì nói thật.
        speak(if (changed) (if (forward) t(S.MSG_SWIPED_NEXT) else t(S.MSG_SWIPED_PREV)) else t(S.MSG_NO_MORE_PAGES), true)
        return true
    }

    private fun screenSignature(): Int =
        com.example.vnscreenreader.core.ScreenSignature.of(visibleNodes()?.map { labelKey(it) }.orEmpty())

    /** Vùng (toạ độ màn hình) của mọi ô mật khẩu đang hiển thị: che đi trước khi chụp để OCR không đọc mật khẩu đang hiện. */
    fun passwordBounds(): List<Rect> {
        val root = activeRoot() ?: return emptyList()
        return NodeCollector.traverse(root)
            .filter { it.isPassword }
            .map { Rect().also { r -> it.getBoundsInScreen(r) } }
    }

    private fun activeRoot(): AccessibilityNodeInfo? {
        val chosen = windowOverrideId
        if (chosen != null) {
            service.windows.firstOrNull { it.id == chosen }?.root?.let { return it }
            windowOverrideId = null // cửa sổ đã đóng
        }
        // Nạp trước cả cây con trong MỘT lần gọi IPC thay vì mỗi nút một lần: giảm mạnh độ trễ trên cây lớn.
        return service.getRootInActiveWindow(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
    }

    private fun visibleNodes(): List<AccessibilityNodeInfo>? {
        val now = SystemClock.uptimeMillis()
        // Bộ đệm chỉ dùng được khi chưa hết hạn VÀ chưa có sự kiện "nội dung đổi/đã cuộn" nào xảy ra sau lần nạp.
        if (cachedNodes.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS && cachedAtMs > staleSinceMs) return cachedNodes
        val root = activeRoot() ?: return null
        cachedNodes = orderByTraversal(NodeCollector.collect(root, detailed = detailedFocus))
        cachedAtMs = now
        return cachedNodes
    }

    /**
     * Thứ tự duyệt ứng dụng khai báo (accessibilityTraversalAfter/Before). `getTraversalAfter/Before` chỉ gọi hệ thống khi
     * ứng dụng có đặt, nên với phần lớn nút việc kiểm tra gần như miễn phí.
     */
    private fun orderByTraversal(nodes: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val after = IntArray(nodes.size) { -1 }
        val before = IntArray(nodes.size) { -1 }
        var any = false
        for ((i, node) in nodes.withIndex()) {
            node.traversalAfter?.let { target ->
                val j = nodes.indexOf(target)
                if (j >= 0) {
                    after[i] = j
                    any = true
                }
            }
            node.traversalBefore?.let { target ->
                val j = nodes.indexOf(target)
                if (j >= 0) {
                    before[i] = j
                    any = true
                }
            }
        }
        if (!any) return nodes
        return TraversalOrder.apply(nodes.size, after, before).map { nodes[it] }
    }

    /**
     * Gọi từ luồng chính khi hệ thống báo nội dung cửa sổ đổi hoặc danh sách vừa cuộn: bỏ bộ đệm điểm dừng để lần vuốt kế
     * tiếp đọc cây MỚI (không đọc nút cũ đã bị vẽ lại). Chỉ ghi một cờ, không gọi hệ thống nên rẻ dù sự kiện dồn dập.
     */
    fun markContentChanged() {
        staleSinceMs = SystemClock.uptimeMillis()
    }

    /**
     * Tiêu điểm trợ năng do ứng dụng/trình duyệt tự đặt (không phải do [focusOn] vừa chọn): ghi nhận để vuốt tiếp đi từ đúng
     * chỗ đó, KHÔNG đọc lại (tránh đọc đúp với thông báo của chính ứng dụng).
     */
    fun onExternalFocus(node: AccessibilityNodeInfo) {
        // Tiếng vọng của chính lần chọn vừa rồi (và các lần vuốt nhanh liên tiếp) không được kéo con trỏ lùi lại.
        if (SystemClock.uptimeMillis() - lastOwnFocusMs < OWN_FOCUS_ECHO_MS) return
        if (node == currentNode) return
        currentNode = node
        rememberTrace(node)
        cursor.bind(nodeText(node))
        highlight(node)
    }

    private companion object {
        const val CACHE_TTL_MS = 800L
        const val MAX_PARENT_LOOKUP = 12
        const val AUTO_SCROLL_SETTLE_MS = 300L
        const val AUTO_SCROLL_RETRY_MS = 500L
        const val MAX_MODE_SCROLLS = 4
        const val MODE_SEARCH_BUDGET_MS = 4000L
        val WINDOW_TYPES = setOf(
            android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION,
            android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM,
            android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD,
        )
        const val MAX_READ_ALL = 300
        const val SWIPE_DURATION_MS = 250L
        const val GESTURE_WAIT_MS = 1000L
        const val NEARBY_PARENT_LEVELS = 2
        const val MAX_NEARBY_CHILDREN = 30
        const val MAX_QUICK_SCROLLS = 4
        const val WEB_FOCUS_SETTLE_MS = 120L
        const val OWN_FOCUS_ECHO_MS = 600L
        const val MAX_EDGE_STEPS = 20
        const val EDGE_STEP_MS = 150L
    }
}
