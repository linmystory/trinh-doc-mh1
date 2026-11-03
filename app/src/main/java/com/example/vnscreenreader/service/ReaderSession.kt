package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import com.example.vnscreenreader.CoroutineGuard
import com.example.vnscreenreader.CrashLogger
import java.util.Locale
import com.example.vnscreenreader.core.ActionHints
import com.example.vnscreenreader.core.AutoOcrPolicy
import com.example.vnscreenreader.core.LabelResolver
import com.example.vnscreenreader.core.NodeSnapshot
import com.example.vnscreenreader.core.EmojiText
import com.example.vnscreenreader.core.SpeechPlan
import com.example.vnscreenreader.core.GestureTestLogic
import com.example.vnscreenreader.core.GestureTestMode
import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.menu.AnnouncementGate
import com.example.vnscreenreader.menu.GestureRoute
import com.example.vnscreenreader.menu.GestureRouter
import kotlinx.coroutines.Job
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
    init {
        speech.planner = { text -> if (settings.speakEmoji) EmojiText.plan(text) else SpeechPlan.single(text) }
    }
    private val navigator = FocusNavigator(service, overlay, customLabels, feedback) { text, interrupt -> speech.speak(text, interrupt) }
    private val ai = AiAssistant(
        capture = ScreenCapture(service),
        overlay = overlay,
        navigator = navigator,
        speech = speech,
        recognizerFactory = { script -> MlKitTextRecognizer(script) },
        labelerFactory = { MlKitImageLabeler() },
        maskPasswords = { !settings.speakPasswords },
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + CoroutineGuard.handler("session"))
    private val closed = AtomicBoolean(false)

    @Volatile private var mapping = gestureStore.load()

    /**
     * Trình đơn đang mở (null = không có). Chỉ GHI trên luồng xử lý duy nhất của [scope]; luồng chính chỉ đọc nhanh
     * để quyết định ngắt lời đọc (xem [handleGesture]), nên cần @Volatile.
     */
    @Volatile private var menu: MenuState? = null
    private var menuTimeout: Job? = null
    private val menuGate = AnnouncementGate()

    // Giữ tham chiếu mạnh vì SharedPreferences chỉ giữ listener bằng weak reference.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        speech.updateVoice(settings.speechRate, settings.pitch)
        applyDetailedFocus()
        applyVerbosity()
        speech.changeEngine(settings.ttsEngine) // không làm gì nếu engine không đổi
        applyVoiceMode()
    }
    private val gestureListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mapping = gestureStore.load()
    }

    /**
     * Áp công tắc "tiêu điểm chi tiết": bật/tắt cờ FLAG_INCLUDE_NOT_IMPORTANT_VIEWS của dịch vụ lúc đang chạy (không
     * cần cài lại) để hệ thống đưa cả view bị ứng dụng đánh dấu "không quan trọng" vào cây trợ năng.
     */
    private fun applyDetailedFocus() {
        val on = settings.detailedFocus
        navigator.detailedFocus = on
        val info = service.serviceInfo ?: return
        val flags = if (on) info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        else info.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
        if (flags != info.flags) {
            info.flags = flags
            service.serviceInfo = info
        }
    }

    /** Áp chế độ công cụ đọc (một/hai công cụ). Chạy trên luồng chính (SharedPreferences và start() đều ở đó). */
    private fun applyVoiceMode() {
        speech.configure(settings.voiceMode, settings.secondEngine, settings.secondLanguage, settings.voiceSplit, settings.firstLanguage)
    }

    private fun applyVerbosity() {
        com.example.vnscreenreader.core.Diag.enabled = settings.diagnosticsEnabled
        navigator.verbosity = settings.verbosity
    }

    /** Ngôn ngữ đã nhắc "thiếu giọng" trong phiên này (chỉ nhắc một lần cho mỗi ngôn ngữ). Chỉ dùng trên luồng chính. */
    private var voiceNoticeLanguage: String? = null

    /**
     * Engine TTS báo có/không có giọng cho ngôn ngữ ứng dụng (luồng chính). Thiếu giọng thì người dùng nghe sai ngôn
     * ngữ mà không biết lý do: lưu cờ cho màn hình Giọng đọc (có nút cài dữ liệu giọng) và nhắc bằng lời một lần.
     */
    private fun onVoiceStatus(missing: Boolean, locale: Locale) {
        if (settings.ttsVoiceMissing != missing) settings.ttsVoiceMissing = missing
        if (!missing) {
            voiceNoticeLanguage = null
            return
        }
        val tag = locale.toLanguageTag()
        if (voiceNoticeLanguage == tag) return
        voiceNoticeLanguage = tag
        scope.launch {
            delay(VOICE_NOTICE_DELAY_MS) // sau lời chào "dịch vụ đã bật"
            speech.speak(t(S.MSG_TTS_VOICE_MISSING, locale.getDisplayLanguage(locale)), false)
        }
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
        val snapshot = node.toSnapshot()
        announceImageHint(snapshot)
        announceActionHint(node)
        val enabled = settings.autoOcrUnlabeled
        if (!enabled) {
            ai.cancelAuto()
            return
        }
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

    /**
     * Gợi ý thao tác ("Chạm hai lần để kích hoạt"), công tắc RIÊNG [SettingsRepository.speakActionHints] (mặc định tắt),
     * độc lập với đọc hướng dẫn. Xếp hàng SAU lời mô tả phần tử và không ngắt: vuốt tiếp là gợi ý bị bỏ, nội dung chính
     * không bao giờ bị chậm hay cắt. Tên cử chỉ lấy theo cấu hình hiện tại nên vẫn đúng khi người dùng đã đổi cử chỉ.
     */
    private fun announceActionHint(node: AccessibilityNodeInfo) {
        if (!settings.speakActionHints) return
        val gesture = mapping.gesturesFor(GestureAction.ACTIVATE).firstOrNull() ?: return
        val kind = navigator.actionHintKind(node) ?: return
        speech.speak(ActionHints.sentence(kind, gesture.label), false)
    }

    /**
     * Gợi ý ngắn (CHỈ khi bật đọc hướng dẫn) cho ảnh không nhãn: dùng trình đơn phần tử để mô tả ảnh hoặc đọc chữ trong
     * ảnh. Xếp hàng SAU lời mô tả phần tử (không ngắt), người dùng thành thạo tắt công tắc là không nghe thấy gì thêm.
     */
    private fun announceImageHint(snapshot: NodeSnapshot) {
        if (!settings.speakHints || snapshot.isPassword || snapshot.isEditable) return
        if (!AutoOcrPolicy.isAnyImage(snapshot.className) || LabelResolver.hasAppLabel(snapshot)) return
        speech.speak(t(S.IMG_HINT), false)
    }

    /** Gọi trên luồng chính. */
    fun start() {
        com.example.vnscreenreader.core.Diag.init(java.io.File(service.filesDir, "diag.log"))
        com.example.vnscreenreader.core.Diag.log("START") { "Android ${android.os.Build.VERSION.SDK_INT} ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" }
        speech.onVoiceStatus = { missing, locale -> onVoiceStatus(missing, locale) }
        overlay.attach()
        applyDetailedFocus()
        applyVerbosity()
        applyVoiceMode()
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
        // Nội dung đổi / đã cuộn xảy ra dồn dập: chỉ đánh dấu bộ đệm cũ (không qua hàng đợi, không làm tràn sự kiện quan trọng).
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            navigator.markContentChanged()
            return
        }
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
            GestureTestMode.touch()
            val label = gesture?.label ?: t(S.GT_UNKNOWN, gestureId)
            GestureTestLogic.announcement(true, label)?.let { speech.speak(it, true) }
            // Chạm hai lần vẫn bấm được nút của CHÍNH màn hình kiểm tra (để thoát); không chạm tới ứng dụng khác.
            if (gesture == Gesture.DOUBLE_TAP) scope.launch { runSafely("thoát kiểm tra cử chỉ") { activateOwnScreen() } }
            return true
        }
        // Quyết định tức thời trên luồng chính: chỉ để báo "đã xử lý" cho hệ thống và ngắt lời đang đọc. Hành động thật
        // được tính lại trên luồng xử lý (bên dưới) vì cử chỉ trước đó có thể vừa mở hoặc đóng trình đơn.
        val preview = GestureRouter.route(gesture, mapping, menu != null)
        com.example.vnscreenreader.core.Diag.log("GESTURE") { "$gesture -> $preview" }
        when (preview) {
            GestureRoute.Unhandled -> return false
            GestureRoute.Swallowed -> return true
            is GestureRoute.InMenu, is GestureRoute.OnScreen -> Unit
        }
        navigator.cancelLongScroll() // cử chỉ mới = dừng vòng cuộn dài đang chạy (cuộn tới đầu/cuối, tìm ngoài màn hình)

        // Trong trình đơn, cử chỉ không dùng được không được ngắt câu đang đọc.
        if (GestureRouter.interruptsSpeech(preview)) speech.stop()
        scope.launch {
            runSafely("thực hiện cử chỉ $gesture") {
                when (val route = GestureRouter.route(gesture, mapping, menu != null)) {
                    is GestureRoute.InMenu -> handleMenuAction(route.action)
                    is GestureRoute.OnScreen -> dispatcher.dispatch(route.action)
                    GestureRoute.Unhandled, GestureRoute.Swallowed -> Unit
                }
            }
        }
        return true
    }

    /** Nhấn phần tử đang chọn nếu nó thuộc màn hình của chính ứng dụng này. */
    private fun activateOwnScreen() {
        if (navigator.current()?.packageName?.toString() == service.packageName) navigator.activate()
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
        speech.onVoiceStatus = null
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
                // Làm mới phần tử trước khi dựng trình đơn: nút cũ có thể cho hành động của phần tử đã biến mất.
                if (navigator.currentFresh() == null) return
                Menus.local(navigator.customActions(), editable = navigator.isCurrentEditable(), imageLike = navigator.isCurrentImage())
            }
            else -> return // trình đơn con chỉ mở từ trình đơn cha
        }
        menuGate.reset()
        // Vào thẳng mục đầu tiên (không đọc tiêu đề và số mục trước): người dùng nghe ngay thứ mình cần chọn.
        val first = MenuState.open(kind, items).next().also { menu = it }
        announce(first.describeCurrent(settings.speakHints))
        armMenuTimeout()
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
        armMenuTimeout()
        announce(next.describeCurrent(settings.speakHints))
    }

    /** Tự đóng trình đơn nếu bỏ mặc, để không bị kẹt ở chế độ trình đơn. */
    private fun armMenuTimeout() {
        menuTimeout?.cancel()
        menuTimeout = scope.launch {
            delay(MENU_IDLE_TIMEOUT_MS)
            if (menu != null) {
                menu = null
                menuGate.reset()
                speech.speak(t(S.MSG_MENU_CLOSED), true)
            }
        }
    }

    private fun closeMenu() {
        menuTimeout?.cancel()
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
        val child = parent.child(kind, items).next() // vào thẳng mục đầu của trình đơn con
        menu = child
        armMenuTimeout()
        announce(child.describeCurrent(settings.speakHints))
    }

    private fun returnToParent(state: MenuState) {
        val parent = state.back() ?: return closeMenu()
        menu = parent
        armMenuTimeout()
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
            // Trình đơn đang mở: focus do ứng dụng/bàn phím tự đặt không được đổi phần tử chọn hay ngắt lời trình đơn.
            AccessibilityEvent.TYPE_VIEW_FOCUSED ->
                if (menu == null) event.source?.let { navigator.focusOn(it) }

            // Khám phá bằng cảm ứng: ngón tay chạm/lướt tới đâu thì chọn và đọc phần tử ở đó.
            // Cũng đặt phần tử hiện tại, nên "chạm hai lần" (ACTIVATE) nhấn đúng phần tử vừa nghe.
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER ->
                if (menu == null) event.source?.let { source ->
                    NodeCollector.findHoverTarget(source)?.let { navigator.focusOn(it) }
                }

            // Tiêu điểm trợ năng do ứng dụng/trình duyệt đặt: đồng bộ con trỏ, không đọc lại.
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED ->
                if (menu == null) event.source?.let { navigator.onExternalFocus(it) }

            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                event.source?.let { node ->
                    navigator.invalidateCache()
                    navigator.highlight(node)
                    speech.speak(t(S.MSG_TAPPED, navigator.describe(node)), false)
                }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val menuWasOpen = menu != null
                menu = null // cửa sổ đổi: trình đơn (nhất là của phần tử) đã lỗi thời
                ai.cancelAll() // và kết quả AI của màn hình cũ sẽ đọc sai
                ai.onWindowChanged() // màn hình mới: các ảnh đã tự đọc được phép đọc lại
                navigator.onWindowChanged()
                val title = event.text.trim()
                // Trình đơn đóng vì hệ thống đổi cửa sổ: báo rõ để người dùng không tưởng nó còn mở.
                if (menuWasOpen) speech.speak(t(S.MSG_MENU_CLOSED), true)
                if (title.isNotEmpty()) speech.speak(title, !menuWasOpen)
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
            CrashLogger.recordNonFatal(what, e)
        } catch (e: VirtualMachineError) {
            // Hết bộ nhớ / tràn ngăn xếp: ghi lại và báo người dùng thay vì để cả tiến trình sập (trình đọc sẽ chết hẳn).
            Log.e(TAG, "Lỗi nặng khi $what", e)
            CrashLogger.recordNonFatal(what, e)
            speech.speak(t(S.MSG_ACTION_FAILED), true)
        }
    }

    private companion object {
        const val TAG = "ReaderSession"
        const val EVENT_BUFFER = 32
        const val MENU_IDLE_TIMEOUT_MS = 30_000L
        const val SEARCH_RESUME_DELAY_MS = 500L
        const val VOICE_NOTICE_DELAY_MS = 2_500L
        val HANDLED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
        )
    }
}
