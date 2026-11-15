package com.example.vnscreenreader.core

/**
 * Quyết định đọc gì khi con trỏ/vùng chọn trong ô nhập đổi (TYPE_VIEW_TEXT_SELECTION_CHANGED). Thuần hàm để kiểm thử.
 *
 * - Có vùng chọn: đọc đúng đoạn được chọn (rút gọn nếu quá dài).
 * - Chỉ con trỏ dịch: đọc ký tự vừa đi qua (sang phải: ký tự bên trái con trỏ mới; sang trái: ký tự bên phải).
 *   Nhảy xa (Home/End, chạm vào chữ): đọc dòng chứa con trỏ.
 * - Độ dài văn bản đổi (đang gõ/xóa) hoặc chưa có mốc: im lặng, vì sự kiện đổi văn bản đã đọc rồi.
 */
object SelectionSpeech {
    class Result(val speech: String?, val caret: Int, val length: Int)

    fun describe(text: String, from: Int, to: Int, previousCaret: Int, previousLength: Int): Result {
        val length = text.length
        if (from < 0 || to < 0 || from > length || to > length) return Result(null, -1, length)
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        if (lo != hi) return Result(clip(text.substring(lo, hi)), to, length)
        val unchangedText = previousLength == length
        if (!unchangedText || previousCaret < 0 || previousCaret > length || previousCaret == lo) {
            return Result(null, lo, length)
        }
        val a = minOf(previousCaret, lo)
        val b = maxOf(previousCaret, lo)
        val spoken = if (b - a <= MAX_STEP_CHARS) text.substring(a, b) else lineAt(text, lo)
        return Result(clip(spoken), lo, length)
    }

    /** Dòng chứa vị trí [pos] (không gồm ký tự xuống dòng). */
    fun lineAt(text: String, pos: Int): String {
        val p = pos.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', (p - 1).coerceAtLeast(0)).let { if (p == 0 || it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', p).let { if (it < 0) text.length else it }
        return text.substring(start, maxOf(start, end))
    }

    private fun clip(s: String): String = if (s.length <= MAX_SPOKEN_CHARS) s else s.take(MAX_SPOKEN_CHARS)

    const val MAX_STEP_CHARS = 40
    const val MAX_SPOKEN_CHARS = 200
}
