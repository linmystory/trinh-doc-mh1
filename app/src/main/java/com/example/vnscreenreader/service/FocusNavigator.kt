package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.CustomLabelStore
import com.example.vnscreenreader.core.FocusIndex
import com.example.vnscreenreader.core.LabelResolver
import com.example.vnscreenreader.core.ScreenSearch
import com.example.vnscreenreader.core.SpeechTextBuilder
import com.example.vnscreenreader.core.TextCursor
import com.example.vnscreenreader.core.TextGranularity
import com.example.vnscreenreader.core.EditOp
import com.example.vnscreenreader.core.EditPolicy
import com.example.vnscreenreader.core.SelectionMath
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
    private var currentNode: AccessibilityNodeInfo? = null
    private var cachedNodes: List<AccessibilityNodeInfo> = emptyList()
    private var cachedAtMs = 0L
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
            reset()
        })
    }

    fun reset() {
        currentNode = null
        invalidateCache()
        overlay.clear()
    }

    fun invalidateCache() {
        cachedNodes = emptyList()
        cachedAtMs = 0L
    }

    fun focusOn(node: AccessibilityNodeInfo) {
        currentNode = node
        cursor.bind(nodeText(node))
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        highlight(node)
        feedback.onFocusChanged()
        speak(describe(node), true)
        onFocused(node)
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
    fun describe(node: AccessibilityNodeInfo): String {
        val snapshot = node.toSnapshot()
        val resolved = LabelResolver.resolve(
            node = snapshot,
            customLabel = { customLabels.find(snapshot.packageName, snapshot.viewId) },
            mergedChildren = { NodeCollector.mergedLabel(node) },
        )
        return SpeechTextBuilder.buildNodeDescription(resolved)
    }

    fun move(forward: Boolean) {
        val nodes = visibleNodes()
        if (nodes == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        if (nodes.isEmpty()) {
            speak(t(S.MSG_SCREEN_EMPTY), true)
            return
        }
        val current = currentNode?.let { cur -> nodes.indexOfFirst { it == cur } } ?: -1
        val target = if (forward) FocusIndex.next(current, nodes.size) else FocusIndex.previous(current, nodes.size)
        if (target == null) {
            feedback.onBoundary()
            speak(if (forward) t(S.MSG_LAST_ELEMENT) else t(S.MSG_FIRST_ELEMENT), true)
            return
        }
        focusOn(nodes[target])
    }

    fun activate() {
        val node = currentNode
        if (node == null) {
            speak(t(S.MSG_NOTHING_SELECTED), true)
            return
        }
        if (!node.refresh()) {
            reset()
            speak(t(S.MSG_ELEMENT_GONE), true)
            return
        }
        val target = NodeCollector.findClickableAncestorOrSelf(node)
        if (target == null) {
            speak(t(S.MSG_CANNOT_CLICK), true)
            return
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            feedback.onActivated()
            invalidateCache()
        } else {
            speak(t(S.MSG_CLICK_FAILED), true)
        }
    }

    fun scroll(forward: Boolean) {
        val root = service.rootInActiveWindow
        if (root == null) {
            speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        // Thử lần lượt từng vùng cuộn (tổ tiên trước, rồi vùng lớn nhất) và từng loại hành động cho tới khi một
        // vùng thật sự cuộn; chỉ khi không vùng nào cuộn được mới báo đã tới cuối.
        val targets = NodeCollector.scrollTargets(currentNode, root, forward)
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
        val start = if (fromCurrent) currentIndexIn(nodes).coerceAtLeast(0) else 0
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
        val index = ScreenSearch.find(nodes.map { describe(it) }, query, currentIndexIn(nodes))
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
        val target = NodeCollector.findAncestorOrSelf(node) { it.isLongClickable }
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            feedback.onActivated()
            invalidateCache()
            speak(t(S.MSG_LONG_CLICKED), true)
        } else {
            speak(t(S.MSG_CANNOT_LONG_CLICK), true)
        }
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
        val node = currentNode ?: return null
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

    private fun currentIndexIn(nodes: List<AccessibilityNodeInfo>): Int =
        currentNode?.let { cur -> nodes.indexOfFirst { it == cur } } ?: -1

    private fun activeRoot(): AccessibilityNodeInfo? =
        // Nạp trước cả cây con trong MỘT lần gọi IPC thay vì mỗi nút một lần: giảm mạnh độ trễ trên cây lớn.
        service.getRootInActiveWindow(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID)

    private fun visibleNodes(): List<AccessibilityNodeInfo>? {
        val now = SystemClock.uptimeMillis()
        if (cachedNodes.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS) return cachedNodes
        val root = activeRoot() ?: return null
        cachedNodes = NodeCollector.collect(root)
        cachedAtMs = now
        return cachedNodes
    }

    private companion object {
        const val CACHE_TTL_MS = 800L
        const val MAX_READ_ALL = 300
    }
}
