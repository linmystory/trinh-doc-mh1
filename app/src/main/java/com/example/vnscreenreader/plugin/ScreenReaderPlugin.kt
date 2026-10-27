package com.example.vnscreenreader.plugin

import android.view.accessibility.AccessibilityNodeInfo

data class PluginResult(val handled: Boolean, val message: String? = null)

/** Hợp đồng cho mọi tiện ích. [execute] nhận nút gốc của cửa sổ đang hoạt động; chạy trên luồng nền. */
interface ScreenReaderPlugin {
    val id: String
    val displayName: String
    val description: String

    fun execute(node: AccessibilityNodeInfo): PluginResult
}
