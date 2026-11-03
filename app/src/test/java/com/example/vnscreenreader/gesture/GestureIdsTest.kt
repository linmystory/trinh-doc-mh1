package com.example.vnscreenreader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureIdsTest {

    @Test
    fun everyGesture_hasARealPositiveSystemId_andRoundTrips() {
        Gesture.entries.forEach {
            assertTrue("${it.name} có mã hệ thống không hợp lệ: ${it.systemId}", it.systemId > 0)
            assertEquals(it, Gesture.fromSystemId(it.systemId))
        }
    }

    @Test
    fun negativeAndUnknownIds_areNotGestures() {
        assertNull(Gesture.fromSystemId(-1)) // GESTURE_PASSTHROUGH
        assertNull(Gesture.fromSystemId(0)) // GESTURE_UNKNOWN
        assertNull(Gesture.fromSystemId(-101))
    }
}
