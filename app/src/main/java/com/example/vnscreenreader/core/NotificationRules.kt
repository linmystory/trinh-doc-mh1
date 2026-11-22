package com.example.vnscreenreader.core

/** Hoàn cảnh lúc có thông báo. */
class NotificationContext(
    val fingerDown: Boolean,
    val inCall: Boolean,
    val dnd: Boolean,
    val mediaPlaying: Boolean,
)

/** Những hoàn cảnh người dùng chọn là KHÔNG đọc thông báo tự động. */
class QuietRules(
    val finger: Boolean,
    val call: Boolean,
    val dnd: Boolean,
    val media: Boolean,
) {
    val anyEnabled: Boolean get() = finger || call || dnd || media

    fun blocks(ctx: NotificationContext): Boolean =
        (finger && ctx.fingerDown) || (call && ctx.inCall) || (dnd && ctx.dnd) || (media && ctx.mediaPlaying)
}

/** Danh sách từ khóa người dùng gõ (mỗi dòng một từ, hoặc ngăn bằng dấu phẩy/chấm phẩy). */
object KeywordList {
    const val MAX_KEYWORDS = 100
    const val MAX_KEYWORD_CHARS = 60
    const val MAX_RAW_CHARS = 4_000

    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.take(MAX_RAW_CHARS).split('\n', '\r', ',', ';')
            .map { TextFold.fold(it.trim()).take(MAX_KEYWORD_CHARS) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_KEYWORDS)
    }

    /** [keywords] đã chuẩn hóa bởi [parse]; [text] chưa chuẩn hóa. */
    fun matches(text: String, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return false
        val folded = TextFold.fold(text)
        return keywords.any { folded.contains(it) }
    }
}

object NotificationRules {

    /**
     * Có nên đọc thông báo này không?
     * 0. Màn hình đang TẮT ([screenOn] = false): không đọc gì, kể cả thông báo có từ khóa "luôn đọc". Thông báo đến lúc màn hình
     *    tắt cũng không được đọc bù khi bật lại; đây là luật cứng, không có công tắc.
     * 1. Chứa từ trong danh sách chặn: không bao giờ đọc.
     * 2. Chứa từ trong danh sách luôn đọc: đọc, kể cả khi đang tắt đọc thông báo hay đang ở hoàn cảnh yên lặng.
     * 3. Đang tắt đọc thông báo: không.
     * 4. Hoàn cảnh yên lặng (ngón tay chạm, đang gọi, không làm phiền, đang phát nhạc) mà người dùng đã chọn: không.
     * [context] chỉ được gọi khi cần (tra hệ thống tốn kém hơn nhiều so với so khớp chữ).
     */
    fun shouldRead(
        screenOn: Boolean,
        masterOn: Boolean,
        quiet: QuietRules,
        context: () -> NotificationContext,
        text: String,
        blacklist: List<String>,
        whitelist: List<String>,
    ): Boolean {
        if (!screenOn) return false
        if (KeywordList.matches(text, blacklist)) return false
        if (KeywordList.matches(text, whitelist)) return true
        if (!masterOn) return false
        if (quiet.anyEnabled && quiet.blocks(context())) return false
        return true
    }

    /**
     * Có nên đọc Toast này không? Toast theo công tắc RIÊNG ([readToasts]), không theo "đọc thông báo" và không theo các hoàn cảnh yên
     * lặng, nhưng cũng như thông báo: không bao giờ đọc khi màn hình tắt.
     */
    fun shouldReadToast(readToasts: Boolean, screenOn: Boolean): Boolean = screenOn && readToasts
}
