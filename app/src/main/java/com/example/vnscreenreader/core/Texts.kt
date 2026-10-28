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

    fun get(key: S, vararg args: Any): String = provider.get(key, args)
}

/** Viết tắt cho [Texts.get]. */
fun t(key: S, vararg args: Any): String = Texts.provider.get(key, args)
