package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechPendingTrackerCompleteTest {
    @Test
    fun complete_returnsTheMessageOnlyWhenItsLastPartFinishes() {
        val t = SpeechPendingTracker()
        t.register("m", "u1")
        t.register("m", "u2")
        assertNull(t.complete("u1"))
        assertEquals("m", t.complete("u2"))
        assertNull("gọi lặp là vô hại", t.complete("u2"))
        assertNull(t.complete("unknown"))
    }
}
