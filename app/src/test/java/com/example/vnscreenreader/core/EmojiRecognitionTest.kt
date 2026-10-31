package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nhận diện đúng emoji, tên đúng cho chuỗi ghép, cờ, khung phím và ký hiệu giao diện. */
class EmojiRecognitionTest {

    private val vi = VietnameseDictionary
    private val en = EnglishDictionary

    private fun cps(vararg codePoints: Int): String = buildString { codePoints.forEach { appendCodePoint(it) } }
    private fun speak(text: String, dictionary: LanguageDictionary = vi) = EmojiText.forSpeech(text, dictionary)

    @Test
    fun interfaceSymbols_areNotReadAsEmoji() {
        val text = "Mũi tên → ⇒ ™ © ▶ và 5% • —"
        assertEquals(text, speak(text))
    }

    @Test
    fun namedSymbols_areReadByName() {
        assertEquals("chấm tròn đen, Online", speak("● Online"))
        assertEquals("dấu tích, Đã giao", speak("✓ Đã giao"))
        assertEquals("ô vuông trống, Việc cần làm", speak("☐ Việc cần làm"))
    }

    @Test
    fun textStyleEmoji_needsSelectorOrIsFamiliar() {
        assertEquals(vi.emojiName("▶"), speak("▶\uFE0F")) // có U+FE0F: là emoji
        assertEquals("▶", speak("▶")) // không có: chỉ là ký tự chữ
        assertEquals("trái tim đỏ", speak("\u2764")) // ❤ quen thuộc nên đọc cả khi không có U+FE0F
    }

    @Test
    fun keycaps_areReadAsKeys() {
        assertEquals("phím số 1", speak("1\uFE0F\u20E3"))
        assertEquals("phím số 7", speak("7\u20E3"))
        assertEquals("phím thăng", speak("#\uFE0F\u20E3"))
        assertEquals("phím sao", speak("*\uFE0F\u20E3"))
        assertEquals("keycap 5", speak("5\uFE0F\u20E3", en))
        assertEquals("5", speak("5")) // chữ số thường không phải emoji
    }

    @Test
    fun flags_useTheCountryNameOfTheLanguage() {
        assertEquals("cờ Việt Nam", speak(cps(0x1F1FB, 0x1F1F3)))
        assertEquals("cờ Pháp", speak(cps(0x1F1EB, 0x1F1F7)))
        assertEquals("flag of France", speak(cps(0x1F1EB, 0x1F1F7), en))
    }

    @Test
    fun regionalFlags_haveNames() {
        // 🏴 + thẻ "gbeng" + thẻ kết thúc
        val england = cps(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F)
        assertEquals("cờ nước Anh", speak(england))
        assertEquals("flag of England", speak(england, en))
    }

    @Test
    fun knownJoinedSequences_haveNaturalNames() {
        assertEquals("gia đình bố mẹ và con gái", speak(cps(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)))
        assertEquals("lập trình viên nữ", speak(cps(0x1F469, 0x200D, 0x1F4BB)))
        assertEquals("cờ cầu vồng", speak(cps(0x1F3F3, 0xFE0F, 0x200D, 0x1F308)))
        assertEquals("trái tim rực lửa", speak(cps(0x2764, 0xFE0F, 0x200D, 0x1F525)))
    }

    @Test
    fun genderSuffix_isAppendedToTheBaseName() {
        assertEquals("người chạy bộ nam", speak(cps(0x1F3C3, 0x200D, 0x2642, 0xFE0F)))
        assertEquals("người chạy bộ nữ", speak(cps(0x1F3C3, 0x200D, 0x2640, 0xFE0F)))
    }

    @Test
    fun unknownJoinedSequence_isReadComponentByComponent_withCommas() {
        assertEquals("người lớn, cây thông Noel", speak(cps(0x1F9D1, 0x200D, 0x1F384)))
    }

    @Test
    fun skinToneAlone_hasAName() {
        assertEquals("màu da trung bình", speak(cps(0x1F3FD)))
    }

    @Test
    fun skinToneOnAnEmoji_isIgnoredForTheName() {
        assertEquals(speak(cps(0x1F44D)), speak(cps(0x1F44D, 0x1F3FD)))
    }

    @Test
    fun newestEmoji_haveNames_andFutureOnesAreStillRead() {
        assertEquals("mặt tan chảy", speak(cps(0x1FAE0)))
        assertEquals("mặt có quầng thâm mắt", speak(cps(0x1FAE9))) // Emoji 16.0
        assertEquals("biểu tượng cảm xúc", speak(cps(0x1FAFF))) // chưa gán: vẫn đọc, không im lặng
    }

    @Test
    fun englishDictionary_usesEnglishNames() {
        assertEquals("face with tears of joy", speak("😂", en))
        assertEquals("and 3 more", en.emojiMore(3))
        assertEquals("và 3 biểu tượng khác", vi.emojiMore(3))
    }

    @Test
    fun emojiData_matchesUnicodeProperties() {
        assertTrue(EmojiData.isPresentation(0x1F602))
        assertTrue(EmojiData.isEmoji(0x2764))
        assertFalse(EmojiData.isPresentation(0x2764)) // ❤ mặc định hiển thị dạng chữ
        assertFalse(EmojiData.isEmoji(0x25CF)) // ● không phải emoji
        assertFalse(EmojiData.isEmoji(0x2713)) // ✓ không phải emoji
        assertTrue(EmojiData.isPlainTextEmoji(0x2764))
        assertFalse(EmojiData.isPlainTextEmoji(0x2122))
    }
}
