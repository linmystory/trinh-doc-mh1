package com.example.vnscreenreader.core

import com.example.vnscreenreader.core.LanguageRuns.Run
import com.example.vnscreenreader.core.LanguageRuns.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageRunsTest {
    private fun viEn(text: String) = LanguageRuns.split(text, "vi", "en")

    @Test
    fun pureVietnamese_isOneRun_unchanged() {
        val text = "Gửi tin nhắn cho bạn bè của tôi."
        assertEquals(listOf(Run(text, Side.PRIMARY)), viEn(text))
    }

    @Test
    fun pureEnglish_isOneSecondRun() {
        val text = "Bluetooth settings"
        assertEquals(listOf(Run(text, Side.SECOND)), viEn(text))
    }

    @Test
    fun englishWordInsideVietnamese_isSplitOut_andRunsConcatenateToTheOriginal() {
        val text = "Bật Bluetooth để kết nối."
        val runs = viEn(text)
        assertEquals(text, runs.joinToString("") { it.text })
        assertEquals(listOf(Side.PRIMARY, Side.SECOND, Side.PRIMARY), runs.map { it.side })
        assertEquals("Bluetooth ", runs[1].text)
    }

    @Test
    fun shortAmbiguousAsciiWord_staysWithItsNeighbour() {
        // "to" và "can" là tiếng Việt hợp lệ khi không dấu: không được kéo cả câu sang công cụ tiếng Anh.
        val text = "con can to"
        assertEquals(listOf(Run(text, Side.PRIMARY)), viEn(text))
    }

    @Test
    fun leadingNeutralWords_belongToTheFirstLanguage() {
        val runs = viEn("123 wifi")
        assertEquals(listOf(Run("123 ", Side.PRIMARY), Run("wifi", Side.SECOND)), runs)
    }

    @Test
    fun accentedWord_isNeverEnglish() {
        val runs = viEn("the việt")
        assertEquals(listOf(Side.SECOND, Side.PRIMARY), runs.map { it.side })
    }

    @Test
    fun blankOrPunctuationOnly_isHandled() {
        assertTrue(viEn("   ").isEmpty())
        assertEquals(listOf(Run("...", Side.PRIMARY)), viEn("..."))
    }

    @Test
    fun differentScripts_areSplitByScript() {
        val runs = LanguageRuns.split("Mở 设置 ngay", "vi", "zh")
        assertEquals("Mở 设置 ngay", runs.joinToString("") { it.text })
        assertEquals(listOf(Side.PRIMARY, Side.SECOND, Side.PRIMARY), runs.map { it.side })
    }

    @Test
    fun latinPairWithoutEnglish_isNotSplit() {
        val text = "Bonjour le monde hola"
        assertEquals(listOf(Run(text, Side.PRIMARY)), LanguageRuns.split(text, "fr", "es"))
    }

    @Test
    fun englishPrimary_vietnameseSecond_swapsTheSides() {
        val runs = LanguageRuns.split("Open Việt Nam", "en", "vi")
        assertEquals(listOf(Side.PRIMARY, Side.SECOND), runs.map { it.side })
    }
}

class LanguageRunsContractionTest {
    @Test
    fun englishContraction_isEnglish_notNeutral() {
        val runs = LanguageRuns.split("Bạn đừng don't bấm", "vi", "en")
        assertEquals("Bạn đừng don't bấm", runs.joinToString("") { it.text })
        assertEquals(listOf(LanguageRuns.Side.PRIMARY, LanguageRuns.Side.SECOND, LanguageRuns.Side.PRIMARY), runs.map { it.side })
    }
}

class LanguageRunsSentenceTest {
    private fun bySentence(text: String) = LanguageRuns.split(text, "vi", "en", SplitMode.SENTENCE)

    @Test
    fun vietnameseSentenceWithOneEnglishWord_staysWhole() {
        val text = "Bật Bluetooth để kết nối."
        assertEquals(listOf(LanguageRuns.Run(text, LanguageRuns.Side.PRIMARY)), bySentence(text))
    }

    @Test
    fun eachSentenceGoesToItsMajorityLanguage_andConcatenatesBack() {
        val text = "Xin chào các bạn. Open the settings app now. Cảm ơn nhiều."
        val runs = bySentence(text)
        assertEquals(text, runs.joinToString("") { it.text })
        assertEquals(
            listOf(LanguageRuns.Side.PRIMARY, LanguageRuns.Side.SECOND, LanguageRuns.Side.PRIMARY),
            runs.map { it.side },
        )
    }

    @Test
    fun neutralSentence_followsThePreviousOne() {
        val runs = bySentence("Open the settings app. 123. Xin chào.")
        assertEquals(listOf(LanguageRuns.Side.SECOND, LanguageRuns.Side.PRIMARY), runs.map { it.side })
        assertEquals("Open the settings app. 123. ", runs[0].text)
    }

    @Test
    fun noEnglishAnywhere_isOneRun() {
        val text = "Xin chào. Tạm biệt."
        assertEquals(listOf(LanguageRuns.Run(text, LanguageRuns.Side.PRIMARY)), bySentence(text))
    }

    @Test
    fun cjkTerminatorsNeedNoSpace() {
        val runs = LanguageRuns.split("设置。Xin chào.", "vi", "zh", SplitMode.SENTENCE)
        assertEquals("设置。Xin chào.", runs.joinToString("") { it.text })
        assertEquals(listOf(LanguageRuns.Side.SECOND, LanguageRuns.Side.PRIMARY), runs.map { it.side })
    }
}

class LanguageRunsSameLanguageTest {
    @Test
    fun sameLanguageOnBothSides_isNeverSplit() {
        val text = "Open the settings app. Bluetooth wifi."
        assertEquals(listOf(LanguageRuns.Run(text, LanguageRuns.Side.PRIMARY)), LanguageRuns.split(text, "en-US", "en"))
        assertEquals(
            listOf(LanguageRuns.Run(text, LanguageRuns.Side.PRIMARY)),
            LanguageRuns.split(text, "en-US", "en", SplitMode.SENTENCE),
        )
    }
}

class LanguageRunsLatinPairsTest {
    private val P = LanguageRuns.Side.PRIMARY
    private val S = LanguageRuns.Side.SECOND

    @Test
    fun vietnameseWithFrench_splitsOnFrenchWords() {
        val text = "Cảm ơn nhiều, merci beaucoup café."
        val runs = LanguageRuns.split(text, "vi", "fr")
        assertEquals(text, runs.joinToString("") { it.text })
        assertEquals(listOf(P, S), runs.map { it.side })
    }

    @Test
    fun accentedFrenchWord_isNotTakenForVietnamese() {
        // "été" bỏ dấu thành "ete": không phải âm tiết tiếng Việt hợp lệ.
        assertEquals(listOf(S), LanguageRuns.split("été", "vi", "fr").map { it.side })
    }

    @Test
    fun vietnameseTonedWord_isVietnamese_evenWhenWrittenDecomposed() {
        val decomposed = java.text.Normalizer.normalize("việt nam hello", java.text.Normalizer.Form.NFD)
        val runs = LanguageRuns.split(decomposed, "vi", "en")
        assertEquals(decomposed, runs.joinToString("") { it.text })
        assertEquals(listOf(P, S), runs.map { it.side })
    }

    @Test
    fun vietnameseWithSpanishAndGerman_splitsOnForeignWords() {
        assertEquals(listOf(P, S), LanguageRuns.split("Xin chào señor", "vi", "es").map { it.side })
        assertEquals(listOf(P, S), LanguageRuns.split("Xin chào Straße", "vi", "de").map { it.side })
    }

    @Test
    fun englishWithFrench_usesAccentsAndStopwords() {
        val runs = LanguageRuns.split("Open the app, le café est bon", "en", "fr")
        assertEquals("Open the app, le café est bon", runs.joinToString("") { it.text })
        assertEquals(listOf(P, S), runs.map { it.side })
    }

    @Test
    fun spanishWithItalian_usesStopwords_andUnknownWordsStayNeutral() {
        val runs = LanguageRuns.split("el perro y il cane non", "es", "it")
        assertEquals("el perro y il cane non", runs.joinToString("") { it.text })
        assertEquals(listOf(P, S), runs.map { it.side })
    }

    @Test
    fun firstLanguageEnglish_secondVietnamese_swapsSides() {
        assertEquals(listOf(P, S), LanguageRuns.split("Hello Việt Nam", "en", "vi").map { it.side })
    }
}
