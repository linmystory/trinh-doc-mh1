package com.example.vnscreenreader.core

/**
 * Chính sách tạo lại engine TextToSpeech khi nó chết giữa chừng (tiến trình TTS bị hệ thống tắt, engine vừa cập nhật):
 * mọi lệnh đọc sau đó trả `ERROR` và máy im lặng cho tới khi người dùng tắt/bật dịch vụ.
 *
 * Để một engine hỏng thật sự không làm ứng dụng lặp vô hạn, chỉ cho phép tối đa [maxAttempts] lần phục hồi trong
 * mỗi [windowMs] mili giây, hai lần liền nhau cách nhau ít nhất [minGapMs]. Một lần đọc xong thành công ([reset])
 * chứng minh engine đã khỏe lại nên xóa bộ đếm. Lớp thuần Kotlin, đồng hồ do người gọi truyền vào để kiểm thử được.
 */
class SpeechRecoveryPolicy(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val minGapMs: Long = DEFAULT_MIN_GAP_MS,
) {
    private val attempts = ArrayDeque<Long>()

    init {
        require(maxAttempts >= 1) { "maxAttempts phải >= 1" }
        require(windowMs > 0 && minGapMs >= 0) { "windowMs phải > 0 và minGapMs >= 0" }
    }

    /** @return true nếu được phép phục hồi ngay bây giờ (và ghi nhận lần này); false nếu phải bỏ qua. */
    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        while (attempts.isNotEmpty() && nowMs - attempts.first() >= windowMs) attempts.removeFirst()
        val last = attempts.lastOrNull()
        if (last != null && nowMs - last < minGapMs) return false
        if (attempts.size >= maxAttempts) return false
        attempts.addLast(nowMs)
        return true
    }

    /** Engine đã đọc xong một câu bình thường: xóa lịch sử phục hồi. */
    @Synchronized
    fun reset() = attempts.clear()

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_WINDOW_MS = 60_000L
        const val DEFAULT_MIN_GAP_MS = 1_500L
    }
}
