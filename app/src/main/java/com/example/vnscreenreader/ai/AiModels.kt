package com.example.vnscreenreader.ai

/** Một khối chữ do OCR nhận ra, tọa độ theo pixel ảnh (gốc ở góc trên trái). */
data class OcrBlock(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** Một nhãn do bộ phân loại ảnh trả về (văn bản tiếng Anh gốc, độ tin cậy 0..1). */
data class LabelScore(val text: String, val confidence: Float)

/** Cổng OCR: tách khỏi ML Kit để logic dùng nó kiểm thử được mà không cần thư viện Android. */
interface TextRecognizerPort : AutoCloseable {
    suspend fun recognize(image: android.graphics.Bitmap): List<OcrBlock>
}

/** Cổng gán nhãn ảnh trên thiết bị. */
interface ImageLabelerPort : AutoCloseable {
    suspend fun label(image: android.graphics.Bitmap): List<LabelScore>
}
