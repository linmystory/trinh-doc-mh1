package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSwipeTest {
    @Test
    fun nextPage_fingerGoesRightToLeft_inLtr() {
        val line = SwipeGeometry.horizontal(0, 0, 1000, 2000, forward = true, rtl = false)!!
        assertTrue(line.startX > line.endX)
        assertEquals(1000, line.startY)
        assertEquals(line.startY, line.endY)
        assertEquals(800, line.startX)
        assertEquals(200, line.endX)
    }

    @Test
    fun previousPage_andRtl_flipDirection() {
        assertTrue(SwipeGeometry.horizontal(0, 0, 1000, 2000, forward = false, rtl = false)!!.let { it.startX < it.endX })
        assertTrue(SwipeGeometry.horizontal(0, 0, 1000, 2000, forward = true, rtl = true)!!.let { it.startX < it.endX })
    }

    @Test
    fun staysAwayFromScreenEdges_andRejectsTinyAreas() {
        val line = SwipeGeometry.horizontal(0, 0, 1080, 2400, true, false)!!
        assertTrue(line.endX > 100 && line.startX < 1080 - 100)
        assertNull(SwipeGeometry.horizontal(0, 0, 150, 2000, true, false))
        assertNull(SwipeGeometry.horizontal(0, 0, 1000, 50, true, false))
    }

    @Test
    fun pagerHints_matchHorizontalContainersOnly() {
        assertTrue(PagerHints.looksHorizontalPager("androidx.viewpager.widget.ViewPager"))
        assertTrue(PagerHints.looksHorizontalPager("com.android.launcher3.Workspace"))
        assertTrue(PagerHints.looksHorizontalPager("android.widget.HorizontalScrollView"))
        assertFalse(PagerHints.looksHorizontalPager("androidx.recyclerview.widget.RecyclerView"))
        assertFalse(PagerHints.looksHorizontalPager("android.widget.ScrollView"))
        assertFalse(PagerHints.looksHorizontalPager(null))
        assertNotNull(S.MSG_SWIPED_NEXT)
    }
}
