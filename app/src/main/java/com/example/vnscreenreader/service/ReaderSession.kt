package com.example.vnscreenreader.service

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.vnscreenreader.core.EventDeduplicator
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.SpeechEngine
import com.example.vnscreenreader.core.SpeechTextBuilder
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureAnalyzer
import com.example.vnscreenreader.gesture.GestureMappingStore
import com.example.vnscreenreader.plugin.PluginRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
    private val speech = SpeechEngine(service, settings.speechRate, settings.pitch)
    private val overlay = HighlightOverlay(service)
    private val navigator = FocusNavigator(service, overlay) { text, interrupt -> speech.speak(text, interrupt) }
    private val dispatcher = ActionDispatcher(
        service = service,
        navigator = navigator,
        registryProvider = { PluginRegistry(service.applicationContext) },
        speech = speech,
    )

    private val events = Channel<ReaderEvent>(EVENT_BUFFER, BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val closed = AtomicBoolean(false)

    @Volatile private var mapping = gestureStore.load()

    // Giữ tham chiếu mạnh vì SharedPreferences chỉ giữ listener bằng weak reference.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        speech.updateVoice(settings.speechRate, settings.pitch)
    }
    private val gestureListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mapping = gestureStore.load()
    }

    /** Gọi trên luồng chính. */
    fun start() {
        overlay.attach()
        scope.launch { processEvents() }
        settings.register(settingsListener)
        gestureStore.register(gestureListener)
        speech.speak("Trình đọc màn hình đã bật")
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

    /** Trả về true để báo đã xử lý cử chỉ (kể cả khi được gán "Không làm gì"). */
    fun handleGesture(gestureId: Int): Boolean {
        val gesture = GestureAnalyzer.analyze(gestureId) ?: return false
        val action = mapping.actionFor(gesture)
        if (action == GestureAction.NONE) return true

        speech.stop() // ngắt lời ngay lập tức, không chờ luồng nền
        scope.launch { runSafely("thực hiện $action") { dispatcher.dispatch(action) } }
        return true
    }

    fun interrupt() = speech.stop()

    /** Idempotent. Gọi trên luồng chính (overlay phải gỡ trên luồng chính). */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        settings.unregister(settingsListener)
        gestureStore.unregister(gestureListener)
        events.close()
        scope.cancel()
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

    private fun handleEvent(event: ReaderEvent) {
        when (event.type) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED ->
                event.source?.let { navigator.focusOn(it) }

            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                event.source?.let { node ->
                    navigator.invalidateCache()
                    navigator.highlight(node)
                    speech.speak("Đã nhấn, " + navigator.describe(node), false)
                }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                navigator.reset()
                val title = event.text.trim()
                if (title.isNotEmpty()) speech.speak(title, true)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                navigator.invalidateCache()
                SpeechTextBuilder.describeTextChange(
                    fullText = event.text,
                    fromIndex = event.fromIndex,
                    addedCount = event.addedCount,
                    removedCount = event.removedCount,
                    isPassword = event.isPassword,
                )?.let { speech.speak(it, true) }
            }
        }
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
        val HANDLED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        )
    }
}
