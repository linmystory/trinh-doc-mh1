package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TraversalOrderTest {
    private fun none(n: Int) = IntArray(n) { -1 }

    @Test fun noConstraintsKeepsOrder() {
        assertEquals(listOf(0, 1, 2, 3), TraversalOrder.apply(4, none(4), none(4)))
        assertEquals(emptyList<Int>(), TraversalOrder.apply(0, none(0), none(0)))
    }

    @Test fun afterMovesItemRightBehindTarget() {
        val after = none(4).also { it[0] = 2 }
        assertEquals(listOf(1, 2, 0, 3), TraversalOrder.apply(4, after, none(4)))
    }

    @Test fun beforeMovesItemRightInFrontOfTarget() {
        val before = none(4).also { it[3] = 1 }
        assertEquals(listOf(0, 3, 1, 2), TraversalOrder.apply(4, none(4), before))
    }

    @Test fun cyclesAndSelfReferencesTerminateWithoutLosingItems() {
        val after = intArrayOf(1, 0, 2)
        val order = TraversalOrder.apply(3, after, none(3))
        assertEquals(listOf(0, 1, 2), order.sorted())
    }

    @Test fun outOfRangeTargetsAreIgnored() {
        val after = intArrayOf(9, -1, -1)
        assertEquals(listOf(0, 1, 2), TraversalOrder.apply(3, after, none(3)))
    }
}
