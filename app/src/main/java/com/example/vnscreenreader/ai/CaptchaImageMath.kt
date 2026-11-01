package com.example.vnscreenreader.ai

import kotlin.math.ceil

/**
 * Tính toán thuần (không dùng Android) để chuẩn bị ảnh xác thực cho OCR: hệ số phóng to và nhị phân hóa đen trắng
 * bằng ngưỡng Otsu. Ảnh xác thực thường nhỏ, nhiễu và nền gợn màu; phóng to rồi tách nền giúp bộ nhận dạng đọc đúng
 * hơn. Độ phức tạp O(số pixel); không gian O(số pixel).
 */
object CaptchaImageMath {

    /** Chiều cao tối thiểu (px) sau khi phóng to. */
    const val MIN_HEIGHT_PX = 120
    const val MAX_SCALE = 4

    /** Trần số pixel sau khi phóng to, để không tốn bộ nhớ trên ảnh lớn. */
    const val MAX_PIXELS = 2_000_000L

    private const val BLACK = -0x1000000 // 0xFF000000
    private const val WHITE = -0x1 // 0xFFFFFFFF

    /** Hệ số phóng to nguyên (1..[MAX_SCALE]) sao cho chiều cao đạt [MIN_HEIGHT_PX] và tổng pixel không vượt [MAX_PIXELS]. */
    fun scaleFor(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var scale = ceil(MIN_HEIGHT_PX.toDouble() / height).toInt().coerceIn(1, MAX_SCALE)
        while (scale > 1 && width.toLong() * scale * height * scale > MAX_PIXELS) scale--
        return scale
    }

    /** Độ xám 0..255 của một điểm ARGB. */
    fun gray(argb: Int): Int =
        (77 * ((argb shr 16) and 0xFF) + 151 * ((argb shr 8) and 0xFF) + 28 * (argb and 0xFF)) shr 8

    /** Ngưỡng Otsu (0..255) từ biểu đồ xám 256 mức; biểu đồ rỗng hoặc phẳng thì 128. */
    fun otsuThreshold(histogram: IntArray): Int {
        require(histogram.size == 256) { "Biểu đồ phải có 256 mức" }
        var total = 0L
        var sumAll = 0L
        for (i in 0 until 256) {
            total += histogram[i]
            sumAll += i.toLong() * histogram[i]
        }
        if (total == 0L) return 128
        var sumBackground = 0L
        var weightBackground = 0L
        var bestVariance = 0.0
        var threshold = 128
        for (i in 0 until 256) {
            weightBackground += histogram[i]
            if (weightBackground == 0L) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0L) break
            sumBackground += i.toLong() * histogram[i]
            val meanBackground = sumBackground.toDouble() / weightBackground
            val meanForeground = (sumAll - sumBackground).toDouble() / weightForeground
            val diff = meanBackground - meanForeground
            val variance = weightBackground.toDouble() * weightForeground.toDouble() * diff * diff
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = i
            }
        }
        return threshold
    }

    /**
     * Đen trắng theo ngưỡng Otsu; chữ luôn là màu ĐEN trên nền TRẮNG (nếu quá nửa số điểm thành đen, tức nền tối chữ
     * sáng, thì đảo màu). Trả mảng mới, không sửa [pixels].
     */
    fun binarizeForOcr(pixels: IntArray): IntArray {
        val histogram = IntArray(256)
        for (p in pixels) histogram[gray(p)]++
        val threshold = otsuThreshold(histogram)
        val out = IntArray(pixels.size) { if (gray(pixels[it]) > threshold) WHITE else BLACK }
        var black = 0
        for (p in out) if (p == BLACK) black++
        if (black * 2 > out.size) {
            for (i in out.indices) out[i] = if (out[i] == BLACK) WHITE else BLACK
        }
        return out
    }
}
