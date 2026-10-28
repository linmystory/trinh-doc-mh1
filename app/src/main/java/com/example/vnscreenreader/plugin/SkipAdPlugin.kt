package com.example.vnscreenreader.plugin

import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.node.NodeCollector
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Tiện ích dựng sẵn: tìm và nhấn nút "Bỏ qua" / "Skip" của quảng cáo trên màn hình hiện tại. */
class SkipAdPlugin : ScreenReaderPlugin {

    override val id = ID
    override val displayName: String get() = t(S.PLUGIN_SKIPAD_NAME)
    override val description: String get() = t(S.PLUGIN_SKIPAD_DESC)

    override fun execute(node: AccessibilityNodeInfo): PluginResult {
        for (keyword in KEYWORDS) {
            for (candidate in node.findAccessibilityNodeInfosByText(keyword)) {
                if (!candidate.isVisibleToUser) continue
                val target = NodeCollector.findClickableAncestorOrSelf(candidate) ?: continue
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return PluginResult(true, t(S.PLUGIN_SKIPAD_DONE))
                }
            }
        }
        return PluginResult(false, t(S.PLUGIN_SKIPAD_NONE))
    }

    companion object {
        const val ID = "builtin_skip_ad"
        private val KEYWORDS = listOf(
            "Bỏ qua quảng cáo", "Đóng quảng cáo", "Skip ads", "Skip ad", "Bỏ qua", "Skip",
        )
    }
}
