package com.example.vnscreenreader.ai.mlkit

import android.graphics.Bitmap
import com.example.vnscreenreader.ai.ImageLabelerPort
import com.example.vnscreenreader.ai.LabelScore
import com.example.vnscreenreader.ai.OcrBlock
import com.example.vnscreenreader.ai.TextRecognizerPort
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Chuyển `Task` của Google Play services thành hàm suspend (không cần thêm thư viện kotlinx-coroutines-play-services). */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * OCR chạy trên máy bằng mô hình ML Kit ĐÓNG GÓI SẴN trong APK (không tải thêm, không dùng mạng).
 * Bộ nhận dạng Latin đọc được chữ Việt ở mức cơ bản; dấu thanh có thể sai ở chữ nhỏ/mờ.
 */
class MlKitTextRecognizer : TextRecognizerPort {
    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(image: Bitmap): List<OcrBlock> {
        val result = client.process(InputImage.fromBitmap(image, 0)).awaitResult()
        return result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                line.boundingBox?.let { OcrBlock(line.text, it.left, it.top, it.right, it.bottom) }
            }
        }
    }

    override fun close() = client.close()
}

/** Gán nhãn ảnh bằng mô hình ML Kit đóng gói sẵn (khoảng 400 nhãn, tiếng Anh). */
class MlKitImageLabeler : ImageLabelerPort {
    private val client = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    override suspend fun label(image: Bitmap): List<LabelScore> =
        client.process(InputImage.fromBitmap(image, 0)).awaitResult().map { LabelScore(it.text, it.confidence) }

    override fun close() = client.close()
}
