package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.FocusIndex
import com.example.vnscreenreader.core.SpeechTextBuilder
import com.example.vnscreenreader.node.NodeCollector
import com.example.vnscreenreader.node.toSnapshot

/**
 * Quản lý phần tử đang chọn và điều hướng. Chỉ được gọi từ một luồng xử lý duy nhất
 * (dispatcher 1 luồng của service) nên không cần khóa.
 */
class FocusNavigator(
    private val service: AccessibilityService,
    private val overlay: HighlightOverlay,
    private val speak: (text: String, interrupt: Boolean) -> Unit,
) {
    private var currentNode: AccessibilityNodeInfo? = null
    private var cachedNodes: List<AccessibilityNodeInfo> = emptyList()
    private var cachedAtMs = 0L

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
        speak(describe(node), true)
    }

    fun highlight(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) overlay.clear() else overlay.show(bounds)
    }

    fun describe(node: AccessibilityNodeInfo): String =
        SpeechTextBuilder.buildNodeDescription(node.toSnapshot())

    fun move(forward: Boolean) {
        val nodes = visibleNodes()
        if (nodes == null) {
            speak("Không đọc được nội dung màn hình", true)
            return
        }
        if (nodes.isEmpty()) {
            speak("Màn hình không có phần tử nào", true)
            return
        }
        val current = currentNode?.let { cur -> nodes.indexOfFirst { it == cur } } ?: -1
        val target = if (forward) FocusIndex.next(current, nodes.size) else FocusIndex.previous(current, nodes.size)
        if (target == null) {
            speak(if (forward) "Đã đến phần tử cuối" else "Đã đến phần tử đầu", true)
            return
        }
        focusOn(nodes[target])
    }

    fun activate() {
        val node = currentNode
        if (node == null) {
            speak("Chưa chọn phần tử nào", true)
            return
        }
        if (!node.refresh()) {
            reset()
            speak("Phần tử không còn trên màn hình", true)
            return
        }
        val target = NodeCollector.findClickableAncestorOrSelf(node)
        if (target == null) {
            speak("Phần tử này không thể nhấn", true)
            return
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            invalidateCache()
        } else {
            speak("Nhấn không thành công", true)
        }
    }

    fun scroll(forward: Boolean) {
        val root = service.rootInActiveWindow
        if (root == null) {
            speak("Không đọc được nội dung màn hình", true)
            return
        }
        val target = NodeCollector.findScrollable(currentNode, root)
        if (target == null) {
            speak("Không có vùng cuộn", true)
            return
        }
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (target.performAction(action)) {
            reset()
            speak(if (forward) "Cuộn xuống" else "Cuộn lên", true)
        } else {
            speak("Không cuộn được nữa", true)
        }
    }

    private fun visibleNodes(): List<AccessibilityNodeInfo>? {
        val now = SystemClock.uptimeMillis()
        if (cachedNodes.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS) return cachedNodes
        val root = service.rootInActiveWindow ?: return null
        cachedNodes = NodeCollector.collect(root)
        cachedAtMs = now
        return cachedNodes
    }

    private companion object {
        const val CACHE_TTL_MS = 800L
    }
}
