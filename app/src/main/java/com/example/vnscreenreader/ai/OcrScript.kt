package com.example.vnscreenreader.ai

/**
 * Bộ chữ mà mô hình OCR ML Kit ĐÓNG GÓI SẴN (chạy hoàn toàn offline) đọc được. Hiện ML Kit chỉ có mô hình gói sẵn
 * cho Latin, Trung, Devanagari, Nhật, Hàn (xem README). Không có Cyrillic/Ả Rập: với các ngôn ngữ đó ứng dụng báo
 * "không hỗ trợ" thay vì đọc ra kết quả sai.
 */
enum class OcrScript { LATIN, CHINESE, UNSUPPORTED }

object OcrScripts {

    /** Ngôn ngữ ứng dụng (mã ISO) → bộ chữ OCR. Ngôn ngữ dùng chữ Latin (vi, en, fr, es...) → LATIN. */
    fun forLanguage(language: String): OcrScript = when (language) {
        "zh" -> OcrScript.CHINESE
        in UNSUPPORTED_LANGUAGES -> OcrScript.UNSUPPORTED
        else -> OcrScript.LATIN
    }

    /** Ngôn ngữ dùng chữ Cyrillic/Ả Rập (và tương tự) mà ML Kit chưa có mô hình gói sẵn. */
    private val UNSUPPORTED_LANGUAGES = setOf("ru", "uk", "bg", "sr", "be", "kk", "ar", "fa", "ur", "he", "el", "th")
}
