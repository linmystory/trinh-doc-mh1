package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSettleTest {
    private fun page(vararg labels: String) = labels.mapIndexed { i, l -> ScrollItem(l, 0, 100 * i, 1000, 100 * i + 100) }

    private val before = page("A", "B")

    @Test
    fun unchangedContentNeverSettles() {
        val settle = ScrollSettle(before)
        repeat(5) { assertFalse(settle.offer(page("A", "B"))) }
        assertFalse(settle.changed)
    }

    @Test
    fun changedThenStableAcrossTwoPollsSettles() {
        val settle = ScrollSettle(before)
        assertFalse(settle.offer(page("B", "C"))) // vừa đổi: còn có thể đang trượt
        assertTrue(settle.changed)
        assertTrue(settle.offer(page("B", "C"))) // hai lần liền giống nhau: dừng
    }

    @Test
    fun stillMovingDoesNotSettle() {
        val settle = ScrollSettle(before)
        assertFalse(settle.offer(page("A", "C")))
        assertFalse(settle.offer(page("B", "C")))
        assertFalse(settle.offer(page("C", "D")))
        assertTrue(settle.changed)
        assertTrue(settle.offer(page("C", "D")))
    }

    @Test
    fun slowAppThatUpdatesLateStillSettles() {
        // Vài lần thăm dò đầu cây giao diện chưa cập nhật (giống hệt trước khi cuộn), rồi mới đổi.
        val settle = ScrollSettle(before)
        assertFalse(settle.offer(page("A", "B")))
        assertFalse(settle.offer(page("A", "B")))
        assertFalse(settle.offer(page("C", "D")))
        assertTrue(settle.offer(page("C", "D")))
    }

    @Test
    fun goingBackToTheOriginalCancelsTheChange() {
        val settle = ScrollSettle(before)
        assertFalse(settle.offer(page("B", "C")))
        assertFalse(settle.offer(page("A", "B"))) // trở về như cũ
        assertFalse(settle.changed)
        assertFalse(settle.offer(page("B", "C"))) // lần đổi mới phải tự đủ hai lần liền
        assertTrue(settle.offer(page("B", "C")))
    }

    @Test
    fun movingTheSameLabelsToNewPositionsCountsAsChanged() {
        val settle = ScrollSettle(before)
        val moved = listOf(ScrollItem("A", 0, 50, 1000, 150), ScrollItem("B", 0, 150, 1000, 250))
        assertFalse(settle.offer(moved))
        assertEquals(true, settle.changed)
        assertTrue(settle.offer(moved))
    }
}
