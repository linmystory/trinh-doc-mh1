package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusTrackerTest {
    private fun box(top: Int) = intArrayOf(0, top, 100, top + 50)
    private val boxes = listOf(box(0), box(100), box(200), box(300))
    private val labels = listOf("A", "B", "C", "D")

    @Test
    fun sameLabelStillPresent_bindsToIt() {
        assertEquals(2, FocusTracker.virtualCurrent(boxes, labels, box(250), "C", true))
    }

    @Test
    fun vanished_forward_nextIsFirstElementAfterSlot() {
        val i = FocusTracker.virtualCurrent(boxes, labels, box(150), "gone", true)
        assertEquals(1, i) // next -> index 2 (top 200), không về 0
        assertEquals(2, FocusIndex.next(i, boxes.size))
    }

    @Test
    fun vanished_backward_previousIsLastElementBeforeSlot() {
        val i = FocusTracker.virtualCurrent(boxes, labels, box(150), "gone", false)
        assertEquals(1, FocusIndex.previous(i, boxes.size)) // top 100
    }

    @Test
    fun pastTheEnd_forward_hitsBoundaryNotStart() {
        val i = FocusTracker.virtualCurrent(boxes, labels, box(900), "", true)
        assertEquals(null, FocusIndex.next(i, boxes.size))
    }

    @Test
    fun emptyList_andDuplicateLabels_pickNearest() {
        assertEquals(-1, FocusTracker.virtualCurrent(emptyList(), emptyList(), box(0), "x", true))
        val dup = listOf("Mua", "Mua", "Mua")
        assertEquals(2, FocusTracker.virtualCurrent(listOf(box(0), box(100), box(200)), dup, box(210), "Mua", true))
    }

    @Test
    fun longPressAndVerticalSwipe() {
        assertEquals(900L, LongPressTiming.durationMs(700))
        assertEquals(500L, LongPressTiming.durationMs(0))
        assertEquals(3000L, LongPressTiming.durationMs(99999))
        val up = SwipeGeometry.vertical(0, 0, 1000, 2000, true)!!
        assertEquals(true, up.startY > up.endY)
        assertEquals(500, up.startX)
        assertEquals(null, SwipeGeometry.vertical(0, 0, 1000, 150, true))
    }
}
