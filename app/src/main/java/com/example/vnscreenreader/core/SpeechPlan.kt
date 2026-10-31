package com.example.vnscreenreader.core

/** Một phần của một lần đọc: một đoạn chữ, hoặc một khoảng lặng chắc chắn giữa hai đoạn. */
sealed interface SpeechPart {
    data class Say(val text: String) : SpeechPart
    data class Pause(val millis: Int) : SpeechPart
}

/**
 * Kế hoạch đọc: danh sách [SpeechPart]. Khoảng lặng được phát bằng "đoạn im lặng" của TTS thay vì trông chờ vào dấu
 * phẩy, vì nhiều giọng đọc (nhất là tiếng Việt) gần như không nghỉ ở dấu phẩy nên các tên emoji dính vào nhau.
 * Thuần hàm, không phụ thuộc Android.
 */
object SpeechPlan {

    const val MIN_PAUSE_MS = 60
    const val MAX_PAUSE_MS = 600

    /** Một đoạn chữ duy nhất; chuỗi trống cho kế hoạch rỗng. */
    fun single(text: String): List<SpeechPart> =
        if (text.isBlank()) emptyList() else listOf(SpeechPart.Say(text))

    /** Bỏ đoạn rỗng, gộp các khoảng lặng liền nhau (lấy dài nhất), bỏ khoảng lặng ở đầu và cuối. */
    fun normalize(parts: List<SpeechPart>): List<SpeechPart> {
        val out = ArrayList<SpeechPart>(parts.size)
        for (part in parts) {
            when (part) {
                is SpeechPart.Say -> if (part.text.isNotBlank()) out.add(part)
                is SpeechPart.Pause -> {
                    if (part.millis <= 0 || out.isEmpty()) continue
                    val last = out.last()
                    if (last is SpeechPart.Pause) {
                        out[out.lastIndex] = SpeechPart.Pause(maxOf(last.millis, part.millis))
                    } else {
                        out.add(part)
                    }
                }
            }
        }
        while (out.isNotEmpty() && out.last() is SpeechPart.Pause) out.removeAt(out.lastIndex)
        return out
    }

    /** Văn bản thuần của kế hoạch (bỏ khoảng lặng, nối các đoạn bằng dấu phẩy): để hiển thị, ghi log và kiểm thử. */
    fun plainText(parts: List<SpeechPart>): String =
        parts.filterIsInstance<SpeechPart.Say>().joinToString(", ") { it.text.trim() }

    /** Chuẩn hóa rồi cắt mỗi đoạn chữ cho vừa giới hạn độ dài của TTS; giữ nguyên các khoảng lặng. */
    fun chunk(parts: List<SpeechPart>, maxLength: Int): List<SpeechPart> {
        val out = ArrayList<SpeechPart>(parts.size)
        for (part in normalize(parts)) {
            if (part is SpeechPart.Say) {
                SpeechChunker.split(part.text, maxLength).forEach { out.add(SpeechPart.Say(it)) }
            } else {
                out.add(part)
            }
        }
        return out
    }

    /** Khoảng lặng theo tốc độ đọc: đọc nhanh thì nghỉ ngắn lại để nhịp không bị chậm so với giọng. */
    fun scaledPause(millis: Int, speechRate: Float): Int {
        val rate = if (speechRate >= 0.1f) speechRate else 1f
        return (millis / rate).toInt().coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
    }
}

/**
 * Chính sách ngắt nghỉ khi gặp CHUỖI emoji liền nhau (ví dụ 😂🤣😍). Mỗi mục được đọc như một đoạn riêng, cách nhau
 * một khoảng lặng; cứ [GROUP_SIZE] mục nghỉ dài hơn để người nghe kịp tách nhóm.
 */
object EmojiRunPolicy {

    /** Từ ngần này cụm GIỐNG HỆT liền nhau trở lên thì gộp "tên, N lần"; ít hơn thì đọc từng cái cho rành mạch. */
    const val REPEAT_THRESHOLD = 4

    /** Một chuỗi đọc tối đa ngần này mục; phần còn lại gộp thành "và N biểu tượng khác". */
    const val MAX_ENTRIES = 12

    const val GROUP_SIZE = 4
    const val ENTRY_GAP_MS = 160
    const val GROUP_GAP_MS = 380

    /** Nghỉ giữa chuỗi emoji và chữ xung quanh. */
    const val EDGE_GAP_MS = 260

    /** Khoảng lặng ngay SAU mục thứ [entryIndex] (đếm từ 0). */
    fun gapAfter(entryIndex: Int): Int =
        if ((entryIndex + 1) % GROUP_SIZE == 0) GROUP_GAP_MS else ENTRY_GAP_MS
}
