package com.example.vnscreenreader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureIdsTest {

    @Test
    fun everyGesture_hasARealPositiveSystemId_andRoundTrips() {
        Gesture.entries.filter { it.fingers > 0 }.forEach {
            assertTrue("${it.name} có mã hệ thống không hợp lệ: ${it.systemId}", it.systemId > 0)
            assertEquals(it, Gesture.fromSystemId(it.systemId))
        }
    }

    @Test
    fun hardwareKeyTriggers_useNegativeIds_andNeverComeFromTheSystem() {
        Gesture.entries.filter { it.fingers == 0 }.forEach {
            assertTrue("${it.name} phải có mã âm", it.systemId < 0)
            assertNull(it.name, Gesture.fromSystemId(it.systemId))
        }
    }

    @Test
    fun negativeAndUnknownIds_areNotGestures() {
        assertNull(Gesture.fromSystemId(-1)) // GESTURE_PASSTHROUGH
        assertNull(Gesture.fromSystemId(0)) // GESTURE_UNKNOWN
        assertNull(Gesture.fromSystemId(-101))
    }
}
