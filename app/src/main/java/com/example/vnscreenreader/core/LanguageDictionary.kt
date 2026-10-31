package com.example.vnscreenreader.core

import java.util.Locale

/**
 * Từ điển theo ngôn ngữ cho hai chỗ phải dịch từ tiếng Anh: nhãn ảnh của ML Kit và từ trong id view.
 * Thêm ngôn ngữ mới = thêm một lớp triển khai và đăng ký trong [Dictionaries]; KHÔNG so sánh mã ngôn ngữ rải rác
 * trong logic. Ngôn ngữ chưa có từ điển dùng [EnglishDictionary] (đọc nhãn tiếng Anh gốc).
 */
interface LanguageDictionary {
    /** Nhãn ảnh (tiếng Anh, từ ML Kit) → chữ để đọc; null nếu không có/không muốn đọc. */
    fun imageLabel(label: String): String?

    /** Một từ trong id view (chữ thường, tiếng Anh) → chữ để đọc; null nếu chưa biết (khi đó không đoán nhãn). */
    fun viewIdWord(word: String): String?

    /** Tên của một emoji (đã chuẩn hóa: bỏ U+FE0F, màu da); null nếu chưa có trong bảng. */
    fun emojiName(emoji: String): String? = EmojiNames.EN[emoji]

    /** Từ đọc cho emoji không có trong bảng. */
    val emojiGeneric: String get() = "emoji"

    /** Từ đọc cho cờ quốc gia không có trong bảng. */
    val emojiFlag: String get() = "flag"

    /** Emoji lặp liên tiếp [count] lần. */
    fun emojiRepeated(name: String, count: Int): String = "$name, $count times"

    /** Ngôn ngữ dùng để lấy tên quốc gia khi đọc cờ (tên nước do hệ điều hành cung cấp theo ngôn ngữ này). */
    val locale: Locale get() = Locale.ENGLISH

    /** Cờ của một quốc gia đã biết tên. */
    fun emojiFlagOf(country: String): String = "flag of $country"

    /** Khung phím: chữ số, # hoặc *. */
    fun emojiKeycap(key: Char): String = when (key) {
        '#' -> "keycap number sign"
        '*' -> "keycap asterisk"
        else -> "keycap $key"
    }

    /** Emoji có hậu tố giới tính (♂ ♀) cho một tên gốc. */
    fun emojiGendered(base: String, female: Boolean): String = if (female) "woman $base" else "man $base"

    /** Chuỗi emoji dài hơn giới hạn đọc: phần còn lại gộp thành một câu. */
    fun emojiMore(count: Int): String = "and $count more"
}

/** Mặc định: giữ nguyên nhãn/từ tiếng Anh (chỉ với từ đã biết nghĩa để không đoán bừa). */
object EnglishDictionary : LanguageDictionary {
    override fun imageLabel(label: String): String? = label.trim().ifEmpty { null }
    override fun viewIdWord(word: String): String? = word.takeIf { it in UnlabeledNamer.KNOWN_WORDS }
}

object VietnameseDictionary : LanguageDictionary {
    override fun imageLabel(label: String): String? = VietnameseImageLabels.translate(label)
    override fun viewIdWord(word: String): String? = UnlabeledNamer.TRANSLATIONS[word]
    override fun emojiName(emoji: String): String? = EmojiNames.VI[emoji]
    override val emojiGeneric: String get() = "biểu tượng cảm xúc"
    override val emojiFlag: String get() = "lá cờ"
    override fun emojiRepeated(name: String, count: Int): String = "$name, $count lần"
    override val locale: Locale get() = Locale.forLanguageTag("vi")
    override fun emojiFlagOf(country: String): String = "cờ $country"
    override fun emojiKeycap(key: Char): String = when (key) {
        '#' -> "phím thăng"
        '*' -> "phím sao"
        else -> "phím số $key"
    }
    override fun emojiGendered(base: String, female: Boolean): String = if (female) "$base nữ" else "$base nam"
    override fun emojiMore(count: Int): String = "và $count biểu tượng khác"
}

object Dictionaries {
    private val byLanguage: Map<String, LanguageDictionary> = mapOf("vi" to VietnameseDictionary)

    /** Từ điển cho mã ngôn ngữ ISO ("vi", "en"...); không có thì tiếng Anh. */
    fun forLanguage(language: String): LanguageDictionary = byLanguage[language] ?: EnglishDictionary

    fun current(): LanguageDictionary = forLanguage(Texts.provider.language)
}
