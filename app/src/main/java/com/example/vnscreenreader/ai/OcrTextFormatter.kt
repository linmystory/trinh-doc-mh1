package com.example.vnscreenreader.ai

/**
 * Ghép các khối chữ OCR thành văn bản đọc được theo ĐÚNG thứ tự đọc (trên xuống, trái sang phải).
 * Các khối nằm cùng một hàng (tâm dọc lệch không quá nửa chiều cao) được nối bằng khoảng trắng,
 * các hàng nối bằng xuống dòng để [com.example.vnscreenreader.core.SpeechChunker] ngắt đúng chỗ.
 *
 * Độ phức tạp: O(n log n) do sắp xếp; không gian O(n).
 */
object OcrTextFormatter {

    const val MAX_CHARS = 6000

    fun format(blocks: List<OcrBlock>, maxChars: Int = MAX_CHARS): String {
        val usable = blocks.filter { isMeaningful(it.text) }
        if (usable.isEmpty()) return ""

        val lines = ArrayList<MutableList<OcrBlock>>()
        for (block in usable.sortedWith(compareBy({ it.centerY }, { it.left }))) {
            val line = lines.lastOrNull()
            if (line != null && kotlin.math.abs(line.first().centerY - block.centerY) <= line.first().height / 2) {
                line.add(block)
            } else {
                lines.add(mutableListOf(block))
            }
        }

        val text = lines.joinToString("\n") { line ->
            line.sortedBy { it.left }.joinToString(" ") { it.text.trim().replace(WHITESPACE, " ") }
        }
        return if (text.length <= maxChars) text else text.substring(0, maxChars).trimEnd()
    }

    /** Loại nhiễu: khối rỗng hoặc chỉ một ký tự không phải chữ/số (vết bẩn, đường kẻ bị nhận nhầm). */
    private fun isMeaningful(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.length > 1 || trimmed[0].isLetterOrDigit()
    }

    private val WHITESPACE = Regex("\\s+")
}
