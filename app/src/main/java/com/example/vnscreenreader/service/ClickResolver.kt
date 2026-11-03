package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.ClickTargetPolicy
import com.example.vnscreenreader.core.DescendantProbe
import com.example.vnscreenreader.node.NodeCollector

/** Kết quả của thao tác kích hoạt; [ActionDispatcher] đọc câu báo tương ứng. */
internal enum class ActivateOutcome { NOTHING_SELECTED, GONE, CLICKED, CLICKED_NEARBY, TAPPED, CANNOT_CLICK, CLICK_FAILED }

/**
 * Chọn và thực hiện thao tác nhấn cho một phần tử, kể cả liên kết/vùng tương tác trong WebView.
 * Quy tắc chọn nằm ở [ClickTargetPolicy] (thuần hàm, có test); lớp này chỉ gắn với AccessibilityNodeInfo.
 * Chỉ gọi từ luồng nền của phiên đọc.
 */
internal class ClickResolver(private val service: AccessibilityService) {

    /**
     * Các nút nên nhận ACTION_CLICK, theo thứ tự thử: tổ tiên/chính nó bấm được gần nhất (không leo qua vùng cuộn hay
     * gốc WebView), rồi phần tử con bấm được tốt nhất (thẻ `<a>` nằm TRONG khối tiêu đề). Rỗng = không có gì để nhấn.
     */
    fun candidates(node: AccessibilityNodeInfo, includeDescendants: Boolean = true): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>(2)
        val chain = chainOf(node)
        ClickTargetPolicy.ancestorToClick(chain.map { NodeCollector.probe(it) })?.let { result += chain[it] }
        // Tìm con tốn tới ~60 lần gọi hệ thống: chỉ dùng khi thật sự nhấn, không dùng khi chỉ cần biết "có bấm được không".
        if (result.isEmpty() && includeDescendants) bestDescendant(node)?.let { result += it }
        return result
    }

    /** Phần tử nằm trong nội dung web (có tổ tiên là WebView/GeckoView)? */
    fun isInsideWeb(node: AccessibilityNodeInfo): Boolean = chainOf(node).any { NodeCollector.isWebRoot(it) }

    fun isWebRoot(node: AccessibilityNodeInfo): Boolean = NodeCollector.isWebRoot(node)

    /**
     * Giả lập một cú chạm vào giữa phần tử khi ACTION_CLICK không có tác dụng (nút web tự vẽ). Chỉ dùng trong nội dung
     * web ([ClickTargetPolicy.shouldTapFallback]). Cần canPerformGestures; false nếu không phát được.
     */
    fun tapCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.isEmpty) return false
        val metrics = service.resources.displayMetrics
        val x = bounds.exactCenterX().coerceIn(1f, (metrics.widthPixels - 1).toFloat())
        val y = bounds.exactCenterY().coerceIn(1f, (metrics.heightPixels - 1).toFloat())
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private fun chainOf(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < NodeCollector.MAX_ANCESTOR_DEPTH) {
            out += current
            current = current.parent
            depth++
        }
        return out
    }

    /** Duyệt rộng các con (tối đa [MAX_DESCENDANTS] nút, sâu [MAX_DESCENDANT_DEPTH]); không đi vào vùng cuộn. */
    private fun bestDescendant(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val probes = ArrayList<DescendantProbe>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(node to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_DESCENDANTS) {
            val (current, depth) = queue.removeFirst()
            visited++
            if (depth > 0) {
                if (!current.isVisibleToUser) continue
                val probe = NodeCollector.probe(current)
                if (probe.scrollable) continue
                if (probe.canClick) {
                    nodes += current
                    probes += DescendantProbe(probe, depth)
                    continue // đã tìm thấy mục tiêu: không cần đi sâu hơn
                }
            }
            if (depth < MAX_DESCENDANT_DEPTH) {
                for (i in 0 until current.childCount) current.getChild(i)?.let { queue.addLast(it to depth + 1) }
            }
        }
        return ClickTargetPolicy.descendantToClick(probes)?.let { nodes[it] }
    }

    private companion object {
        const val MAX_DESCENDANTS = 60
        const val MAX_DESCENDANT_DEPTH = 3
        const val TAP_DURATION_MS = 50L
    }
}
