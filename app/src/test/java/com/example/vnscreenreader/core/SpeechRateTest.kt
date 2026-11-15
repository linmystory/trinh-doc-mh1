package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Điều khiển đọc "Tốc độ đọc": bước 0,1 trong khoảng 0,5..2,0, không tích lũy sai số số thực. */
class SpeechRateTest {

    @Test
    fun step_movesByOneTenth() {
        assertEquals(1.1f, SpeechRate.step(1.0f, +1), 0.0001f)
        assertEquals(0.9f, SpeechRate.step(1.0f, -1), 0.0001f)
        assertEquals(1.0f, SpeechRate.step(1.0f, 0), 0.0001f)
    }

    @Test
    fun step_clampsAtBothEnds() {
        assertEquals(SpeechRate.MAX, SpeechRate.step(SpeechRate.MAX, +1), 0.0001f)
        assertEquals(SpeechRate.MIN, SpeechRate.step(SpeechRate.MIN, -1), 0.0001f)
        assertEquals(SpeechRate.MAX, SpeechRate.step(1.95f, +1), 0.0001f)
        assertEquals(SpeechRate.MIN, SpeechRate.step(0.55f, -1), 0.0001f)
    }

    @Test
    fun step_ofAValueOutsideTheRange_landsInsideIt() {
        assertEquals(SpeechRate.MIN, SpeechRate.step(0.0f, -1), 0.0001f)
        assertEquals(0.6f, SpeechRate.step(0.1f, +1), 0.0001f)
        assertEquals(SpeechRate.MAX, SpeechRate.step(9f, +1), 0.0001f)
        assertEquals(1.9f, SpeechRate.step(9f, -1), 0.0001f)
    }

    @Test
    fun step_roundsOddValuesToTheNearestTenth() {
        assertEquals(1.3f, SpeechRate.step(1.23f, +1), 0.0001f) // 1,23 -> 1,2 -> 1,3
        assertEquals(1.1f, SpeechRate.step(1.23f, -1), 0.0001f)
    }

    @Test
    fun step_hugeDirection_isStillOneStep() {
        assertEquals(1.1f, SpeechRate.step(1.0f, 7), 0.0001f)
        assertEquals(0.9f, SpeechRate.step(1.0f, -7), 0.0001f)
    }

    @Test
    fun fifteenStepsUp_reachTheMax_withoutDrift() {
        var rate = SpeechRate.MIN
        repeat(15) { rate = SpeechRate.step(rate, +1) }
        assertEquals(SpeechRate.MAX, rate, 0.0001f)
        assertEquals(SpeechRate.MAX, SpeechRate.step(rate, +1), 0.0001f)
        repeat(15) { rate = SpeechRate.step(rate, -1) }
        assertEquals(SpeechRate.MIN, rate, 0.0001f)
    }

    @Test
    fun everyStepLandsOnAMultipleOfTenPercent() {
        var rate = 1.0f
        repeat(40) { index ->
            rate = SpeechRate.step(rate, if (index % 7 < 4) +1 else -1)
            assertEquals("bước $index", 0, SpeechRate.percent(rate) % 10)
            assertTrue(rate in SpeechRate.MIN..SpeechRate.MAX)
        }
    }

    @Test
    fun percent_isTheSpokenValue() {
        assertEquals(100, SpeechRate.percent(1.0f))
        assertEquals(150, SpeechRate.percent(1.5f))
        assertEquals(50, SpeechRate.percent(0.5f))
        assertEquals(200, SpeechRate.percent(2.0f))
        assertEquals(110, SpeechRate.percent(1.1f)) // 1,1f là 1,10000002: vẫn làm tròn đúng
        assertEquals("Tốc độ đọc 130 phần trăm", t(S.MSG_RATE_SET, SpeechRate.percent(1.3f)))
    }

    @Test
    fun theRange_matchesTheSettingsSlider() {
        assertEquals(SettingsRepository.MIN_VALUE, SpeechRate.MIN, 0.0001f)
        assertEquals(SettingsRepository.MAX_VALUE, SpeechRate.MAX, 0.0001f)
    }
}
