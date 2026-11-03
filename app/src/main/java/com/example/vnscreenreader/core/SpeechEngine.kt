package com.example.vnscreenreader.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Giọng đọc của ứng dụng: ghép một hoặc hai [VoiceEngine] theo [VoiceMode].
 *
 * - [VoiceMode.SINGLE]: một công cụ đọc mọi thứ, đúng như bản cũ (không có đường mã mới nào chen vào).
 * - [VoiceMode.BOTH]: công cụ thứ nhất đọc ngôn ngữ ứng dụng, công cụ thứ hai đọc ngôn ngữ thứ hai (ví dụ tiếng Anh).
 *   Văn bản trộn được tách đoạn ([LanguageRuns]) và phát LẦN LƯỢT từng đoạn ([RunScheduler]) để hai công cụ không đọc
 *   chồng lên nhau. Văn bản chỉ một ngôn ngữ thì vẫn đi thẳng một đoạn.
 *
 * An toàn khi gọi từ nhiều luồng.
 */
class SpeechEngine(
    context: Context,
    initialRate: Float,
    initialPitch: Float,
    initialEnginePackage: String = "",
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    // Các trường dưới đây chỉ được đọc/ghi khi giữ [lock].
    private var rate = initialRate
    private var pitch = initialPitch
    private var primaryPackage = initialEnginePackage
    private var secondPackage = ""
    private var mode = VoiceMode.SINGLE
    private var primary: VoiceEngine? = null
    private var second: VoiceEngine? = null
    private var closed = false
    private val missingBy = HashMap<String, Pair<Boolean, Locale>>()
    private val scheduler = RunScheduler()
    private var watchdog: Runnable? = null

    @Volatile private var secondTag = DEFAULT_SECOND_LANGUAGE
    @Volatile private var splitMode = SplitMode.WORD

    /** Ngôn ngữ công cụ thứ nhất đọc; rỗng = theo ngôn ngữ ứng dụng. */
    @Volatile private var firstTag = ""
    @Volatile private var lastText: String? = null

    /** Lập kế hoạch đọc (emoji, khoảng lặng) cho từng đoạn; áp cho mọi công cụ. */
    @Volatile var planner: (String) -> List<SpeechPart> = { SpeechPlan.single(it) }
        set(value) {
            field = value
            synchronized(lock) { listOfNotNull(primary, second).forEach { it.planner = value } }
        }

    /** Gọi trên luồng chính mỗi khi kiểm tra lại giọng của MỘT công cụ: (thiếu giọng hay không, ngôn ngữ đã kiểm tra). */
    @Volatile var onVoiceStatus: ((missing: Boolean, locale: Locale) -> Unit)? = null

    /**
     * Gộp trạng thái giọng của từng công cụ: báo "thiếu" nếu BẤT KỲ công cụ nào đang dùng thiếu giọng. Nếu không gộp,
     * công cụ này báo đủ giọng sẽ xóa mất cảnh báo thiếu giọng của công cụ kia.
     */
    private fun reportVoiceStatus(key: String, missing: Boolean, locale: Locale) {
        val (anyMissing, shown) = synchronized(lock) {
            missingBy[key] = missing to locale
            val firstMissing = missingBy.values.firstOrNull { it.first }
            (firstMissing != null) to (firstMissing?.second ?: locale)
        }
        onVoiceStatus?.invoke(anyMissing, shown)
    }

    /** true khi một công cụ đang dùng không có giọng cho ngôn ngữ của nó. */
    val voiceMissing: Boolean
        get() = synchronized(lock) { listOfNotNull(primary, second).any { it.voiceMissing } }

    init {
        synchronized(lock) { primary = newEngine(primaryPackage, appLocale = true) }
    }

    private fun appLocale(): Locale = appContext.resources.configuration.locales[0]

    private fun firstLocale(): Locale = if (firstTag.isBlank()) appLocale() else Locale.forLanguageTag(firstTag)

    private fun newEngine(pkg: String, appLocale: Boolean): VoiceEngine =
        VoiceEngine(
            appContext, rate, pitch, pkg,
            if (appLocale) ({ firstLocale() }) else ({ Locale.forLanguageTag(secondTag) }),
        ).also {
            it.planner = planner
            val key = if (appLocale) "first" else "second"
            it.onVoiceStatus = { missing, locale -> reportVoiceStatus(key, missing, locale) }
        }

    /**
     * Áp chế độ công cụ. Gọi được từ luồng bất kỳ; đổi chế độ tạo hoặc đóng kết nối ngay, không cần khởi động lại dịch vụ.
     * @param secondPackage gói công cụ thứ hai (rỗng = mặc định hệ thống).
     * @param secondLanguageTag ngôn ngữ công cụ thứ hai, dạng BCP 47 ("en", "zh"...).
     * @param split cách chia văn bản giữa hai công cụ (chỉ dùng ở [VoiceMode.BOTH]).
     * @param firstLanguageTag ngôn ngữ công cụ thứ nhất đọc (rỗng = ngôn ngữ ứng dụng).
     */
    fun configure(
        newMode: VoiceMode,
        secondPackage: String,
        secondLanguageTag: String,
        split: SplitMode = SplitMode.WORD,
        firstLanguageTag: String = "",
    ) {
        val toShutdown = ArrayList<VoiceEngine>()
        var languageChanged = false
        synchronized(lock) {
            if (closed) return
            languageChanged = secondLanguageTag != secondTag
            splitMode = split
            val firstChanged = firstLanguageTag != firstTag
            firstTag = firstLanguageTag
            if (firstChanged) primary?.updateLanguage()
            secondTag = secondLanguageTag.ifBlank { DEFAULT_SECOND_LANGUAGE }
            val needSecond = newMode == VoiceMode.BOTH
            if (primary == null) primary = newEngine(primaryPackage, appLocale = true)
            if (needSecond) {
                val current = second
                if (current == null) {
                    second = newEngine(secondPackage, appLocale = false)
                } else {
                    if (secondPackage != this.secondPackage) current.changeEngine(secondPackage)
                    if (languageChanged) current.updateLanguage()
                }
            } else {
                second?.let { toShutdown += it; second = null; missingBy.remove("second") }
            }
            this.secondPackage = secondPackage
            if (newMode != mode) {
                mode = newMode
                scheduler.clear()
                cancelWatchdog()
            }
        }
        toShutdown.forEach { it.stop(); it.shutdown() }
    }

    /**
     * interrupt = true: ngắt câu đang đọc ngay. false: xếp hàng (bị bỏ nếu hàng đã đầy). Xem [VoiceEngine.speak].
     */
    fun speak(rawText: String, interrupt: Boolean = true) {
        if (rawText.isBlank()) return
        lastText = rawText
        val direct: VoiceEngine?
        synchronized(lock) {
            if (closed) return
            direct = when (mode) {
                VoiceMode.SINGLE -> primary
                VoiceMode.BOTH -> null
            }
        }
        if (direct != null) {
            direct.speak(rawText, interrupt)
            return
        }
        val runs = LanguageRuns.split(rawText, firstLocale().toLanguageTag(), secondTag, splitMode)
        synchronized(lock) {
            if (closed) return
            if (interrupt) {
                // Hai công cụ không chia sẻ hàng đợi: phải dừng cả hai, nếu không công cụ kia còn đọc chồng lên.
                primary?.stop()
                second?.stop()
            }
            if (!scheduler.submit(runs, interrupt)) return
            pumpLocked()
        }
    }

    /** Phát đoạn kế tiếp nếu rảnh. Gọi khi đang giữ [lock]. */
    private fun pumpLocked() {
        while (true) {
            val (run, token) = scheduler.next() ?: return
            val engine = when (run.side) {
                LanguageRuns.Side.PRIMARY -> primary ?: second
                LanguageRuns.Side.SECOND -> second ?: primary
            }
            val accepted = engine?.speak(run.text, interrupt = true) { onRunFinished(token) } ?: false
            if (accepted) {
                armWatchdogLocked(token, run.text.length)
                return
            }
            scheduler.finished(token) // không phát được: sang đoạn kế
        }
    }

    private fun onRunFinished(token: Int) {
        synchronized(lock) {
            if (closed || !scheduler.finished(token)) return
            cancelWatchdog()
            pumpLocked()
        }
    }

    /**
     * Phòng khi một công cụ chết giữa chừng và không bao giờ báo "xong": hết thời gian dự kiến thì tự đi tiếp để hàng đợi
     * không kẹt vĩnh viễn.
     */
    private fun armWatchdogLocked(token: Int, chars: Int) {
        cancelWatchdog()
        val task = Runnable { onRunFinished(token) }
        watchdog = task
        mainHandler.postDelayed(task, WATCHDOG_BASE_MS + chars * WATCHDOG_PER_CHAR_MS)
    }

    private fun cancelWatchdog() {
        watchdog?.let { mainHandler.removeCallbacks(it) }
        watchdog = null
    }

    /** Gọi khi người dùng đổi ngôn ngữ ứng dụng lúc dịch vụ đang chạy. */
    fun updateLanguage() {
        val engines = synchronized(lock) { listOfNotNull(primary, second) }
        engines.forEach { it.updateLanguage() }
    }

    /** Đổi công cụ TTS thứ nhất (gói rỗng = mặc định hệ thống). Công cụ thứ hai đổi qua [configure]. */
    fun changeEngine(newPackage: String) {
        val engine = synchronized(lock) {
            if (closed || newPackage == primaryPackage) return
            primaryPackage = newPackage
            primary
        }
        engine?.changeEngine(newPackage)
    }

    /** Đọc lại câu gần nhất. @return false nếu chưa đọc gì. */
    fun repeatLast(): Boolean {
        val text = lastText ?: return false
        speak(text, interrupt = true)
        return true
    }

    fun updateVoice(newRate: Float, newPitch: Float) {
        val engines = synchronized(lock) {
            rate = newRate
            pitch = newPitch
            listOfNotNull(primary, second)
        }
        engines.forEach { it.updateVoice(newRate, newPitch) }
    }

    fun stop() {
        val engines = synchronized(lock) {
            scheduler.clear()
            cancelWatchdog()
            listOfNotNull(primary, second)
        }
        engines.forEach { it.stop() }
    }

    fun shutdown() {
        val engines = synchronized(lock) {
            if (closed) return
            closed = true
            scheduler.clear()
            cancelWatchdog()
            listOfNotNull(primary, second).also { primary = null; second = null }
        }
        engines.forEach { it.stop(); it.shutdown() }
    }

    companion object {
        const val DEFAULT_SECOND_LANGUAGE = "en"
        private const val WATCHDOG_BASE_MS = 4000L
        private const val WATCHDOG_PER_CHAR_MS = 150L
    }
}
