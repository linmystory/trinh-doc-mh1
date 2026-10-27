package com.example.vnscreenreader.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.example.vnscreenreader.service.ScreenReaderService

object AccessibilityStatus {

    /** Kiểm tra dịch vụ đọc màn hình của ứng dụng này đã được bật trong Cài đặt chưa. */
    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                service.packageName == context.packageName &&
                    service.name == ScreenReaderService::class.java.name
            }
    }
}
