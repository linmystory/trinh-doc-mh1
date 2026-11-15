package com.example.vnscreenreader.core

import java.util.Locale
import kotlin.math.roundToInt

/** Lời đọc vị trí hiện tại. Thuần hàm để kiểm thử. */
object LocationText {
    /** Sai số (mét) từ đó trở lên thì thêm lời cảnh báo tín hiệu yếu. */
    const val WEAK_ACCURACY_M = 50

    /** Vị trí cũ hơn ngưỡng này (mili giây) thì thêm lời cảnh báo cũ. */
    const val OLD_AFTER_MS = 2 * 60 * 1000L

    private const val MAX_ADDRESS_CHARS = 200

    /** Một câu cần đọc: khóa chuỗi và đối số. */
    data class Line(val key: S, val args: List<Any> = emptyList())

    /**
     * Các câu cần đọc theo thứ tự. Có địa chỉ thì đọc địa chỉ kèm độ chính xác; không có thì đọc vĩ độ/kinh độ (5 chữ số thập
     * phân, ≈1 mét). Thêm cảnh báo khi sai số lớn hoặc vị trí cũ, vì nói sai chỗ đứng còn nguy hiểm hơn không nói.
     */
    fun lines(address: String?, latitude: Double, longitude: Double, accuracyMeters: Float?, ageMs: Long): List<Line> {
        val accuracy = (accuracyMeters ?: DEFAULT_ACCURACY_M).roundToInt().coerceAtLeast(1)
        val cleanAddress = address?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_ADDRESS_CHARS).orEmpty()
        val result = mutableListOf<Line>()
        if (cleanAddress.isNotEmpty()) {
            result += Line(S.MSG_LOC_HERE, listOf(cleanAddress, accuracy))
        } else {
            result += Line(S.MSG_LOC_COORDS, listOf(coordinate(latitude), coordinate(longitude), accuracy))
        }
        if (accuracy >= WEAK_ACCURACY_M) result += Line(S.MSG_LOC_WEAK)
        if (ageMs > OLD_AFTER_MS) result += Line(S.MSG_LOC_OLD)
        return result
    }

    /** 5 chữ số thập phân, luôn dùng dấu chấm (không phụ thuộc ngôn ngữ máy). */
    fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.5f", value)

    private const val DEFAULT_ACCURACY_M = 100f
}
