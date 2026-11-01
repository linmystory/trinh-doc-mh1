package com.example.vnscreenreader.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaReaderTest {

    private fun block(text: String, left: Int, confidence: Float? = 0.9f) =
        OcrBlock(text, left, 0, left + 90, 40, confidence)

    private fun reading(code: String, confidence: Float? = 0.9f, plausible: Boolean = code.length in 3..10) =
        CaptchaReading(code, confidence, plausible)

    // ---- clean

    @Test fun cleanJoinsBlocksInReadingOrderAndKeepsOnlyLettersAndDigits() {
        // Thứ tự khối bị xáo trộn: kết quả vẫn đúng thứ tự trái → phải trên cùng một hàng.
        val r = CaptchaReader.clean(listOf(block("7-9", 110, 0.8f), block("a K", 0, 0.9f)))
        assertEquals("aK79", r.code)
        assertEquals(0.85f, r.confidence!!, 0.001f)
        assertTrue(r.plausible)
    }

    @Test fun cleanDropsNoiseAndFlagsOddLengths() {
        val short = CaptchaReader.clean(listOf(block("—", 0), block("X7", 100)))
        assertEquals("X7", short.code)
        assertFalse(short.plausible)
        val long = CaptchaReader.clean(listOf(block("ABCDEFGHIJK", 0)))
        assertFalse(long.plausible)
    }

    @Test fun cleanOfNothingIsEmpty() {
        val r = CaptchaReader.clean(emptyList())
        assertEquals("", r.code)
        assertNull(r.confidence)
        assertFalse(r.plausible)
    }

    @Test fun cleanWithoutConfidenceReportsNull() {
        assertNull(CaptchaReader.clean(listOf(block("AB12", 0, confidence = null))).confidence)
    }

    // ---- speak

    @Test fun allUppercaseIsAnnouncedOnce() {
        assertEquals("Mã xác thực: toàn chữ hoa, A, K, 7, 9", CaptchaReader.speak(reading("AK79", 0.95f)))
    }

    @Test fun allLowercaseIsAnnouncedOnce() {
        assertEquals("Mã xác thực: toàn chữ thường, a, k, 7, 9", CaptchaReader.speak(reading("ak79", 0.95f)))
    }

    @Test fun mixedCaseMarksOnlyUppercaseLetters() {
        assertEquals("Mã xác thực: a, hoa K, 7, 9", CaptchaReader.speak(reading("aK79", 0.9f)))
    }

    @Test fun digitsOnlyHaveNoCaseAnnouncement() {
        assertEquals("Mã xác thực: 4, 8, 2, 1", CaptchaReader.speak(reading("4821", 0.9f)))
    }

    @Test fun lowConfidenceAddsWarning() {
        val text = CaptchaReader.speak(reading("AK79", 0.4f))!!
        assertTrue(text, text.endsWith(". Có thể sai."))
        assertTrue(text.startsWith("Mã xác thực: toàn chữ hoa, A, K, 7, 9"))
    }

    @Test fun oddLengthAddsWarning() {
        assertTrue(CaptchaReader.speak(reading("AK", 0.95f))!!.endsWith("Có thể sai."))
    }

    @Test fun unknownConfidenceDoesNotWarnByItself() {
        assertFalse(CaptchaReader.speak(reading("AK79", null))!!.contains("Có thể sai"))
    }

    @Test fun nothingToSpeakWhenEmpty() {
        assertNull(CaptchaReader.speak(reading("", 0.9f)))
    }

    // ---- isGoodEnough / best

    @Test fun goodEnoughNeedsLengthAndConfidence() {
        assertTrue(CaptchaReader.isGoodEnough(reading("AK79", 0.9f)))
        assertTrue(CaptchaReader.isGoodEnough(reading("AK79", 0.85f)))
        assertFalse(CaptchaReader.isGoodEnough(reading("AK79", 0.8f)))
        assertFalse(CaptchaReader.isGoodEnough(reading("AK79", null)))
        assertFalse(CaptchaReader.isGoodEnough(reading("AK", 0.99f)))
        assertFalse(CaptchaReader.isGoodEnough(reading("", 0.99f)))
    }

    @Test fun bestPrefersAgreementBetweenAttempts() {
        val a = reading("AK79", 0.6f)
        val b = reading("ak79", 0.7f) // cùng mã (không phân biệt hoa thường) → cộng điểm đồng thuận
        val c = reading("ZZZZ", 0.9f)
        assertEquals(b, CaptchaReader.best(listOf(a, b)))
        // Một kết quả khác rất tin cậy vẫn thắng hai kết quả yếu không trùng nhau.
        assertEquals(c, CaptchaReader.best(listOf(reading("AK79", 0.5f), reading("AK78", 0.6f), c)))
    }

    @Test fun bestPenalizesOddLength() {
        val odd = reading("AK", 0.95f)
        val normal = reading("AK79", 0.6f)
        assertEquals(normal, CaptchaReader.best(listOf(odd, normal)))
    }

    @Test fun bestTieKeepsTheFirst() {
        val first = reading("AAAA", 0.7f)
        val second = reading("BBBB", 0.7f)
        assertEquals(first, CaptchaReader.best(listOf(first, second)))
    }

    @Test fun bestIgnoresEmptyReadings() {
        assertNull(CaptchaReader.best(emptyList()))
        assertNull(CaptchaReader.best(listOf(reading("", null, false))))
        val good = reading("AK79")
        assertEquals(good, CaptchaReader.best(listOf(reading("", null, false), good)))
    }
}
