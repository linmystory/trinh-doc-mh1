package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bảng tên phải phủ MỌI emoji và không có hai ký tự đơn trùng tên (trùng tên làm việc gộp "N lần" sai). */
class EmojiNamesCoverageTest {

    @Test
    fun everyPresentationEmoji_hasNamesInBothLanguages() {
        val missing = ArrayList<String>()
        EmojiData.forEachPresentation { cp ->
            val key = String(Character.toChars(cp))
            if (EmojiNames.VI[key].isNullOrBlank()) missing.add("vi U+%X".format(cp))
            if (EmojiNames.EN[key].isNullOrBlank()) missing.add("en U+%X".format(cp))
        }
        assertTrue("Thiếu tên: $missing", missing.isEmpty())
    }

    @Test
    fun singleCodePointNames_areUniquePerLanguage() {
        for ((language, table) in listOf("vi" to EmojiNames.VI, "en" to EmojiNames.EN)) {
            val seen = HashMap<String, String>()
            for ((key, name) in table) {
                if (key.codePointCount(0, key.length) != 1) continue
                val other = seen.put(name.trim().lowercase(), key)
                assertTrue("$language: '$name' trùng giữa $other và $key", other == null)
            }
        }
    }

    @Test
    fun namedSymbols_areNotEmoji_andHaveNames() {
        for (cp in EmojiNames.SYMBOL_CODE_POINTS) {
            val key = String(Character.toChars(cp))
            assertFalse("U+%X là emoji nên không phải ký hiệu".format(cp), EmojiData.isEmoji(cp))
            assertTrue(EmojiNames.VI.containsKey(key) && EmojiNames.EN.containsKey(key))
        }
    }

    @Test
    fun tables_areLargeAndNamesAreNotBlank() {
        assertTrue(EmojiNames.VI.size > 1400)
        assertTrue(EmojiNames.EN.size > 1400)
        assertTrue(EmojiNames.VI.values.none { it.isBlank() })
        assertTrue(EmojiNames.EN.values.none { it.isBlank() })
    }

    @Test
    fun vietnameseAndEnglishCoverTheSameKeys() {
        assertTrue(EmojiNames.VI.keys == EmojiNames.EN.keys)
    }
}
