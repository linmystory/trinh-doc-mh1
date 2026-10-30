package com.example.vnscreenreader.core

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bọc TextToSpeech: giọng tiếng Việt, luồng âm thanh dành cho hỗ trợ tiếp cận, ngắt lời tức thì
 * và hàng đợi thông minh. An toàn khi gọi từ nhiều luồng.
 *
 * Khác biệt so với bản cũ:
 * - Văn bản dài được chia nhỏ (TTS từ chối > 4000 ký tự, trước đây người dùng không nghe thấy gì).
 * - Khởi tạo thất bại (hay gặp ngay sau khi khởi động máy) được thử lại với backoff, không câm vĩnh viễn.
 * - `onInit` luôn được xử lý trên luồng chính SAU khi constructor của TextToSpeech trả về: khi lỗi,
 *   TextToSpeech có thể gọi `onInit` đồng bộ ngay trong constructor lúc biến `tts` còn null.
 * - Mọi chuyển trạng thái (ready/pending/closed) nằm dưới một khóa duy nhất → hết race condition
 *   giữa luồng chính (onInit) và luồng nền (speak).
 */
class SpeechEngine(
    context: Context,
    initialRate: Float,
    initialPitch: Float,
    initialEnginePackage: String = "",
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    // Các trường dưới đây chỉ được đọc/ghi khi giữ [lock].
    private val lock = Any()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var generation = 0 // tăng mỗi lần tạo engine mới / đóng; callback của thế hệ cũ bị bỏ qua
    private var failures = 0
    private var rate = initialRate
    private var pitch = initialPitch
    private var pendingText: String? = null
    private var enginePackage = initialEnginePackage

    @Volatile private var lastText: String? = null

    private val pendingIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Tăng mỗi khi stop(): một lần speak() đang chia nhỏ văn bản dài sẽ dừng xếp hàng các đoạn còn lại.
    private val stopEpoch = AtomicInteger()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            utteranceId?.let { pendingIds.remove(it) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            utteranceId?.let { pendingIds.remove(it) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId?.let { pendingIds.remove(it) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            utteranceId?.let { pendingIds.remove(it) }
        }
    }

    init {
        connect()
    }

    /** Tạo (hoặc tạo lại) engine TextToSpeech. Luôn chạy trên luồng chính. */
    private fun connect() {
        val gen = synchronized(lock) {
            if (closed) return
            ++generation
        }
        // Callback của TextToSpeech có thể chạy đồng bộ khi lỗi → luôn chuyển qua handler để `tts` đã được gán.
        val listener = TextToSpeech.OnInitListener { status -> mainHandler.post { onInitResult(gen, status) } }
        val pkg = synchronized(lock) { enginePackage }
        val engine = if (pkg.isEmpty()) TextToSpeech(appContext, listener) else TextToSpeech(appContext, listener, pkg)
        val stale = synchronized(lock) {
            if (closed || gen != generation) {
                true
            } else {
                tts = engine
                false
            }
        }
        if (stale) engine.shutdown()
    }

    private fun onInitResult(gen: Int, status: Int) {
        val engine = synchronized(lock) {
            if (closed || gen != generation) return
            tts ?: return
        }
        if (status != TextToSpeech.SUCCESS) {
            onInitFailed(engine, status)
            return
        }
        configure(engine)
        val queued = synchronized(lock) {
            if (closed || gen != generation) return
            ready = true
            failures = 0
            pendingText.also { pendingText = null }
        }
        if (queued != null) speak(queued, interrupt = true)
    }

    private fun onInitFailed(engine: TextToSpeech, status: Int) {
        Log.e(TAG, "Không khởi tạo được TextToSpeech, status=$status")
        val retryDelayMs = synchronized(lock) {
            tts = null
            failures++
            SpeechRetryPolicy.delayMs(failures)
        }
        engine.shutdown()
        if (retryDelayMs != null) {
            mainHandler.postDelayed({ connect() }, retryDelayMs)
        } else {
            Log.e(TAG, "Bỏ cuộc sau ${SpeechRetryPolicy.MAX_RETRIES} lần thử lại khởi tạo TextToSpeech")
        }
    }

    private fun configure(engine: TextToSpeech) {
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        applyLanguage(engine)
        engine.setOnUtteranceProgressListener(progressListener)
        val (currentRate, currentPitch) = synchronized(lock) { rate to pitch }
        engine.setSpeechRate(currentRate)
        engine.setPitch(currentPitch)
    }

    /** Đọc theo ngôn ngữ hiện hành của ứng dụng (đổi trong Cài đặt hệ thống → Ngôn ngữ ứng dụng). */
    private fun applyLanguage(engine: TextToSpeech) {
        // Lấy theo cấu hình tài nguyên của ứng dụng (đã áp dụng "ngôn ngữ theo ứng dụng"), không theo Locale.getDefault().
        val locale: Locale = appContext.resources.configuration.locales[0]
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Không có giọng cho ngôn ngữ này: giữ giọng hiện có của engine (không im lặng) và ghi log để dễ chẩn đoán.
            Log.w(TAG, "No TTS voice for $locale (result=$result); keeping the engine's current voice.")
        }
    }

    /** Gọi khi người dùng đổi ngôn ngữ ứng dụng lúc dịch vụ đang chạy. */
    fun updateLanguage() {
        val engine = synchronized(lock) { if (closed || !ready) null else tts } ?: return
        applyLanguage(engine)
    }

    /**
     * interrupt = true: ngắt câu đang đọc ngay lập tức. false: xếp hàng (tự flush nếu hàng đợi quá dài).
     * Chưa sẵn sàng: giữ lại câu mới nhất và đọc ngay khi engine khởi tạo xong.
     */
    fun speak(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        lastText = text
        val engine = synchronized(lock) {
            if (closed) return
            if (!ready) {
                pendingText = text
                return
            }
            tts
        } ?: return

        val epoch = stopEpoch.get()
        val chunks = SpeechChunker.split(text, TextToSpeech.getMaxSpeechInputLength())
        for ((index, chunk) in chunks.withIndex()) {
            if (stopEpoch.get() != epoch) return // stop() được gọi giữa chừng: bỏ các đoạn còn lại
            // Chỉ đoạn đầu mới theo chính sách hàng đợi; các đoạn sau luôn nối tiếp để không tự flush chính mình.
            val flush = index == 0 &&
                SpeechQueuePolicy.modeFor(interrupt, pendingIds.size) == SpeechQueuePolicy.Mode.FLUSH
            enqueue(engine, chunk, flush)
        }
    }

    private fun enqueue(engine: TextToSpeech, chunk: String, flush: Boolean) {
        val queueMode = if (flush) {
            pendingIds.clear()
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }
        val id = UUID.randomUUID().toString()
        pendingIds.add(id)
        if (engine.speak(chunk, queueMode, null, id) == TextToSpeech.ERROR) {
            pendingIds.remove(id)
            Log.w(TAG, "TextToSpeech.speak trả về ERROR")
        }
    }

    /**
     * Đổi engine TTS (gói rỗng = mặc định hệ thống) và kết nối lại ngay, không cần khởi động lại dịch vụ.
     * Chạy được từ luồng bất kỳ; việc tạo TextToSpeech luôn diễn ra trên luồng chính.
     */
    fun changeEngine(newPackage: String) {
        val old = synchronized(lock) {
            if (closed || newPackage == enginePackage) return
            enginePackage = newPackage
            ready = false
            failures = 0
            generation++ // vô hiệu callback của engine cũ
            pendingText = null
            tts.also { tts = null }
        }
        pendingIds.clear()
        old?.stop()
        old?.shutdown()
        mainHandler.post { connect() }
    }

    /** Đọc lại câu gần nhất (để nghe lại điều vừa bỏ lỡ). @return false nếu chưa đọc gì. */
    fun repeatLast(): Boolean {
        val text = lastText ?: return false
        speak(text, interrupt = true)
        return true
    }

    fun updateVoice(newRate: Float, newPitch: Float) {
        val engine = synchronized(lock) {
            rate = newRate
            pitch = newPitch
            if (ready) tts else null
        }
        engine?.setSpeechRate(newRate)
        engine?.setPitch(newPitch)
    }

    fun stop() {
        stopEpoch.incrementAndGet()
        pendingIds.clear()
        val engine = synchronized(lock) {
            pendingText = null // câu đang chờ engine khởi tạo đã cũ, không đọc nữa
            tts
        }
        engine?.stop()
    }

    fun shutdown() {
        val engine = synchronized(lock) {
            if (closed) return
            closed = true
            ready = false
            generation++
            pendingText = null
            tts.also { tts = null }
        }
        mainHandler.removeCallbacksAndMessages(null)
        pendingIds.clear()
        engine?.stop()
        engine?.shutdown()
    }

    private companion object {
        const val TAG = "SpeechEngine"
    }
}
