package com.example.vnscreenreader.ai

import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Dữ liệu tối thiểu của một thông báo hệ thống (trích từ sự kiện TYPE_NOTIFICATION_STATE_CHANGED). */
data class NotificationInfo(val packageName: String, val appLabel: String, val texts: List<String>)

/**
 * Tóm tắt thông báo THEO LUẬT, chạy hoàn toàn trên máy (không mô hình, không mạng):
 *  - Làm sạch: gộp khoảng trắng, thay đường link dài bằng t(S.NOTIF_LINK), bỏ dòng trùng.
 *  - Rút gọn nội dung dài ở ranh giới từ (không cắt giữa từ), nối bằng "...".
 *  - Chống làm phiền: thông báo y hệt trong cửa sổ ngắn bị bỏ; nếu một ứng dụng bắn nhiều thông báo
 *    liên tiếp thì gộp thành "N thông báo mới từ X, mới nhất: ...".
 * Không thread-safe: dùng trên một luồng xử lý duy nhất. Thời gian O(độ dài văn bản), bộ nhớ bị chặn.
 */
class NotificationSummarizer(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val duplicateWindowMs: Long = DEFAULT_DUPLICATE_WINDOW_MS,
    private val burstWindowMs: Long = DEFAULT_BURST_WINDOW_MS,
    private val burstThreshold: Int = DEFAULT_BURST_THRESHOLD,
) {
    private class Seen(var text: String, var timeMs: Long)

    private val lastByPackage = LinkedHashMap<String, Seen>()
    private val burstByPackage = LinkedHashMap<String, ArrayDeque<Long>>()

    /** Trả về câu cần đọc, hoặc null nếu nên bỏ qua (rỗng/trùng). */
    fun summarize(info: NotificationInfo, nowMs: Long): String? {
        val body = clean(info.texts)
        if (body.isEmpty()) return null

        val previous = lastByPackage[info.packageName]
        if (previous != null && previous.text == body && nowMs - previous.timeMs in 0..duplicateWindowMs) {
            previous.timeMs = nowMs
            return null
        }
        lastByPackage.remove(info.packageName) // đặt lại cuối hàng => loại theo LRU, ứng dụng đang hoạt động không bị đẩy ra
        lastByPackage[info.packageName] = Seen(body, nowMs)
        if (lastByPackage.size > MAX_TRACKED_APPS) lastByPackage.remove(lastByPackage.keys.first())

        val times = burstByPackage.remove(info.packageName) ?: ArrayDeque()
        burstByPackage[info.packageName] = times
        times.addLast(nowMs)
        while (times.isNotEmpty() && nowMs - times.first() > burstWindowMs) times.removeFirst()
        if (burstByPackage.size > MAX_TRACKED_APPS) burstByPackage.remove(burstByPackage.keys.first())

        val app = info.appLabel.ifBlank { t(S.NOTIF_APP_FALLBACK) }
        return if (times.size >= burstThreshold) {
            t(S.NOTIF_BURST, app, times.size, body)
        } else {
            t(S.NOTIF_SINGLE, app, body)
        }
    }

    private fun clean(texts: List<String>): String {
        val parts = texts.asSequence()
            .map { URL.replace(it, t(S.NOTIF_LINK)).replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        return truncateAtWord(parts.joinToString(". "), maxChars)
    }

    private fun truncateAtWord(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val cut = text.lastIndexOf(' ', limit).let { if (it < limit / 2) limit else it }
        return text.substring(0, cut).trimEnd(',', '.', ';', ':', ' ') + "..."
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 240
        const val DEFAULT_DUPLICATE_WINDOW_MS = 3_000L
        const val DEFAULT_BURST_WINDOW_MS = 10_000L
        const val DEFAULT_BURST_THRESHOLD = 3
        private const val MAX_TRACKED_APPS = 32
        private val URL = Regex("https?://\\S+|www\\.\\S+")
        private val WHITESPACE = Regex("\\s+")
    }
}
