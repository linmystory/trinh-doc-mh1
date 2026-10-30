package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollOrderingTest {

    @Test
    fun ancestorsFirstNearestFirstThenLargestArea() {
        val ordered = ScrollOrdering.order(
            listOf(
                ScrollCandidate(0, null, 1_000),      // danh sách ngang nhỏ, đứng đầu cây
                ScrollCandidate(1, null, 2_000_000),  // nội dung chính
                ScrollCandidate(2, 1, 500_000),       // cha xa
                ScrollCandidate(3, 0, 10_000),        // cha gần nhất
            ),
        )
        assertEquals(listOf(3, 2, 1, 0), ordered.map { it.id })
    }

    @Test
    fun smallCarouselNoLongerWinsOverMainList() {
        val ordered = ScrollOrdering.order(listOf(ScrollCandidate(0, null, 100), ScrollCandidate(1, null, 9_000)))
        assertEquals(1, ordered.first().id)
    }

    @Test
    fun emptyAndStableForEqualArea() {
        assertEquals(emptyList<ScrollCandidate>(), ScrollOrdering.order(emptyList()))
        val same = listOf(ScrollCandidate(0, null, 5), ScrollCandidate(1, null, 5))
        assertEquals(listOf(0, 1), ScrollOrdering.order(same).map { it.id })
    }
}
