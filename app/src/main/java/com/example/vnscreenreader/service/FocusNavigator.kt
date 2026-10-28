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

    /** Phần tử đang chọn (có thể đã cũ); null nếu chưa chọn. */
    fun current(): AccessibilityNodeInfo? = currentNode

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
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        highlight(node)
        feedback.onFocusChanged()
        speak(describe(node), true)
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
        val target = NodeCollector.findScrollable(currentNode, root)
        if (target == null) {
            speak(t(S.MSG_NO_SCROLL_AREA), true)
            return
        }
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (target.performAction(action)) {
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
        val node = currentNode
        if (node == null) {
            speak(t(S.MSG_NOTHING_SELECTED), true)
            return
        }
        val target = NodeCollector.findAncestorOrSelf(node) { it.isLongClickable }
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            feedback.onActivated()
            invalidateCache()
        } else {
            speak(t(S.MSG_CANNOT_LONG_CLICK), true)
        }
    }

    /** Chữ của phần tử đang chọn; null nếu chưa chọn, rỗng hoặc là ô mật khẩu (không bao giờ lộ mật khẩu). */
    fun focusedText(): Result<String> {
        val node = currentNode ?: return Result.failure(IllegalStateException(t(S.MSG_NOTHING_SELECTED)))
        if (node.isPassword) return Result.failure(IllegalStateException(t(S.MSG_PASSWORD_NO_ACTION)))
        val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        return if (text.isEmpty()) Result.failure(IllegalStateException(t(S.MSG_NO_TEXT))) else Result.success(text)
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
