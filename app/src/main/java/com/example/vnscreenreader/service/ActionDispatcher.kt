package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.BatteryManager
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.SpeechFormats
import com.example.vnscreenreader.menu.MenuKind
import java.time.LocalTime
import com.example.vnscreenreader.core.SpeechEngine
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.plugin.PluginRegistry
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Thực thi hành động đã gán cho cử chỉ. Chạy trên luồng nền của service. */
internal class ActionDispatcher(
    private val service: AccessibilityService,
    private val navigator: FocusNavigator,
    private val registryProvider: () -> PluginRegistry,
    private val speech: SpeechEngine,
    private val ai: AiAssistant,
    private val settings: SettingsRepository,
    private val openMenu: (MenuKind) -> Unit,
) {
    fun dispatch(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.NEXT_ELEMENT -> navigator.move(forward = true)
            GestureAction.PREVIOUS_ELEMENT -> navigator.move(forward = false)
            GestureAction.ACTIVATE -> navigator.activate()
            GestureAction.SCROLL_FORWARD -> navigator.scroll(forward = true)
            GestureAction.SCROLL_BACKWARD -> navigator.scroll(forward = false)
            GestureAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK, t(S.MSG_BACK))
            GestureAction.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME, t(S.MSG_HOME))
            GestureAction.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, t(S.MSG_RECENTS))
            GestureAction.NOTIFICATIONS ->
                global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, t(S.MSG_NOTIFICATIONS))
            GestureAction.QUICK_SETTINGS ->
                global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, t(S.MSG_QUICK_SETTINGS))
            GestureAction.RUN_PLUGINS -> runPlugins()
            GestureAction.STOP_SPEECH -> speech.stop()
            GestureAction.OCR_SCREEN -> ai.ocrScreen()
            GestureAction.DESCRIBE_FOCUSED -> ai.describeFocused()
            GestureAction.LABEL_FOCUSED -> editLabel()
            GestureAction.OPEN_GLOBAL_MENU -> openMenu(MenuKind.GLOBAL)
            GestureAction.OPEN_LOCAL_MENU -> openMenu(MenuKind.LOCAL)
            GestureAction.READ_FROM_TOP -> readScreen(fromCurrent = false)
            GestureAction.READ_FROM_CURRENT -> readScreen(fromCurrent = true)
            GestureAction.REPEAT_LAST -> if (!speech.repeatLast()) speech.speak(t(S.MSG_NOTHING_TO_REPEAT), true)
            GestureAction.CYCLE_FEEDBACK -> cycleFeedback()
            GestureAction.SPEAK_STATUS -> speakStatus()
            GestureAction.SEARCH_SCREEN -> launch(SEARCH_ACTIVITY_CLASS, t(S.MSG_OPENING_SEARCH))
            GestureAction.OPEN_SETTINGS -> launch(MAIN_ACTIVITY_CLASS, t(S.MSG_OPENING_SETTINGS))
            GestureAction.COPY_FOCUSED -> copyFocused()
            GestureAction.SPELL_FOCUSED -> navigator.focusedText().fold(
                onSuccess = { speech.speak(SpeechFormats.spell(it), true) },
                onFailure = { speech.speak(it.message ?: t(S.MSG_CANNOT_SPELL), true) },
            )
            GestureAction.LONG_CLICK_FOCUSED -> navigator.longClick()
        }
    }

    /**
     * Mở màn hình gắn nhãn cho phần tử đang chọn. Gọi Activity bằng tên lớp (chuỗi) để lớp điều phối không phụ
     * thuộc tầng giao diện. Dịch vụ trợ năng được phép mở Activity từ nền.
     */
    private fun editLabel() {
        val node = navigator.current()
        if (node == null) {
            speech.speak(t(S.MSG_LABEL_NOTHING), true)
            return
        }
        val viewId = node.viewIdResourceName
        val pkg = node.packageName?.toString()
        if (viewId.isNullOrEmpty() || pkg.isNullOrEmpty()) {
            speech.speak(t(S.MSG_LABEL_NO_ID), true)
            return
        }
        val intent = Intent()
            .setClassName(service.packageName, LABEL_EDITOR_CLASS)
            .putExtra(EXTRA_PACKAGE, pkg)
            .putExtra(EXTRA_VIEW_ID, viewId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
            speech.speak(t(S.MSG_LABEL_OPENING), true)
        } catch (e: ActivityNotFoundException) {
            speech.speak(t(S.MSG_LABEL_FAILED), true)
        }
    }

    private fun readScreen(fromCurrent: Boolean) {
        val text = navigator.describeScreen(fromCurrent)
        speech.speak(
            when {
                text == null -> t(S.MSG_CANNOT_READ_SCREEN)
                text.isBlank() -> t(S.MSG_NOTHING_TO_READ)
                else -> text
            },
            true,
        )
    }

    /** Đổi chế độ rung/âm báo, có hiệu lực ngay (phản hồi đọc cài đặt mỗi lần phát) và không đụng tới TTS. */
    private fun cycleFeedback() {
        val next = settings.feedbackMode.next()
        settings.feedbackMode = next
        speech.speak(next.label, true)
    }

    private fun speakStatus() {
        val now = LocalTime.now()
        val battery = service.getSystemService(BatteryManager::class.java)
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val text = buildString {
            append(SpeechFormats.time(now.hour, now.minute))
            if (percent in 0..100) append(". ").append(SpeechFormats.battery(percent, battery?.isCharging == true))
        }
        speech.speak(text, true)
    }

    private fun copyFocused() {
        navigator.focusedText().fold(
            onSuccess = { text ->
                val clipboard = service.getSystemService(ClipboardManager::class.java)
                if (clipboard == null) {
                    speech.speak(t(S.MSG_CLIPBOARD_UNAVAILABLE), true)
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", text))
                    speech.speak(t(S.MSG_COPIED), true)
                }
            },
            onFailure = { speech.speak(it.message ?: t(S.MSG_CANNOT_COPY), true) },
        )
    }

    /** Mở một Activity của ứng dụng theo tên lớp (không phụ thuộc tầng giao diện). */
    private fun launch(className: String, announcement: String) {
        val intent = Intent().setClassName(service.packageName, className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            service.startActivity(intent)
            speech.speak(announcement, true)
        } catch (e: ActivityNotFoundException) {
            speech.speak(t(S.MSG_CANNOT_OPEN_SCREEN), true)
        }
    }

    private fun global(actionId: Int, label: String) {
        if (service.performGlobalAction(actionId)) {
            navigator.reset()
            speech.speak(label, true)
        } else {
            speech.speak(t(S.MSG_GLOBAL_FAILED, label), true)
        }
    }

    private fun runPlugins() {
        val root = service.rootInActiveWindow
        if (root == null) {
            speech.speak(t(S.MSG_CANNOT_READ_SCREEN), true)
            return
        }
        val results = registryProvider().runEnabled(root)
        val message = when {
            results.isEmpty() -> t(S.MSG_NO_PLUGINS)
            else -> results.firstOrNull { it.handled }?.message
                ?: results.last().message
                ?: t(S.MSG_PLUGIN_NOTHING_FOUND)
        }
        if (results.any { it.handled }) navigator.reset()
        speech.speak(message, true)
    }

    companion object {
        const val LABEL_EDITOR_CLASS = "com.example.vnscreenreader.LabelEditorActivity"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_VIEW_ID = "extra_view_id"
        const val SEARCH_ACTIVITY_CLASS = "com.example.vnscreenreader.ScreenSearchActivity"
        const val MAIN_ACTIVITY_CLASS = "com.example.vnscreenreader.MainActivity"
    }
}
