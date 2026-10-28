package com.example.vnscreenreader.core

import java.text.Normalizer

/**
 * Tìm phần tử theo chữ người dùng gõ. So khớp KHÔNG phân biệt hoa/thường và dấu tiếng Việt ("tim kiem" khớp
 * "Tìm kiếm"), vì người khiếm thị thường gõ không dấu. Tìm tiếp theo từ phần tử đang chọn và quay vòng, nên
 * tìm lại cùng một từ sẽ nhảy sang kết quả kế tiếp.
 */
object ScreenSearch {

    /** @return chỉ số kết quả khớp đầu tiên sau [currentIndex] (quay vòng), hoặc null nếu không có. */
    fun find(descriptions: List<String>, query: String, currentIndex: Int): Int? {
        val needle = fold(query)
        if (needle.isEmpty() || descriptions.isEmpty()) return null
        val size = descriptions.size
        val start = if (currentIndex in 0 until size) currentIndex + 1 else 0
        for (step in 0 until size) {
            val index = (start + step) % size
            if (fold(descriptions[index]).contains(needle)) return index
        }
        return null
    }

    /** Chữ thường, bỏ dấu, "đ" thành "d", gộp khoảng trắng. */
    internal fun fold(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace('đ', 'd')
            .replace(WHITESPACE, " ")

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")
}
