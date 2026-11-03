package com.example.vnscreenreader.core

/**
 * Hàng đợi phát lần lượt từng đoạn khi hai công cụ đọc cùng tồn tại: mỗi lúc chỉ MỘT đoạn được phát (hai TextToSpeech
 * độc lập không có hàng đợi chung, nên nếu không điều phối chúng đọc chồng lên nhau). Thuần Kotlin, KHÔNG tự đồng bộ:
 * người gọi phải gọi từ trong một khóa duy nhất.
 *
 * Quy trình: [submit] → [next] (lấy đoạn kế khi rảnh) → khi đoạn phát xong hoặc không phát được gọi [finished] với token
 * đã nhận. Ngắt ([submit] với interrupt) đổi token nên các báo "xong" muộn của đoạn cũ bị bỏ qua.
 */
class RunScheduler(private val maxPending: Int = SpeechQueuePolicy.MAX_PENDING) {

    private val items = ArrayDeque<ArrayDeque<LanguageRuns.Run>>()
    private var playing = false
    var token: Int = 0
        private set

    /** Số câu đang chờ (kể cả câu đang phát dở). */
    fun pendingMessages(): Int = items.size + if (playing) 1 else 0

    /** @return false nếu câu bị bỏ (ưu tiên thấp mà hàng đã đầy hoặc không có gì để đọc). */
    fun submit(runs: List<LanguageRuns.Run>, interrupt: Boolean): Boolean {
        if (runs.isEmpty()) return false
        if (interrupt) {
            token++
            items.clear()
            playing = false
        } else if (pendingMessages() >= maxPending) {
            return false
        }
        items.addLast(ArrayDeque(runs))
        return true
    }

    /** Đoạn kế tiếp cần phát cùng token của nó; null nếu đang bận hoặc hết. */
    fun next(): Pair<LanguageRuns.Run, Int>? {
        if (playing) return null
        while (true) {
            val head = items.firstOrNull() ?: return null
            val run = head.removeFirstOrNull()
            if (head.isEmpty()) items.removeFirst()
            if (run != null) {
                playing = true
                return run to token
            }
        }
    }

    /** Đoạn mang [forToken] đã xong (hoặc không phát được). @return false nếu báo muộn của thế hệ cũ (bỏ qua). */
    fun finished(forToken: Int): Boolean {
        if (forToken != token || !playing) return false
        playing = false
        return true
    }

    /** Dừng hẳn: bỏ mọi thứ đang chờ và vô hiệu báo muộn. */
    fun clear() {
        token++
        items.clear()
        playing = false
    }
}
