package com.example.vnscreenreader.service

import android.graphics.Bitmap
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
import kotlinx.coroutines.cancel
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var recognizer: TextRecognizerPort? = null
    private var recognizerScript: OcrScript? = null
    private var labeler: ImageLabelerPort? = null

    /** Gọi trên luồng xử lý của service. */
    @Synchronized
    fun ocrScreen() = launchTask("ocr", S.AI_OCR_FAILED) {
        val recognizer = ocr() ?: return@launchTask speech.speak(t(S.OCR_UNSUPPORTED), true)
        speech.speak(t(S.AI_READING_SCREEN), true)
        val screenshot = overlay.withHidden { capture.capture() }.getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
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
        if (node.isPassword) {
            speech.speak(t(S.AI_PASSWORD_NO_DESCRIBE), true)
            return
        }
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        launchTask("ocr_focused", S.AI_OCR_FAILED) {
            val recognizer = ocr() ?: return@launchTask speech.speak(t(S.OCR_UNSUPPORTED), true)
            speech.speak(t(S.AI_READING_SCREEN), true)
            val screenshot = overlay.withHidden { capture.capture() }.getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
            var crop: Bitmap? = null
            try {
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
        if (node.isPassword) {
            speech.speak(t(S.AI_PASSWORD_NO_DESCRIBE), true)
            return
        }
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        launchTask("describe", S.AI_DESCRIBE_FAILED) {
            speech.speak(t(S.AI_DESCRIBING), true)
            val screenshot = overlay.withHidden { capture.capture() }.getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
            var crop: Bitmap? = null
            try {
                crop = cropOrNull(screenshot, bounds)
                if (crop == null) return@launchTask speech.speak(t(S.AI_TOO_SMALL), true)
                val description = ImageDescriptionBuilder.build(labeler().label(crop))
                // Ngôn ngữ chưa có OCR: vẫn mô tả vật thể, chỉ bỏ phần chữ (không đọc kết quả sai).
                val text = ocr()?.let { ocrSpeech(it.recognize(crop), maxChars = 300) }.orEmpty()
                speech.speak(if (text.isEmpty()) description else t(S.AI_TEXT_IN_IMAGE, description, text), true)
            } finally {
                crop?.takeIf { it !== screenshot }?.recycle()
                screenshot.recycle()
            }
        }
    }

    /** Hủy tác vụ đang chạy và giải phóng bộ nhận dạng. Idempotent. */
    @Synchronized
    fun close() {
        scope.cancel()
        runCatching { recognizer?.close() }
        runCatching { labeler?.close() }
        recognizer = null
        recognizerScript = null
        labeler = null
    }

    /** Bộ nhận dạng theo ngôn ngữ ứng dụng hiện hành (tạo lại khi đổi bộ chữ); null nếu chưa hỗ trợ ngôn ngữ này. */
    @Synchronized
    private fun ocr(): TextRecognizerPort? {
        val script = OcrScripts.forLanguage(Texts.provider.language)
        if (script == OcrScript.UNSUPPORTED) return null
        if (recognizerScript != script) {
            runCatching { recognizer?.close() }
            recognizer = recognizerFactory(script)
            recognizerScript = script
        }
        return recognizer
    }

    @Synchronized
    private fun labeler(): ImageLabelerPort = labeler ?: labelerFactory().also { labeler = it }

    /** Chữ để đọc từ kết quả OCR; độ tin cậy thấp thì thêm cảnh báo "có thể sai". */
    private fun ocrSpeech(blocks: List<OcrBlock>, maxChars: Int?): String {
        val text = if (maxChars == null) OcrTextFormatter.format(blocks) else OcrTextFormatter.format(blocks, maxChars = maxChars)
        if (text.isEmpty()) return text
        return if (AutoOcrPolicy.isLowConfidence(blocks.map { it.confidence })) t(S.OCR_MAYBE_WRONG, text) else text
    }

    private var autoJob: Job? = null
    private val autoTracker = AutoOcrTracker()

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
        if (!eligible || !autoTracker.tryStart(windowKey, elementKey)) return
        val recognizer = ocr() ?: return
        autoJob = scope.launch {
            try {
                delay(AUTO_OCR_DELAY_MS) // vuốt nhanh qua nhiều phần tử thì các lần chọn trước bị hủy, không chụp
                if (!node.refresh()) return@launch
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                val screenshot = overlay.withHidden { capture.capture() }.getOrNull() ?: return@launch
                var crop: Bitmap? = null
                try {
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

    private fun launchTask(what: String, failureMessage: S, block: suspend () -> Unit) {
        autoJob?.cancel()
        job?.cancel()
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
