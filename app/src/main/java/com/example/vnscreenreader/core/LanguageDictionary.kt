package com.example.vnscreenreader.core

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
}

/** Mặc định: giữ nguyên nhãn/từ tiếng Anh (chỉ với từ đã biết nghĩa để không đoán bừa). */
object EnglishDictionary : LanguageDictionary {
    override fun imageLabel(label: String): String? = label.trim().ifEmpty { null }
    override fun viewIdWord(word: String): String? = word.takeIf { it in UnlabeledNamer.KNOWN_WORDS }
}

object VietnameseDictionary : LanguageDictionary {
    override fun imageLabel(label: String): String? = com.example.vnscreenreader.ai.LabelTranslator.toVietnamese(label)
    override fun viewIdWord(word: String): String? = UnlabeledNamer.TRANSLATIONS[word]
}

object Dictionaries {
    private val byLanguage: Map<String, LanguageDictionary> = mapOf("vi" to VietnameseDictionary)

    /** Từ điển cho mã ngôn ngữ ISO ("vi", "en"...); không có thì tiếng Anh. */
    fun forLanguage(language: String): LanguageDictionary = byLanguage[language] ?: EnglishDictionary

    fun current(): LanguageDictionary = forLanguage(Texts.provider.language)
}
