package com.example.vnscreenreader.core

import java.text.Normalizer
import java.util.Locale

/** Chuẩn hóa chữ để so khớp: không phân biệt hoa thường và dấu (gõ "thong bao" vẫn khớp "thông báo"). */
object TextFold {
    fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
                ch == 'đ' || ch == 'Đ' -> out.append('d')
                else -> out.append(ch)
            }
        }
        return out.toString().lowercase(Locale.ROOT)
    }
}

/** Một dòng chỉ mục tìm kiếm: chữ thuộc mục [sectionKey]; [isTitle] = tên mục hoặc tên một cài đặt (xếp hạng cao hơn mô tả). */
class SearchEntry(val sectionKey: String, val text: String, val isTitle: Boolean)

object SettingsSearch {

    /**
     * Khóa các mục khớp truy vấn, tốt nhất trước. Mọi từ của truy vấn (tách theo khoảng trắng) phải xuất hiện; khớp trong
     * MỘT tên (mục hoặc cài đặt) xếp trước khớp rải rác qua mô tả. Truy vấn rỗng trả danh sách rỗng.
     * Thời gian O(số dòng chỉ mục * số từ).
     */
    fun search(entries: List<SearchEntry>, query: String): List<String> {
        val tokens = TextFold.fold(query).split(' ', '\t', '\n').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val folded = entries.map { TextFold.fold(it.text) }
        val bySection = LinkedHashMap<String, MutableList<Int>>()
        entries.forEachIndexed { i, e -> bySection.getOrPut(e.sectionKey) { ArrayList() }.add(i) }
        val ranked = ArrayList<Pair<Int, String>>()
        for ((key, indexes) in bySection) {
            val titleHit = indexes.any { entries[it].isTitle && tokens.all { t -> folded[it].contains(t) } }
            if (titleHit) {
                ranked += 0 to key
                continue
            }
            val all = indexes.joinToString(" ") { folded[it] }
            if (tokens.all { all.contains(it) }) ranked += 1 to key
        }
        return ranked.sortedBy { it.first }.map { it.second } // sortedBy ổn định: giữ thứ tự mục trong cùng hạng
    }
}
