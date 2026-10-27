package com.example.vnscreenreader.plugin

import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.node.NodeCollector

/** Tiện ích dựng sẵn: tìm và nhấn nút "Bỏ qua quảng cáo" trên màn hình hiện tại. */
class SkipAdPlugin : ScreenReaderPlugin {

    override val id = ID
    override val displayName = "Bỏ qua quảng cáo"
    override val description = "Tìm và nhấn nút Bỏ qua / Skip quảng cáo trên màn hình hiện tại."

    override fun execute(node: AccessibilityNodeInfo): PluginResult {
        for (keyword in KEYWORDS) {
            for (candidate in node.findAccessibilityNodeInfosByText(keyword)) {
                if (!candidate.isVisibleToUser) continue
                val target = NodeCollector.findClickableAncestorOrSelf(candidate) ?: continue
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return PluginResult(true, "Đã bỏ qua quảng cáo")
                }
            }
        }
        return PluginResult(false, "Không thấy nút bỏ qua quảng cáo")
    }

    companion object {
        const val ID = "builtin_skip_ad"
        private val KEYWORDS = listOf(
            "Bỏ qua quảng cáo", "Đóng quảng cáo", "Skip ads", "Skip ad", "Bỏ qua", "Skip",
        )
    }
}
