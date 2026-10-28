package com.example.vnscreenreader.feedback

import kotlin.math.PI
import kotlin.math.sin

/**
 * Tổng hợp âm báo ngắn (earcon) dạng PCM 16-bit đơn kênh, không cần tệp âm thanh: APK nhỏ, không phụ thuộc tài nguyên.
 * Có đường bao tăng/giảm dần ở hai đầu để không phát ra tiếng "tách" khó chịu.
 * Độ phức tạp O(số mẫu).
 */
object ToneSynth {

    const val SAMPLE_RATE = 22_050
    private const val FADE_MS = 5

    fun sine(frequencyHz: Int, durationMs: Int, volume: Float = 1f, sampleRate: Int = SAMPLE_RATE): ShortArray {
        require(frequencyHz in 20..(sampleRate / 2 - 1)) { "Tần số ngoài khoảng cho phép" }
        require(durationMs > 0) { "durationMs phải > 0" }
        val count = sampleRate * durationMs / 1000
        val fade = (sampleRate * FADE_MS / 1000).coerceAtMost(count / 2).coerceAtLeast(1)
        val amplitude = Short.MAX_VALUE * volume.coerceIn(0f, 1f)
        return ShortArray(count) { i ->
            val envelope = when {
                i < fade -> i.toFloat() / fade
                i >= count - fade -> (count - 1 - i).toFloat() / fade
                else -> 1f
            }
            (sin(2.0 * PI * frequencyHz * i / sampleRate) * amplitude * envelope).toInt().toShort()
        }
    }
}
