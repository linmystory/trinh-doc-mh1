package com.example.vnscreenreader.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hình học danh sách trình đơn chạm được ([MenuTouchMap]): chạm ở đâu thì ra hàng nào, và cửa sổ hàng khi trình đơn dài. */
class MenuTouchMapTest {

    @Test
    fun rowsShareTheWholeHeightEvenly() {
        val map = MenuTouchMap(count = 14, heightPx = 2340, minRowPx = 146)
        assertEquals(14, map.visible)
        assertEquals(167, map.rowPx) // 2340 / 14 = 167; phần dư 2 px thuộc hàng cuối
        assertEquals(0, map.rowAt(0f, 0))
        assertEquals(0, map.rowAt(166.9f, 0))
        assertEquals(1, map.rowAt(167f, 0))
        assertEquals(13, map.rowAt(2338f, 0))
        assertEquals(13, map.rowAt(2339f, 0)) // phần dư vẫn là hàng cuối, không rơi ra ngoài
    }

    @Test
    fun outsideTheDrawingAreaIsNoRow() {
        val map = MenuTouchMap(5, 1000, 100)
        assertEquals(-1, map.rowAt(-0.5f, 0))
        assertEquals(-1, map.rowAt(1000f, 0))
        assertEquals(-1, map.rowAt(Float.NaN, 0))
        assertEquals(4, map.rowAt(999.9f, 0))
    }

    @Test
    fun emptyOrZeroHeightHasNoRows() {
        for (map in listOf(MenuTouchMap(0, 1000, 100), MenuTouchMap(3, 0, 100))) {
            assertEquals(0, map.visible)
            assertEquals(-1, map.rowAt(10f, 0))
            assertEquals(0, map.firstFor(2, 0))
        }
    }

    @Test
    fun longMenusShowAWindowThatFollowsTheSelection() {
        val map = MenuTouchMap(count = 33, heightPx = 1000, minRowPx = 100)
        assertEquals(10, map.visible)
        assertEquals(100, map.rowPx)
        assertEquals(0, map.firstFor(0, 0))
        assertEquals(0, map.firstFor(9, 0)) // còn trong cửa sổ: không dịch
        assertEquals(3, map.firstFor(12, 0)) // dịch vừa đủ để mục 12 nằm ở hàng cuối của cửa sổ
        assertEquals(3, map.firstFor(5, 3)) // trong cửa sổ [3, 13): giữ nguyên
        assertEquals(2, map.firstFor(2, 3)) // đi lên quá đầu cửa sổ: dịch ít nhất có thể
        assertEquals(23, map.firstFor(32, 0)) // cuối danh sách: cửa sổ không trượt quá cuối
        assertEquals(0, map.firstFor(0, 23)) // vòng về đầu
        // Toạ độ chạm tính theo cửa sổ đang hiện.
        assertEquals(23, map.rowAt(0f, 23))
        assertEquals(32, map.rowAt(999f, 23))
    }

    @Test
    fun aSingleItemCoversTheWholeScreen() {
        val map = MenuTouchMap(1, 2000, 146)
        assertEquals(1, map.visible)
        assertEquals(0, map.rowAt(1999f, 0))
        assertEquals(2000, map.rowBottom(0))
    }

    @Test
    fun minimumRowHeightLargerThanTheScreenStillShowsOneRow() {
        val map = MenuTouchMap(3, 50, 100) // không chia cho 0, không bỏ trống
        assertEquals(1, map.visible)
        assertEquals(50, map.rowPx)
        assertEquals(2, map.firstFor(2, 0))
        assertEquals(2, map.rowAt(10f, 2))
    }

    @Test
    fun rowEdgesCoverTheHeightWithoutGaps() {
        val map = MenuTouchMap(14, 2340, 146)
        assertEquals(0, map.rowTop(0))
        for (local in 1 until map.visible) assertEquals(map.rowBottom(local - 1), map.rowTop(local))
        assertEquals(2340, map.rowBottom(map.visible - 1))
    }

    @Test
    fun windowAlwaysContainsTheSelectionAndStaysInRange() {
        for (count in listOf(1, 2, 7, 14, 33)) {
            for (height in listOf(50, 500, 1000, 2340)) {
                val map = MenuTouchMap(count, height, 100)
                for (selected in 0 until count) {
                    for (first in 0..count) {
                        val f = map.firstFor(selected, first)
                        assertTrue("first=$f", f in 0..(count - map.visible))
                        assertTrue("selected=$selected in [$f, ${f + map.visible})", selected in f until f + map.visible)
                    }
                }
            }
        }
    }
}
