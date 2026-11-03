package com.example.vnscreenreader.core

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/**
 * Xử lý "ký tự người dùng thấy" (grapheme cluster) và chuẩn hóa NFC cho văn bản đa ngôn ngữ.
 *
 * Vì sao cần: một ký tự nhìn thấy có thể gồm nhiều đơn vị Unicode. Chữ tiếng Việt ở dạng NFD ("ạ" = "a" + U+0323),
 * emoji có màu da hoặc ghép bằng ZWJ, cờ quốc gia, cặp surrogate... Duyệt theo `Char` hoặc theo code point sẽ đọc
 * thành nhiều "ký tự" rời, hoặc cắt đôi một cặp surrogate khiến TTS đọc rác.
 *
 * Trên Android [BreakIterator] chạy bằng ICU nên theo đúng quy tắc cụm ký tự mở rộng của Unicode.
 * Thời gian O(n), không gian O(n). Mỗi lần gọi tạo một [BreakIterator] riêng vì lớp này không an toàn đa luồng.
 */
object Graphemes {

    /** Chuẩn hóa NFC: dấu thanh viết rời được gộp thành ký tự dựng sẵn. Trả lại chính [text] nếu đã ở dạng NFC. */
    fun nfc(text: String): String =
        if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text else Normalizer.normalize(text, Normalizer.Form.NFC)

    /** Các đoạn [IntRange] (chỉ số UTF-16, đóng hai đầu) của từng cụm ký tự trong [text], theo thứ tự. */
    fun clusters(text: String): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val out = ArrayList<IntRange>(text.length)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            out += start until end
            start = end
            end = iterator.next()
        }
        return out
    }

    /** Các cụm ký tự của [text] dưới dạng chuỗi. */
    fun split(text: String): List<String> = clusters(text).map { text.substring(it.first, it.last + 1) }
}
