package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.content.pm.PackageManager
import com.example.vnscreenreader.ai.NotificationInfo
import com.example.vnscreenreader.ai.NotificationSummarizer
import com.example.vnscreenreader.ai.ScreenCapture
import com.example.vnscreenreader.ai.mlkit.MlKitImageLabeler
import com.example.vnscreenreader.ai.mlkit.MlKitTextRecognizer
import com.example.vnscreenreader.core.CustomLabelStore
import com.example.vnscreenreader.core.EventDeduplicator
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.SpeechEngine
import com.example.vnscreenreader.core.SpeechTextBuilder
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureAnalyzer
import com.example.vnscreenreader.gesture.GestureMappingStore
import com.example.vnscreenreader.core.AutoOcrPolicy
import com.example.vnscreenreader.core.GestureTestLogic
import com.example.vnscreenreader.core.GestureTestMode
import com.example.vnscreenreader.menu.AnnouncementGate
import com.example.vnscreenreader.menu.MenuItem
import com.example.vnscreenreader.menu.MenuKind
import com.example.vnscreenreader.menu.MenuState
import com.example.vnscreenreader.menu.Menus
import com.example.vnscreenreader.node.NodeCollector
import com.example.vnscreenreader.node.toSnapshot
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.plugin.PluginRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/**
 * Toàn bộ tài nguyên gắn với MỘT lần kết nối dịch vụ: TTS, overlay, luồng nền, hàng đợi sự kiện.
 *
 * Kiến trúc: callback hệ thống (luồng chính) chỉ trích dữ liệu nhẹ rồi đẩy vào Channel; mọi xử lý nặng
 * (duyệt cây, TTS, plugin) chạy tuần tự trên một luồng nền duy nhất (`limitedParallelism(1)`).
 *
 * Tách khỏi [ScreenReaderService] (Single Responsibility): service chỉ giữ vòng đời, còn ở đây không có
 * trường nullable/@Volatile rải rác - tạo ở `onServiceConnected`, [close] một lần ở `onUnbind/onDestroy`.
 */
internal class ReaderSession(private val service: AccessibilityService) {

    private val settings = SettingsRepository(service)
    private val gestureStore = GestureMappingStore(service)
    private val customLabels = CustomLabelStore(service)
    private val feedback = DeviceFeedback(service, settings)
    private val speech = SpeechEngine(service, settings.speechRate, settings.pitch, settings.ttsEngine)
    private val overlay = HighlightOverlay(service)
    private val navigator = FocusNavigator(service, overlay, customLabels, feedback) { text, interrupt -> speech.speak(text, interrupt) }
    private val ai = AiAssistant(
        capture = ScreenCapture(service),
        overlay = overlay,
        navigator = navigator,
        speech = speech,
        recognizerFactory = { script -> MlKitTextRecognizer(script) },
        labelerFactory = { MlKitImageLabeler() },
    )
    private val notificationSummarizer = NotificationSummarizer()
    init {
        navigator.onFocused = { node -> onElementFocused(node) }
    }
    private val dispatcher = ActionDispatcher(
        service = service,
        navigator = navigator,
        registryProvider = { PluginRegistry(service.applicationContext) },
        speech = speech,
        ai = ai,
        settings = settings,
        openMenu = { kind -> openMenu(kind) },
    )

    private val events = Channel<ReaderEvent>(EVENT_BUFFER, BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val closed = AtomicBoolean(false)

    @Volatile private var mapping = gestureStore.load()

    /** Trình đơn đang mở (null = không có). Chỉ đọc/ghi trên luồng xử lý duy nhất của [scope]. */
    private var menu: MenuState? = null
    private val menuGate = AnnouncementGate()

    // Giữ tham chiếu mạnh vì SharedPreferences chỉ giữ listener bằng weak reference.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        speech.updateVoice(settings.speechRate, settings.pitch)
        speech.changeEngine(settings.ttsEngine) // không làm gì nếu engine không đổi
    }
    private val gestureListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mapping = gestureStore.load()
    }

    /** Gọi trên luồng chính khi cấu hình (ngôn ngữ) đổi. */
    fun onLocaleChanged() {
        speech.updateLanguage()
    }

    /**
     * Tự đọc chữ trong ảnh không nhãn (công tắc mặc định TẮT). Điều kiện và chống lặp nằm ở [AutoOcrPolicy]; việc
     * hủy tác vụ cũ khi chọn phần tử khác nằm ở [AiAssistant.onFocusChanged].
     */
    private fun onElementFocused(node: AccessibilityNodeInfo) {
        val enabled = settings.autoOcrUnlabeled
        if (!enabled) {
            ai.cancelAuto()
            return
        }
        val snapshot = node.toSnapshot()
        val eligible = AutoOcrPolicy.eligible(
            enabled = true,
            node = snapshot,
            hasCustomLabel = !customLabels.find(snapshot.packageName, snapshot.viewId).isNullOrBlank(),
            hasMergedChildLabel = NodeCollector.mergedLabel(node).isNotBlank(),
        )
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        ai.onFocusChanged(
            node = node,
            eligible = eligible,
            windowKey = "${snapshot.packageName}#${node.windowId}",
            elementKey = "${snapshot.viewId}|$bounds",
        )
    }

    /** Gọi trên luồng chính. */
    fun start() {
        overlay.attach()
        scope.launch { processEvents() }
        ScreenSearchBridge.register { query ->
            // Chờ cửa sổ ứng dụng quay lại (màn hình nhập từ khóa vừa đóng) rồi mới tìm.
            scope.launch {
                delay(SEARCH_RESUME_DELAY_MS)
                runSafely("tìm chữ") { navigator.search(query) }
            }
        }
        settings.register(settingsListener)
        gestureStore.register(gestureListener)
        speech.speak(t(S.MSG_SERVICE_ON))
    }

    /** Chạy trên luồng chính: chỉ lọc loại sự kiện và trích dữ liệu, không xử lý nặng. */
    fun offer(event: AccessibilityEvent) {
        if (event.eventType !in HANDLED_EVENT_TYPES) return
        try {
            events.trySend(ReaderEvent.from(event))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không trích được sự kiện ${event.eventType}", e)
        }
    }

    /** Trả về true để báo đã xử lý cử chỉ (kể cả khi được gán t(S.GA_NONE)). */
    fun handleGesture(gestureId: Int): Boolean {
        val gesture = GestureAnalyzer.analyze(gestureId)
        // Chế độ "Kiểm tra cử chỉ": chỉ đọc tên cử chỉ hệ thống gửi tới, KHÔNG thực hiện chức năng đã gán.
        if (GestureTestMode.active) {
            val label = gesture?.label ?: t(S.GT_UNKNOWN, gestureId)
            GestureTestLogic.announcement(true, label)?.let { speech.speak(it, true) }
            return true
        }
        if (gesture == null) return false
        val action = mapping.actionFor(gesture)
        if (action == GestureAction.NONE) return true

        speech.stop() // ngắt lời ngay lập tức, không chờ luồng nền
        scope.launch {
            runSafely("thực hiện $action") {
                if (menu != null) handleMenuAction(action) else dispatcher.dispatch(action)
            }
        }
        return true
    }

    fun interrupt() = speech.stop()

    /** Idempotent. Gọi trên luồng chính (overlay phải gỡ trên luồng chính). */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        ScreenSearchBridge.clear()
        settings.unregister(settingsListener)
        gestureStore.unregister(gestureListener)
        events.close()
        scope.cancel()
        ai.close()
        feedback.close()
        customLabels.close()
        overlay.detach()
        speech.shutdown()
    }

    private suspend fun processEvents() {
        val deduplicator = EventDeduplicator()
        for (event in events) {
            runSafely("xử lý sự kiện ${event.type}") {
                if (deduplicator.shouldProcess(event.dedupKey(), event.timeMs)) handleEvent(event)
            }
        }
    }

    /** Mở trình đơn và đọc phần mở đầu. Gọi trên luồng xử lý. */
    private fun openMenu(kind: MenuKind) {
        val items = when (kind) {
            MenuKind.GLOBAL -> Menus.global()
            MenuKind.LOCAL -> {
                if (navigator.current() == null) {
                    speech.speak(t(S.MSG_NOTHING_SELECTED), true)
                    return
                }
                Menus.local(navigator.customActions(), editable = navigator.isCurrentEditable())
            }
            else -> return // trình đơn con chỉ mở từ trình đơn cha
        }
        menuGate.reset()
        announce(MenuState.open(kind, items).also { menu = it }.opening())
    }

    /** Đọc một thông báo của trình đơn và ghi nhận để lời nhắc không bị đọc lặp liên tiếp. */
    private fun announce(text: String) {
        menuGate.record(text)
        speech.speak(text, true)
    }

    /**
     * Khi trình đơn đang mở, cử chỉ được hiểu theo CHỨC NĂNG đã gán: chuyển mục (phần tử kế/trước), thực hiện
     * (nhấn), đóng (quay lại/dừng đọc/mở lại trình đơn). Giúp người dùng giữ nguyên thói quen cử chỉ của mình.
     */
    private fun handleMenuAction(action: GestureAction) {
        val current = menu ?: return
        when (action) {
            GestureAction.NEXT_ELEMENT -> advance(current.next())
            GestureAction.PREVIOUS_ELEMENT -> advance(current.previous())
            GestureAction.ACTIVATE -> {
                val item = current.current
                if (item == null) {
                    if (menuGate.shouldSpeak(t(S.MSG_MENU_PICK_FIRST))) speech.speak(t(S.MSG_MENU_PICK_FIRST), true)
                    return
                }
                when {
                    item.isBack -> returnToParent(current)
                    item.submenu != null -> openSubmenu(current, item.submenu)
                    else -> {
                        menu = null
                        execute(item)
                    }
                }
            }
            // Quay lại: từ trình đơn con thì về trình đơn cha, từ cấp đầu thì đóng hẳn.
            GestureAction.BACK -> if (current.parent != null) returnToParent(current) else closeMenu()
            GestureAction.STOP_SPEECH, GestureAction.OPEN_GLOBAL_MENU, GestureAction.OPEN_LOCAL_MENU -> closeMenu()
            // Cử chỉ không dùng được trong trình đơn: nhắc ngắn MỘT lần, tắt hướng dẫn thì im lặng.
            else -> if (settings.speakHints) {
                val hint = t(S.MSG_MENU_HINT)
                if (menuGate.shouldSpeak(hint)) speech.speak(hint, true)
            }
        }
    }

    private fun advance(next: MenuState) {
        menu = next
        announce(next.describeCurrent(settings.speakHints))
    }

    private fun closeMenu() {
        menu = null
        menuGate.reset()
        speech.speak(t(S.MSG_MENU_CLOSED), true)
    }

    private fun openSubmenu(parent: MenuState, kind: MenuKind) {
        val items = when (kind) {
            MenuKind.GLOBAL_MORE -> Menus.globalMore()
            MenuKind.LOCAL_EDIT -> Menus.localEdit()
            else -> return
        }
        val child = parent.child(kind, items)
        menu = child
        announce(child.opening())
    }

    private fun returnToParent(state: MenuState) {
        val parent = state.back() ?: return closeMenu()
        menu = parent
        announce(parent.describeCurrent(settings.speakHints))
    }

    private fun execute(item: MenuItem) {
        item.action?.let { dispatcher.dispatch(it) }
        item.customActionId?.let { id ->
            if (!navigator.performCustomAction(id)) speech.speak(t(S.MSG_ACTION_FAILED), true)
        }
    }

    private fun handleEvent(event: ReaderEvent) {
        when (event.type) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED ->
                event.source?.let { navigator.focusOn(it) }

            // Khám phá bằng cảm ứng: ngón tay chạm/lướt tới đâu thì chọn và đọc phần tử ở đó.
            // Cũng đặt phần tử hiện tại, nên "chạm hai lần" (ACTIVATE) nhấn đúng phần tử vừa nghe.
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER ->
                if (menu == null) event.source?.let { source ->
                    NodeCollector.findHoverTarget(source)?.let { navigator.focusOn(it) }
                }

            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                event.source?.let { node ->
                    navigator.invalidateCache()
                    navigator.highlight(node)
                    speech.speak(t(S.MSG_TAPPED, navigator.describe(node)), false)
                }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                menu = null // cửa sổ đổi: trình đơn (nhất là của phần tử) đã lỗi thời
                navigator.reset()
                val title = event.text.trim()
                if (title.isNotEmpty()) speech.speak(title, true)
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> handleNotification(event)

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                navigator.invalidateCache()
                SpeechTextBuilder.describeTextChange(
                    fullText = event.text,
                    fromIndex = event.fromIndex,
                    addedCount = event.addedCount,
                    removedCount = event.removedCount,
                    isPassword = event.isPassword,
                    speakPasswordChars = settings.speakPasswords,
                )?.let { speech.speak(it, true) }
            }
        }
    }

    /** Đọc thông báo mới đã được tóm tắt; bỏ qua thông báo của chính ứng dụng này và khi người dùng tắt. */
    private fun handleNotification(event: ReaderEvent) {
        if (!settings.readNotifications) return
        val pkg = event.packageName ?: return
        if (pkg == service.packageName) return
        val summary = notificationSummarizer.summarize(NotificationInfo(pkg, appLabel(pkg), event.texts), event.timeMs)
        if (summary != null) speech.speak(summary, false)
    }

    /** Tên hiển thị của ứng dụng; nếu hệ thống không cho tra (package visibility) thì dùng đoạn cuối tên gói. */
    private fun appLabel(pkg: String): String = try {
        val pm = service.packageManager
        val info = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        pm.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg.substringAfterLast('.')
    }

    /** Một lỗi runtime trong một tác vụ không được làm sập luồng xử lý; việc hủy coroutine vẫn được tôn trọng. */
    private inline fun runSafely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(TAG, "Lỗi khi $what", e)
        }
    }

    private companion object {
        const val TAG = "ReaderSession"
        const val EVENT_BUFFER = 32
        const val SEARCH_RESUME_DELAY_MS = 500L
        val HANDLED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
        )
    }
}
