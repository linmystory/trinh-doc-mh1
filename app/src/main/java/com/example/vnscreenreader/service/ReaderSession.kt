package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.app.NotificationManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.vnscreenreader.core.CallRingPolicy
import com.example.vnscreenreader.core.CallerText
import com.example.vnscreenreader.core.LocationText
import com.example.vnscreenreader.ai.NotificationInfo
import com.example.vnscreenreader.ai.NotificationSummarizer
import com.example.vnscreenreader.ai.ScreenCapture
import com.example.vnscreenreader.ai.mlkit.MlKitImageLabeler
import com.example.vnscreenreader.ai.mlkit.MlKitTextRecognizer
import com.example.vnscreenreader.core.CustomLabelStore
import com.example.vnscreenreader.core.CoalescingReceiver
import com.example.vnscreenreader.core.DeviceStorage
import com.example.vnscreenreader.core.DictationGate
import com.example.vnscreenreader.core.DictationReadback
import com.example.vnscreenreader.core.Diag
import com.example.vnscreenreader.core.EventDeduplicator
import com.example.vnscreenreader.core.HoverEchoGuard
import com.example.vnscreenreader.core.HoverOutcome
import com.example.vnscreenreader.core.HoverStats
import com.example.vnscreenreader.core.MicRecording
import com.example.vnscreenreader.core.ScreenAnnouncement
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.SpeechEngine
import com.example.vnscreenreader.core.SpeechFormats
import com.example.vnscreenreader.core.SpeechShield
import com.example.vnscreenreader.core.SpeechTextBuilder
import com.example.vnscreenreader.core.StoragePolicy
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureAnalyzer
import com.example.vnscreenreader.gesture.GestureMappingStore
import com.example.vnscreenreader.CoroutineGuard
import com.example.vnscreenreader.CrashLogger
import java.time.LocalTime
import java.util.Locale
import com.example.vnscreenreader.core.ActionHints
import com.example.vnscreenreader.core.KeywordList
import com.example.vnscreenreader.core.LiftToTypePolicy
import com.example.vnscreenreader.core.ListRange
import com.example.vnscreenreader.core.LiveRegionGate
import com.example.vnscreenreader.core.PackageSet
import com.example.vnscreenreader.core.TypingEcho
import com.example.vnscreenreader.core.VolumeStreamPolicy
import com.example.vnscreenreader.core.WindowFocusMemory
import com.example.vnscreenreader.core.SelectionSpeech
import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.input.HeadsetKeyNav
import com.example.vnscreenreader.input.KeyShortcuts
import com.example.vnscreenreader.input.VolumeKeyNav
import com.example.vnscreenreader.core.NotificationContext
import com.example.vnscreenreader.core.NotificationRules
import com.example.vnscreenreader.core.QuietRules
import com.example.vnscreenreader.gesture.AppGestureOverrides
import com.example.vnscreenreader.gesture.AppGestureStore
import com.example.vnscreenreader.gesture.GestureMapping
import com.example.vnscreenreader.core.AutoOcrPolicy
import com.example.vnscreenreader.core.LabelResolver
import com.example.vnscreenreader.core.NodeSnapshot
import com.example.vnscreenreader.core.EmojiText
import com.example.vnscreenreader.core.SpeechPlan
import com.example.vnscreenreader.menu.AnnouncementGate
import com.example.vnscreenreader.menu.GestureRoute
import com.example.vnscreenreader.menu.GestureRouter
import com.example.vnscreenreader.menu.MenuGestures
import kotlinx.coroutines.Job
import com.example.vnscreenreader.menu.MenuItem
import com.example.vnscreenreader.menu.MenuKind
import com.example.vnscreenreader.menu.MenuLayoutStore
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
    private val menuLayoutStore = MenuLayoutStore(service)
    private val customLabels = CustomLabelStore(service)
    private val feedback = DeviceFeedback(service, settings)
    private val speech = SpeechEngine(service, settings.speechRate, settings.pitch, settings.ttsEngine)
    private val overlay = HighlightOverlay(service)

    // Trình đơn vẽ thành danh sách chạm được: chạm/lướt tới hàng nào thì chọn và đọc hàng đó. Callback chạy trên luồng chính.
    private val menuOverlay = MenuOverlay(service) { row -> onMenuRowTouched(row) }
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
        toggleKeysPause = { toggleKeysPause() },
        toggleVolumeForApp = { toggleVolumeForApp() },
        adjustVolume = { adjustVolume(it) },
        sayLocation = { sayLocation() },
    )

    private val events = Channel<ReaderEvent>(EVENT_BUFFER, BufferOverflow.DROP_OLDEST)

    // Nội dung đổi (live region, tiêu đề khung) đi kênh riêng, nhỏ: dồn dập cũng không làm rơi sự kiện chạm/cử chỉ ở [events].
    private val contentEvents = Channel<ReaderEvent>(CONTENT_BUFFER, BufferOverflow.DROP_OLDEST)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + CoroutineGuard.handler("session"))
    private val closed = AtomicBoolean(false)

    @Volatile private var mapping = gestureStore.load()

    // Cử chỉ riêng theo ứng dụng: gói của cửa sổ ứng dụng đang dùng (null = chính ứng dụng này hoặc chưa rõ -> bản đồ chung).
    private val appGestureStore = AppGestureStore(service)
    @Volatile private var activePackage: String? = null
    private val liftToType = LiftToTypePolicy(clock = { SystemClock.uptimeMillis() })
    private var hoverJob: Job? = null

    // Chạm vào phần tử: chỉ truy cập trên luồng xử lý duy nhất của [scope] (cùng luồng với [handleEvent] và công việc chờ độ trễ chạm).
    private val hoverGuard = HoverEchoGuard()
    private val hoverStats = HoverStats()
    @Volatile private var fingerDownSince = 0L

    // Đọc chính tả bằng giọng nói: chỉ truy cập trên luồng xử lý duy nhất của [scope]; [recordingCallback] (luồng chính) chỉ chuyển dữ liệu sang đó.
    private val dictationGate = DictationGate()
    private val dictationReadback = DictationReadback()
    private var dictationMuted = false
    private var dictationRecheck: Job? = null
    private var recordingCallbackRegistered = false
    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
            guardMain("đổi trạng thái thu âm") { onRecordingsChanged(configs) }
        }
    }

    // Chỉ truy cập trên luồng xử lý duy nhất của [scope].
    private val liveGate = LiveRegionGate()
    private val focusMemory = WindowFocusMemory()
    private var lastWindowKey: String? = null
    private var focusJob: Job? = null
    private var lastCaret = -1
    private var lastTextLength = -1

    // Chỉ truy cập trên luồng chính (onKeyEvent, Handler chính).
    private val keyHandler = Handler(Looper.getMainLooper())

    private val volumeNav = VolumeKeyNav()

    /** Tạm dừng phím cứng + phím âm lượng (Alt+P hoặc cử chỉ). Không lưu: khởi động lại dịch vụ là hoạt động lại. */
    @Volatile private var keysPaused = false
    @Volatile private var pendingRange: ListRange.Range? = null
    private val rangeRunnable = Runnable { guardMain("đọc phạm vi danh sách") { speakPendingRange() } }

    // Hai ngón vuốt là cuộn gốc của Android (dịch vụ không tự chạy scroll): khi ngừng cuộn, bỏ phần tử đang chọn nếu nó đã khuất.
    private val scrollSettled = Runnable {
        guardMain("sau khi cuộn") {
            if (!closed.get()) scope.launch { runSafely("sau khi cuộn") { navigator.releaseIfScrolledAway() } }
        }
    }
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            guardMain("đổi trạng thái màn hình") {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenState(on = false)
                    Intent.ACTION_SCREEN_ON -> onScreenState(on = true)
                }
            }
        }
    }
    private val consumedKeys = HashSet<Int>()
    private var ctrlAlone = false
    private var lastContentOfferMs = 0L
    private val locationReader = LocationReader(service)
    private val callPolicy = CallRingPolicy()
    private var callReceiverRegistered = false
    private val callFallback = Runnable { guardMain("đọc người gọi (hết chờ số)") { handleCallDecision(callPolicy.onWaitExpired()) } }
    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            guardMain("đọc người gọi") {
                val decision = callPolicy.onBroadcast(
                    state = intent.getStringExtra(TelephonyManager.EXTRA_STATE),
                    hasNumberExtra = intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER),
                    number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER),
                    hasNumberPermission = hasPermission(android.Manifest.permission.READ_CALL_LOG),
                )
                handleCallDecision(decision)
            }
        }
    }

    private val headsetNav = HeadsetKeyNav()
    private val headsetLong = Runnable { guardMain("nút tai nghe (giữ)") { headsetNav.onLongPress()?.let { runTrigger(it) } } }
    private val headsetSingle = Runnable { guardMain("nút tai nghe (bấm)") { headsetNav.onSingleTimeout()?.let { runTrigger(it) } } }

    private val volumeLongPress = mapOf(
        VolumeKeyNav.Key.UP to Runnable { guardMain("phím âm lượng (giữ)") { onVolumeLongPress(VolumeKeyNav.Key.UP) } },
        VolumeKeyNav.Key.DOWN to Runnable { guardMain("phím âm lượng (giữ)") { onVolumeLongPress(VolumeKeyNav.Key.DOWN) } },
    )

    /**
     * Trình đơn đang mở (null = không có). Chỉ GHI trên luồng xử lý duy nhất của [scope]; luồng chính chỉ đọc nhanh
     * để quyết định ngắt lời đọc (xem [handleGesture]), nên cần @Volatile.
     */
    @Volatile private var menu: MenuState? = null
    private var menuTimeout: Job? = null
    private val menuGate = AnnouncementGate()

    // Chỉ dùng trên luồng xử lý duy nhất: lần cuối đọc lại mục đang chọn vì bị chạm khi không hiện được danh sách chạm được.
    private var lastMenuTouchSpeakMs = 0L

    // Giữ câu báo màn hình bật/tắt khỏi bị lời tự động của màn hình khóa (tiêu đề cửa sổ, tiêu điểm) cắt ngang; xem [interruptAllowed].
    private val shield = SpeechShield()

    // Giữ tham chiếu mạnh vì SharedPreferences chỉ giữ listener bằng weak reference.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (closed.get()) return@OnSharedPreferenceChangeListener
        guardMain("áp cài đặt") {
            speech.updateVoice(settings.speechRate, settings.pitch)
            applyServiceFlags()
            applyVerbosity()
            navigator.scrollAdjustsSliders = settings.scrollAdjustsSliders
            speech.changeEngine(settings.ttsEngine) // không làm gì nếu engine không đổi
            applyVoiceMode()
            syncCallReceiver()
        }
    }
    private val gestureListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        guardMain("nạp bản đồ cử chỉ") { mapping = gestureStore.load() }
    }

    /**
     * Áp các cờ dịch vụ đặt lúc chạy (không cần cài lại):
     * - Công tắc "tiêu điểm chi tiết": bật/tắt FLAG_INCLUDE_NOT_IMPORTANT_VIEWS để hệ thống đưa cả view bị ứng dụng đánh dấu
     *   "không quan trọng" vào cây trợ năng.
     * - FLAG_REQUEST_2_FINGER_PASSTHROUGH (luôn bật): hai ngón vuốt được chuyển nguyên cho ứng dụng/Android như một ngón vuốt
     *   thay vì bị dịch vụ nuốt làm cử chỉ. Nhờ vậy điều hướng bằng cử chỉ của Android chạy bằng hai ngón như ở các trình đọc
     *   màn hình lớn: hai ngón vuốt lên từ mép dưới = Màn hình chính (giữ = Ứng dụng gần đây), vào từ mép trái/phải = Quay lại,
     *   xuống từ mép trên = Thông báo; còn giữa màn hình thì cuộn nội dung/chuyển trang tại chỗ. Cờ chỉ có tác dụng khi dịch vụ
     *   đã bật FLAG_REQUEST_MULTI_FINGER_GESTURES (đã khai trong accessibility_service_config.xml). Ba ngón vuốt lên từ mép dưới
     *   cũng được chuyển nguyên cho nút hỗ trợ tiếp cận của hệ thống.
     */
    private fun applyServiceFlags() {
        val on = settings.detailedFocus
        navigator.detailedFocus = on
        val info = service.serviceInfo ?: return
        val base = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH
        val flags = if (on) base or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        else base and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
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
        // Máy chưa mở khóa: dữ liệu giọng của engine có thể còn nằm ở bộ nhớ bị khóa nên "thiếu giọng" chưa chắc đúng. Không nhắc, không lưu cờ;
        // [onUserUnlocked] cho engine kiểm tra lại ngay sau khi mở khóa.
        if (missing && !DeviceStorage.isUnlocked(service)) return
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
     * Gọi trên luồng chính khi máy vừa mở khóa lần đầu sau khi khởi động (dịch vụ đã chạy từ lúc còn khóa). Giọng đọc nối lại engine đã chọn
     * nếu lúc khóa phải đọc tạm bằng engine hệ thống, và kiểm tra lại giọng của ngôn ngữ.
     */
    fun onUserUnlocked() {
        Diag.log("UNLOCK") { "user unlocked: reconnect voice if needed" }
        speech.onUserUnlocked()
    }

    /**
     * Theo dõi việc thu âm của MỌI ứng dụng (không cần quyền ghi âm; hệ thống ẩn tên ứng dụng thu, chỉ cho biết nguồn âm và bản thu có bị câm không).
     * Mục đích: khi người dùng đọc chính tả bằng nút micro của bàn phím thì trình đọc im lặng để tiếng đọc không lọt vào micro ([DictationGate]).
     * Gọi trên luồng chính.
     */
    private fun registerRecordingCallback() {
        val audio = service.getSystemService(AudioManager::class.java) ?: return
        try {
            audio.registerAudioRecordingCallback(recordingCallback, keyHandler)
            recordingCallbackRegistered = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không theo dõi được trạng thái thu âm", e)
        }
    }

    private fun unregisterRecordingCallback() {
        if (!recordingCallbackRegistered) return
        recordingCallbackRegistered = false
        try {
            service.getSystemService(AudioManager::class.java)?.unregisterAudioRecordingCallback(recordingCallback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không gỡ được bộ theo dõi thu âm", e)
        }
    }

    /**
     * Danh sách thu âm của MỌI ứng dụng đổi (luồng chính): chỉ rút số nguồn và cờ "bị câm" rồi chuyển sang luồng xử lý, nơi hỏi ngữ cảnh gõ chữ
     * và áp trạng thái IM ([applyRecordings]). Không đọc, không ghi tên ứng dụng thu hay nội dung gì.
     */
    private fun onRecordingsChanged(configs: List<AudioRecordingConfiguration>) {
        if (closed.get()) return
        val recordings = configs.map { MicRecording(source = it.clientAudioSource, silenced = it.isClientSilenced) }
        scope.launch { runSafely("thu âm") { applyRecordings(recordings) } }
    }

    private fun applyRecordings(recordings: List<MicRecording>) {
        val now = SystemClock.uptimeMillis()
        dictationGate.update(recordings, now) { typingContextActive() }
        Diag.log("MIC") {
            "recordings=${recordings.size} counted=${recordings.count { dictationGate.counts(it) }} " +
                "sources=${recordings.joinToString(",") { "${it.source}${if (it.silenced) "s" else ""}" }}"
        }
        refreshDictationMute()
    }

    /** Có bàn phím đang mở (một cửa sổ phương thức nhập). Hỏi hệ thống nên [DictationGate] chỉ gọi khi có bản thu đáng tính. Chỉ chạy trên luồng xử lý. */
    private fun typingContextActive(): Boolean = service.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    /**
     * Áp trạng thái IM hiện tại của [dictationGate] lên giọng đọc và âm báo, rồi hẹn kiểm lại đúng lúc nó có thể đổi theo thời gian (hết đuôi
     * sau khi dừng thu, hoặc van an toàn mở). Bật IM thì dừng ngay câu đang đọc; hết IM thì đọc lại MỘT lần chữ vừa nhập ([DictationReadback]).
     * Chỉ chạy trên luồng xử lý.
     */
    private fun refreshDictationMute() {
        if (closed.get()) return
        val now = SystemClock.uptimeMillis()
        val muted = dictationGate.isMuted(now)
        if (muted != dictationMuted) {
            dictationMuted = muted
            speech.setMuted(muted)
            feedback.muted = muted
            Diag.log("MIC") { if (muted) "mute on" else "mute off" }
            if (muted) dictationReadback.clear() else dictationReadback.take()?.let { speech.speak(it, false) }
        }
        dictationRecheck?.cancel()
        dictationRecheck = dictationGate.nextChangeAt(now)?.let { at ->
            scope.launch {
                delay((at - now).coerceAtLeast(1L))
                runSafely("hết im lặng nhập giọng nói") { refreshDictationMute() }
            }
        }
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
            hasMergedChildLabel = NodeCollector.mergedLabel(node, everything = true).isNotBlank(),
        )
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        ai.onFocusChanged(
            node = node,
            eligible = eligible,
            windowKey = "${snapshot.packageName}#${node.windowId}",
            elementKey = "${snapshot.viewId}|$bounds",
        )
    }

    /** Bản đồ cử chỉ đang hiệu lực: bản đồ chung cộng phần riêng của ứng dụng đang mở (nếu có). */
    private fun effectiveMapping(): GestureMapping {
        val raw = appGestureStore.overridesFor(activePackage) ?: return mapping
        return AppGestureOverrides.apply(mapping, raw)
    }

    /**
     * Gợi ý thao tác ("Chạm hai lần để kích hoạt"), công tắc RIÊNG [SettingsRepository.speakActionHints] (mặc định tắt),
     * độc lập với đọc hướng dẫn. Xếp hàng SAU lời mô tả phần tử và không ngắt: vuốt tiếp là gợi ý bị bỏ, nội dung chính
     * không bao giờ bị chậm hay cắt. Tên cử chỉ lấy theo cấu hình hiện tại nên vẫn đúng khi người dùng đã đổi cử chỉ.
     */
    private fun announceActionHint(node: AccessibilityNodeInfo) {
        if (!settings.speakActionHints) return
        val gesture = effectiveMapping().gesturesFor(GestureAction.ACTIVATE).firstOrNull() ?: return
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

    /** "tên bản (mã bản)" của ứng dụng, ghi vào đầu nhật ký chẩn đoán để biết nhật ký của bản nào; "?" nếu không đọc được. */
    private fun appVersionText(): String = try {
        val info = service.packageManager.getPackageInfo(service.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    } catch (e: RuntimeException) {
        "?"
    }

    /** Gọi trên luồng chính. */
    fun start() {
        // Nhật ký ở bộ nhớ bảo vệ theo thiết bị: ghi được cả khi dịch vụ khởi động lúc máy chưa mở khóa (xem [DeviceStorage]).
        Diag.init(java.io.File(DeviceStorage.filesDir(service), StoragePolicy.DIAG_FILE))
        Diag.log("START") {
            "app ${appVersionText()} Android ${android.os.Build.VERSION.SDK_INT} ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                "unlocked=${DeviceStorage.isUnlocked(service)}"
        }
        speech.onVoiceStatus = { missing, locale -> onVoiceStatus(missing, locale) }
        overlay.attach()
        applyServiceFlags()
        applyVerbosity()
        navigator.scrollAdjustsSliders = settings.scrollAdjustsSliders
        applyVoiceMode()
        scope.launch { processEvents() }
        scope.launch { processContentEvents() }
        ScreenSearchBridge.register { query ->
            // Chờ cửa sổ ứng dụng quay lại (màn hình nhập từ khóa vừa đóng) rồi mới tìm.
            scope.launch {
                delay(SEARCH_RESUME_DELAY_MS)
                runSafely("tìm chữ") { navigator.search(query) }
            }
        }
        settings.register(settingsListener)
        gestureStore.register(gestureListener)
        registerScreenReceiver()
        registerRecordingCallback()
        syncCallReceiver()
        speech.speak(t(S.MSG_SERVICE_ON))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(service, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Chỉ lắng nghe trạng thái điện thoại khi người dùng BẬT "đọc người gọi" và đã cấp quyền; tắt công tắc hoặc thu hồi quyền
     * thì gỡ bộ nhận ngay (luồng chính). Số điện thoại chỉ nằm trong bộ nhớ lúc đọc, không ghi nhật ký, không lưu.
     */
    private fun syncCallReceiver() {
        val want = settings.announceCalls && hasPermission(android.Manifest.permission.READ_PHONE_STATE)
        if (want && !callReceiverRegistered) {
            try {
                service.registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
                callReceiverRegistered = true
            } catch (e: RuntimeException) {
                Log.w(TAG, "Không đăng ký được bộ nhận trạng thái điện thoại", e)
            }
        } else if (!want && callReceiverRegistered) {
            unregisterCallReceiver()
        }
    }

    private fun unregisterCallReceiver() {
        try {
            service.unregisterReceiver(callReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bộ nhận cuộc gọi đã được gỡ", e)
        }
        callReceiverRegistered = false
        keyHandler.removeCallbacks(callFallback)
        callPolicy.reset()
    }

    private fun handleCallDecision(decision: CallRingPolicy.Decision) {
        when (decision) {
            CallRingPolicy.Decision.None -> Unit
            CallRingPolicy.Decision.WaitForNumber -> {
                keyHandler.removeCallbacks(callFallback)
                keyHandler.postDelayed(callFallback, CallRingPolicy.WAIT_FOR_NUMBER_MS)
            }
            is CallRingPolicy.Decision.Announce -> {
                keyHandler.removeCallbacks(callFallback)
                announceCaller(decision.number)
            }
        }
    }

    /** Tra danh bạ (nếu đã cấp quyền đọc danh bạ) rồi đọc, trên luồng xử lý để không chặn luồng chính. */
    private fun announceCaller(number: String?) {
        scope.launch {
            runSafely("đọc người gọi") {
                val name = if (!number.isNullOrBlank() &&
                    hasPermission(android.Manifest.permission.READ_CONTACTS)
                ) lookupContactName(number) else null
                val (key, arg) = CallerText.message(name, number)
                speech.speak(if (arg == null) t(key) else t(key, arg), true)
            }
        }
    }

    private fun lookupContactName(number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        service.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: SecurityException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    /** Nói vị trí hiện tại (luồng bất kỳ → chạy trên luồng chính). Mọi bước đều cần người dùng đã bật và cấp quyền. */
    private fun sayLocation() {
        keyHandler.post {
            guardMain("nói vị trí") {
                when {
                    !settings.locationEnabled -> speech.speak(t(S.MSG_LOC_OFF), true)
                    !locationReader.hasPermission() -> speech.speak(t(S.MSG_LOC_NO_PERM), true)
                    else -> {
                        speech.speak(t(S.MSG_LOC_SEARCHING), true)
                        locationReader.locate { fix ->
                            guardMain("đọc vị trí") {
                                if (closed.get()) return@guardMain
                                if (fix == null) {
                                    speech.speak(t(S.MSG_LOC_FAILED), true)
                                    return@guardMain
                                }
                                val lines = LocationText.lines(fix.address, fix.latitude, fix.longitude, fix.accuracyMeters, fix.ageMs)
                                lines.forEachIndexed { index, line -> speech.speak(t(line.key, *line.args.toTypedArray()), index == 0) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            service.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không đăng ký được bộ nhận bật/tắt màn hình", e)
        }
    }

    /**
     * Màn hình tắt/bật (luồng chính). Luôn báo bằng lời (không còn công tắc): tắt màn hình đọc "Đã tắt màn hình", bật màn hình đọc giờ
     * hiện tại; đang đổ chuông hay trong cuộc gọi thì im lặng cả hai chiều ([ScreenAnnouncement]). Đổi trạng thái màn hình còn:
     *  - dừng ngay mọi giọng đọc khi TẮT;
     *  - bỏ trạng thái phím đang giữ dở (phím nhả khi tắt màn hình sẽ không bao giờ tới);
     *  - hủy mọi việc đang chờ mà sau đó có thể đọc: chuyển tiêu điểm, chờ độ trễ chạm, kết quả AI;
     *  - đóng trình đơn đang mở một cách IM LẶNG (nếu để lại, danh sách chạm được của nó nằm trên màn hình khóa);
     *  - giữ câu vừa báo khỏi bị lời tự động của màn hình khóa cắt ngang ([SpeechShield]).
     */
    private fun onScreenState(on: Boolean) {
        if (closed.get()) return
        resetKeyState()
        pendingRange = null
        scope.launch {
            runSafely("đổi trạng thái màn hình") {
                focusJob?.cancel()
                hoverJob?.cancel()
                ai.cancelAll()
                val menuWasOpen = menu != null
                if (menuWasOpen) {
                    menuTimeout?.cancel()
                    menuGate.reset()
                    setMenu(null)
                }
                if (!on) speech.stop()
                val now = LocalTime.now() // lấy giờ ngay lúc đọc, không phải lúc nhận broadcast
                val inCall = callOrRingActive()
                val text = ScreenAnnouncement.text(on, inCall, now.hour, now.minute)
                Diag.log("SCREEN") { "${if (on) "on" else "off"} said=${text != null} inCall=$inCall menuClosed=$menuWasOpen" }
                if (text != null) {
                    speech.speak(text, true)
                    shield.protect(SystemClock.uptimeMillis())
                }
            }
        }
    }

    /**
     * Màn hình đang ở trạng thái tương tác (bật, kể cả khi đang ở màn hình khóa). Không hỏi được hệ thống thì coi như bật: đọc thừa
     * còn hơn im lặng nhầm với người không nhìn thấy màn hình.
     */
    private fun screenInteractive(): Boolean = service.getSystemService(PowerManager::class.java)?.isInteractive != false

    /**
     * Lời TỰ ĐỘNG (tiêu đề cửa sổ, tiêu điểm do hệ thống đặt) có được ngắt lời đang đọc không? Không, khi đó là câu báo màn hình bật/tắt
     * còn đang được giữ ([SpeechShield]): lời tự động xếp hàng sau nó. Cử chỉ, chạm và phím của người dùng không hỏi hàm này.
     */
    private fun interruptAllowed(): Boolean = shield.mayInterrupt(SystemClock.uptimeMillis(), speech.isSpeaking)

    /** Đang đổ chuông hoặc trong cuộc gọi (kể cả gọi qua mạng): chế độ âm thanh khác bình thường. */
    private fun callOrRingActive(): Boolean {
        val mode = service.getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode != AudioManager.MODE_NORMAL
    }

    /** Luồng chính. Xóa mọi trạng thái phím dở dang. */
    private fun resetKeyState() {
        volumeNav.reset()
        headsetNav.reset()
        keyHandler.removeCallbacks(headsetLong)
        keyHandler.removeCallbacks(headsetSingle)
        consumedKeys.clear()
        ctrlAlone = false
        volumeLongPress.values.forEach { keyHandler.removeCallbacks(it) }
    }

    /** Cuộn: nhớ phạm vi mục đang hiện, chỉ đọc khi danh sách đã ngừng cuộn một lúc ([LIST_RANGE_SETTLE_MS]). */
    private fun offerListRange(event: AccessibilityEvent) {
        if (!settings.speakListRange) return
        val range = ListRange.from(event.fromIndex, event.toIndex, event.itemCount) ?: return
        pendingRange = range
        keyHandler.removeCallbacks(rangeRunnable)
        keyHandler.postDelayed(rangeRunnable, LIST_RANGE_SETTLE_MS)
    }

    private fun speakPendingRange() {
        val range = pendingRange ?: return
        pendingRange = null
        if (menu == null) speech.speak(t(S.MSG_LIST_RANGE, range.first, range.last, range.total), false)
    }

    /**
     * Nội dung cửa sổ đổi: chỉ các kiểu đổi có thể là thông báo (chữ, mô tả, cây con, tiêu đề khung) mới được chuyển đi,
     * và tối đa một sự kiện mỗi [CONTENT_OFFER_MIN_INTERVAL_MS] để tải trên luồng chính luôn nhỏ. Việc có phải vùng
     * cập nhật trực tiếp hay không do luồng nền kiểm tra ([handleContentChanged]).
     */
    private fun offerContentChange(event: AccessibilityEvent) {
        if (event.contentChangeTypes and LIVE_CONTENT_TYPES == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastContentOfferMs < CONTENT_OFFER_MIN_INTERVAL_MS) return
        lastContentOfferMs = now
        try {
            contentEvents.trySend(ReaderEvent.from(event))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không trích được sự kiện nội dung đổi", e)
        }
    }

    /** Chạy trên luồng chính: chỉ lọc loại sự kiện và trích dữ liệu, không xử lý nặng. */
    fun offer(event: AccessibilityEvent) {
        // Cờ "ngón tay đang chạm màn hình" (cho quy tắc đọc thông báo): chỉ ghi một số, không qua hàng đợi.
        if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            fingerDownSince = SystemClock.uptimeMillis()
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) fingerDownSince = 0L // vẫn đi tiếp: nhập phím bằng nhấc tay cần nó
        // Nội dung đổi / đã cuộn xảy ra dồn dập: chỉ đánh dấu bộ đệm cũ (không qua hàng đợi, không làm tràn sự kiện quan trọng).
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            navigator.markContentChanged()
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) offerContentChange(event)
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                offerListRange(event)
                keyHandler.removeCallbacks(scrollSettled)
                keyHandler.postDelayed(scrollSettled, LIST_RANGE_SETTLE_MS)
            }
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
        // Quyết định tức thời trên luồng chính: chỉ để báo "đã xử lý" cho hệ thống và ngắt lời đang đọc. Hành động thật
        // được tính lại trên luồng xử lý (bên dưới) vì cử chỉ trước đó có thể vừa mở hoặc đóng trình đơn.
        val preview = GestureRouter.route(gesture, effectiveMapping(), menu != null)
        Diag.log("GESTURE") { "$gesture -> $preview" }
        when (preview) {
            GestureRoute.Unhandled -> return false
            GestureRoute.Swallowed -> return true
            is GestureRoute.InMenu, is GestureRoute.OnScreen -> Unit
        }
        // Chỉnh âm lượng không được ngắt lời đang đọc và vẫn dùng được khi trình đơn đang mở.
        val previewAction = (preview as? GestureRoute.OnScreen)?.action ?: (preview as? GestureRoute.InMenu)?.action
        if (previewAction == GestureAction.VOLUME_RAISE || previewAction == GestureAction.VOLUME_LOWER) {
            runAction(previewAction)
            return true
        }
        navigator.cancelLongScroll() // cử chỉ mới = dừng vòng cuộn dài đang chạy (cuộn tới đầu/cuối, tìm ngoài màn hình)

        // Trong trình đơn, cử chỉ không dùng được không được ngắt câu đang đọc.
        if (GestureRouter.interruptsSpeech(preview)) speech.stop()
        scope.launch {
            runSafely("thực hiện cử chỉ $gesture") {
                when (val route = GestureRouter.route(gesture, effectiveMapping(), menu != null)) {
                    is GestureRoute.InMenu -> handleMenuAction(route.action)
                    is GestureRoute.OnScreen -> dispatcher.dispatch(route.action)
                    GestureRoute.Unhandled, GestureRoute.Swallowed -> Unit
                }
            }
        }
        return true
    }

    /** Thực hiện một hành động (từ phím tắt, phím âm lượng, nút tai nghe) theo cùng quy tắc, kể cả khi trình đơn đang mở. */
    private fun runAction(action: GestureAction) {
        if (action == GestureAction.NONE) return
        // Chỉnh âm lượng không được ngắt lời đang đọc và vẫn dùng được khi trình đơn đang mở.
        if (action == GestureAction.VOLUME_RAISE || action == GestureAction.VOLUME_LOWER) {
            adjustVolume(if (action == GestureAction.VOLUME_RAISE) 1 else -1)
            return
        }
        navigator.cancelLongScroll()

        // Trong trình đơn, hành động không dùng được không được ngắt câu đang đọc.
        val inMenu = menu != null
        if (!inMenu || action in MenuGestures.HANDLED) speech.stop()
        scope.launch {
            runSafely("thực hiện $action") {
                if (menu != null) handleMenuAction(action) else dispatcher.dispatch(action)
            }
        }
    }

    /**
     * Phím cứng và phím âm lượng. Chạy trên luồng chính, phải trả về nhanh. true = đã tiêu thụ phím. Mọi lỗi đều trả về
     * false để không bao giờ làm kẹt phím của người dùng. Không làm gì khi đã đóng phiên.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (closed.get()) return false
        return try {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> handleVolumeKey(event)
                KeyEvent.KEYCODE_HEADSETHOOK -> handleHeadsetKey(event)
                else -> handleKeyboardKey(event)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Lỗi khi xử lý phím", e)
            CrashLogger.recordNonFatal("phím", e)
            false
        }
    }

    /** Tạm dừng/tiếp tục phím cứng và phím âm lượng. Chạy trên luồng xử lý; trạng thái phím dọn trên luồng chính. */
    private fun toggleKeysPause() {
        keysPaused = !keysPaused
        keyHandler.post { guardMain("dọn trạng thái phím") { resetKeyState() } }
        speech.speak(t(if (keysPaused) S.MSG_KEYS_PAUSED else S.MSG_KEYS_RESUMED), true)
    }

    /** Đảo việc dùng phím âm lượng để điều hướng trong ứng dụng đang mở (lưu theo gói ứng dụng). */
    private fun toggleVolumeForApp() {
        val pkg = activePackage
        if (pkg == null) {
            speech.speak(t(S.MSG_NOTHING_SELECTED), true)
            return
        }
        val (next, nowExcluded) = PackageSet.toggle(settings.volumeExcludedApps, pkg)
        settings.volumeExcludedApps = next
        speech.speak(t(if (nowExcluded) S.MSG_VOLUME_APP_OFF else S.MSG_VOLUME_APP_ON, appLabel(pkg)), true)
    }

    private fun handleKeyboardKey(event: KeyEvent): Boolean {
        if (!settings.keyboardShortcuts || (keysPaused && !isPauseToggle(event))) {
            consumedKeys.clear()
            ctrlAlone = false
            return false
        }
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        // Ctrl bấm riêng (không kèm phím nào) = dừng đọc; không tiêu thụ để Ctrl+phím của ứng dụng vẫn hoạt động.
        if (KeyShortcuts.isCtrlKey(code)) {
            if (down) {
                if (event.repeatCount == 0) ctrlAlone = true
            } else if (ctrlAlone) {
                ctrlAlone = false
                runAction(GestureAction.STOP_SPEECH)
            }
            return false
        }
        if (down) ctrlAlone = false
        if (!down) return consumedKeys.remove(code) // nhả phím mà lúc nhấn đã bị tiêu thụ thì cũng tiêu thụ nốt
        val action = KeyShortcuts.resolve(code, event.isAltPressed, event.isMetaPressed, event.isShiftPressed, event.isCtrlPressed)
            ?: return false
        if (keysPaused && action != GestureAction.TOGGLE_KEYS_PAUSE) return false
        consumedKeys.add(code)
        if (event.repeatCount > 0 && !KeyShortcuts.isRepeatable(action)) return true
        runAction(action)
        return true
    }

    /**
     * Alt/Meta+P (không Ctrl, không Shift), hoặc nhả phím đó: luôn được nhận kể cả khi đang tạm dừng, để còn tiếp tục lại.
     */
    private fun isPauseToggle(event: KeyEvent): Boolean =
        event.keyCode == KeyEvent.KEYCODE_P &&
            (event.isAltPressed || event.isMetaPressed) && !event.isCtrlPressed && !event.isShiftPressed ||
            (event.action == KeyEvent.ACTION_UP && consumedKeys.contains(event.keyCode))

    /** Màn hình đang sáng và không trong cuộc gọi/chuông reo: phím âm lượng và nút tai nghe chỉ bị chiếm trong lúc này. */
    private fun deviceReadyForKeys(): Boolean {
        val power = service.getSystemService(PowerManager::class.java)
        val audio = service.getSystemService(AudioManager::class.java)
        return power?.isInteractive == true && audio?.mode == AudioManager.MODE_NORMAL
    }

    /**
     * Phím âm lượng chỉ bị chiếm khi người dùng bật, không tạm dừng, ứng dụng đang mở không bị loại và thiết bị sẵn sàng
     * ([deviceReadyForKeys]). Lúc bị chiếm, phím tắt hỗ trợ tiếp cận của Android (giữ hai phím) không tới được hệ thống.
     */
    private fun volumeShortcutsActive(): Boolean {
        if (!deviceReadyForKeys()) return false
        return settings.volumeKeyNav && !keysPaused && !PackageSet.contains(settings.volumeExcludedApps, activePackage)
    }

    /** Chạy hành động gán cho một "cử chỉ" phím (phím âm lượng, nút tai nghe), theo bản đồ hiệu lực của ứng dụng đang mở. */
    private fun runTrigger(trigger: Gesture) = runAction(effectiveMapping().actionFor(trigger))

    private fun handleVolumeKey(event: KeyEvent): Boolean {
        val key = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) VolumeKeyNav.Key.UP else VolumeKeyNav.Key.DOWN
        val now = SystemClock.uptimeMillis()
        volumeNav.expireIfIdle(now)
        if (event.action == KeyEvent.ACTION_UP) {
            if (!volumeNav.isHeld(key)) return false // lúc nhấn không bị chiếm thì nhả cũng để hệ thống xử lý
            keyHandler.removeCallbacks(volumeLongPress.getValue(key))
            volumeNav.onUp(key, now)?.let { runTrigger(it) }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return volumeNav.isHeld(key) // lặp khi giữ: chỉ nuốt nếu lần nhấn đầu đã bị chiếm
        if (!volumeShortcutsActive()) return false
        val trigger = volumeNav.onDown(key, 0, now)
        if (trigger != null) {
            volumeLongPress.values.forEach { keyHandler.removeCallbacks(it) }
            runTrigger(trigger)
        } else {
            keyHandler.postDelayed(volumeLongPress.getValue(key), VolumeKeyNav.LONG_PRESS_MS)
        }
        return true
    }

    private fun onVolumeLongPress(key: VolumeKeyNav.Key) {
        volumeNav.onLongPress(key)?.let { runTrigger(it) }
    }

    /**
     * Nút tai nghe: bấm một lần / hai lần / giữ → hành động gán cho [Gesture.HEADSET_SHORT] / [Gesture.HEADSET_DOUBLE] /
     * [Gesture.HEADSET_LONG]. Chỉ khi người dùng bật, không tạm dừng và thiết bị sẵn sàng (không cướp nút nghe/nhận cuộc gọi).
     */
    private fun handleHeadsetKey(event: KeyEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        if (event.action == KeyEvent.ACTION_UP) {
            if (!headsetNav.isHeld()) return false
            keyHandler.removeCallbacks(headsetLong)
            val result = headsetNav.onUp(now)
            if (result != null) {
                keyHandler.removeCallbacks(headsetSingle)
                runTrigger(result)
            } else {
                keyHandler.postDelayed(headsetSingle, HeadsetKeyNav.DOUBLE_GAP_MS)
            }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return headsetNav.isHeld()
        if (!settings.headsetKey || keysPaused || !deviceReadyForKeys()) return false
        headsetNav.onDown()
        keyHandler.postDelayed(headsetLong, HeadsetKeyNav.LONG_PRESS_MS)
        return true
    }

    /**
     * Chỉnh âm lượng giống trình đọc màn hình (vì phím âm lượng đã bị chiếm nên phải tự chỉnh): trong cuộc gọi chỉnh âm cuộc
     * gọi; đang đọc hoặc đang chạm màn hình thì chỉnh luồng trợ năng (đúng giọng đọc); còn lại chỉnh âm phương tiện. Không
     * hiện bảng âm lượng hệ thống mà đọc phần trăm mới.
     */
    private fun adjustVolume(direction: Int) {
        val audio = service.getSystemService(AudioManager::class.java) ?: return
        try {
            val stream = VolumeStreamPolicy.choose(audio.mode, speech.isSpeaking, fingerDownSince != 0L)
            audio.adjustStreamVolume(stream, if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
            val percent = VolumeStreamPolicy.percent(audio.getStreamVolume(stream), audio.getStreamMaxVolume(stream))
            speech.speak(t(S.MSG_VOLUME_PCT, percent), true)
        } catch (e: SecurityException) {
            Log.w(TAG, "Không chỉnh được âm lượng", e)
        }
    }

    fun interrupt() = speech.stop()

    /** Idempotent. Gọi trên luồng chính (overlay phải gỡ trên luồng chính). */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        ScreenSearchBridge.clear()
        settings.unregister(settingsListener)
        gestureStore.unregister(gestureListener)
        events.close()
        contentEvents.close()
        locationReader.cancel()
        if (callReceiverRegistered) unregisterCallReceiver()
        unregisterRecordingCallback()
        keyHandler.removeCallbacksAndMessages(null)
        if (screenReceiverRegistered) {
            try {
                service.unregisterReceiver(screenReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Bộ nhận màn hình đã được gỡ", e)
            }
            screenReceiverRegistered = false
        }
        scope.cancel()
        ai.close()
        feedback.close()
        customLabels.close()
        overlay.detach()
        menuOverlay.release()
        speech.onVoiceStatus = null
        speech.shutdown()
    }

    private suspend fun processEvents() {
        val deduplicator = EventDeduplicator()
        // Lướt nhanh hơn tốc độ xử lý: chỉ giữ sự kiện chạm MỚI NHẤT đang xếp hàng (ngón tay đã đi qua các mục cũ), xem CoalescingReceiver.
        val receiver = CoalescingReceiver(events) { it.type == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER }
        while (true) {
            val event = receiver.receive() ?: break
            repeat(receiver.takeSkipped()) { hoverStats.record(HoverOutcome.COALESCED) }
            runSafely("xử lý sự kiện ${event.type}") {
                // Chạm vào phần tử không qua bộ lọc trùng chung (khóa của nó không phân biệt được các phần tử): xem EventDedupPolicy.
                if (deduplicator.admit(event.type, event.dedupKey(), event.timeMs)) handleEvent(event)
            }
        }
    }

    private suspend fun processContentEvents() {
        for (event in contentEvents) {
            runSafely("xử lý nội dung đổi") { handleContentChanged(event) }
        }
    }

    /**
     * Vùng cập nhật trực tiếp (liveRegion) và khung mới xuất hiện (paneTitle: Snackbar, bảng dưới...) được đọc theo mức ưu tiên của ứng dụng:
     * "assertive" ngắt lời, còn lại xếp hàng. Cùng một nguồn không đọc lại nếu chữ không đổi.
     */
    private fun handleContentChanged(event: ReaderEvent) {
        val src = event.source ?: return
        if (menu != null) return
        val key = "${event.packageName}|${src.viewIdResourceName}|${src.className}"
        if (event.contentChangeTypes and (AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE) != 0) {
            val title = src.paneTitle?.toString().orEmpty().trim()
            if (title.isNotEmpty() && liveGate.allow("pane|$key", title, event.timeMs)) {
                speech.speak(title, false)
                return
            }
        }
        val mode = src.liveRegion
        if (mode == View.ACCESSIBILITY_LIVE_REGION_NONE) return
        if (src.isPassword && !settings.speakPasswords) return
        val text = liveText(src)
        if (text.isBlank() || !liveGate.allow("live|$key", text, event.timeMs)) return
        speech.speak(text, mode == View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE)
    }

    private fun liveText(node: AccessibilityNodeInfo): String =
        node.text?.toString()?.trim().orEmpty().ifEmpty {
            node.contentDescription?.toString()?.trim().orEmpty().ifEmpty { NodeCollector.mergedLabel(node, everything = true).trim() }
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
                // Đọc bố cục lúc mở trình đơn (không giữ bản sao): người dùng sửa ở màn Cài đặt xong là dùng được ngay.
                Menus.local(
                    navigator.customActions(),
                    editable = navigator.isCurrentEditable(),
                    imageLike = navigator.isCurrentImage(),
                    layout = menuLayoutStore.load(),
                )
            }
            else -> return // trình đơn con chỉ mở từ trình đơn cha
        }
        menuGate.reset()
        // Vào thẳng mục đầu tiên (không đọc tiêu đề và số mục trước): người dùng nghe ngay thứ mình cần chọn.
        val first = MenuState.open(kind, items).next().also { setMenu(it) }
        Diag.log("MENU") { "open kind=${kind.name} items=${items.size}" }
        announce(first.describeCurrent(settings.speakHints))
        armMenuTimeout()
    }

    /**
     * Đặt trình đơn đang mở (null = đóng) và giữ danh sách chạm được trên màn hình ([menuOverlay]) khớp với nó: mở thì hiện, đổi sang
     * trình đơn khác (vào nhóm / quay lại) thì thay nội dung, chỉ đổi mục thì tô sáng, đóng thì gỡ. MỌI thay đổi [menu] phải đi qua
     * đây. Chỉ gọi trên luồng xử lý duy nhất của [scope].
     */
    private fun setMenu(state: MenuState?) {
        val previous = menu
        menu = state
        when {
            state == null -> if (previous != null) menuOverlay.hide()
            previous == null || previous.items !== state.items -> menuOverlay.show(state.items.map { it.label }, state.index)
            previous.index != state.index -> menuOverlay.select(state.index)
        }
    }

    /** Người dùng chạm/lướt tới hàng [row] của danh sách trình đơn (luồng chính): chuyển sang luồng xử lý để không chạy song song với cử chỉ. */
    private fun onMenuRowTouched(row: Int) {
        if (closed.get()) return
        scope.launch { runSafely("chạm trình đơn") { selectMenuRow(row) } }
    }

    /** Chọn mục [row] vì ngón tay chạm tới và đọc nó như khi vuốt tới; chạm hai lần sau đó chạy đúng mục này. */
    private fun selectMenuRow(row: Int) {
        val current = menu ?: return
        if (row !in current.items.indices) return
        Diag.log("MENU") { "touch row=${row + 1}/${current.items.size}" }
        val next = current.select(row)
        if (next !== current) setMenu(next)
        armMenuTimeout()
        announce(next.describeCurrent(settings.speakHints))
    }

    /**
     * Hệ thống vẫn báo chạm của ứng dụng bên dưới khi KHÔNG hiện được danh sách chạm được (thêm cửa sổ bị từ chối): đọc lại mục đang chọn
     * để chạm không bao giờ im lặng, tối đa một lần mỗi [MENU_TOUCH_REPEAT_MS].
     */
    private fun speakMenuForTouch() {
        val current = menu ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastMenuTouchSpeakMs < MENU_TOUCH_REPEAT_MS) return
        lastMenuTouchSpeakMs = now
        announce(current.describeCurrent(settings.speakHints))
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
            // Vuốt một ngón xuống/lên ("tiến/lùi theo điều khiển đọc") chuyển mục y như vuốt phải/trái.
            GestureAction.NEXT_ELEMENT, GestureAction.NEXT_BY_CONTROL -> advance(current.next())
            GestureAction.PREVIOUS_ELEMENT, GestureAction.PREVIOUS_BY_CONTROL -> advance(current.previous())
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
                        Diag.log("MENU") {
                            "run ${item.action?.name ?: "custom"} at=${current.index + 1}/${current.items.size} in=${current.kind.name}"
                        }
                        setMenu(null)
                        // Chức năng sắp chạy có thể phát cử chỉ chạm theo toạ độ (nhấn, nhấn giữ, cuộn, chuyển trang): đợi lớp danh sách
                        // gỡ hẳn để chạm đó rơi vào ứng dụng bên dưới chứ không vào lớp phủ.
                        menuOverlay.awaitHidden()
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
        setMenu(next)
        armMenuTimeout()
        announce(next.describeCurrent(settings.speakHints))
    }

    /** Tự đóng trình đơn nếu bỏ mặc, để không bị kẹt ở chế độ trình đơn. */
    private fun armMenuTimeout() {
        menuTimeout?.cancel()
        menuTimeout = scope.launch {
            delay(MENU_IDLE_TIMEOUT_MS)
            if (menu != null) {
                Diag.log("MENU") { "timeout" }
                setMenu(null)
                menuGate.reset()
                speech.speak(t(S.MSG_MENU_CLOSED), true)
            }
        }
    }

    private fun closeMenu() {
        menuTimeout?.cancel()
        Diag.log("MENU") { "close" }
        setMenu(null)
        menuGate.reset()
        speech.speak(t(S.MSG_MENU_CLOSED), true)
    }

    private fun openSubmenu(parent: MenuState, kind: MenuKind) {
        val items = Menus.submenu(kind) ?: return // nhóm của trình đơn chung hoặc "Chỉnh sửa văn bản"; loại khác không mở được
        val child = parent.child(kind, items).next() // vào thẳng mục đầu của trình đơn con
        setMenu(child)
        Diag.log("MENU") { "enter kind=${kind.name} items=${items.size}" }
        armMenuTimeout()
        // Bật gợi ý: đọc tên nhóm và số mục trước mục đầu để người dùng biết mình vừa vào đâu (tắt gợi ý thì vào thẳng mục đầu như trước).
        val first = child.describeCurrent(settings.speakHints)
        announce(if (settings.speakHints) child.opening() + " " + first else first)
    }

    private fun returnToParent(state: MenuState) {
        val parent = state.back() ?: return closeMenu()
        setMenu(parent)
        Diag.log("MENU") { "back to=${parent.kind.name}" }
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
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                resetCaretMark() // sang ô nhập khác: mốc con trỏ cũ không còn ý nghĩa
                if (menu == null) event.source?.let { navigator.focusOn(it, interrupt = interruptAllowed()) }
            }

            // Khám phá bằng cảm ứng: ngón tay chạm/lướt tới đâu thì chọn và đọc phần tử ở đó.
            // Cũng đặt phần tử hiện tại, nên "chạm hai lần" (ACTIVATE) nhấn đúng phần tử vừa nghe.
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> if (menu == null) handleHover(event) else if (!menuOverlay.isShowing) speakMenuForTouch()

            // Nhập phím bằng nhấc tay: hệ thống báo kết thúc chạm (có thể hai sự kiện liền nhau, chính sách chỉ cho nhấn một lần).
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                onFingerLifted()
                if (menu == null && liftToType.onTouchEnd(settings.liftToType)) navigator.activate()
            }

            // Bắt đầu một cử chỉ: không phải thao tác gõ, và hủy việc đọc chờ độ trễ chạm.
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                liftToType.onGestureStart()
                hoverJob?.cancel()
            }

            // Tiêu điểm trợ năng do ứng dụng/trình duyệt đặt: đồng bộ con trỏ, không đọc lại.
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                resetCaretMark()
                if (menu == null) event.source?.let { navigator.onExternalFocus(it) }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                event.source?.let { node ->
                    navigator.invalidateCache()
                    navigator.highlight(node)
                    speech.speak(t(S.MSG_TAPPED, navigator.describe(node)), false)
                }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowState(event)

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ->
                if (isToast(event)) handleToast(event) else handleNotification(event)

            // Ứng dụng chủ động báo (announceForAccessibility): xếp hàng, không ngắt lời đang đọc.
            AccessibilityEvent.TYPE_ANNOUNCEMENT ->
                if (menu == null) event.text.trim().takeIf { it.isNotEmpty() }?.let { speech.speak(it, false) }

            // Chuyển tab: ứng dụng đổi tab mà tiêu điểm không đổi sẽ không có tiếng nào nếu thiếu sự kiện này.
            AccessibilityEvent.TYPE_VIEW_SELECTED ->
                if (menu == null) event.source?.let { node ->
                    if (node.isSelected && node.className?.contains(TAB_CLASS_MARK, ignoreCase = true) == true) {
                        speech.speak(navigator.describe(node), false)
                    }
                }

            // Nhấn giữ do người dùng/hệ thống thực hiện; bỏ qua tiếng vọng của chính lệnh nhấn giữ của trình đọc.
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                if (menu == null && SystemClock.uptimeMillis() - navigator.lastOwnLongClickMs > OWN_ACTION_ECHO_MS) {
                    speech.speak(t(S.MSG_LONG_CLICKED), false)
                }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> handleSelectionChanged(event)

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                navigator.invalidateCache()
                handleTextChanged(event)
            }
        }
    }

    /**
     * Đọc khi gõ: từng ký tự ([SettingsRepository.echoChars], mặc định bật), từ vừa gõ xong ([SettingsRepository.echoWords]),
     * "Chữ hoa" trước chữ viết hoa ([SettingsRepository.speakCapitals]). Từ nào xong thì đọc trước, ký tự (dấu cách/dấu câu) xếp sau.
     * Ô mật khẩu chỉ đọc ký tự theo quy tắc cũ, không bao giờ đọc từ hay chữ hoa.
     */
    private fun handleTextChanged(event: ReaderEvent) {
        if (dictationMuted) {
            // Đang im vì người dùng đọc chính tả: không đọc từng ký tự (bộ nhận dạng thay đi thay lại đoạn đang soạn); ghi lại để đọc MỘT lần khi hết
            // im. Ô mật khẩu tuyệt đối không ghi lại.
            if (!event.isPassword) dictationReadback.onTextChanged(event.text, event.fromIndex)
            return
        }
        val chars = if (settings.echoChars) {
            SpeechTextBuilder.describeTextChange(
                fullText = event.text,
                fromIndex = event.fromIndex,
                addedCount = event.addedCount,
                removedCount = event.removedCount,
                isPassword = event.isPassword,
                speakPasswordChars = settings.speakPasswords,
            )
        } else {
            null
        }
        val word = if (settings.echoWords && !event.isPassword) {
            TypingEcho.completedWord(event.text, event.fromIndex, event.addedCount)
        } else {
            null
        }
        if (word != null) speech.speak(word, true)
        if (chars != null) {
            val typed = if (event.addedCount == 1) event.text.getOrNull(event.fromIndex) else null
            val capital = settings.speakCapitals && !event.isPassword && typed != null && typed.isUpperCase()
            speech.speak(if (capital) t(S.MSG_CAPITAL, chars) else chars, word == null)
        }
    }

    /**
     * Cửa sổ đổi. Bàn phím (IME) và lớp phủ trợ năng KHÔNG phải màn hình mới: hiện/ẩn bàn phím không được xóa vị trí đang
     * chọn, đóng trình đơn hay ngắt lời đang đọc. Mọi cửa sổ khác (ứng dụng, hộp thoại, thanh thông báo) vẫn đặt lại như trước.
     * Khi màn hình đang TẮT thì vẫn cập nhật trạng thái nhưng không đọc gì (không ai nhìn, và lời đó sẽ nói chồng lên "Đã tắt màn hình");
     * khi vừa báo giờ lúc bật màn hình thì tiêu đề xếp hàng sau câu giờ thay vì cắt nó ([interruptAllowed]).
     */
    private fun handleWindowState(event: ReaderEvent) {
        val windowType = event.source?.window?.type
        val title = event.text.trim()
        val audible = screenInteractive()
        resetCaretMark()
        // Danh sách trình đơn của chính dịch vụ (MenuOverlay) cũng là một cửa sổ mới: nếu hệ thống không cho biết loại cửa sổ của nó
        // (windowType == null) thì vẫn nhận ra qua gói của chính ta khi lớp đang hiện, để việc MỞ trình đơn không tự làm nó đóng.
        val ownMenuLayer = menuOverlay.isShowing && windowType == null && event.packageName == service.packageName
        if (windowType == AccessibilityWindowInfo.TYPE_INPUT_METHOD || windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || ownMenuLayer) {
            navigator.markContentChanged()
            if (title.isNotEmpty() && menu == null && audible) speech.speak(title, false)
            return
        }
        val menuWasOpen = menu != null
        if (menuWasOpen) Diag.log("MENU") { "closed by window change" }
        setMenu(null) // cửa sổ đổi: trình đơn (nhất là của phần tử) đã lỗi thời
        ai.cancelAll() // và kết quả AI của màn hình cũ sẽ đọc sai
        ai.onWindowChanged() // màn hình mới: các ảnh đã tự đọc được phép đọc lại
        focusJob?.cancel()
        // Nhớ phần tử đang chọn của cửa sổ vừa rời TRƯỚC khi reset (vết sẽ bị xóa).
        val windowKey = "${event.packageName}|$title"
        val previousKey = lastWindowKey
        if (settings.rememberFocus && previousKey != null && previousKey != windowKey) {
            navigator.latestTrace()?.let { focusMemory.remember(previousKey, it) }
        }
        lastWindowKey = windowKey
        navigator.onWindowChanged()
        // Chỉ cửa sổ ứng dụng thật mới đổi "ứng dụng đang mở" (thanh thông báo không làm mất cử chỉ riêng).
        if (windowType == AccessibilityWindowInfo.TYPE_APPLICATION) {
            activePackage = event.packageName?.takeIf { it != service.packageName }
        }
        // Màn hình tắt: trạng thái đã cập nhật xong ở trên; không đọc gì và không hẹn chuyển tiêu điểm.
        if (!audible) return
        // Trình đơn đóng vì hệ thống đổi cửa sổ: báo rõ để người dùng không tưởng nó còn mở.
        if (menuWasOpen) speech.speak(t(S.MSG_MENU_CLOSED), true)
        if (title.isNotEmpty()) speech.speak(title, !menuWasOpen && interruptAllowed())

        // Sau lời đọc tiêu đề: quay lại đúng phần tử đã nhớ, hoặc (hộp thoại mới) vào phần tử đầu tiên.
        val remembered = if (settings.rememberFocus) focusMemory.recall(windowKey) else null
        if (remembered != null) {
            scheduleWindowFocus { navigator.restoreFromTrace(remembered, interrupt = false) }
        } else if (settings.focusDialogs && !menuWasOpen && isDialog(event)) {
            scheduleWindowFocus { navigator.focusFirst(interrupt = false) }
        }
    }

    private fun isDialog(event: ReaderEvent): Boolean {
        val name = event.className ?: return false
        return name.contains(DIALOG_CLASS_MARK, ignoreCase = true) || name.contains(SHEET_CLASS_MARK, ignoreCase = true)
    }

    /**
     * Chờ giao diện ổn định rồi chuyển tiêu điểm, nhưng CHỈ khi người dùng chưa tự chọn gì và không đang ở trong trình đơn,
     * để không bao giờ giật tiêu điểm khỏi chỗ người dùng vừa chọn.
     */
    private fun scheduleWindowFocus(action: () -> Boolean) {
        focusJob = scope.launch {
            delay(WINDOW_FOCUS_DELAY_MS)
            runSafely("chuyển tiêu điểm cửa sổ") {
                if (menu == null && navigator.current() == null) action()
            }
        }
    }

    private fun resetCaretMark() {
        lastCaret = -1
        lastTextLength = -1
    }

    /** Toast do hệ thống gửi dưới dạng TYPE_NOTIFICATION_STATE_CHANGED với lớp android.widget.Toast. */
    private fun isToast(event: ReaderEvent): Boolean = event.className?.contains(TOAST_CLASS_MARK) == true

    /**
     * Toast được đọc theo công tắc RIÊNG (mặc định bật), không phụ thuộc "đọc thông báo", không gộp "N thông báo mới",
     * không đọc tên ứng dụng, và Toast của chính ứng dụng này cũng được đọc. Như thông báo: không đọc khi màn hình tắt
     * ([NotificationRules.shouldReadToast]).
     */
    private fun handleToast(event: ReaderEvent) {
        if (!NotificationRules.shouldReadToast(settings.readToasts, screenInteractive())) return
        val text = event.texts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(". ")
        if (text.isEmpty() || !liveGate.allow("toast", text, event.timeMs)) return
        speech.speak(text, false)
    }

    /**
     * Con trỏ/vùng chọn trong ô nhập đổi (bàn phím ngoài, chạm vào chữ, ứng dụng tự đặt): đọc ký tự vừa đi qua hoặc đoạn
     * được chọn. Bỏ qua khi chính trình đọc vừa đặt con trỏ (nó đã tự đọc) và khi đang gõ/xóa (sự kiện đổi văn bản đã đọc).
     */
    private fun handleSelectionChanged(event: ReaderEvent) {
        if (menu != null) return
        val result = SelectionSpeech.describe(event.text, event.fromIndex, event.toIndex, lastCaret, lastTextLength)
        lastCaret = result.caret
        lastTextLength = result.length
        if (event.isPassword && !settings.speakPasswords) return
        if (SystemClock.uptimeMillis() - navigator.lastOwnSelectionMs < OWN_ACTION_ECHO_MS) return
        val spoken = result.speech?.takeIf { it.isNotEmpty() } ?: return
        speech.speak(if (spoken.isBlank()) SpeechFormats.spell(spoken) else spoken, true)
    }

    /**
     * Đọc thông báo mới đã được tóm tắt; bỏ qua thông báo của chính ứng dụng này, khi người dùng tắt, và LUÔN bỏ qua khi màn hình đang
     * tắt (hệ thống báo sự kiện thông báo ngay lúc nó được đăng, kể cả khi màn hình tắt; thông báo bỏ lỡ đó không được đọc bù khi bật lại).
     * Nhật ký chỉ ghi trạng thái màn hình và kết quả, không ghi nội dung thông báo.
     */
    private fun handleNotification(event: ReaderEvent) {
        val pkg = event.packageName ?: return
        if (pkg == service.packageName) return
        val label = appLabel(pkg)
        val screenOn = screenInteractive()
        val allowed = NotificationRules.shouldRead(
            screenOn = screenOn,
            masterOn = settings.readNotifications,
            quiet = QuietRules(settings.notifQuietFinger, settings.notifQuietCall, settings.notifQuietDnd, settings.notifQuietMedia),
            context = { notificationContext() },
            text = (listOf(label) + event.texts).joinToString(" "),
            blacklist = KeywordList.parse(settings.notifBlacklist),
            whitelist = KeywordList.parse(settings.notifWhitelist),
        )
        Diag.log("NOTIF") { "screenOn=$screenOn allowed=$allowed" }
        if (!allowed) return
        val summary = notificationSummarizer.summarize(NotificationInfo(pkg, label, event.texts), event.timeMs)
        if (summary != null) speech.speak(summary, false)
    }

    /**
     * Hoàn cảnh hiện tại, không cần quyền nào. Cờ chạm tự hết hiệu lực sau [FINGER_DOWN_MAX_MS] phòng khi mất sự kiện
     * kết thúc chạm (nếu không, thông báo sẽ bị nín mãi).
     */
    private fun notificationContext(): NotificationContext {
        val audio = service.getSystemService(AudioManager::class.java)
        val nm = service.getSystemService(NotificationManager::class.java)
        val mode = audio?.mode
        val filter = nm?.currentInterruptionFilter
        val since = fingerDownSince
        return NotificationContext(
            fingerDown = since != 0L && SystemClock.uptimeMillis() - since < FINGER_DOWN_MAX_MS,
            inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE,
            dnd = filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            mediaPlaying = audio?.isMusicActive == true,
        )
    }

    /** Tên hiển thị của ứng dụng; nếu hệ thống không cho tra (package visibility) thì dùng đoạn cuối tên gói. */
    private fun appLabel(pkg: String): String = try {
        val pm = service.packageManager
        val info = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        pm.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg.substringAfterLast('.')
    }

    /**
     * Sự kiện chạm vào một phần tử: tìm phần tử đáng đọc ([NodeCollector.findHoverTarget]) rồi đọc ([onHoverTarget]). Mỗi kết cục được
     * đếm ([HoverStats], chỉ có số) để nhật ký cho biết lần đặt ngón tay nào có phần tử bị im và vì sao.
     */
    private fun handleHover(event: ReaderEvent) {
        val source = event.source
        if (source == null) {
            hoverStats.record(HoverOutcome.NO_SOURCE)
            return
        }
        val target = NodeCollector.findHoverTarget(source)
        if (target == null) {
            hoverStats.record(if (source.isVisibleToUser) HoverOutcome.UNREADABLE else HoverOutcome.HIDDEN)
            return
        }
        onHoverTarget(target)
    }

    /** Ngón tay đã nhấc: lần chạm kế tiếp luôn được đọc ([HoverEchoGuard.onLift]); ghi nhật ký nếu lần đặt ngón tay vừa rồi có chạm bị im. */
    private fun onFingerLifted() {
        hoverGuard.onLift()
        hoverStats.takeSummary()?.let { summary -> com.example.vnscreenreader.core.Diag.log("HOVER") { summary } }
    }

    /**
     * Ngón tay lướt tới [target]. Có độ trễ chạm thì chỉ đọc khi ngón tay còn dừng ở đó đủ lâu (lướt nhanh qua thì bỏ).
     * Phím bàn phím luôn đọc ngay khi bật nhập bằng nhấc tay: nhấc tay là gõ nên phải biết chắc đang ở phím nào.
     */
    private fun onHoverTarget(target: AccessibilityNodeInfo) {
        val isKey = target.isTextEntryKey || target.window?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        liftToType.onHover(isKey)
        hoverJob?.cancel()
        val delayMs = if (isKey && settings.liftToType) 0 else settings.touchDelayMs
        if (delayMs <= 0) {
            readHoverTarget(target, dropEnclosing = true)
            return
        }
        hoverJob = scope.launch {
            delay(delayMs.toLong())
            runSafely("đọc phần tử đang chạm") { if (menu == null) readHoverTarget(target, dropEnclosing = false) }
        }
    }

    /**
     * Đọc phần tử đang chạm, trừ khi đó là tiếng vọng ([HoverEchoGuard]): của chính phần tử vừa đọc, hoặc (khi [dropEnclosing]) của phần tử CHỨA nó.
     * Chạm vào một dòng thì hệ thống báo cho dòng rồi vài mili giây sau cho phần tử chứa nó (dòng thông báo bấm được); không bỏ thì dòng bị đọc, rồi cả
     * cụm bị đọc tiếp. Chạm có độ trễ (cài đặt độ trễ chạm) đi đường cũ ([dropEnclosing] = false): sự kiện mới đã hủy việc chờ của sự kiện cũ nên
     * chỉ có MỘT lần đọc.
     */
    private fun readHoverTarget(target: AccessibilityNodeInfo, dropEnclosing: Boolean) {
        val now = SystemClock.uptimeMillis()
        val read = if (dropEnclosing) {
            hoverGuard.shouldRead(target, now) { last ->
                last is AccessibilityNodeInfo && NodeCollector.findAncestorOrSelf(last) { it == target } != null
            }
        } else {
            hoverGuard.shouldRead(target, now)
        }
        if (!read) {
            hoverStats.record(HoverOutcome.ECHO)
            return
        }
        hoverStats.record(HoverOutcome.READ)
        navigator.focusOn(target)
    }

    /**
     * Việc chạy thẳng trên luồng chính (bộ nhận, Handler, listener cài đặt): một ngoại lệ ở đây sẽ làm sập cả tiến trình
     * và trình đọc chết theo, nên bắt lại, ghi nhật ký rồi đi tiếp.
     */
    private inline fun guardMain(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Lỗi khi $what", e)
            CrashLogger.recordNonFatal(what, e)
        }
    }

    /** Một lỗi runtime trong một tác vụ không được làm sập luồng xử lý; việc hủy coroutine vẫn được tôn trọng. */
    private inline fun runSafely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
        // Lớn hơn trước (32): rê ngón tay nhanh khi luồng xử lý bận không được làm rơi sự kiện nhấc tay (nhập phím).
        const val EVENT_BUFFER = 128

        const val WINDOW_FOCUS_DELAY_MS = 350L
        const val LIST_RANGE_SETTLE_MS = 450L
        const val DIALOG_CLASS_MARK = "Dialog"
        const val SHEET_CLASS_MARK = "BottomSheet"
        const val CONTENT_BUFFER = 16
        const val CONTENT_OFFER_MIN_INTERVAL_MS = 80L
        const val OWN_ACTION_ECHO_MS = 1_500L
        const val TOAST_CLASS_MARK = "Toast"
        const val TAB_CLASS_MARK = "Tab"
        const val LIVE_CONTENT_TYPES = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
        const val MENU_IDLE_TIMEOUT_MS = 30_000L
        const val MENU_TOUCH_REPEAT_MS = 1_500L
        const val SEARCH_RESUME_DELAY_MS = 500L
        const val VOICE_NOTICE_DELAY_MS = 2_500L
        const val FINGER_DOWN_MAX_MS = 30_000L
        val HANDLED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
        )
    }
}
