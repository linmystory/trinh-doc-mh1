package com.example.vnscreenreader.ai

import com.example.vnscreenreader.core.Dictionaries
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/**
 * Biến nhãn tiếng Anh của bộ phân loại ảnh ML Kit (chạy offline) thành câu mô tả. Từ điển dịch hiện chỉ có
 * tiếng Việt ([com.example.vnscreenreader.core.VietnameseDictionary]); ngôn ngữ khác đọc nhãn tiếng Anh gốc.
 * Mô hình chỉ cho biết "có gì trong ảnh" (không viết thành câu như AI sinh văn bản), nên câu luôn
 * dùng lời dè dặt "có thể" để không khẳng định sai với người không nhìn được ảnh.
 */
object ImageDescriptionBuilder {

    const val DEFAULT_MIN_CONFIDENCE = 0.6f
    const val DEFAULT_MAX_LABELS = 4

    fun build(
        labels: List<LabelScore>,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        maxLabels: Int = DEFAULT_MAX_LABELS,
    ): String {
        val confident = labels.filter { it.confidence >= minConfidence }.sortedByDescending { it.confidence }
        if (confident.isEmpty()) return t(S.IMG_NONE)

        val dictionary = Dictionaries.current()
        val translated = confident.mapNotNull { dictionary.imageLabel(it.text) }.distinct().take(maxLabels)
        // Nhãn chưa có bản dịch KHÔNG được đọc thô: giọng đọc của ngôn ngữ hiện hành sẽ phát âm sai tiếng Anh.
        if (translated.isEmpty()) return t(S.IMG_NONE)
        return t(S.IMG_MAY_BE, translated.joinToString(", "))
    }
}
