package com.example.vnscreenreader.core

/** Nguồn chuỗi theo khóa [S]. Bản Android lấy từ tài nguyên; bản JVM dùng [FallbackTexts]. */
interface TextProvider {
    fun get(key: S, args: Array<out Any>): String

    /** Mã ngôn ngữ ISO hiện hành ("vi", "en", "ar"...) cho logic phụ thuộc ngôn ngữ. */
    val language: String
}

/**
 * Điểm truy cập chuỗi cho mã không có `Context` (logic thuần Kotlin, unit test).
 * [VnApplication] cài provider thật khi tiến trình khởi động; trước đó dùng tiếng Việt dự phòng.
 */
object Texts {
    @Volatile
    var provider: TextProvider = FallbackTexts

    fun get(key: S, vararg args: Any): String = lookup(key, args)

    /**
     * Tra chuỗi mà không bao giờ ném lỗi: thiếu tài nguyên hoặc sai định dạng không được làm hỏng luồng đọc.
     * Lùi về chuỗi tiếng Việt dự phòng, cuối cùng mới là tên khóa (vẫn đọc được và lộ ra ngay khi kiểm thử).
     */
    internal fun lookup(key: S, args: Array<out Any>): String = try {
        provider.get(key, args)
    } catch (e: RuntimeException) {
        try {
            FallbackTexts.get(key, args)
        } catch (e2: RuntimeException) {
            key.name
        }
    }
}

/** Viết tắt cho [Texts.get]. */
fun t(key: S, vararg args: Any): String = Texts.lookup(key, args)
