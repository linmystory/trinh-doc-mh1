package com.example.vnscreenreader.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.ai.ImageDescriptionBuilder
import com.example.vnscreenreader.ai.ImageLabelerPort
import com.example.vnscreenreader.ai.OcrTextFormatter
import com.example.vnscreenreader.ai.OcrBlock
import com.example.vnscreenreader.ai.OcrScript
import com.example.vnscreenreader.ai.OcrScripts
import com.example.vnscreenreader.ai.ScreenCapture
import com.example.vnscreenreader.ai.TextRecognizerPort
import com.example.vnscreenreader.core.AutoOcrPolicy
import com.example.vnscreenreader.core.AutoOcrTracker
import com.example.vnscreenreader.core.SpeechEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.Texts
import com.example.vnscreenreader.core.t

/**
 * AI trên máy: đọc chữ trên màn hình (OCR) và mô tả hình ảnh của phần tử đang chọn.
 *
 * - Bộ nhận dạng được tạo LAZY ở lần dùng đầu (tiết kiệm RAM khi người dùng không dùng AI) và đóng ở [close].
 * - Chỉ một tác vụ AI chạy một lúc: gọi lại sẽ hủy tác vụ cũ (không chồng chất, không rò rỉ bitmap).
 * - Bitmap luôn được `recycle()` trong `finally`. Không có truy cập mạng, không ghi ảnh ra đĩa.
 */
internal class AiAssistant(
    private val capture: ScreenCapture,
    private val overlay: HighlightOverlay,
    private val navigator: FocusNavigator,
    private val speech: SpeechEngine,
    private val recognizerFactory: (OcrScript) -> TextRecognizerPort,
    private val labelerFactory: () -> ImageLabelerPort,
    /** true (mặc định) = che ô mật khẩu khỏi OCR/mô tả; false khi người dùng đã bật "đọc mật khẩu". */
    private val maskPasswords: () -> Boolean = { true },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var recognizer: TextRecognizerPort? = null
    private var recognizerScript: OcrScript? = null
    private var labeler: ImageLabelerPort? = null
    /** Bộ nhận dạng cũ (đã đổi bộ chữ) có thể còn đang chạy trong tác vụ khác: chỉ đóng ở [close], không đóng ngay. */
    private val retired = ArrayList<TextRecognizerPort>()
    private var closed = false
    /** Tác vụ [job] gắn với phần tử đang chọn (mô tả/đọc chữ ảnh): đổi phần tử thì hủy để không đọc nhầm phần tử cũ. */
    private var jobFocusBound = false

    /** Gọi trên luồng xử lý của service. */
    @Synchronized
    fun ocrScreen() = launchTask("ocr", S.AI_OCR_FAILED) {
        val recognizer = ocr() ?: return@launchTask speech.speak(t(S.OCR_UNSUPPORTED), true)
        speech.speak(t(S.AI_READING_SCREEN), true)
        val screenshot = captureMasked().getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
        try {
            val text = ocrSpeech(recognizer.recognize(screenshot), maxChars = null)
            speech.speak(if (text.isEmpty()) t(S.AI_NO_TEXT) else text, true)
        } finally {
            screenshot.recycle()
        }
    }

    /**
     * Chỉ đọc CHỮ trong vùng ảnh của phần tử đang chọn (không mô tả vật thể): dùng cho ảnh không nhãn như biển
     * hiệu, ảnh chụp tài liệu, nút dạng ảnh có chữ. Cùng đường xử lý với [describeFocused] nên cùng bảo vệ
     * ô mật khẩu và cùng giải phóng bitmap.
     */
    @Synchronized
    fun ocrFocused() {
        if (navigator.current() == null) {
            speech.speak(t(S.AI_DESCRIBE_NOTHING), true)
            return
        }
        val node = navigator.currentFresh() ?: return
        if (node.isPassword && maskPasswords()) {
            speech.speak(t(S.AI_PASSWORD_NO_DESCRIBE), true)
            return
        }
        launchTask("ocr_focused", S.AI_OCR_FAILED, focusBound = true) {
            val recognizer = ocr() ?: return@launchTask speech.speak(t(S.OCR_UNSUPPORTED), true)
            speech.speak(t(S.AI_READING_SCREEN), true)
            val screenshot = captureMasked().getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
            var crop: Bitmap? = null
            try {
                // Bounds lấy SAU khi chụp: nội dung có thể đã cuộn trong lúc chờ ẩn khung/đọc "đang đọc".
                val bounds = boundsNow(node) ?: return@launchTask speech.speak(t(S.AI_DESCRIBE_NOTHING), true)
                crop = cropOrNull(screenshot, bounds)
                if (crop == null) return@launchTask speech.speak(t(S.AI_TOO_SMALL), true)
                val text = ocrSpeech(recognizer.recognize(crop), maxChars = 600)
                speech.speak(if (text.isEmpty()) t(S.AI_NO_TEXT) else text, true)
            } finally {
                crop?.takeIf { it !== screenshot }?.recycle()
                screenshot.recycle()
            }
        }
    }

    @Synchronized
    fun describeFocused() {
        if (navigator.current() == null) {
            speech.speak(t(S.AI_DESCRIBE_NOTHING), true)
            return
        }
        val node = navigator.currentFresh() ?: return
        if (node.isPassword && maskPasswords()) {
            speech.speak(t(S.AI_PASSWORD_NO_DESCRIBE), true)
            return
        }
        launchTask("describe", S.AI_DESCRIBE_FAILED, focusBound = true) {
            speech.speak(t(S.AI_DESCRIBING), true)
            val screenshot = captureMasked().getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
            var crop: Bitmap? = null
            try {
                val bounds = boundsNow(node) ?: return@launchTask speech.speak(t(S.AI_DESCRIBE_NOTHING), true)
                crop = cropOrNull(screenshot, bounds)
                if (crop == null) return@launchTask speech.speak(t(S.AI_TOO_SMALL), true)
                val image = crop
                // Hai nguồn độc lập chạy SONG SONG; một nguồn lỗi không làm mất nguồn kia.
                val (labels, text) = coroutineScope {
                    val labelsJob = async { attempt { labeler().label(image) } }
                    // Ngôn ngữ chưa có OCR: vẫn mô tả vật thể, chỉ bỏ phần chữ (không đọc kết quả sai).
                    val textJob = async { attempt { ocr()?.let { ocrSpeech(it.recognize(image), maxChars = 300) }.orEmpty() } }
                    labelsJob.await() to textJob.await()
                }
                if (labels == null && text == null) throw IllegalStateException("Cả nhãn ảnh lẫn OCR đều lỗi")
                val ocrText = text.orEmpty()
                val description = labels?.let { ImageDescriptionBuilder.build(it) }
                speech.speak(
                    when {
                        description == null -> ocrText.ifEmpty { t(S.AI_DESCRIBE_FAILED) }
                        ocrText.isEmpty() -> description
                        else -> t(S.AI_TEXT_IN_IMAGE, description, ocrText)
                    },
                    true,
                )
            } finally {
                crop?.takeIf { it !== screenshot }?.recycle()
                screenshot.recycle()
            }
        }
    }

    /** Chạy [block]; lỗi thường → null (nguồn này bị bỏ), nhưng HỦY vẫn được ném lại. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Một nguồn AI lỗi", e)
        null
    }

    /** Làm mới nút rồi lấy vùng hiện tại; null nếu nút đã biến mất. */
    private fun boundsNow(node: AccessibilityNodeInfo): Rect? {
        if (!node.refresh()) return null
        return Rect().also { node.getBoundsInScreen(it) }
    }

    /** Chụp (ẩn khung viền) rồi tô đen mọi ô mật khẩu đang hiển thị, để OCR/mô tả không bao giờ thấy chúng. */
    private suspend fun captureMasked(): Result<Bitmap> {
        val result = overlay.withHidden { capture.capture() }
        val bitmap = result.getOrNull() ?: return result
        (if (maskPasswords()) runCatching { navigator.passwordBounds() }.getOrDefault(emptyList()) else emptyList()).takeIf { it.isNotEmpty() && bitmap.isMutable }?.let { areas ->
            val canvas = Canvas(bitmap)
            val paint = android.graphics.Paint().apply { color = Color.BLACK }
            areas.forEach { canvas.drawRect(it, paint) }
        }
        return result
    }

    /** Hủy tác vụ đang chạy và giải phóng bộ nhận dạng. Idempotent. */
    @Synchronized
    fun close() {
        closed = true
        scope.cancel()
        runCatching { recognizer?.close() }
        retired.forEach { r -> runCatching { r.close() } }
        retired.clear()
        runCatching { labeler?.close() }
        recognizer = null
        recognizerScript = null
        labeler = null
    }

    /** Bộ nhận dạng theo ngôn ngữ ứng dụng hiện hành (tạo lại khi đổi bộ chữ); null nếu chưa hỗ trợ ngôn ngữ này. */
    @Synchronized
    private fun ocr(): TextRecognizerPort? {
        if (closed) return null
        val script = OcrScripts.forLanguage(Texts.provider.language)
        if (script == OcrScript.UNSUPPORTED) return null
        if (recognizerScript != script) {
            recognizer?.let { retired.add(it) } // có thể đang được tác vụ khác dùng: đóng muộn ở close()
            recognizer = recognizerFactory(script)
            recognizerScript = script
        }
        return recognizer
    }

    @Synchronized
    private fun labeler(): ImageLabelerPort {
        check(!closed) { "AiAssistant đã đóng" }
        return labeler ?: labelerFactory().also { labeler = it }
    }

    /** Chữ để đọc từ kết quả OCR; độ tin cậy thấp thì thêm cảnh báo "có thể sai". */
    private fun ocrSpeech(blocks: List<OcrBlock>, maxChars: Int?): String {
        val text = if (maxChars == null) OcrTextFormatter.format(blocks) else OcrTextFormatter.format(blocks, maxChars = maxChars)
        if (text.isEmpty()) return text
        return if (AutoOcrPolicy.isLowConfidence(blocks.map { it.confidence })) t(S.OCR_MAYBE_WRONG, text) else text
    }

    private var autoJob: Job? = null
    private val autoTracker = AutoOcrTracker()

    /** Hủy mọi tác vụ AI đang chạy (đổi cửa sổ: kết quả của màn hình cũ không còn đúng). */
    @Synchronized
    fun cancelAll() {
        autoJob?.cancel()
        autoJob = null
        job?.cancel()
    }

    /** Hủy tác vụ tự đọc đang chạy (khi chọn phần tử khác hoặc người dùng tự yêu cầu đọc). */
    @Synchronized
    fun cancelAuto() {
        autoJob?.cancel()
        autoJob = null
    }

    /**
     * Gọi mỗi khi người dùng chọn một phần tử. Luôn hủy tác vụ tự đọc cũ (mỗi lần chỉ một tác vụ). Chỉ khi
     * [eligible] (xem [AutoOcrPolicy]) và phần tử này chưa được đọc trong cửa sổ này mới chụp màn hình, cắt đúng vùng
     * ảnh và nhận dạng. Chạy im lặng khi lỗi (ví dụ cửa sổ bảo mật cấm chụp); không ghi gì ra đĩa.
     */
    @Synchronized
    fun onFocusChanged(node: AccessibilityNodeInfo, eligible: Boolean, windowKey: String, elementKey: String) {
        cancelAuto()
        if (jobFocusBound) job?.cancel() // người dùng đã sang phần tử khác: không đọc kết quả của phần tử cũ
        if (!eligible || !autoTracker.tryStart(windowKey, elementKey)) return
        val recognizer = ocr() ?: return
        autoJob = scope.launch {
            try {
                delay(AUTO_OCR_DELAY_MS) // vuốt nhanh qua nhiều phần tử thì các lần chọn trước bị hủy, không chụp
                if (!node.refresh()) return@launch
                val screenshot = captureMasked().getOrNull() ?: return@launch
                var crop: Bitmap? = null
                try {
                    val bounds = boundsNow(node) ?: return@launch
                    crop = cropOrNull(screenshot, bounds) ?: return@launch
                    val text = ocrSpeech(recognizer.recognize(crop), maxChars = 300)
                    if (text.isNotEmpty()) speech.speak(t(S.OCR_AUTO_TEXT, text), false)
                } finally {
                    crop?.takeIf { it !== screenshot }?.recycle()
                    screenshot.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Tự đọc chữ trong ảnh lỗi", e)
            }
        }
    }

    private fun launchTask(what: String, failureMessage: S, focusBound: Boolean = false, block: suspend () -> Unit) {
        autoJob?.cancel()
        job?.cancel()
        jobFocusBound = focusBound
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Task failed: $what", e)
                speech.speak(t(failureMessage), true)
            }
        }
    }

    private fun cropOrNull(source: Bitmap, bounds: Rect): Bitmap? {
        val left = bounds.left.coerceIn(0, source.width)
        val top = bounds.top.coerceIn(0, source.height)
        val right = bounds.right.coerceIn(0, source.width)
        val bottom = bounds.bottom.coerceIn(0, source.height)
        if (right - left < MIN_CROP_PX || bottom - top < MIN_CROP_PX) return null
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private companion object {
        const val TAG = "AiAssistant"
        const val MIN_CROP_PX = 16
        const val AUTO_OCR_DELAY_MS = 500L
    }
}
