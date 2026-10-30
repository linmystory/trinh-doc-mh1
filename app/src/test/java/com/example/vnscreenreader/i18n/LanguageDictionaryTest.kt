package com.example.vnscreenreader.i18n

import com.example.vnscreenreader.core.Dictionaries
import com.example.vnscreenreader.core.EnglishDictionary
import com.example.vnscreenreader.core.VietnameseDictionary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDictionaryTest {
    @Test fun unknownLanguageFallsBackToEnglish() {
        assertSame(VietnameseDictionary, Dictionaries.forLanguage("vi"))
        assertSame(EnglishDictionary, Dictionaries.forLanguage("fr"))
        assertSame(EnglishDictionary, Dictionaries.forLanguage("zh"))
        assertEquals("dog", EnglishDictionary.imageLabel(" dog "))
        assertEquals("close", EnglishDictionary.viewIdWord("close"))
        assertNull(EnglishDictionary.viewIdWord("zzqx"))
    }

    /** Không được so sánh mã ngôn ngữ cứng trong logic; chỉ Dictionaries được ánh xạ ngôn ngữ → từ điển. */
    @Test fun noHardCodedLanguageComparisons() {
        val root = listOf("src/main/java", "app/src/main/java", "VnScreenReaderPro/app/src/main/java")
            .map(::File).firstOrNull { it.isDirectory }
        assertTrue("Không tìm thấy thư mục mã nguồn từ ${File(".").absolutePath}", root != null)
        val pattern = Regex("""\.language\s*(==|!=)\s*"""")
        val offenders = root!!.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> pattern.containsMatchIn(f.readText()) }.map { it.name }.toList()
        assertTrue("So sánh ngôn ngữ cứng trong: $offenders", offenders.isEmpty())
    }
}
