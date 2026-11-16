package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollEdgeTest {
    private val area = intArrayOf(0, 100, 500, 900)

    // Mã hành động giả: chỉ cần khác nhau.
    private val left = 11
    private val right = 12
    private val up = 13
    private val down = 14
    private val forward = 15
    private val backward = 16

    private fun horizontalOnly(vararg ids: Int) = ScrollEdge.isHorizontalOnly(ids.toList(), left, right, up, down)

    @Test
    fun centerInsideAreaCounts() {
        assertTrue(ScrollEdge.containsCenter(area, intArrayOf(10, 200, 110, 300)))
    }

    @Test
    fun leftAndTopEdgesAreInsideButRightAndBottomEdgesAreOutside() {
        assertTrue(ScrollEdge.containsCenter(area, intArrayOf(-10, 90, 10, 110))) // tâm (0, 100)
        assertFalse(ScrollEdge.containsCenter(area, intArrayOf(490, 400, 510, 410))) // tâm x = 500
        assertFalse(ScrollEdge.containsCenter(area, intArrayOf(100, 890, 200, 910))) // tâm y = 900
        assertTrue(ScrollEdge.containsCenter(area, intArrayOf(100, 889, 200, 909))) // tâm y = 899
    }

    @Test
    fun itemWhoseCenterIsOutsideIsOutsideEvenIfItsBoxOverlaps() {
        assertFalse(ScrollEdge.containsCenter(area, intArrayOf(0, 50, 100, 140))) // tâm y = 95, phía trên vùng
        assertFalse(ScrollEdge.containsCenter(area, intArrayOf(0, 860, 100, 1000))) // tâm y = 930, phía dưới vùng
    }

    @Test
    fun carouselsWithOnlySidewaysActionsAreHorizontalOnly() {
        assertTrue(horizontalOnly(left, right))
        assertTrue(horizontalOnly(left, forward, backward)) // chỉ có trái/phải kèm tiến/lùi chung
        assertTrue(horizontalOnly(right))
    }

    @Test
    fun anyVerticalActionMakesItAVerticalScroller() {
        assertFalse(horizontalOnly(left, right, up, down)) // cuộn cả hai chiều
        assertFalse(horizontalOnly(left, down))
        assertFalse(horizontalOnly(up, down, forward, backward))
        assertFalse(horizontalOnly(forward, backward)) // chỉ tiến/lùi chung: coi là dọc
        assertFalse(horizontalOnly())
    }

    @Test
    fun valuePickersAndRangesAreNotContentScrollers() {
        assertFalse(ScrollEdge.isContentScroller("android.widget.NumberPicker", false))
        assertFalse(ScrollEdge.isContentScroller("android.widget.TimePicker", false))
        assertFalse(ScrollEdge.isContentScroller("android.widget.SeekBar", false))
        assertFalse(ScrollEdge.isContentScroller("com.google.android.material.slider.Slider", false))
        assertFalse(ScrollEdge.isContentScroller("com.example.ui.WheelView", false))
        assertFalse(ScrollEdge.isContentScroller("androidx.recyclerview.widget.RecyclerView", true)) // có khoảng giá trị
    }

    @Test
    fun listsAndPagesAreContentScrollers() {
        assertTrue(ScrollEdge.isContentScroller("androidx.recyclerview.widget.RecyclerView", false))
        assertTrue(ScrollEdge.isContentScroller("android.widget.ScrollView", false))
        assertTrue(ScrollEdge.isContentScroller("android.webkit.WebView", false))
        assertTrue(ScrollEdge.isContentScroller("android.view.View", false)) // khung của Compose
        assertTrue(ScrollEdge.isContentScroller(null, false)) // không biết lớp: coi là khung nội dung
        assertEquals(true, ScrollEdge.isContentScroller("", false))
    }
}
