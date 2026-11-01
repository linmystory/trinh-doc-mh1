package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoScrollWithinTest {
    @Test
    fun forward_ignoresFixedHeaderThatIsAlwaysNew() {
        // "" = nút tiêu đề không nhãn ở đầu màn hình, nằm NGOÀI vùng cuộn.
        val old = listOf("", "A", "B", "C")
        val new = listOf("", "C", "D", "E")
        val inside = listOf(false, true, true, true)
        assertEquals(2, AutoScrollPicker.pickWithin(old, new, inside, "C", true)) // sau anchor C là D
    }

    @Test
    fun forward_fullPageScroll_anchorGone_picksFirstNewInside() {
        val old = listOf("", "A", "B", "C")
        val new = listOf("", "D", "E", "F")
        val inside = listOf(false, true, true, true)
        assertEquals(1, AutoScrollPicker.pickWithin(old, new, inside, "C", true))
    }

    @Test
    fun backward_picksItemBeforeAnchor() {
        val new = listOf("X", "Y", "A", "B")
        assertEquals(1, AutoScrollPicker.pickWithin(listOf("A", "B"), new, listOf(true, true, true, true), "A", false))
    }

    @Test
    fun nothingNewInside_returnsNull() {
        assertNull(AutoScrollPicker.pickWithin(listOf("A"), listOf("A"), listOf(true), "A", true))
        assertNull(AutoScrollPicker.pickWithin(listOf("A"), listOf("Z"), listOf(false), "A", true))
    }
}
