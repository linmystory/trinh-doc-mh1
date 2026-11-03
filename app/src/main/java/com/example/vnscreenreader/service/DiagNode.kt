package com.example.vnscreenreader.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Mô tả một nút cho nhật ký chẩn đoán: CHỈ siêu dữ liệu (lớp, id, vùng, cờ, hành động), KHÔNG có chữ hiển thị. */
internal fun AccessibilityNodeInfo?.diag(): String {
    val n = this ?: return "null"
    val r = Rect().also { n.getBoundsInScreen(it) }
    val flags = buildString {
        if (n.isClickable) append("clk ")
        if (n.isLongClickable) append("lclk ")
        if (n.isScrollable) append("scr ")
        if (n.isCheckable) append("chk ")
        if (n.isEditable) append("edit ")
        if (!n.isVisibleToUser) append("hidden ")
        if (!n.isEnabled) append("disabled ")
    }.trim()
    val actions = n.actionList.map { it.id }.filter { it < 0x10000 }.joinToString(",") // chỉ hành động chuẩn
    return "${n.className?.toString()?.substringAfterLast('.')} id=${n.viewIdResourceName?.substringAfterLast('/')} " +
        "pkg=${n.packageName} [${r.left},${r.top},${r.right},${r.bottom}] {$flags} act=[$actions]"
}
