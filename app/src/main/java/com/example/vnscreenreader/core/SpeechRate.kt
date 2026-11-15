package com.example.vnscreenreader.core

import kotlin.math.roundToInt

/**
 * Chỉnh tốc độ đọc bằng điều khiển đọc "Tốc độ đọc" (vuốt lên = nhanh hơn, xuống = chậm hơn). Cùng thang với thanh trượt trong
 * Cài đặt giọng đọc: 0,5 đến 2,0, mỗi bước 0,1. Thuần hàm nên kiểm thử được bằng JVM.
 */
object SpeechRate {
    const val MIN = 0.5f
    const val MAX = 2.0f
    const val STEP = 0.1f

    /**
     * Tốc độ mới sau một bước theo [direction] (dương = nhanh hơn, âm = chậm hơn, 0 = giữ). Làm tròn về bội của 0,1 để không
     * tích lũy sai số số thực, rồi kẹp vào [MIN, MAX]; đã ở giới hạn thì giữ nguyên.
     */
    fun step(current: Float, direction: Int): Float {
        val steps = (current.coerceIn(MIN, MAX) / STEP).roundToInt() + direction.coerceIn(-1, 1)
        return (steps * STEP).coerceIn(MIN, MAX).let { (it * 10f).roundToInt() / 10f }
    }

    /** Tốc độ dưới dạng phần trăm để đọc ra: 1,0 = 100. */
    fun percent(rate: Float): Int = (rate * 100f).roundToInt()
}
