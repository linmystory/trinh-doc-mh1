package com.example.vnscreenreader.core

/** Câu đọc cho các thông tin ngắn: đánh vần, giờ, pin. Thuần Kotlin để kiểm thử được. */
object SpeechFormats {

    /**
     * "Xin" -> "X, i, n"; khoảng trắng thành "dấu cách". Đọc theo CỤM KÝ TỰ (không cắt đôi emoji hay tách dấu thanh
     * khỏi chữ) sau khi chuẩn hóa NFC; cắt ở [MAX_SPELL_CHARS] cụm để không đọc quá dài.
     */
    fun spell(text: String): String {
        val clusters = Graphemes.split(Graphemes.nfc(text))
        val parts = clusters.take(MAX_SPELL_CHARS).map { c ->
            when (c) {
                " " -> t(S.SP_SPACE)
                "\n", "\r\n", "\r" -> t(S.SP_NEWLINE)
                "\t" -> t(S.SP_TAB)
                else -> c
            }
        }
        val suffix = if (clusters.size > MAX_SPELL_CHARS) t(S.SP_SPELL_MORE) else ""
        return parts.joinToString(", ") + suffix
    }

    fun time(hour: Int, minute: Int): String =
        if (minute == 0) t(S.STATUS_TIME_EXACT, hour) else t(S.STATUS_TIME_HM, hour, minute)

    fun battery(percent: Int, charging: Boolean): String =
        t(S.STATUS_BATTERY, percent.coerceIn(0, 100)) + if (charging) t(S.STATUS_CHARGING) else ""

    const val MAX_SPELL_CHARS = 100
}
