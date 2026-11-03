package com.example.vnscreenreader.core

/**
 * Chế độ công cụ đọc (TTS engine).
 * - [SINGLE]: một công cụ đọc mọi thứ (mặc định, giống bản cũ).
 * - [BOTH]: hai công cụ, mỗi công cụ đọc một ngôn ngữ; văn bản trộn được tách theo ngôn ngữ ([LanguageRuns]).
 */
enum class VoiceMode {
    SINGLE, BOTH;

    /** Chế độ kế tiếp khi xoay vòng bằng cử chỉ. */
    fun next(): VoiceMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(id: String?): VoiceMode = entries.firstOrNull { it.name == id } ?: SINGLE
    }
}

/**
 * Cách chia văn bản giữa hai công cụ ở [VoiceMode.BOTH].
 * - [WORD]: đổi công cụ ngay tại từ thuộc ngôn ngữ kia (chính xác nhất nhưng đổi giọng nhiều).
 * - [SENTENCE]: mỗi câu do MỘT công cụ đọc trọn (theo ngôn ngữ chiếm nhiều từ hơn); mượt hơn, ít đổi giọng.
 */
enum class SplitMode {
    WORD, SENTENCE;

    companion object {
        fun parse(id: String?): SplitMode = entries.firstOrNull { it.name == id } ?: WORD
    }
}
