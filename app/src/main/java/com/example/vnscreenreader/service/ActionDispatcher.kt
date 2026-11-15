package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.view.KeyEvent
import com.example.vnscreenreader.core.CaretMove
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.ReadingControl
import com.example.vnscreenreader.core.ReadingMode
import com.example.vnscreenreader.core.SpeechFormats
import com.example.vnscreenreader.core.SpeechRate
import com.example.vnscreenreader.core.UnitMove
import com.example.vnscreenreader.menu.MenuKind
import java.time.LocalTime
import com.example.vnscreenreader.core.SpeechEngine
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.plugin.PluginRegistry
import com.example.vnscreenreader.core.EditOp
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
    private val toggleKeysPause: () -> Unit = {},
    private val toggleVolumeForApp: () -> Unit = {},
    private val adjustVolume: (Int) -> Unit = {},
    private val sayLocation: () -> Unit = {},
) {
    fun dispatch(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.NEXT_ELEMENT -> navigator.move(forward = true)
            GestureAction.PREVIOUS_ELEMENT -> navigator.move(forward = false)
            GestureAction.NEXT_BY_CONTROL -> stepByControl(forward = true)
            GestureAction.PREVIOUS_BY_CONTROL -> stepByControl(forward = false)
            GestureAction.NEXT_CONTROL -> cycleControl(forward = true)
            GestureAction.PREVIOUS_CONTROL -> cycleControl(forward = false)
            GestureAction.MEDIA_PLAY_PAUSE -> mediaPlayPause()
            GestureAction.ACTIVATE -> activate()
            GestureAction.SCROLL_FORWARD -> navigator.scroll(forward = true)
            GestureAction.SCROLL_BACKWARD -> navigator.scroll(forward = false)
            GestureAction.PAGE_NEXT -> navigator.scrollPage(forward = true)
            GestureAction.PAGE_PREVIOUS -> navigator.scrollPage(forward = false)
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
            GestureAction.OCR_FOCUSED -> ai.ocrFocused()
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
            GestureAction.UNIT_NEXT -> moveUnit(forward = true)
            GestureAction.UNIT_PREVIOUS -> moveUnit(forward = false)
            GestureAction.CYCLE_GRANULARITY -> speech.speak(t(S.MSG_GRANULARITY, navigator.cycleGranularity().label), true)
            GestureAction.COPY_UNIT -> copyUnit()
            GestureAction.PASTE_FOCUSED -> editText(EditOp.PASTE)
            GestureAction.CUT_FOCUSED -> editText(EditOp.CUT)
            GestureAction.SELECT_ALL_FOCUSED -> editText(EditOp.SELECT_ALL)
            GestureAction.CURSOR_START -> editText(EditOp.CURSOR_START)
            GestureAction.CURSOR_END -> editText(EditOp.CURSOR_END)
            GestureAction.SELECT_TO_START -> editText(EditOp.SELECT_TO_START)
            GestureAction.SELECT_TO_END -> editText(EditOp.SELECT_TO_END)
            GestureAction.TOGGLE_DETAILED_FOCUS -> toggleDetailedFocus()
            GestureAction.CYCLE_VOICE_MODE -> cycleVoiceMode()
            GestureAction.NEXT_READING_MODE -> navigator.cycleReadingMode(forward = true)
            GestureAction.PREVIOUS_READING_MODE -> navigator.cycleReadingMode(forward = false)
            GestureAction.NEXT_CONTAINER -> navigator.moveContainer(forward = true)
            GestureAction.PREVIOUS_CONTAINER -> navigator.moveContainer(forward = false)
            GestureAction.NEXT_WINDOW -> navigator.moveWindow(forward = true)
            GestureAction.PREVIOUS_WINDOW -> navigator.moveWindow(forward = false)
            GestureAction.TOGGLE_KEYS_PAUSE -> toggleKeysPause()
            GestureAction.VOLUME_EXCLUDE_APP -> toggleVolumeForApp()
            GestureAction.VOLUME_RAISE -> adjustVolume(1)
            GestureAction.VOLUME_LOWER -> adjustVolume(-1)
            GestureAction.SLIDER_UP -> if (!navigator.adjustRange(1)) speech.speak(t(S.MSG_SLIDER_NONE), true)
            GestureAction.SLIDER_DOWN -> if (!navigator.adjustRange(-1)) speech.speak(t(S.MSG_SLIDER_NONE), true)
            GestureAction.SLIDER_TO_END -> navigator.dragToEnd()
            GestureAction.SAY_LOCATION -> sayLocation()
            GestureAction.CLEAR_FIELD -> speakClear(navigator.clearField())
            GestureAction.RESTORE_CLEARED -> speakClear(navigator.restoreCleared())
            GestureAction.DESCRIBE_TECHNICAL ->
                speech.speak(navigator.technicalInfo() ?: t(S.MSG_NOTHING_SELECTED), true)
            GestureAction.JUMP_FIRST -> navigator.jumpEdge(first = true)
            GestureAction.JUMP_LAST -> navigator.jumpEdge(first = false)
            GestureAction.NEXT_HEADING -> navigator.quickMove(FocusNavigator.QuickKind.HEADING, forward = true)
            GestureAction.PREVIOUS_HEADING -> navigator.quickMove(FocusNavigator.QuickKind.HEADING, forward = false)
            GestureAction.NEXT_LINK -> navigator.quickMove(FocusNavigator.QuickKind.LINK, forward = true)
            GestureAction.PREVIOUS_LINK -> navigator.quickMove(FocusNavigator.QuickKind.LINK, forward = false)
            GestureAction.SCROLL_TO_TOP -> navigator.scrollToEdge(forward = false)
            GestureAction.SCROLL_TO_BOTTOM -> navigator.scrollToEdge(forward = true)
        }
    }

    /**
     * Kích hoạt phần tử đang chọn. Việc chọn mục tiêu nhấn (thẻ liên kết `<a>`/vùng tương tác trong WebView, nút chỉ
     * khai báo hành động nhấn, con bấm được, giả lập chạm khi cần) nằm ở [FocusNavigator.activate]; ở đây chỉ đọc
     * câu báo cho các kết quả cần báo. Thành công thì im lặng: ứng dụng tự báo qua sự kiện "đã nhấn".
     */
    private fun activate() {
        val message = when (navigator.activate()) {
            ActivateOutcome.NOTHING_SELECTED -> t(S.MSG_NOTHING_SELECTED)
            ActivateOutcome.GONE -> t(S.MSG_ELEMENT_GONE)
            ActivateOutcome.CANNOT_CLICK -> t(S.MSG_CANNOT_CLICK)
            ActivateOutcome.CLICK_FAILED -> t(S.MSG_CLICK_FAILED)
            ActivateOutcome.CLICKED, ActivateOutcome.CLICKED_NEARBY, ActivateOutcome.TAPPED -> return
        }
        speech.speak(message, true)
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
            // Chế độ đọc khác mặc định ảnh hưởng cách vuốt nên luôn nhắc ở đây.
            if (navigator.readingMode != ReadingMode.ELEMENT) append(". ").append(t(S.MSG_MODE_SET, navigator.readingMode.label))
            // Điều khiển đọc khác "phần tử" đổi ý nghĩa của vuốt một ngón xuống/lên nên cũng nhắc.
            val control = navigator.readingControl
            if (control != ReadingControl.ELEMENT) append(". ").append(t(S.MSG_CONTROL_SET, control.label))
        }
        speech.speak(text, true)
    }

    /**
     * [crossElements] = true (điều khiển đọc theo đơn vị chữ): hết chữ, đầu chữ hoặc phần tử không có chữ thì sang phần tử
     * kế/trước thay vì chỉ báo "Hết văn bản". Ba ngón vuốt xuống/lên vẫn dừng lại và báo, như trước.
     */
    private fun moveUnit(forward: Boolean, crossElements: Boolean = false) {
        val move = navigator.moveUnit(forward)
        if (crossElements && ReadingControl.crossesElement(move)) {
            navigator.moveToNeighbour(forward)
            return
        }
        val text = when (move) {
            is UnitMove.At -> spoken(move.text)
            UnitMove.End -> t(S.MSG_UNIT_END)
            UnitMove.Start -> t(S.MSG_UNIT_START)
            UnitMove.Changed -> return // navigator đã báo "phần tử đã thay đổi"
            null -> t(S.MSG_NO_TEXT)
        }
        speech.speak(text, true)
    }

    /**
     * Vuốt một ngón xuống ([forward] = true) / lên, theo thứ tự ưu tiên (đúng cách các trình đọc màn hình chuẩn làm):
     *  1. Đang chọn thanh trượt: chỉnh giá trị (xuống giảm, lên tăng), bất kể điều khiển đọc nào.
     *  2. Đang ở ô nhập (không phải mật khẩu) và điều khiển là chữ hoặc "phần tử": dời con trỏ THẬT theo đơn vị của điều khiển
     *     (mặc định là ký tự) và đọc đoạn vừa đi qua; hết chữ thì sang phần tử kế/trước để không kẹt trong ô.
     *  3. Còn lại: di chuyển theo điều khiển (phần tử, tiêu đề, điều khiển, liên kết, từng đơn vị chữ) hoặc chỉnh tốc độ đọc.
     */
    private fun stepByControl(forward: Boolean) {
        if (navigator.adjustRange(ReadingControl.sliderDirection(forward))) return
        val control = navigator.readingControl
        control.caretUnit?.let { unit ->
            when (val caret = navigator.moveCaret(unit, forward)) {
                is CaretMove.Moved -> {
                    speech.speak(spoken(caret.text), true)
                    return
                }
                CaretMove.Edge -> {
                    navigator.moveToNeighbour(forward)
                    return
                }
                CaretMove.Unavailable -> Unit // không phải ô nhập: đi theo điều khiển như thường
            }
        }
        when (control) {
            ReadingControl.ELEMENT -> navigator.moveElement(forward)
            ReadingControl.HEADING -> navigator.quickMove(FocusNavigator.QuickKind.HEADING, forward)
            ReadingControl.LINK -> navigator.quickMove(FocusNavigator.QuickKind.LINK, forward)
            ReadingControl.CONTROL -> navigator.moveToMode(ReadingMode.CONTROL, forward)
            ReadingControl.SPEECH_RATE -> adjustSpeechRate(ReadingControl.sliderDirection(forward))
            ReadingControl.CHARACTER, ReadingControl.WORD, ReadingControl.SENTENCE, ReadingControl.PARAGRAPH ->
                moveUnit(forward, crossElements = true)
        }
    }

    /**
     * Chữ để đọc ra cho một đoạn: đoạn chỉ gồm khoảng trắng/xuống dòng thì đọc TÊN của nó (qua [SpeechFormats.spell]) thay vì để
     * bộ đọc im lặng khiến người dùng tưởng không có gì.
     */
    private fun spoken(text: String): String = if (text.isBlank()) SpeechFormats.spell(text) else text

    /**
     * Điều khiển đọc "Tốc độ đọc": [direction] dương = nhanh hơn, âm = chậm hơn, mỗi bước 10% trong khoảng của thanh trượt ở Cài đặt
     * giọng đọc. Áp ngay vào bộ đọc rồi đọc tốc độ mới bằng chính tốc độ đó; đã ở giới hạn thì đọc lại tốc độ hiện tại.
     */
    private fun adjustSpeechRate(direction: Int) {
        val next = SpeechRate.step(settings.speechRate, direction)
        settings.speechRate = next
        speech.updateVoice(next, settings.pitch)
        speech.speak(t(S.MSG_RATE_SET, SpeechRate.percent(next)), true)
    }

    /**
     * Phát/tạm dừng nhạc hoặc video bằng phím media chuẩn (giống nút trên tai nghe): gửi một cặp nhấn/nhả KEYCODE_MEDIA_PLAY_PAUSE tới
     * ứng dụng đang giữ phiên phát. Không cần quyền nào; không có phiên phát thì không có gì xảy ra.
     */
    private fun mediaPlayPause() {
        val audio = service.getSystemService(AudioManager::class.java) ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    /** Chuyển sang điều khiển đọc kế/trước theo vòng tròn và đọc tên điều khiển mới. */
    private fun cycleControl(forward: Boolean) {
        val control = navigator.cycleReadingControl(forward)
        speech.speak(t(S.MSG_CONTROL_SET, control.label), true)
    }

    /** Sao chép đơn vị đang đọc; chưa chọn đơn vị nào thì sao chép cả phần tử (giống nút "Sao chép chữ"). */
    private fun copyUnit() {
        val unit = navigator.currentUnit()
        if (unit == null) {
            copyFocused()
            return
        }
        if (putOnClipboard(unit)) speech.speak(t(S.MSG_UNIT_COPIED, unit), true)
    }

    /** Bật/tắt tiêu điểm chi tiết; phiên đọc áp cờ hệ thống khi cài đặt đổi (xem ReaderSession). */
    /** Xoay vòng: một công cụ ↔ hai công cụ. Cài đặt đổi thì dịch vụ tự áp ngay. */
    private fun cycleVoiceMode() {
        val next = settings.voiceMode.next()
        settings.voiceMode = next
        // Áp NGAY (không chờ callback cài đặt) để câu báo đọc bằng đúng công cụ của chế độ mới và không bị cắt giữa chừng.
        speech.configure(next, settings.secondEngine, settings.secondLanguage, settings.voiceSplit, settings.firstLanguage)
        val label = when (next) {
            com.example.vnscreenreader.core.VoiceMode.SINGLE -> t(S.VO2_MODE_SINGLE)
            com.example.vnscreenreader.core.VoiceMode.BOTH -> t(S.VO2_MODE_BOTH)
        }
        speech.speak(t(S.MSG_VOICE_MODE, label), true)
    }

    private fun toggleDetailedFocus() {
        val next = !settings.detailedFocus
        settings.detailedFocus = next
        speech.speak(if (next) t(S.MSG_DETAILED_ON) else t(S.MSG_DETAILED_OFF), true)
    }

    private fun speakClear(result: FocusNavigator.ClearResult) {
        speech.speak(
            when (result) {
                FocusNavigator.ClearResult.DONE -> t(S.MSG_CLEAR_DONE)
                FocusNavigator.ClearResult.EMPTY -> t(S.MSG_CLEAR_EMPTY)
                FocusNavigator.ClearResult.NO_SELECTION -> t(S.MSG_NOTHING_SELECTED)
                FocusNavigator.ClearResult.NOT_EDITABLE -> t(S.MSG_NOT_EDITABLE)
                FocusNavigator.ClearResult.PASSWORD -> t(S.MSG_PASSWORD_NO_ACTION)
                FocusNavigator.ClearResult.FAILED -> t(S.MSG_CLEAR_FAILED)
                FocusNavigator.ClearResult.RESTORED -> t(S.MSG_RESTORE_DONE)
                FocusNavigator.ClearResult.NOTHING_TO_RESTORE -> t(S.MSG_RESTORE_NONE)
                FocusNavigator.ClearResult.NOT_EMPTY -> t(S.MSG_RESTORE_NOT_EMPTY)
            },
            true,
        )
    }

    private fun editText(op: EditOp) {
        val result = navigator.editText(op)
        speech.speak(
            when (result) {
                FocusNavigator.EditResult.DONE -> when (op) {
                    EditOp.PASTE -> t(S.MSG_PASTED)
                    EditOp.CUT -> t(S.MSG_CUT_DONE)
                    EditOp.SELECT_ALL -> t(S.MSG_SELECTED_ALL)
                    EditOp.CURSOR_START -> t(S.MSG_CURSOR_START)
                    EditOp.CURSOR_END -> t(S.MSG_CURSOR_END)
                    EditOp.SELECT_TO_START -> t(S.MSG_SELECTED_TO_START)
                    EditOp.SELECT_TO_END -> t(S.MSG_SELECTED_TO_END)
                }
                FocusNavigator.EditResult.NO_SELECTION -> t(S.MSG_NOTHING_SELECTED)
                FocusNavigator.EditResult.NOT_EDITABLE -> t(S.MSG_NOT_EDITABLE)
                FocusNavigator.EditResult.PASSWORD -> t(S.MSG_PASSWORD_NO_ACTION)
                FocusNavigator.EditResult.FAILED -> t(S.MSG_ACTION_FAILED)
            },
            true,
        )
    }

    private fun putOnClipboard(text: String): Boolean {
        val clipboard = service.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            speech.speak(t(S.MSG_CLIPBOARD_UNAVAILABLE), true)
            return false
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("", text))
        return true
    }

    private fun copyFocused() {
        navigator.focusedText(preferSelection = true).fold(
            onSuccess = { text -> if (putOnClipboard(text)) speech.speak(t(S.MSG_COPIED), true) },
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
