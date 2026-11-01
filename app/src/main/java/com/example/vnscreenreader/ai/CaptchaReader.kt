package com.example.vnscreenreader.ai

import com.example.vnscreenreader.core.AutoOcrPolicy
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/**
 * Mã đọc được từ ảnh xác thực: chỉ gồm chữ và số (đã bỏ khoảng trắng, dấu và nhiễu).
 * [plausible] = độ dài nằm trong khoảng thường gặp của mã xác thực.
 */
data class CaptchaReading(val code: String, val confidence: Float?, val plausible: Boolean)

/**
 * Biến kết quả OCR của ảnh xác thực thành CÂU ĐỌC TỪNG KÝ TỰ để người khiếm thị TỰ nhập tay, và chọn kết quả tốt
 * nhất giữa các lần nhận dạng. Thuần hàm để kiểm thử. Không tự điền, không sao chép vào clipboard, không lưu.
 *
 * Độ phức tạp: O(n log n) theo số khối chữ (sắp xếp trong [OcrTextFormatter]); không gian O(n).
 */
object CaptchaReader {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 10

    /** Từ ngưỡng này trở lên (và độ dài hợp lý) thì không cần thử thêm bản đen trắng. */
    const val GOOD_CONFIDENCE = 0.85f

    private const val MAX_RAW_CHARS = 200
    private const val UNKNOWN_CONFIDENCE = 0.5f
    private const val IMPLAUSIBLE_FACTOR = 0.5f
    private const val AGREEMENT_BONUS = 0.15f

    /** Ghép các khối chữ theo đúng thứ tự đọc rồi chỉ giữ chữ và số. */
    fun clean(blocks: List<OcrBlock>): CaptchaReading {
        val code = OcrTextFormatter.format(blocks, maxChars = MAX_RAW_CHARS).filter { it.isLetterOrDigit() }
        val known = blocks.mapNotNull { it.confidence }
        val confidence = if (known.isEmpty()) null else known.average().toFloat()
        return CaptchaReading(code, confidence, code.length in MIN_LENGTH..MAX_LENGTH)
    }

    fun isGoodEnough(reading: CaptchaReading): Boolean =
        reading.code.isNotEmpty() && reading.plausible && (reading.confidence ?: 0f) >= GOOD_CONFIDENCE

    /**
     * Kết quả tốt nhất: điểm = độ tin cậy (chưa biết thì 0,5), nhân một nửa nếu độ dài bất thường, cộng thêm khi hai
     * lần nhận dạng ra cùng một mã. Hòa thì lấy kết quả đến trước. Null nếu không có ký tự nào.
     */
    fun best(readings: List<CaptchaReading>): CaptchaReading? {
        val usable = readings.filter { it.code.isNotEmpty() }
        if (usable.isEmpty()) return null
        return usable.withIndex().maxByOrNull { (index, reading) -> score(reading) + agreement(usable, index) }?.value
    }

    private fun score(reading: CaptchaReading): Float {
        val base = reading.confidence ?: UNKNOWN_CONFIDENCE
        return if (reading.plausible) base else base * IMPLAUSIBLE_FACTOR
    }

    private fun agreement(usable: List<CaptchaReading>, index: Int): Float {
        val code = usable[index].code
        val others = usable.indices.count { it != index && usable[it].code.equals(code, ignoreCase = true) }
        return others * AGREEMENT_BONUS
    }

    /**
     * Câu đọc từng ký tự, hoặc null nếu không đọc được ký tự nào. Toàn chữ hoa hoặc toàn chữ thường thì báo MỘT lần
     * rồi đọc ký tự trơn; lẫn lộn thì chỉ đánh dấu chữ hoa. Độ tin cậy thấp hoặc độ dài lạ thì thêm "có thể sai".
     */
    fun speak(reading: CaptchaReading): String? {
        val code = reading.code
        if (code.isEmpty()) return null
        val letters = code.filter { it.isLetter() }
        val allUpper = letters.isNotEmpty() && letters.all { it.isUpperCase() }
        val allLower = letters.isNotEmpty() && letters.all { it.isLowerCase() }
        val parts = ArrayList<String>(code.length + 1)
        if (allUpper) {
            parts.add(t(S.CAPTCHA_ALL_UPPER))
        } else if (allLower) {
            parts.add(t(S.CAPTCHA_ALL_LOWER))
        }
        for (c in code) {
            if (c.isLetter() && c.isUpperCase() && !allUpper) {
                parts.add(t(S.CAPTCHA_UPPER_MARK, c.toString()))
            } else {
                parts.add(c.toString())
            }
        }
        val result = t(S.CAPTCHA_RESULT, parts.joinToString(", "))
        val unsure = !reading.plausible || AutoOcrPolicy.isLowConfidence(listOf(reading.confidence))
        return if (unsure) t(S.OCR_MAYBE_WRONG, result) else result
    }
}
