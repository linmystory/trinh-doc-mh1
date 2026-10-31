package com.example.vnscreenreader.ai.mlkit

import android.graphics.Bitmap
import com.example.vnscreenreader.ai.ImageLabelerPort
import com.example.vnscreenreader.ai.LabelScore
import com.example.vnscreenreader.ai.OcrBlock
import com.example.vnscreenreader.ai.OcrScript
import com.example.vnscreenreader.ai.TextRecognizerPort
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Chuyển `Task` của Google Play services thành hàm suspend (không cần thêm thư viện kotlinx-coroutines-play-services). */
private suspend fun <T> Task<T>.awaitResult(): T = withContext(NonCancellable) {
    // KHÔNG hủy giữa chừng: tác vụ ML Kit không hủy được và vẫn đọc bitmap; nếu trả về sớm thì `finally { recycle() }`
    // của người gọi sẽ giải phóng bitmap khi ML Kit còn đang dùng. Tác vụ ngắn (< vài giây); hủy được xử lý ngay sau khi xong.
    suspendCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.resumeWithException(CancellationException("ML Kit task canceled")) }
    }
}

/**
 * OCR chạy trên máy bằng mô hình ML Kit ĐÓNG GÓI SẴN trong APK (không tải thêm, không dùng mạng).
 * Latin (vi, en, fr, es...) hoặc Trung theo ngôn ngữ ứng dụng; dấu thanh tiếng Việt có thể sai ở chữ nhỏ/mờ.
 */
class MlKitTextRecognizer(script: OcrScript) : TextRecognizerPort {
    private val client = TextRecognition.getClient(
        when (script) {
            OcrScript.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
            // UNSUPPORTED không bao giờ tới đây (AiAssistant chặn trước); dùng Latin cho an toàn.
            OcrScript.LATIN, OcrScript.UNSUPPORTED -> TextRecognizerOptions.DEFAULT_OPTIONS
        },
    )

    override suspend fun recognize(image: Bitmap): List<OcrBlock> {
        val result = client.process(InputImage.fromBitmap(image, 0)).awaitResult()
        currentCoroutineContext().ensureActive()
        return result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                line.boundingBox?.let { OcrBlock(line.text, it.left, it.top, it.right, it.bottom, line.confidence) }
            }
        }
    }

    override fun close() = client.close()
}

/** Gán nhãn ảnh bằng mô hình ML Kit đóng gói sẵn (khoảng 400 nhãn, tiếng Anh). */
class MlKitImageLabeler : ImageLabelerPort {
    private val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    override suspend fun label(image: Bitmap): List<LabelScore> {
        val labels = client.process(InputImage.fromBitmap(image, 0)).awaitResult()
        currentCoroutineContext().ensureActive()
        return labels.map { LabelScore(it.text, it.confidence) }
    }

    override fun close() = client.close()
}
