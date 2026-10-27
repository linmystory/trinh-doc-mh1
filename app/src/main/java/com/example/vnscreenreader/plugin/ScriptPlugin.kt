package com.example.vnscreenreader.plugin

import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.node.NodeCollector

/** Tiện ích chạy theo một [ScriptDefinition] nạp từ bên ngoài. */
class ScriptPlugin(private val definition: ScriptDefinition) : ScreenReaderPlugin {

    override val id: String get() = definition.id
    override val displayName: String get() = definition.name
    override val description: String get() = definition.description

    override fun execute(node: AccessibilityNodeInfo): PluginResult {
        if (definition.packages.isNotEmpty()) {
            val pkg = node.packageName?.toString()
            if (pkg == null || pkg !in definition.packages) {
                return PluginResult(false, "${definition.name}: không áp dụng cho ứng dụng này")
            }
        }
        val nodes = NodeCollector.traverse(node)
        for (rule in definition.rules) {
            for (candidate in nodes) {
                if (!rule.matches(candidate.text, candidate.contentDescription, candidate.viewIdResourceName)) continue
                val done = when (rule.action) {
                    RuleAction.CLICK ->
                        NodeCollector.findClickableAncestorOrSelf(candidate)
                            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    RuleAction.FOCUS ->
                        candidate.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                }
                if (done) return PluginResult(true, "${definition.name}: đã thực hiện")
            }
        }
        return PluginResult(false, "${definition.name}: không tìm thấy phần tử phù hợp")
    }
}
