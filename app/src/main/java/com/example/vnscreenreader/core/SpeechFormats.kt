package com.example.vnscreenreader.core

/** Câu đọc cho các thông tin ngắn: đánh vần, giờ, pin. Thuần Kotlin để kiểm thử được. */
object SpeechFormats {

    /** "Xin" -> "X, i, n"; khoảng trắng thành "dấu cách". Cắt ở [MAX_SPELL_CHARS] ký tự để không đọc quá dài. */
    fun spell(text: String): String {
        val limited = text.take(MAX_SPELL_CHARS)
        val parts = limited.map { c ->
            when {
                c == ' ' -> t(S.SP_SPACE)
                c == '\n' -> t(S.SP_NEWLINE)
                c == '\t' -> t(S.SP_TAB)
                else -> c.toString()
            }
        }
        val suffix = if (text.length > MAX_SPELL_CHARS) t(S.SP_SPELL_MORE) else ""
        return parts.joinToString(", ") + suffix
    }

    fun time(hour: Int, minute: Int): String =
        if (minute == 0) t(S.STATUS_TIME_EXACT, hour) else t(S.STATUS_TIME_HM, hour, minute)

    fun battery(percent: Int, charging: Boolean): String =
        t(S.STATUS_BATTERY, percent.coerceIn(0, 100)) + if (charging) t(S.STATUS_CHARGING) else ""

    const val MAX_SPELL_CHARS = 100
}
