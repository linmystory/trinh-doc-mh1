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
        // Cụm đầy đủ: khớp một phần. Từ chung ("Bỏ qua", "Skip"): chỉ khi CẢ nhãn đúng bằng từ đó, để không bấm nhầm
        // "Bỏ qua bước này"/"Skip verification" trong các luồng xác nhận.
        for ((keyword, exact) in KEYWORDS) {
            for (candidate in node.findAccessibilityNodeInfosByText(keyword)) {
                if (!candidate.isVisibleToUser) continue
                if (exact && !isExactly(candidate, keyword)) continue
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
            // Cụm đầy đủ (khớp một phần): tiếng Việt, Anh, Tây Ban Nha, Pháp, Nga, Trung, Ả Rập.
            "Bỏ qua quảng cáo" to false, "Đóng quảng cáo" to false, "Skip ads" to false, "Skip ad" to false,
            "Saltar anuncio" to false, "Omitir anuncio" to false, "Passer l'annonce" to false,
            "Ignorer l'annonce" to false, "Пропустить рекламу" to false, "跳过广告" to false, "跳過廣告" to false,
            "تخطي الإعلان" to false,
            // Từ chung: chỉ khớp khi CẢ nhãn đúng bằng từ đó.
            "Bỏ qua" to true, "Skip" to true, "Saltar" to true, "Omitir" to true, "Passer" to true,
            "Пропустить" to true, "跳过" to true, "跳過" to true, "تخطي" to true,
        )

        private fun isExactly(node: AccessibilityNodeInfo, keyword: String): Boolean =
            listOf(node.text, node.contentDescription).any { it?.toString()?.trim().equals(keyword, ignoreCase = true) }
    }
}
