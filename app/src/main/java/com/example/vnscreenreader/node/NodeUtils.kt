package com.example.vnscreenreader.node

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.core.CheckedState
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
)

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
    private const val MAX_ANCESTOR_DEPTH = 12

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

    /** Chỉ các phần tử có ý nghĩa để điều hướng bằng vuốt. */
    fun collect(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        traverse(root).filter { isMeaningful(it) }

    private fun isMeaningful(node: AccessibilityNodeInfo): Boolean =
        node.isEnabled && (
            node.isClickable ||
                node.isFocusable ||
                node.isScreenReaderFocusable ||
                !node.text.isNullOrBlank() ||
                !node.contentDescription.isNullOrBlank()
            )

    fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.isClickable) return current
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
