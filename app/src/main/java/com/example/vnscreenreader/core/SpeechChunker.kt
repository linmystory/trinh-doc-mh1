package com.example.vnscreenreader.core

/**
 * Chia văn bản dài thành các đoạn không vượt quá `maxLength` ký tự.
 *
 * Lý do: TextToSpeech từ chối (trả về ERROR, người dùng không nghe thấy gì) mọi văn bản dài hơn
 * `TextToSpeech.getMaxSpeechInputLength()` (4000 ký tự).
 *
 * Ưu tiên cắt tại ranh giới câu/dòng, sau đó tại khoảng trắng; chỉ cắt cứng khi không còn lựa chọn
 * và không bao giờ tách đôi một cặp surrogate (emoji, ký tự ngoài BMP).
 *
 * Độ phức tạp: thời gian O(n) (mỗi ký tự được quét tối đa hai lần), không gian O(n) cho kết quả.
 */
object SpeechChunker {

    private const val MIN_MAX_LENGTH = 2
    private val SENTENCE_TERMINATORS = charArrayOf('.', '!', '?', '…', ';', ':')

    fun split(text: String, maxLength: Int): List<String> {
        require(maxLength >= MIN_MAX_LENGTH) { "maxLength phải >= $MIN_MAX_LENGTH" }
        val source = text.trim()
        if (source.isEmpty()) return emptyList()
        if (source.length <= maxLength) return listOf(source)

        val chunks = ArrayList<String>(source.length / maxLength + 1)
        var start = 0
        while (start < source.length) {
            val hardEnd = minOf(source.length, start + maxLength)
            val end = if (hardEnd == source.length) hardEnd else findCut(source, start, hardEnd)
            val piece = source.substring(start, end).trim()
            if (piece.isNotEmpty()) chunks += piece
            start = end
        }
        return chunks
    }

    /**
     * Chọn vị trí cắt trong (start, hardEnd]. Chỉ xét nửa sau của cửa sổ để tránh sinh ra
     * các đoạn quá nhỏ. Luôn trả về giá trị > start nên vòng lặp bên ngoài chắc chắn kết thúc.
     */
    private fun findCut(text: String, start: Int, hardEnd: Int): Int {
        val lowest = start + (hardEnd - start) / 2
        var whitespaceCut = -1
        for (cut in hardEnd downTo lowest + 1) {
            if (isSentenceBoundary(text, cut)) return cut
            if (whitespaceCut < 0 && text[cut - 1].isWhitespace()) whitespaceCut = cut
        }
        if (whitespaceCut > 0) return whitespaceCut
        // Cắt cứng: lùi một ký tự nếu sắp tách cặp surrogate (hardEnd - 1 >= start + 1 vì maxLength >= 2).
        return if (Character.isHighSurrogate(text[hardEnd - 1])) hardEnd - 1 else hardEnd
    }

    /** Vị trí `cut` là ranh giới nếu ký tự ngay trước nó kết thúc câu (dấu chấm phải theo sau bởi khoảng trắng, tránh tách "3.14"). */
    private fun isSentenceBoundary(text: String, cut: Int): Boolean {
        val previous = text[cut - 1]
        if (previous == '\n') return true
        if (previous !in SENTENCE_TERMINATORS) return false
        return cut >= text.length || text[cut].isWhitespace()
    }
}
