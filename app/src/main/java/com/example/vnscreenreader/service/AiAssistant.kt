package com.example.vnscreenreader.service

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.vnscreenreader.ai.ImageDescriptionBuilder
import com.example.vnscreenreader.ai.ImageLabelerPort
import com.example.vnscreenreader.ai.OcrTextFormatter
import com.example.vnscreenreader.ai.ScreenCapture
import com.example.vnscreenreader.ai.TextRecognizerPort
import com.example.vnscreenreader.core.SpeechEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.vnscreenreader.core.S
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
    private val navigator: FocusNavigator,
    private val speech: SpeechEngine,
    private val recognizerFactory: () -> TextRecognizerPort,
    private val labelerFactory: () -> ImageLabelerPort,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var recognizer: TextRecognizerPort? = null
    private var labeler: ImageLabelerPort? = null

    /** Gọi trên luồng xử lý của service. */
    @Synchronized
    fun ocrScreen() = launchTask("ocr", S.AI_OCR_FAILED) {
        speech.speak(t(S.AI_READING_SCREEN), true)
        val screenshot = capture.capture().getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
        try {
            val text = OcrTextFormatter.format(ocr().recognize(screenshot))
            speech.speak(if (text.isEmpty()) t(S.AI_NO_TEXT) else text, true)
        } finally {
            screenshot.recycle()
        }
    }

    @Synchronized
    fun describeFocused() {
        val node = navigator.current()
        if (node == null) {
            speech.speak(t(S.AI_DESCRIBE_NOTHING), true)
            return
        }
        if (node.isPassword) {
            speech.speak(t(S.AI_PASSWORD_NO_DESCRIBE), true)
            return
        }
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        launchTask("describe", S.AI_DESCRIBE_FAILED) {
            speech.speak(t(S.AI_DESCRIBING), true)
            val screenshot = capture.capture().getOrElse { return@launchTask speech.speak(it.message ?: t(S.SHOT_FAILED), true) }
            var crop: Bitmap? = null
            try {
                crop = cropOrNull(screenshot, bounds)
                if (crop == null) return@launchTask speech.speak(t(S.AI_TOO_SMALL), true)
                val description = ImageDescriptionBuilder.build(labeler().label(crop))
                val text = OcrTextFormatter.format(ocr().recognize(crop), maxChars = 300)
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
        labeler = null
    }

    @Synchronized
    private fun ocr(): TextRecognizerPort = recognizer ?: recognizerFactory().also { recognizer = it }

    @Synchronized
    private fun labeler(): ImageLabelerPort = labeler ?: labelerFactory().also { labeler = it }

    private fun launchTask(what: String, failureMessage: S, block: suspend () -> Unit) {
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
    }
}
