package com.example.vnscreenreader.plugin

import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.node.NodeCollector
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Tiện ích chạy theo một [ScriptDefinition] nạp từ bên ngoài. */
class ScriptPlugin(private val definition: ScriptDefinition) : ScreenReaderPlugin {

    override val id: String get() = definition.id
    override val displayName: String get() = definition.name
    override val description: String get() = definition.description

    override fun execute(node: AccessibilityNodeInfo): PluginResult {
        if (definition.packages.isNotEmpty()) {
            val pkg = node.packageName?.toString()
            if (pkg == null || pkg !in definition.packages) {
                return PluginResult(false, t(S.PLUGIN_NOT_APPLICABLE, definition.name))
            }
        }
        val nodes = NodeCollector.traverse(node)
        for (rule in definition.rules) {
            for (candidate in nodes) {
                if (!rule.matches(candidate.text, candidate.contentDescription, candidate.viewIdResourceName, candidate.className)) continue
                val done = when (rule.action) {
                    RuleAction.CLICK ->
                        NodeCollector.findClickableAncestorOrSelf(candidate)
                            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    RuleAction.LONG_CLICK ->
                        NodeCollector.findAncestorOrSelf(candidate) { it.isLongClickable }
                            ?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
                    RuleAction.FOCUS ->
                        candidate.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                    RuleAction.SCROLL_FORWARD ->
                        NodeCollector.findAncestorOrSelf(candidate) { it.isScrollable }
                            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
                    RuleAction.SCROLL_BACKWARD ->
                        NodeCollector.findAncestorOrSelf(candidate) { it.isScrollable }
                            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
                }
                if (done) return PluginResult(true, rule.message ?: t(S.PLUGIN_DONE, definition.name))
            }
        }
        return PluginResult(false, t(S.PLUGIN_NO_MATCH, definition.name))
    }
}
