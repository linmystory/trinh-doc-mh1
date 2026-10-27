package com.example.vnscreenreader.core

/**
 * Chính sách thử lại khi khởi tạo TextToSpeech thất bại (ví dụ ngay sau khi khởi động máy,
 * engine TTS chưa sẵn sàng). Backoff lũy thừa: 1s, 2s, 4s, 8s, 16s rồi bỏ cuộc.
 */
object SpeechRetryPolicy {

    const val MAX_RETRIES = 5
    private const val BASE_DELAY_MS = 1_000L

    /** @param failures số lần khởi tạo thất bại liên tiếp (>= 1). Trả về null khi không nên thử lại nữa. */
    fun delayMs(failures: Int): Long? {
        if (failures < 1 || failures > MAX_RETRIES) return null
        return BASE_DELAY_MS shl (failures - 1)
    }
}
