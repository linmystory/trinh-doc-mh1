package com.example.vnscreenreader.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneSynthTest {
    @Test
    fun hasExpectedLength_andFadesAtEnds() {
        val pcm = ToneSynth.sine(1000, 100)
        assertEquals(ToneSynth.SAMPLE_RATE / 10, pcm.size)
        assertEquals(0, pcm.first().toInt())
        assertEquals(0, pcm.last().toInt())
        assertTrue(pcm.any { it > 10_000 })
    }

    @Test
    fun volumeScalesAmplitude() {
        val loud = ToneSynth.sine(1000, 50, 1f).maxOf { it.toInt() }
        val quiet = ToneSynth.sine(1000, 50, 0.25f).maxOf { it.toInt() }
        assertTrue(quiet < loud / 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFrequency_isRejected() {
        ToneSynth.sine(5, 10)
    }
}

class FeedbackThrottleTest {
    @Test
    fun allowsFirst_blocksWithinInterval_allowsAfter() {
        val t = FeedbackThrottle(50)
        assertTrue(t.tryAcquire(1_000))
        assertFalse(t.tryAcquire(1_020))
        assertTrue(t.tryAcquire(1_050))
    }

    @Test
    fun timeGoingBackwards_isAllowed() {
        val t = FeedbackThrottle(50)
        assertTrue(t.tryAcquire(5_000))
        assertTrue(t.tryAcquire(100))
    }
}
