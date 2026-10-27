package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDeduplicatorTest {

    @Test
    fun firstEvent_isProcessed() {
        assertTrue(EventDeduplicator(100).shouldProcess("a", 1_000))
    }

    @Test
    fun sameKeyWithinWindow_isDropped() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 1_000))
        assertFalse(dedup.shouldProcess("a", 1_050))
    }

    @Test
    fun sameKeyAfterWindow_isProcessed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 1_000))
        assertTrue(dedup.shouldProcess("a", 1_101))
    }

    @Test
    fun differentKey_isProcessed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 1_000))
        assertTrue(dedup.shouldProcess("b", 1_010))
    }

    @Test
    fun continuousStorm_staysSuppressed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 0))
        assertFalse(dedup.shouldProcess("a", 80))
        assertFalse(dedup.shouldProcess("a", 160))
    }

    @Test
    fun timeGoingBackwards_isNotTreatedAsDuplicate() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 5_000))
        assertTrue(dedup.shouldProcess("a", 4_000))
    }
}

class SpeechQueuePolicyTest {

    @Test
    fun interrupt_alwaysFlushes() {
        assertEquals(SpeechQueuePolicy.Mode.FLUSH, SpeechQueuePolicy.modeFor(true, 0))
    }

    @Test
    fun shortQueue_addsWhenNotInterrupting() {
        assertEquals(SpeechQueuePolicy.Mode.ADD, SpeechQueuePolicy.modeFor(false, SpeechQueuePolicy.MAX_PENDING - 1))
    }

    @Test
    fun longQueue_flushesToDropStaleSpeech() {
        assertEquals(SpeechQueuePolicy.Mode.FLUSH, SpeechQueuePolicy.modeFor(false, SpeechQueuePolicy.MAX_PENDING))
    }
}

class EventDeduplicatorLruTest {

    @Test
    fun alternatingKeys_areBothSuppressed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 0))
        assertTrue(dedup.shouldProcess("b", 10))
        assertFalse(dedup.shouldProcess("a", 20))
        assertFalse(dedup.shouldProcess("b", 30))
    }

    @Test
    fun capacityIsBounded_oldestKeyForgotten() {
        val dedup = EventDeduplicator(1_000, capacity = 2)
        assertTrue(dedup.shouldProcess("a", 0))
        assertTrue(dedup.shouldProcess("b", 1))
        assertTrue(dedup.shouldProcess("c", 2)) // đẩy "a" ra
        assertTrue(dedup.shouldProcess("a", 3)) // "a" đã bị quên
    }
}
