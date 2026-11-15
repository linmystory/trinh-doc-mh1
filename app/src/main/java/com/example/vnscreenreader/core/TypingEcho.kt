package com.example.vnscreenreader.core

/** Quy tắc đọc khi gõ: từ vừa gõ xong. Thuần hàm để kiểm thử. */
object TypingEcho {
    private const val MAX_WORD_CHARS = 60
    private const val TRAILING_JOINERS = "'’-"

    /**
     * Từ vừa hoàn tất: ký tự duy nhất vừa thêm (tại [fromIndex]) là dấu cách, dấu câu hay xuống dòng, và ngay trước nó
     * là một từ (chữ cái/chữ số, kể cả dấu nháy và gạch nối nằm giữa từ). Trả về null nếu không phải trường hợp đó.
     */
    fun completedWord(fullText: String, fromIndex: Int, addedCount: Int): String? {
        if (addedCount != 1 || fromIndex !in 1 until fullText.length) return null
        if (fullText[fromIndex].isLetterOrDigit()) return null
        // Dấu nháy/gạch nối ngay trước ký tự vừa gõ (ví dụ "dogs' ") không thuộc từ: bỏ nó rồi mới tìm từ.
        var end = fromIndex
        if (end >= 2 && fullText[end - 1] in TRAILING_JOINERS && fullText[end - 2].isLetterOrDigit()) end--
        var start = end
        while (start > 0 && isWordChar(fullText, start - 1)) start--
        if (start == end) return null
        val word = fullText.substring(start, end)
        return if (word.length <= MAX_WORD_CHARS) word else word.takeLast(MAX_WORD_CHARS)
    }

    private fun isWordChar(text: String, index: Int): Boolean {
        val c = text[index]
        if (c.isLetterOrDigit()) return true
        // Dấu nháy/gạch nối chỉ thuộc từ khi nằm giữa hai ký tự chữ-số.
        return (c == '\'' || c == '’' || c == '-') && index > 0 && text[index - 1].isLetterOrDigit() &&
            index + 1 < text.length && text[index + 1].isLetterOrDigit()
    }
}

/** Phạm vi mục đang hiện của danh sách sau khi cuộn. Thuần hàm để kiểm thử. */
object ListRange {
    class Range(val first: Int, val last: Int, val total: Int)

    /**
     * Chỉ số 0-based của sự kiện cuộn thành phạm vi 1-based. Null nếu số liệu không đáng tin (âm, đảo ngược, vượt tổng) hoặc
     * toàn bộ danh sách đã hiện hết (không có gì để báo).
     */
    fun from(fromIndex: Int, toIndex: Int, itemCount: Int): Range? {
        if (fromIndex < 0 || toIndex < fromIndex || itemCount <= 0 || toIndex >= itemCount) return null
        if (toIndex - fromIndex + 1 >= itemCount) return null
        return Range(fromIndex + 1, toIndex + 1, itemCount)
    }
}
