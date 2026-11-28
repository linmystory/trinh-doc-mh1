package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.AutoOcrPolicy
import com.example.vnscreenreader.core.CaretMath
import com.example.vnscreenreader.core.CaretMove
import com.example.vnscreenreader.core.CustomLabelStore
import com.example.vnscreenreader.core.ScrollContinuation
import com.example.vnscreenreader.core.ScrollEdge
import com.example.vnscreenreader.core.ScrollExhaustion
import com.example.vnscreenreader.core.ScrollItem
import com.example.vnscreenreader.core.ScrollSettle
import com.example.vnscreenreader.core.ActionHintKind
import com.example.vnscreenreader.core.ClickTargetPolicy
import com.example.vnscreenreader.core.ActionHints
import com.example.vnscreenreader.core.FocusIndex
import com.example.vnscreenreader.core.FocusTrace
import com.example.vnscreenreader.core.FocusTraceBuffer
import com.example.vnscreenreader.core.NavigationWindow
import com.example.vnscreenreader.core.QuickNav
import com.example.vnscreenreader.core.ReadingControl
import com.example.vnscreenreader.core.ReadingMode
import com.example.vnscreenreader.core.ReadingModeSearch
import com.example.vnscreenreader.core.TraversalOrder
import com.example.vnscreenreader.core.SpeechDetail
import com.example.vnscreenreader.core.Verbosity
import com.example.vnscreenreader.gesture.AppGestureStore
import com.example.vnscreenreader.node.isKeyboardKey
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
import com.example.vnscreenreader.core.SliderMath
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
    /** Cử chỉ cuộn đổi giá trị thanh trượt đang chọn thay vì cuộn trang. Do phiên đọc đặt từ cài đặt. */
    @Volatile var scrollAdjustsSliders: Boolean = true

    /**
     * Tiêu điểm chi tiết đang bật: hệ thống đưa cả view bị ứng dụng đánh dấu "không quan trọng" vào cây trợ năng (cờ dịch vụ do phiên đọc
     * đặt). Đổi giá trị thì bỏ bộ đệm điểm dừng. Việc chọn điểm dừng không còn phụ thuộc công tắc này: nút bấm được không nhãn nằm trong
     * khung bấm được luôn là điểm dừng riêng ([StopSelection]).
     */
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

    /** Vùng cuộn vừa cuộn mà không lộ ra phần tử mới: vuốt kế đi thẳng sang phần tử kế thay vì chờ cuộn lại vô ích ([reset] xóa). */
    private val exhaustion = ScrollExhaustion(EXHAUSTED_TTL_MS)
    private val clicker = ClickResolver(service)
    private val appStore = AppGestureStore(service)
    // @Volatile: invalidateCache() cũng được gọi từ luồng chính (callback chạm giữ, đổi cài đặt) trong khi luồng nền đọc.
    @Volatile private var cachedNodes: List<AccessibilityNodeInfo> = emptyList()
    @Volatile private var cachedAtMs = 0L

    /**
     * Cửa sổ của các nút trong [cachedNodes]. Danh sách lưu tạm gắn với cửa sổ: chọn phần tử ở cửa sổ khác (chạm vào bàn phím) thì không dùng
     * lại danh sách của cửa sổ ứng dụng trong 800 ms còn hạn. Chỉ truy cập trên luồng xử lý.
     */
    private var cachedWindowId: Int? = null

    /** Cửa sổ khác cửa sổ cơ sở mà [navigationRoot] đang theo (chỉ để nhật ký ghi MỘT dòng mỗi lần bắt đầu theo cửa sổ mới). */
    private var followedWindowId: Int? = null

    /** Thời điểm (uptimeMillis) chính trình đọc vừa đặt vùng chọn/con trỏ; để không đọc lặp sự kiện do chính nó gây ra. */
    @Volatile var lastOwnSelectionMs = 0L
        private set

    /** Thời điểm chính trình đọc vừa nhấn giữ; để không đọc lặp "đã nhấn giữ" khi sự kiện của hệ thống tới. */
    @Volatile var lastOwnLongClickMs = 0L
        private set
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
        exhaustion.clear() // nội dung đã đổi (cuộn tay, trang mới): "đã hết" trước đó không còn đúng
    }

    /**
     * Gọi khi danh sách đã ngừng cuộn một lúc. Hai ngón vuốt là thao tác cuộn gốc của Android (dịch vụ không tự chạy [scroll]),
     * nên nếu phần tử đang chọn đã cuộn khuất khỏi màn hình (hoặc không còn hợp lệ) thì bỏ nó để lần vuốt kế bắt đầu từ phần
     * tử đầu tiên đang hiện, y như sau [scroll]. Phần tử vẫn hiện thì giữ nguyên: ứng dụng tự cuộn để đưa phần tử vừa chọn vào
     * màn hình cũng sinh sự kiện cuộn, và không được làm mất tiêu điểm ở trường hợp đó. Chạy trên luồng xử lý.
     */
    fun releaseIfScrolledAway() {
        val cur = currentNode ?: return
        val stillVisible = try {
            cur.refresh() && cur.isVisibleToUser
        } catch (e: RuntimeException) {
            false
        }
        if (!stillVisible) reset()
    }

    /**
     * Gọi khi cửa sổ thật sự đổi (TYPE_WINDOW_STATE_CHANGED): bỏ cửa sổ đã chọn bằng "cửa sổ kế/trước" và đưa chế độ đọc cùng
     * điều khiển đọc về mặc định (từng phần tử). KHÔNG làm việc này trong [reset] vì reset còn được gọi sau khi cuộn hay chạy plugin.
     */
    fun onWindowChanged() {
        windowOverrideId = null
        readingMode = ReadingMode.ELEMENT
        readingControl = ReadingControl.ELEMENT
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

    /** [interrupt] = false: xếp hàng sau lời đang đọc (dùng khi tự chuyển tiêu điểm vào hộp thoại/cửa sổ vừa đọc tiêu đề). */
    fun focusOn(node: AccessibilityNodeInfo, prefix: String? = null, interrupt: Boolean = true) {
        selectQuietly(node)
        feedback.onFocusChanged()
        val spoken = describeFocus(node)
        speak(if (prefix.isNullOrBlank()) spoken else "$prefix. $spoken", interrupt)
        onFocused(node)
    }

    /** Chọn phần tử (bỏ qua việc đọc): dùng khi còn đang tìm, chỉ đọc khi đã đến phần tử cuối cùng. */
    private fun selectQuietly(node: AccessibilityNodeInfo) {
        lastOwnFocusMs = SystemClock.uptimeMillis()
        // Sang phần tử khác thì luôn bắt đầu lại vị trí đơn vị chữ, kể cả khi hai phần tử có chữ giống hệt nhau (danh sách nút "Xóa").
        if (node != currentNode) cursor.reset()
        currentNode = node
        rememberTrace(node)
        cursor.bind(nodeText(node))
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        highlight(node)
    }

    /** Vết của phần tử chọn gần nhất (để nhớ vị trí theo cửa sổ); null nếu chưa chọn gì. */
    fun latestTrace(): FocusTrace? = traces.latest()

    /** Chọn phần tử đầu tiên của cửa sổ đang hoạt động (hộp thoại vừa mở). false nếu cửa sổ chưa có phần tử nào. */
    fun focusFirst(interrupt: Boolean): Boolean {
        val first = visibleNodes()?.firstOrNull() ?: return false
        focusOn(first, interrupt = interrupt)
        return true
    }

    /** Tìm lại phần tử theo [trace] đã nhớ (khớp mạnh: mã, nhãn) và chọn nó. false nếu không còn thấy. */
    fun restoreFromTrace(trace: FocusTrace, interrupt: Boolean): Boolean {
        val nodes = visibleNodes() ?: return false
        val index = locateByTrace(nodes, listOf(trace))
        if (index < 0) return false
        focusOn(nodes[index], interrupt = interrupt)
        return true
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
        moveElement(forward)
    }

    /**
     * Sang phần tử kế/trước theo thứ tự đọc, KHÔNG xét [readingMode]: dùng cho điều khiển đọc "Phần tử" của vuốt một ngón
     * xuống/lên và cho việc sang phần tử bên cạnh khi điều khiển theo đơn vị chữ đã hết chữ ([moveToNeighbour]).
     */
    fun moveElement(forward: Boolean) {
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
        // Sắp rời khỏi một danh sách/trang còn cuộn được (phần tử kế nằm NGOÀI vùng: thanh dưới, nút nổi, tiêu đề cố định): cuộn tiếp vùng
        // đó trước rồi mới sang phần tử ngoài vùng. Cuộn không lộ ra gì (hết thật) thì đi tiếp như thường.
        // `current` có thể là "khe" ngoài danh sách (vị trí suy ra từ vết sau khi giao diện đổi): khi đó không có phần tử đang chọn thật.
        if (current in nodes.indices) {
            val container = containerToScrollFirst(nodes, current, forward)
            if (container != null && autoScrollAndSelect(nodes, forward, within = container)) return
        }
        focusOn(nodes[target])
    }

    /**
     * Kích hoạt phần tử đang chọn. Thứ tự: ACTION_CLICK lên nút bấm được gần nhất (kể cả nút chỉ khai báo hành động
     * nhấn, như thẻ liên kết trong WebView) → nhấn con bấm được → nhấn điều khiển cạnh dòng chữ (KHÔNG với phím và ở màn hình khóa:
     * [ClickTargetPolicy.mayClickNearby]) → giả lập chạm giữa phần tử (trong nội dung web và ở màn hình khóa:
     * [ClickTargetPolicy.shouldTapFallback]). Câu báo lỗi do [ActionDispatcher] đọc theo kết quả trả về. Mỗi lần kích hoạt ghi một dòng nhật ký chẩn đoán
     * (chỉ siêu dữ liệu: kết quả, gói, lớp, cờ), để biết vì sao một phím không nhấn được.
     */
    internal fun activate(): ActivateOutcome {
        val selected = currentNode ?: return loggedActivation(ActivateOutcome.NOTHING_SELECTED, null, "none")
        val node = if (selected.refresh()) selected else relocateCurrent() ?: run {
            release()
            return loggedActivation(ActivateOutcome.GONE, null, "gone")
        }
        // Ứng dụng người dùng đã chọn "chạm thẳng": gửi cú chạm thật vào giữa phần tử trước (ACTION_CLICK không có tác dụng ở
        // một số ứng dụng tự vẽ giao diện); chạm không phát được thì rơi về cách thường bên dưới.
        if (appStore.isRawTap(node.packageName?.toString()) && clicker.tapCenter(node)) {
            feedback.onActivated()
            invalidateCache()
            return loggedActivation(ActivateOutcome.TAPPED, node, "rawTap")
        }
        val candidates = clicker.candidates(node)
        if (candidates.any { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }) {
            feedback.onActivated()
            invalidateCache()
            return loggedActivation(ActivateOutcome.CLICKED, node, "click candidates=${candidates.size}")
        }
        val keyguard = clicker.isKeyguardShowing()
        val key = node.isKeyboardKey()
        // Dòng chữ không bấm được (hoặc bấm không có tác dụng): thường là nhãn của một ô tích/nút nằm ngay cạnh.
        // Không áp dụng cho phím và ở màn hình khóa: bên cạnh một phím là phím khác, nhấn nhầm là gõ sai.
        if (ClickTargetPolicy.mayClickNearby(key, keyguard) && activateNearby(node)) {
            return loggedActivation(ActivateOutcome.CLICKED_NEARBY, node, "nearby")
        }
        val insideWeb = clicker.isInsideWeb(node)
        if (ClickTargetPolicy.shouldTapFallback(insideWeb, clicker.isWebRoot(node), keyguard) && clicker.tapCenter(node)) {
            feedback.onActivated()
            invalidateCache()
            return loggedActivation(ActivateOutcome.TAPPED, node, "tapFallback web=$insideWeb keyguard=$keyguard key=$key")
        }
        val outcome = if (candidates.isEmpty()) ActivateOutcome.CANNOT_CLICK else ActivateOutcome.CLICK_FAILED
        return loggedActivation(outcome, node, "failed candidates=${candidates.size} web=$insideWeb keyguard=$keyguard key=$key")
    }

    /** Ghi một dòng chẩn đoán cho một lần kích hoạt rồi trả lại [outcome]. Không ghi chữ trên màn hình: chỉ gói, lớp và cờ của nút. */
    private fun loggedActivation(outcome: ActivateOutcome, node: AccessibilityNodeInfo?, detail: String): ActivateOutcome {
        com.example.vnscreenreader.core.Diag.log("ACTIVATE") {
            "$outcome $detail pkg=${node?.packageName} cls=${node?.className} clickable=${node?.isClickable} enabled=${node?.isEnabled}"
        }
        return outcome
    }

    /** Màn hình khóa đang hiện? Cho nhật ký chẩn đoán của phiên đọc. */
    fun isKeyguardShowing(): Boolean = clicker.isKeyguardShowing()

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
        val root = navigationRoot() ?: return false
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
        val root = navigationRoot() ?: return false
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

    /**
     * Nút có khoảng giá trị VÀ chỉnh được ([SliderMath.isAdjustable]). Thanh tiến trình chỉ-đọc cũng có khoảng giá trị nhưng
     * không có hành động nào: không phải thanh trượt, để vuốt xuống/lên đi tiếp theo điều khiển đọc thay vì đứng yên ở đó.
     */
    private fun isAdjustableRange(node: AccessibilityNodeInfo): Boolean =
        node.rangeInfo != null && SliderMath.isAdjustable(
            enabled = node.isEnabled,
            actionIds = node.actionList.map { it.id },
            setProgressId = AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            scrollForwardId = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
            scrollBackwardId = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
        )

    /** Thanh trượt của phần tử đang chọn: chính nó hoặc cha trực tiếp (nút bao quanh) có khoảng giá trị và chỉnh được. */
    private fun sliderOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        node.takeIf { isAdjustableRange(it) } ?: node.parent?.takeIf { isAdjustableRange(it) }

    /**
     * Tăng/giảm giá trị thanh trượt đang chọn một bước (≈5%). Trả về false nếu phần tử đang chọn không phải thanh trượt
     * (người gọi quyết định báo lỗi hay làm việc khác). Dùng ACTION_SET_PROGRESS nếu có, không thì cuộn tới/lui của chính nó.
     */
    fun adjustRange(direction: Int): Boolean {
        val cur = currentFresh() ?: return false
        val slider = sliderOf(cur) ?: return false
        val info = slider.rangeInfo ?: return false
        val isInt = info.type == AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT
        val next = SliderMath.next(info.current, info.min, info.max, direction, isInt)
        val setProgress = AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS
        if (slider.actionList.any { it.id == setProgress.id } && next != info.current) {
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, next) }
            slider.performAction(setProgress.id, args)
        } else {
            slider.performAction(
                if (direction >= 0) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            )
        }
        invalidateCache()
        SystemClock.sleep(SLIDER_SETTLE_MS) // ứng dụng cần một chút để cập nhật giá trị mới
        slider.refresh()
        speak(describeFocus(slider), true)
        return true
    }

    /**
     * Kéo thanh trượt đang chọn tới cuối (bên phải) theo lệnh của người dùng. Thanh chuẩn có ACTION_SET_PROGRESS thì đặt thẳng giá
     * trị lớn nhất; còn núm kéo (ví dụ "kéo sang phải để xác nhận") thì thực hiện một cú kéo từ tâm núm tới mép phải đường trượt.
     * Chỉ kéo thẳng tới cuối, không nhìn ảnh.
     */
    fun dragToEnd() {
        val cur = currentFresh()
        if (cur == null) {
            speak(t(S.MSG_SLIDER_NONE), true)
            return
        }
        val slider = sliderOf(cur)
        val info = slider?.rangeInfo
        val setProgress = AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS
        if (slider != null && info != null && slider.actionList.any { it.id == setProgress.id }) {
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, info.max) }
            if (slider.performAction(setProgress.id, args)) {
                invalidateCache()
                SystemClock.sleep(SLIDER_SETTLE_MS)
                slider.refresh()
                speak(describeFocus(slider), true)
                return
            }
        }
        val handle = Rect().also { cur.getBoundsInScreen(it) }
        val track = trackBoundsOf(cur, handle)
        if (handle.isEmpty || track == null) {
            speak(t(S.MSG_SLIDER_FAILED), true)
            return
        }
        val endX = SliderMath.dragEndX(handle.centerX(), handle.width(), track.right)
        if (endX == null) {
            speak(t(S.MSG_SLIDER_DONE), true) // đã ở cuối
            return
        }
        val path = android.graphics.Path().apply {
            moveTo(handle.exactCenterX(), handle.exactCenterY())
            lineTo(endX.toFloat(), handle.exactCenterY())
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, DRAG_DURATION_MS))
            .build()
        val done = t(S.MSG_SLIDER_DONE)
        val failed = t(S.MSG_SLIDER_FAILED)
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: android.accessibilityservice.GestureDescription?) {
                    invalidateCache()
                    speak(done, true)
                }

                override fun onCancelled(g: android.accessibilityservice.GestureDescription?) {
                    speak(failed, true)
                }
            },
            null,
        )
        if (!accepted) speak(failed, true)
    }

    /** Đường trượt của núm [handle]: tổ tiên gần nhất (tối đa 4 cấp) đủ rộng (≥ 3 lần núm) và chứa tâm núm. */
    private fun trackBoundsOf(node: AccessibilityNodeInfo, handle: Rect): Rect? {
        var parent = node.parent
        repeat(TRACK_LEVELS) {
            val p = parent ?: return null
            val r = Rect().also { p.getBoundsInScreen(it) }
            if (!r.isEmpty && r.width() >= handle.width() * 3 && r.contains(handle.centerX(), handle.centerY())) return r
            parent = p.parent
        }
        return null
    }

    fun scroll(forward: Boolean) {
        if (scrollAdjustsSliders && adjustRange(if (forward) 1 else -1)) return
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
        lastOwnLongClickMs = SystemClock.uptimeMillis()
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

    /**
     * Đổi đơn vị đọc (đoạn → câu → từ → ký tự) và trả về đơn vị mới. Điều khiển đọc đi theo đơn vị mới, nên vuốt một ngón xuống/lên
     * lập tức di chuyển theo đơn vị đó.
     */
    fun cycleGranularity(): TextGranularity {
        val unit = cursor.cycle()
        readingControl = ReadingControl.forUnit(unit)
        return unit
    }

    /**
     * Điều khiển đọc của vuốt một ngón xuống/lên; [ReadingControl.ELEMENT] = từng phần tử. Do người dùng đổi bằng cử chỉ
     * ([cycleReadingControl]) hoặc bằng "đổi đơn vị đọc" ([cycleGranularity]); về mặc định khi cửa sổ đổi ([onWindowChanged]).
     */
    @Volatile var readingControl: ReadingControl = ReadingControl.ELEMENT
        private set

    /**
     * Chuyển sang điều khiển đọc kế/trước theo vòng tròn và trả về điều khiển mới. Điều khiển theo đơn vị chữ cũng đặt lại đơn vị
     * của con trỏ chữ, nên "sao chép đơn vị" và ba ngón vuốt xuống/lên dùng đúng đơn vị đang chọn. Người gọi đọc tên điều khiển.
     */
    fun cycleReadingControl(forward: Boolean): ReadingControl {
        val control = if (forward) readingControl.next() else readingControl.previous()
        readingControl = control
        control.unit?.let { cursor.select(it) }
        com.example.vnscreenreader.core.Diag.log("CONTROL") { "control=$control" }
        return control
    }

    /** Di chuyển tới đơn vị kế/trước của phần tử đang chọn; null nếu chưa chọn phần tử hoặc không có chữ. */
    fun moveUnit(forward: Boolean): UnitMove? {
        if (currentNode == null) return null
        val node = currentFresh() ?: return UnitMove.Changed // đã đọc "phần tử đã thay đổi" và reset
        cursor.bind(nodeText(node))
        val move = cursor.move(forward)
        if (move is UnitMove.End && cursor.isEmpty) return null
        return move
    }

    /**
     * Sang phần tử kế/trước khi điều khiển đọc theo đơn vị chữ đã hết chữ (đọc cả phần tử mới như vuốt phải/trái). Lùi sang
     * phần tử trước thì đặt con trỏ chữ ở CUỐI phần tử đó: vuốt lên tiếp đọc đơn vị cuối cùng thay vì nhảy qua cả phần tử.
     */
    fun moveToNeighbour(forward: Boolean) {
        val before = currentNode
        moveElement(forward)
        if (!forward && currentNode != before) cursor.parkAtEnd()
    }

    /**
     * Nhảy tới phần tử cùng loại [mode] kế/trước mà KHÔNG đổi [readingMode] (điều khiển đọc "Điều khiển" của vuốt một ngón
     * xuống/lên dùng đường này). Cách tìm, cuộn và đọc giống hệt chế độ đọc cũ.
     */
    fun moveToMode(mode: ReadingMode, forward: Boolean) = moveByMode(forward, mode)

    /**
     * Dời con trỏ THẬT trong ô nhập đang chọn theo [unit] về phía [forward] bằng ACTION_SET_SELECTION (vuốt một ngón xuống/lên khi
     * đang ở ô nhập, như chuẩn của Bộ trợ năng Android). Vị trí gốc là đầu cuối của vùng chọn hiện có; chưa biết vị trí con trỏ thì
     * bắt đầu từ đầu chữ (tiến) hoặc cuối chữ (lùi). Kết quả:
     *  - [CaretMove.Moved]: đã dời, kèm đoạn chữ vừa đi qua để đọc;
     *  - [CaretMove.Edge]: hết chữ theo hướng này hoặc ô trống/đang hiện gợi ý, người gọi sang phần tử bên cạnh để khỏi kẹt trong ô;
     *  - [CaretMove.Unavailable]: không phải ô nhập, là ô mật khẩu ([EditPolicy]: không bao giờ lộ chữ), chưa chọn gì hoặc hệ thống
     *    từ chối, người gọi đi theo điều khiển như thường.
     * Việc tự đặt vùng chọn được ghi nhận ([lastOwnSelectionMs]) để sự kiện "vùng chọn đổi" dội lại không bị đọc thêm lần nữa.
     */
    fun moveCaret(unit: TextGranularity, forward: Boolean): CaretMove {
        // Dùng chung đường tìm ô nhập với "xóa tất cả": chưa chọn gì, phần tử đã đổi, không phải ô nhập hay ô mật khẩu đều là
        // "không dời được" (người gọi đi theo điều khiển như thường).
        val (target, blocked) = editTargetOrResult()
        if (target == null || blocked != null) return CaretMove.Unavailable
        val text = realText(target)
        val reported = target.textSelectionEnd
        val caret = if (reported >= 0) reported else if (forward) 0 else text.length
        val destination = CaretMath.target(text, caret, unit, forward) ?: return CaretMove.Edge
        // Ô đang giữ tiêu điểm nhập thì không gửi lại ACTION_FOCUS: một số ứng dụng chọn lại toàn bộ chữ mỗi lần được yêu cầu.
        if (!target.isFocused) target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!setSelection(target, destination, destination)) return CaretMove.Unavailable
        return CaretMove.Moved(CaretMath.traversed(text, caret, destination))
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
        // Ô đang giữ tiêu điểm nhập thì không gửi lại ACTION_FOCUS (như [moveCaret]): một số ứng dụng chọn lại toàn bộ chữ mỗi lần
        // được yêu cầu, khiến "dán" ngay sau đó ghi đè cả ô.
        if (!target.isFocused) target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val ok = when (op) {
            EditOp.PASTE -> target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            EditOp.CUT -> target.performAction(AccessibilityNodeInfo.ACTION_CUT)
            else -> {
                // Độ dài chữ THẬT: ô trống đang hiện gợi ý có text = gợi ý, dùng độ dài đó sẽ đặt vùng chọn quá cuối chữ và bị từ chối.
                val length = realText(target).length
                val start = target.textSelectionStart
                val end = target.textSelectionEnd
                val range = SelectionMath.target(op, length, start, end)
                when {
                    range == null -> false
                    // Đã đúng vị trí (vd. "về đầu ô" khi con trỏ đang ở đầu): báo xong, không gửi lại vì TextView trả false khi
                    // vùng chọn không đổi.
                    SelectionMath.isNoOp(op, length, start, end) -> true
                    else -> setSelection(target, range.first, range.second)
                }
            }
        }
        if (ok) invalidateCache()
        return if (ok) EditResult.DONE else EditResult.FAILED
    }

    enum class ClearResult { DONE, EMPTY, NO_SELECTION, NOT_EDITABLE, PASSWORD, FAILED, RESTORED, NOTHING_TO_RESTORE, NOT_EMPTY }

    /** Chữ vừa bị "xóa tất cả" để còn khôi phục; chỉ nằm trong bộ nhớ, không bao giờ lấy từ ô mật khẩu. */
    @Volatile private var clearedText: String? = null

    private fun editTargetOrResult(): Pair<AccessibilityNodeInfo?, ClearResult?> {
        if (currentNode == null) return null to ClearResult.NO_SELECTION
        val node = currentFresh() ?: return null to ClearResult.FAILED
        val target = NodeCollector.findAncestorOrSelf(node) { it.isEditable } ?: return null to ClearResult.NOT_EDITABLE
        if (target.isPassword) return null to ClearResult.PASSWORD
        return target to null
    }

    /** Chữ thật của ô (rỗng nếu chỉ đang hiện gợi ý "hint"). */
    private fun realText(target: AccessibilityNodeInfo): String =
        if (target.isShowingHintText) "" else target.text?.toString().orEmpty()

    private fun setFieldText(target: AccessibilityNodeInfo, text: String): Boolean {
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) invalidateCache()
        return ok
    }

    /**
     * Xóa TẤT CẢ chữ trong ô nhập đang chọn (ACTION_SET_TEXT rỗng, không đụng clipboard) và nhớ chữ cũ trong bộ nhớ để
     * [restoreCleared]. Ô mật khẩu bị chặn theo [EditPolicy]: không xóa, không nhớ.
     */
    fun clearField(): ClearResult {
        val (target, error) = editTargetOrResult()
        if (target == null) return error ?: ClearResult.FAILED
        val before = realText(target)
        if (before.isEmpty()) return ClearResult.EMPTY
        if (!setFieldText(target, "")) return ClearResult.FAILED
        clearedText = before.take(MAX_RESTORE_CHARS)
        return ClearResult.DONE
    }

    /** Đặt lại chữ vừa xóa, CHỈ khi ô đang trống (không ghi đè chữ mới gõ). */
    fun restoreCleared(): ClearResult {
        val saved = clearedText ?: return ClearResult.NOTHING_TO_RESTORE
        val (target, error) = editTargetOrResult()
        if (target == null) return error ?: ClearResult.FAILED
        if (realText(target).isNotEmpty()) return ClearResult.NOT_EMPTY
        if (!setFieldText(target, saved)) return ClearResult.FAILED
        clearedText = null
        return ClearResult.RESTORED
    }

    private fun setSelection(node: AccessibilityNodeInfo, start: Int, end: Int): Boolean {
        lastOwnSelectionMs = SystemClock.uptimeMillis()
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
    private fun locateByTrace(nodes: List<AccessibilityNodeInfo>, history: List<FocusTrace> = traces.newestFirst()): Int {
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
     * Ảnh một vùng cuộn tại một thời điểm: các phần tử có tâm nằm trong hộp vùng, theo thứ tự đọc. [items] là số liệu thô để so trước/sau
     * khi cuộn, [nodes] (cùng chỉ số) là nút để chọn.
     */
    private class AreaView(val items: List<ScrollItem>, val nodes: List<AccessibilityNodeInfo>)

    /** Kết cục của việc cuộn một khung để lộ phần tử mới (xem [scrollAndPick]). */
    private enum class ScrollOutcome {
        /** Đã chọn được phần tử mới. */
        SELECTED,

        /** Khung không nhận hành động cuộn nào: thử khung khác. */
        REFUSED,

        /** Khung nhận hành động nhưng nội dung không đổi (hết thật, view tự vẽ, hoặc cập nhật quá chậm). */
        UNCHANGED,

        /** Nội dung có đổi nhưng không lộ ra phần tử mới nào (hết danh sách, hoặc mới chỉ thêm ảnh không nhãn). */
        NOTHING_NEW,
    }

    /**
     * Vùng cuộn DỌC mà lần vuốt này sắp RỜI khỏi: phần tử đang chọn ([current]) nằm trong vùng, vùng còn cuộn được theo hướng vuốt, và
     * KHÔNG còn phần tử nào nằm trong vùng ở phía trước (tiến) / phía sau (lùi) phần tử đó, tức phần tử kế nằm ngoài danh sách (thanh
     * dưới, nút nổi, tiêu đề cố định...). Theo thông lệ của trình đọc màn hình: cuộn hết danh sách trước rồi mới sang phần tử ngoài danh sách. null nếu không phải
     * trường hợp đó, hoặc vùng vừa được cuộn mà không lộ ra gì ([EXHAUSTED_TTL_MS]).
     */
    private fun containerToScrollFirst(nodes: List<AccessibilityNodeInfo>, current: Int, forward: Boolean): AccessibilityNodeInfo? {
        val container = NodeCollector.verticalScrollAncestor(nodes[current], forward) ?: return null
        val box = boxOf(container) ?: return null
        if (exhaustion.isExhausted(scrollKey(container, forward), SystemClock.uptimeMillis())) return null
        val rest = if (forward) nodes.subList(current + 1, nodes.size) else nodes.subList(0, current)
        val r = Rect()
        val center = IntArray(4)
        for (n in rest) {
            n.getBoundsInScreen(r)
            center[0] = r.left
            center[1] = r.top
            center[2] = r.right
            center[3] = r.bottom
            if (ScrollEdge.containsCenter(box, center)) return null // vùng còn phần tử phía trước: cứ đi tiếp như thường
        }
        return container
    }

    /**
     * Hết phần tử ở rìa vùng nhìn thấy: cuộn rồi chọn phần tử MỚI đầu tiên (tiến) hoặc cuối cùng (lùi). [within] = vùng cuộn cần cuộn
     * (lần vuốt sắp rời khỏi vùng đó); null = thử các khung nội dung theo thứ tự ưu tiên. [rounds] = số lần cuộn liên tiếp tối đa khi
     * lần cuộn trước chưa lộ ra phần tử mới (vd mới chỉ thêm ảnh không nhãn). Trả về true nếu đã chọn được; false nếu không có vùng cuộn,
     * cuộn không được hoặc không có phần tử mới. Chạy trên luồng xử lý nền duy nhất và CÓ CHỜ (xem [awaitScrolled]); tổng thời gian bị
     * chặn bởi [AUTO_SCROLL_TOTAL_MS]. Chỉ ghi nhật ký con số, không ghi nội dung chữ.
     */
    private fun autoScrollAndSelect(
        oldNodes: List<AccessibilityNodeInfo>,
        forward: Boolean,
        quiet: Boolean = false,
        within: AccessibilityNodeInfo? = null,
        rounds: Int = MAX_SCROLL_ROUNDS,
    ): Boolean {
        val root = navigationRoot() ?: return false
        val start = nodesBeforeScroll(oldNodes) ?: return false
        val targets = if (within != null) listOf(within) else NodeCollector.contentScrollTargets(currentQuiet(), root, forward)
        com.example.vnscreenreader.core.Diag.log("AUTOSCROLL") { "start fwd=$forward within=${within != null} targets=${targets.size} rounds=$rounds" }
        val deadline = SystemClock.uptimeMillis() + AUTO_SCROLL_TOTAL_MS
        var swipeBox: IntArray? = null
        var tried = 0
        for (target in targets) {
            if (tried >= MAX_SCROLL_CANDIDATES || SystemClock.uptimeMillis() > deadline) break
            val box = boxOf(target) ?: continue
            val key = scrollKey(target, forward)
            if (exhaustion.isExhausted(key, SystemClock.uptimeMillis())) continue
            tried++
            val outcome = scrollAndPick(target, box, start, forward, quiet, rounds, deadline)
            com.example.vnscreenreader.core.Diag.log("AUTOSCROLL") { "outcome=$outcome tried=$tried" }
            when (outcome) {
                ScrollOutcome.SELECTED -> return true
                ScrollOutcome.REFUSED -> Unit // khung không nhận hành động cuộn: thử khung kế
                ScrollOutcome.UNCHANGED -> {
                    // Nhận hành động mà không đổi gì: nhớ để lần vuốt kế khỏi chờ lại. Chỉ khi tìm ở cấp cả cửa sổ mới thử thêm cú vuốt
                    // dự phòng bên dưới; khi sắp rời một vùng, "không đổi" thường chỉ là đã hết, không giả lập vuốt (có thể kéo-làm-mới).
                    exhaustion.mark(key, SystemClock.uptimeMillis())
                    if (within == null && swipeBox == null) swipeBox = box
                }
                ScrollOutcome.NOTHING_NEW -> {
                    // Đã cuộn đủ số lần cho phép mà không lộ ra gì. Tìm theo chế độ chỉ cuộn một lần nên chưa đủ căn cứ để nhớ là đã hết.
                    if (rounds > 1) exhaustion.mark(key, SystemClock.uptimeMillis())
                    return false
                }
            }
        }
        // Dự phòng: giả lập cú vuốt dọc cho khung nhận hành động mà không đổi gì, hoặc khung "cuộn được" mà không báo hành động nào (view
        // tự vẽ). Chỉ khi tìm ở cấp cả cửa sổ mới dò khung không có hành động; với vùng đã biết ([within]) chỉ thử đúng vùng đó.
        val stuck = swipeBox ?: if (within == null) stuckBox(root) else null
        if (stuck == null) return autoScrollFailed("NO_TARGET")
        val swipeKey = boxKey(stuck, forward)
        if (exhaustion.isExhausted(swipeKey, SystemClock.uptimeMillis())) return autoScrollFailed("EXHAUSTED")
        val line = SwipeGeometry.vertical(stuck[0], stuck[1], stuck[2], stuck[3], forward) ?: return autoScrollFailed("NO_SWIPE_LINE")
        val before = areaView(start, stuck)
        if (!swipeBlocking(line)) return autoScrollFailed("SWIPE_FAILED")
        val after = awaitScrolled(stuck, before.items)
        if (after == null) {
            exhaustion.mark(swipeKey, SystemClock.uptimeMillis())
            return autoScrollFailed("SWIPE_UNCHANGED")
        }
        return pickNew(before, after, stuck, forward, quiet, "swipe")
    }

    /** Ghi lý do tìm-bằng-cuộn không chọn được gì (để nhật ký luôn có dòng kết cục, kể cả khi không có khung nào để cuộn); trả false. */
    private fun autoScrollFailed(reason: String): Boolean {
        com.example.vnscreenreader.core.Diag.log("AUTOSCROLL") { "outcome=$reason" }
        return false
    }

    /**
     * Cuộn [target] bằng hành động của chính khung, tối đa [rounds] lần, đến khi lộ ra phần tử mới thì chọn nó. Sau mỗi lần cuộn CHỜ nội
     * dung đổi rồi dừng hẳn ([awaitScrolled]) thay vì chờ cố định, rồi đối chiếu vị trí + nhãn trước/sau ([ScrollContinuation]) để bỏ
     * qua phần đã thấy, thanh dính và mục trùng chữ. [start] = danh sách phần tử ngay trước lần cuộn đầu.
     */
    private fun scrollAndPick(
        target: AccessibilityNodeInfo,
        box: IntArray,
        start: List<AccessibilityNodeInfo>,
        forward: Boolean,
        quiet: Boolean,
        rounds: Int,
        deadline: Long,
    ): ScrollOutcome {
        var before = areaView(start, box)
        for (round in 0 until rounds) {
            if (round > 0 && (SystemClock.uptimeMillis() > deadline || !canScroll(target, forward))) return ScrollOutcome.NOTHING_NEW
            if (!NodeCollector.scrollActions(forward).any { target.performAction(it) }) {
                return if (round == 0) ScrollOutcome.REFUSED else ScrollOutcome.NOTHING_NEW
            }
            val after = awaitScrolled(box, before.items)
                ?: return if (round == 0) ScrollOutcome.UNCHANGED else ScrollOutcome.NOTHING_NEW
            if (pickNew(before, after, box, forward, quiet, "action")) return ScrollOutcome.SELECTED
            before = after
        }
        return ScrollOutcome.NOTHING_NEW
    }

    /** Đối chiếu ảnh trước/sau khi cuộn rồi chọn phần tử mới; false nếu không có phần tử mới nào. */
    private fun pickNew(before: AreaView, after: AreaView, box: IntArray, forward: Boolean, quiet: Boolean, via: String): Boolean {
        val match = ScrollContinuation.analyze(before.items, after.items, box, forward)
        com.example.vnscreenreader.core.Diag.log("AUTOSCROLL") {
            "via=$via fwd=$forward before=${before.items.size} after=${after.items.size} shift=${match.shift} old=${match.old} pinned=${match.pinned} pick=${match.index}"
        }
        val index = match.index ?: return false
        val node = after.nodes[index]
        if (quiet) selectQuietly(node) else focusOn(node)
        return true
    }

    /**
     * Chờ vùng [box] đổi nội dung rồi DỪNG hẳn sau một lần cuộn: thăm dò mỗi [AUTO_SCROLL_POLL_MS] đến tối đa [AUTO_SCROLL_MAX_WAIT_MS],
     * xong khi hai lần thăm dò liên tiếp cho cùng một danh sách khác trước khi cuộn ([ScrollSettle]). Hết hạn mà vẫn đang đổi thì dùng
     * ảnh mới nhất; không đổi gì cả (hoặc không đọc được cây giao diện) thì null.
     */
    private fun awaitScrolled(box: IntArray, before: List<ScrollItem>): AreaView? {
        val settle = ScrollSettle(before)
        val deadline = SystemClock.uptimeMillis() + AUTO_SCROLL_MAX_WAIT_MS
        var changedView: AreaView? = null
        while (true) {
            SystemClock.sleep(AUTO_SCROLL_POLL_MS)
            invalidateCache()
            val nodes = visibleNodes() ?: return changedView
            val view = areaView(nodes, box)
            val stable = settle.offer(view.items)
            changedView = if (settle.changed) view else null
            if (stable) return view
            if (SystemClock.uptimeMillis() >= deadline) return changedView
        }
    }

    /**
     * Nhãn để đối chiếu phần tử trước/sau khi cuộn: nhãn riêng ([labelKey]); khung bấm được không có chữ riêng (thẻ/hàng trong danh sách,
     * rất phổ biến) thì dùng chữ gộp của các con hiển thị ([NodeCollector.mergedLabel]). Nếu để rỗng, mọi hàng kiểu thẻ đều "không nhãn"
     * nên không thể nhận ra hàng nào đã thấy. Ô mật khẩu luôn rỗng. Chỉ so sánh trong bộ nhớ, không ghi ra nhật ký.
     */
    private fun scrollLabel(node: AccessibilityNodeInfo): String {
        val own = labelKey(node)
        if (own.isNotEmpty() || node.isPassword) return own
        return NodeCollector.mergedLabel(node)
    }

    /** Ảnh các phần tử có tâm nằm trong [box] (nhãn theo [scrollLabel]; bỏ chữ của ô mật khẩu). */
    private fun areaView(nodes: List<AccessibilityNodeInfo>, box: IntArray): AreaView {
        val items = ArrayList<ScrollItem>()
        val inside = ArrayList<AccessibilityNodeInfo>()
        val r = Rect()
        val center = IntArray(4)
        for (n in nodes) {
            n.getBoundsInScreen(r)
            center[0] = r.left
            center[1] = r.top
            center[2] = r.right
            center[3] = r.bottom
            if (!ScrollEdge.containsCenter(box, center)) continue
            items += ScrollItem(scrollLabel(n), r.left, r.top, r.right, r.bottom)
            inside += n
        }
        return AreaView(items, inside)
    }

    /**
     * Danh sách phần tử NGAY TRƯỚC khi cuộn: dùng lại [oldNodes] nếu nó còn là bộ đệm mới (chưa hết hạn, chưa có sự kiện đổi nội dung nào
     * sau lần nạp), ngược lại nạp lại để ảnh "trước" đúng với màn hình thật.
     */
    private fun nodesBeforeScroll(oldNodes: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo>? {
        val now = SystemClock.uptimeMillis()
        if (cachedNodes === oldNodes && cachedAtMs > staleSinceMs && now - cachedAtMs < CACHE_TTL_MS) return oldNodes
        invalidateCache()
        return visibleNodes()
    }

    /** Khung còn hành động cuộn theo hướng [forward] sau khi làm mới (khung đã hết chỗ cuộn thì không còn hành động đó). */
    private fun canScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        if (!node.refresh()) return false
        val wanted = NodeCollector.scrollActions(forward)
        return node.actionList.any { it.id in wanted }
    }

    /** Hộp (trái, trên, phải, dưới) của [node] theo toạ độ màn hình; null nếu rỗng. */
    private fun boxOf(node: AccessibilityNodeInfo): IntArray? {
        val r = Rect().also { node.getBoundsInScreen(it) }
        return if (r.isEmpty) null else intArrayOf(r.left, r.top, r.right, r.bottom)
    }

    private fun stuckBox(root: AccessibilityNodeInfo): IntArray? =
        NodeCollector.stuckScrollableBounds(root)?.takeUnless { it.isEmpty }?.let { intArrayOf(it.left, it.top, it.right, it.bottom) }

    /** Khóa để nhớ khung cuộn đã hết ([ScrollExhaustion]): mã của nút + hướng. Hai bit thấp: hướng, loại khóa (0 = nút, 2 = hộp). */
    private fun scrollKey(node: AccessibilityNodeInfo, forward: Boolean): Long =
        (node.hashCode().toLong() shl 2) or (if (forward) 1L else 0L)

    private fun boxKey(box: IntArray, forward: Boolean): Long =
        (box.contentHashCode().toLong() shl 2) or 2L or (if (forward) 1L else 0L)

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
     * Nhảy tới phần tử cùng loại với [mode] (mặc định [readingMode]) kế/trước. Hết màn hình thì cuộn và tìm tiếp (tối đa
     * [MAX_MODE_SCROLLS] lần), cuộn trong im lặng để chỉ đọc phần tử cuối cùng tìm được.
     */
    private fun moveByMode(forward: Boolean, mode: ReadingMode = readingMode) {
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
            if (outOfBudget || !autoScrollAndSelect(snapshot, forward, quiet = true, rounds = 1)) {
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

    /**
     * Gốc cây để DUYỆT (vuốt, cuộn, nhảy theo vị trí): thường là [activeRoot]. Riêng khi phần tử đang chọn thuộc cửa sổ KHÁC cửa sổ đó và cửa sổ
     * ấy còn mở thì lấy gốc của cửa sổ của phần tử đang chọn ([NavigationWindow.follow]). Bảng biểu tượng cảm xúc và bộ nhớ tạm nằm trong cửa
     * sổ bàn phím, mà nhấc tay lên là Android trả "cửa sổ đang hoạt động" về cửa sổ ứng dụng: nếu cứ duyệt cây của cửa sổ ứng dụng thì không
     * tìm thấy vị trí hiện tại và tiêu điểm nhảy sang dòng khác của ứng dụng.
     *
     * Không lưu lựa chọn bền (khác [windowOverrideId]): mỗi lần tính lại từ phần tử đang chọn nên không có trạng thái để quên; chạm sang phần
     * tử của cửa sổ khác là tự theo cửa sổ mới. [passwordBounds] cố ý vẫn dùng [activeRoot]: vùng mật khẩu để che khi chụp màn hình lấy theo
     * cửa sổ ứng dụng, đổi sang cửa sổ bàn phím sẽ làm sót ô mật khẩu của ứng dụng.
     */
    private fun navigationRoot(): AccessibilityNodeInfo? {
        val base = activeRoot()
        val selected = currentNode?.windowId
        // Trường hợp thường gặp nhất (cùng cửa sổ, hoặc chưa chọn gì): không tốn thêm một lần gọi hệ thống để liệt kê cửa sổ.
        if (selected == null || selected == base?.windowId) {
            followedWindowId = null
            return base
        }
        val windows = service.windows
        val follow = NavigationWindow.follow(selected, base?.windowId, windows.map { it.id })
        val root = follow?.let { id ->
            windows.firstOrNull { it.id == id }?.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)
        }
        if (root == null) {
            followedWindowId = null
            return base
        }
        if (followedWindowId != follow) {
            followedWindowId = follow
            com.example.vnscreenreader.core.Diag.log("WINDOW") { "follow selected element into window=$follow base=${base?.windowId}" }
        }
        return root
    }

    private fun visibleNodes(): List<AccessibilityNodeInfo>? {
        val now = SystemClock.uptimeMillis()
        val selectedWindowId = currentNode?.windowId
        // Bộ đệm chỉ dùng được khi chưa hết hạn, chưa có sự kiện "nội dung đổi/đã cuộn" nào xảy ra sau lần nạp, VÀ phần tử đang chọn (nếu có)
        // thuộc đúng cửa sổ của danh sách (chọn sang cửa sổ khác, ví dụ bàn phím, thì danh sách của cửa sổ cũ không còn đúng).
        if (cachedNodes.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS && cachedAtMs > staleSinceMs &&
            (selectedWindowId == null || selectedWindowId == cachedWindowId)
        ) {
            return cachedNodes
        }
        var collected = NodeCollector.collect(navigationRoot() ?: return null)
        if (!collected.complete) {
            // Có nút con không lấy được (cây đang đổi, thường ngay lúc ứng dụng vẽ lại): đợi một nhịp ngắn rồi lấy lại cây MỚI một lần
            // để không để lọt phần tử. Vẫn thiếu thì dùng bản này nhưng không giữ trong bộ đệm.
            SystemClock.sleep(RECOLLECT_DELAY_MS)
            navigationRoot()?.let { collected = NodeCollector.collect(it) }
        }
        cachedWindowId = collected.nodes.firstOrNull()?.windowId
        cachedNodes = orderByTraversal(collected.nodes)
        // Danh sách còn thiếu nút thì coi như hết hạn ngay: lần vuốt kế sẽ nạp lại thay vì dùng tiếp bản khuyết trong 800 ms.
        cachedAtMs = if (collected.complete) now else 0L
        com.example.vnscreenreader.core.Diag.log("COLLECT") { "stops=${cachedNodes.size} complete=${collected.complete}" }
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
        cursor.reset() // phần tử khác: vị trí đơn vị chữ cũ không còn ý nghĩa
        cursor.bind(nodeText(node))
        highlight(node)
    }

    private companion object {
        const val MAX_RESTORE_CHARS = 20_000
        const val SLIDER_SETTLE_MS = 120L
        const val DRAG_DURATION_MS = 450L
        const val TRACK_LEVELS = 4
        const val CACHE_TTL_MS = 800L

        /** Chờ trước khi nạp lại cây khi lần nạp đầu thiếu nút con (xem [visibleNodes]). */
        const val RECOLLECT_DELAY_MS = 80L
        const val MAX_PARENT_LOOKUP = 12
        const val AUTO_SCROLL_SETTLE_MS = 300L

        /** Chu kỳ thăm dò và hạn chờ tối đa mỗi lần cuộn (xem [awaitScrolled]); tổng thời gian một lần vuốt tự cuộn bị chặn bởi [AUTO_SCROLL_TOTAL_MS]. */
        const val AUTO_SCROLL_POLL_MS = 120L
        const val AUTO_SCROLL_MAX_WAIT_MS = 1500L
        const val AUTO_SCROLL_TOTAL_MS = 5000L

        /** Số khung cuộn tối đa thử mỗi lần, số lần cuộn liên tiếp tối đa khi chưa lộ ra phần tử mới, thời gian nhớ khung đã hết. */
        const val MAX_SCROLL_CANDIDATES = 3
        const val MAX_SCROLL_ROUNDS = 3
        const val EXHAUSTED_TTL_MS = 5000L
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
