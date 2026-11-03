package com.example.vnscreenreader.core

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * MỘT công cụ đọc (một kết nối TextToSpeech). [SpeechEngine] ghép một hoặc hai [VoiceEngine] tùy chế độ.
 *
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
class VoiceEngine(
    context: Context,
    initialRate: Float,
    initialPitch: Float,
    initialEnginePackage: String = "",
    /** Ngôn ngữ công cụ này đọc; mặc định là ngôn ngữ ứng dụng. Gọi lại mỗi lần áp ngôn ngữ nên đổi được lúc chạy. */
    private val localeProvider: () -> Locale = { context.applicationContext.resources.configuration.locales[0] },
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
    private var pendingFinished: (() -> Unit)? = null
    private var enginePackage = initialEnginePackage

    /** Câu gần nhất ở dạng GỐC (chưa lập kế hoạch) để "đọc lại" lập lại đúng các khoảng lặng. */
    @Volatile private var lastText: String? = null

    /**
     * Lập kế hoạch đọc từ chữ gốc: đổi emoji thành tên và xen khoảng lặng giữa các mục. Đặt một lần khi khởi tạo
     * phiên; mặc định đọc nguyên văn.
     */
    @Volatile var planner: (String) -> List<SpeechPart> = { SpeechPlan.single(it) }

    // Đếm theo CÂU (một lần speak gồm nhiều đoạn và khoảng lặng), không theo từng đoạn phát.
    private val pending = SpeechPendingTracker()

    /** Hàm gọi khi cả câu xong (theo mã câu). Câu bị ngắt/hủy thì KHÔNG gọi (người gọi tự theo dõi việc ngắt). */
    private val finishedCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    private fun completeUtterance(utteranceId: String?) {
        val message = utteranceId?.let { pending.complete(it) } ?: return
        finishedCallbacks.remove(message)?.invoke()
    }

    // Phục hồi khi engine chết giữa chừng (xem SpeechRecoveryPolicy).
    private val recovery = SpeechRecoveryPolicy()

    /** true khi engine không có giọng cho ngôn ngữ ứng dụng (người dùng nghe bằng giọng khác ngôn ngữ). */
    @Volatile
    var voiceMissing: Boolean = false
        private set

    /** Gọi trên luồng chính mỗi khi kiểm tra lại giọng: (thiếu giọng hay không, ngôn ngữ đã kiểm tra). */
    @Volatile
    var onVoiceStatus: ((missing: Boolean, locale: Locale) -> Unit)? = null

    // Tăng mỗi khi stop(): một lần speak() đang chia nhỏ văn bản dài sẽ dừng xếp hàng các đoạn còn lại.
    private val stopEpoch = AtomicInteger()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            completeUtterance(utteranceId)
            recovery.reset() // đọc xong bình thường: engine đã khỏe
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            completeUtterance(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            completeUtterance(utteranceId)
            if (errorCode == TextToSpeech.ERROR_SERVICE) recover("onError ERROR_SERVICE", null, null)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            completeUtterance(utteranceId)
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
            Pair(pendingText, pendingFinished).also { pendingText = null; pendingFinished = null }
        }
        if (queued.first != null) speak(queued.first!!, interrupt = true, onFinished = queued.second)
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
        // CÙNG nguồn với chữ giao diện ([com.example.vnscreenreader.i18n.ResourceTexts.language]): giọng và chữ không lệch nhau.
        val locale: Locale = localeProvider()
        val result = engine.setLanguage(locale)
        val missing = result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
        if (missing) {
            // Không có giọng cho ngôn ngữ này: giữ giọng hiện có của engine (không im lặng), ghi log, và báo ra ngoài
            // để dịch vụ nhắc người dùng cài dữ liệu giọng (nếu không họ nghe sai ngôn ngữ mà không biết lý do).
            Log.w(TAG, "No TTS voice for $locale (result=$result); keeping the engine's current voice.")
        }
        voiceMissing = missing
        onVoiceStatus?.invoke(missing, locale)
    }

    /** Gọi khi người dùng đổi ngôn ngữ ứng dụng lúc dịch vụ đang chạy. */
    fun updateLanguage() {
        val engine = synchronized(lock) { if (closed || !ready) null else tts } ?: return
        applyLanguage(engine)
    }

    /**
     * interrupt = true: ngắt câu đang đọc ngay lập tức. false: xếp hàng (tự flush nếu hàng đợi quá dài).
     * Chưa sẵn sàng: giữ lại câu mới nhất và đọc ngay khi engine khởi tạo xong.
     *
     * [onFinished] được gọi (từ luồng của TTS) khi cả câu đọc xong. Không được gọi nếu câu bị ngắt hoặc bị hủy.
     * @return true nếu câu đã được nhận (sẽ có [onFinished] khi xong); false nếu không có gì để đọc (rỗng, bị bỏ vì hàng
     *   đầy, đã đóng hoặc engine từ chối): khi đó [onFinished] KHÔNG được gọi và người gọi phải tự đi tiếp.
     */
    fun speak(rawText: String, interrupt: Boolean = true, onFinished: (() -> Unit)? = null): Boolean {
        if (rawText.isBlank()) return false
        // NFC: chữ tiếng Việt viết dạng rời ("a" + dấu) được gộp lại để mọi engine đọc giống nhau.
        val text = Graphemes.nfc(rawText)
        val parts = SpeechPlan.chunk(planner(text), TextToSpeech.getMaxSpeechInputLength())
        if (parts.isEmpty()) return false
        lastText = text
        val engine = synchronized(lock) {
            if (closed) return false
            if (!ready) {
                pendingText = text
                pendingFinished = onFinished
                return true
            }
            tts
        } ?: return false

        val epoch = stopEpoch.get()
        val messageId = UUID.randomUUID().toString()
        // Chính sách hàng đợi chỉ quyết định MỘT lần cho cả câu; các đoạn sau luôn nối tiếp để không tự flush chính mình.
        val mode = SpeechQueuePolicy.modeFor(interrupt, pending.pendingMessages())
        if (mode == SpeechQueuePolicy.Mode.DROP) return false
        val flush = mode == SpeechQueuePolicy.Mode.FLUSH
        // Ngắt: các câu cũ bị hủy nên hàm "xong" của chúng bị bỏ (đặt trước khi đăng ký hàm của câu mới).
        if (flush) finishedCallbacks.clear()
        if (onFinished != null) finishedCallbacks[messageId] = onFinished
        val currentRate = synchronized(lock) { rate }
        for ((index, part) in parts.withIndex()) {
            if (stopEpoch.get() != epoch) {
                finishedCallbacks.remove(messageId)
                return false // stop() được gọi giữa chừng: bỏ các phần còn lại
            }
            if (!enqueue(engine, part, flush && index == 0, messageId, currentRate, text, onFinished)) {
                finishedCallbacks.remove(messageId)
                return false
            }
        }
        return true
    }

    /** @return false nếu engine từ chối (đã yêu cầu phục hồi): không xếp tiếp các đoạn còn lại của câu. */
    private fun enqueue(
        engine: TextToSpeech,
        part: SpeechPart,
        flush: Boolean,
        messageId: String,
        speechRate: Float,
        text: String,
        onFinished: (() -> Unit)?,
    ): Boolean {
        val queueMode = if (flush) {
            pending.clear()
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }
        val id = UUID.randomUUID().toString()
        pending.register(messageId, id)
        val result = when (part) {
            is SpeechPart.Say -> engine.speak(part.text, queueMode, null, id)
            is SpeechPart.Pause ->
                engine.playSilentUtterance(SpeechPlan.scaledPause(part.millis, speechRate).toLong(), queueMode, id)
        }
        if (result != TextToSpeech.ERROR) return true
        pending.complete(id)
        Log.w(TAG, "TextToSpeech trả về ERROR khi xếp hàng một đoạn")
        // Engine chết (tiến trình TTS bị tắt hoặc đang cập nhật) trả ERROR cho mọi lệnh: tạo lại engine và đọc lại câu
        // vừa lỡ. Đoạn im lặng không tính, vì lỗi ở đó không chứng minh engine hỏng.
        if (part is SpeechPart.Say && part.text.isNotBlank()) {
            recover("speak trả về ERROR", text, onFinished)
            return false
        }
        return true
    }

    /**
     * Tạo lại engine khi nó chết giữa chừng, nếu [SpeechRecoveryPolicy] cho phép. [failedText] (nếu có) được đọc lại
     * ngay khi engine mới sẵn sàng. Chạy được từ luồng bất kỳ; việc tạo TextToSpeech luôn diễn ra trên luồng chính.
     */
    private fun recover(reason: String, failedText: String?, failedFinished: (() -> Unit)?) {
        val old = synchronized(lock) {
            if (closed || !ready || !recovery.tryAcquire(SystemClock.elapsedRealtime())) return
            ready = false
            failures = 0
            generation++ // vô hiệu callback của engine cũ
            pendingText = failedText
            pendingFinished = failedFinished
            tts.also { tts = null }
        }
        Log.w(TAG, "Phục hồi TextToSpeech: $reason")
        pending.clear()
        finishedCallbacks.clear()
        try {
            old?.shutdown()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Đóng engine cũ lỗi", e)
        }
        mainHandler.post { connect() }
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
            pendingFinished = null
            tts.also { tts = null }
        }
        pending.clear()
        finishedCallbacks.clear()
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

    /** Gói công cụ TTS đang dùng (rỗng = mặc định hệ thống). */
    fun currentPackage(): String = synchronized(lock) { enginePackage }

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
        pending.clear()
        finishedCallbacks.clear()
        val engine = synchronized(lock) {
            pendingText = null // câu đang chờ engine khởi tạo đã cũ, không đọc nữa
            pendingFinished = null
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
            pendingFinished = null
            tts.also { tts = null }
        }
        mainHandler.removeCallbacksAndMessages(null)
        pending.clear()
        finishedCallbacks.clear()
        engine?.stop()
        engine?.shutdown()
    }

    private companion object {
        const val TAG = "SpeechEngine"
    }
}
