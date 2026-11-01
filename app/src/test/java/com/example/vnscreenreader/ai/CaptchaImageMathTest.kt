package com.example.vnscreenreader.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaImageMathTest {

    private val black = -0x1000000
    private val white = -0x1

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun gray(v: Int): Int = argb(v, v, v)

    // ---- scaleFor

    @Test fun smallImagesAreScaledUpToReadableHeight() {
        assertEquals(3, CaptchaImageMath.scaleFor(200, 40))
        assertEquals(2, CaptchaImageMath.scaleFor(200, 60))
    }

    @Test fun largeEnoughImagesAreNotScaled() {
        assertEquals(1, CaptchaImageMath.scaleFor(200, 120))
        assertEquals(1, CaptchaImageMath.scaleFor(200, 300))
    }

    @Test fun scaleIsCapped() {
        assertEquals(CaptchaImageMath.MAX_SCALE, CaptchaImageMath.scaleFor(100, 20))
    }

    @Test fun scaleRespectsPixelBudget() {
        assertEquals(2, CaptchaImageMath.scaleFor(5000, 100)) // đúng bằng trần: vẫn được
        assertEquals(1, CaptchaImageMath.scaleFor(6000, 100)) // vượt trần: giảm hệ số
    }

    @Test fun badSizesMeanNoScaling() {
        assertEquals(1, CaptchaImageMath.scaleFor(0, 10))
        assertEquals(1, CaptchaImageMath.scaleFor(10, 0))
        assertEquals(1, CaptchaImageMath.scaleFor(-5, -5))
    }

    // ---- gray

    @Test fun grayOfPrimaries() {
        assertEquals(0, CaptchaImageMath.gray(gray(0)))
        assertEquals(255, CaptchaImageMath.gray(gray(255)))
        assertEquals(76, CaptchaImageMath.gray(argb(255, 0, 0)))
    }

    // ---- Otsu

    @Test fun otsuSeparatesTwoClusters() {
        val histogram = IntArray(256)
        histogram[30] = 500
        histogram[220] = 500
        val threshold = CaptchaImageMath.otsuThreshold(histogram)
        assertTrue("ngưỡng $threshold", threshold in 30 until 220)
    }

    @Test fun otsuOnEmptyOrSingleLevelIs128() {
        assertEquals(128, CaptchaImageMath.otsuThreshold(IntArray(256)))
        val flat = IntArray(256)
        flat[100] = 1000
        assertEquals(128, CaptchaImageMath.otsuThreshold(flat))
    }

    @Test(expected = IllegalArgumentException::class)
    fun otsuNeedsExactly256Levels() {
        CaptchaImageMath.otsuThreshold(IntArray(10))
    }

    // ---- binarizeForOcr

    @Test fun darkTextOnLightBackgroundStaysDarkText() {
        val pixels = IntArray(100) { if (it < 20) gray(20) else gray(230) }
        val out = CaptchaImageMath.binarizeForOcr(pixels)
        assertEquals(20, out.count { it == black })
        assertEquals(80, out.count { it == white })
        assertTrue((0 until 20).all { out[it] == black })
    }

    @Test fun lightTextOnDarkBackgroundIsInverted() {
        val pixels = IntArray(100) { if (it < 20) gray(240) else gray(16) }
        val out = CaptchaImageMath.binarizeForOcr(pixels)
        // Chữ (sáng) thành ĐEN, nền (tối) thành TRẮNG.
        assertTrue((0 until 20).all { out[it] == black })
        assertTrue((20 until 100).all { out[it] == white })
    }

    @Test fun binarizeDoesNotModifyItsInputAndKeepsSize() {
        val pixels = IntArray(50) { gray(if (it % 2 == 0) 10 else 245) }
        val copy = pixels.copyOf()
        val out = CaptchaImageMath.binarizeForOcr(pixels)
        assertArrayEquals(copy, pixels)
        assertEquals(pixels.size, out.size)
        assertTrue(out.all { it == black || it == white })
    }

    @Test fun uniformImageBecomesUniformWhite() {
        val out = CaptchaImageMath.binarizeForOcr(IntArray(30) { gray(90) })
        assertTrue(out.all { it == white })
    }
}
