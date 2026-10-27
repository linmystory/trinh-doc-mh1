package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import com.example.vnscreenreader.core.SpeechEngine
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.plugin.PluginRegistry

/** Thực thi hành động đã gán cho cử chỉ. Chạy trên luồng nền của service. */
class ActionDispatcher(
    private val service: AccessibilityService,
    private val navigator: FocusNavigator,
    private val registryProvider: () -> PluginRegistry,
    private val speech: SpeechEngine,
) {
    fun dispatch(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.NEXT_ELEMENT -> navigator.move(forward = true)
            GestureAction.PREVIOUS_ELEMENT -> navigator.move(forward = false)
            GestureAction.ACTIVATE -> navigator.activate()
            GestureAction.SCROLL_FORWARD -> navigator.scroll(forward = true)
            GestureAction.SCROLL_BACKWARD -> navigator.scroll(forward = false)
            GestureAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK, "Quay lại")
            GestureAction.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME, "Màn hình chính")
            GestureAction.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Ứng dụng gần đây")
            GestureAction.NOTIFICATIONS ->
                global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Thanh thông báo")
            GestureAction.QUICK_SETTINGS ->
                global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Cài đặt nhanh")
            GestureAction.RUN_PLUGINS -> runPlugins()
            GestureAction.STOP_SPEECH -> speech.stop()
        }
    }

    private fun global(actionId: Int, label: String) {
        if (service.performGlobalAction(actionId)) {
            navigator.reset()
            speech.speak(label, true)
        } else {
            speech.speak("Không thực hiện được: $label", true)
        }
    }

    private fun runPlugins() {
        val root = service.rootInActiveWindow
        if (root == null) {
            speech.speak("Không đọc được nội dung màn hình", true)
            return
        }
        val results = registryProvider().runEnabled(root)
        val message = when {
            results.isEmpty() -> "Chưa có tiện ích nào được bật"
            else -> results.firstOrNull { it.handled }?.message
                ?: results.last().message
                ?: "Không tìm thấy phần tử phù hợp"
        }
        if (results.any { it.handled }) navigator.reset()
        speech.speak(message, true)
    }
}
