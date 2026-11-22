package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechShieldTest {

    @Test
    fun nothingIsProtectedUntilAskedTo() {
        val shield = SpeechShield(holdMs = 6_000)
        assertTrue(shield.mayInterrupt(nowMs = 0, speaking = true))
        assertTrue(shield.mayInterrupt(nowMs = 123_456, speaking = true))
    }

    @Test
    fun protectedSentenceCannotBeCutWhileItIsStillSpeaking() {
        val shield = SpeechShield(holdMs = 6_000)
        shield.protect(nowMs = 10_000)
        assertFalse(shield.mayInterrupt(nowMs = 10_000, speaking = true))
        assertFalse(shield.mayInterrupt(nowMs = 15_999, speaking = true))
    }

    @Test
    fun holdEndsExactlyAfterHoldMs() {
        val shield = SpeechShield(holdMs = 6_000)
        shield.protect(nowMs = 10_000)
        assertTrue(shield.mayInterrupt(nowMs = 16_000, speaking = true))
        assertTrue(shield.mayInterrupt(nowMs = 60_000, speaking = true))
    }

    @Test
    fun whenNothingIsSpeakingThereIsNothingToProtect() {
        val shield = SpeechShield(holdMs = 6_000)
        shield.protect(nowMs = 10_000)
        assertTrue(shield.mayInterrupt(nowMs = 10_500, speaking = false))
    }

    @Test
    fun protectingAgainRestartsTheHold() {
        val shield = SpeechShield(holdMs = 6_000)
        shield.protect(nowMs = 10_000)
        shield.protect(nowMs = 14_000)
        assertFalse(shield.mayInterrupt(nowMs = 19_999, speaking = true))
        assertTrue(shield.mayInterrupt(nowMs = 20_000, speaking = true))
    }

    @Test
    fun defaultHoldIsTheDocumentedConstant() {
        val shield = SpeechShield()
        shield.protect(nowMs = 0)
        assertFalse(shield.mayInterrupt(nowMs = SpeechShield.DEFAULT_HOLD_MS - 1, speaking = true))
        assertTrue(shield.mayInterrupt(nowMs = SpeechShield.DEFAULT_HOLD_MS, speaking = true))
    }
}
