package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickNavTest {
    private val matches = listOf(false, true, false, false, true, false)

    @Test fun forwardSkipsCurrent() {
        assertEquals(4, QuickNav.find(matches.size, 1, forward = true) { matches[it] })
        assertEquals(1, QuickNav.find(matches.size, 0, forward = true) { matches[it] })
    }

    @Test fun backwardSkipsCurrent() {
        assertEquals(1, QuickNav.find(matches.size, 4, forward = false) { matches[it] })
        assertNull(QuickNav.find(matches.size, 1, forward = false) { matches[it] })
    }

    @Test fun noSelectionStartsFromEdges() {
        assertEquals(1, QuickNav.find(matches.size, -1, forward = true) { matches[it] })
        assertEquals(4, QuickNav.find(matches.size, -1, forward = false) { matches[it] })
    }

    @Test fun noneLeftAndEmptyAndOutOfRange() {
        assertNull(QuickNav.find(matches.size, 4, forward = true) { matches[it] })
        assertNull(QuickNav.find(0, -1, forward = true) { true })
        assertEquals(4, QuickNav.find(matches.size, 99, forward = false) { matches[it] }) // chỉ số vượt biên được kẹp
    }

    @Test fun listPositionPrefersRowsThenColumnsAndRejectsUnknown() {
        val p = ListPositions.of(rowIndex = 2, rowCount = 12, columnIndex = 0, columnCount = 1)!!
        assertEquals(3, p.index)
        assertEquals(12, p.total)
        assertEquals(2, ListPositions.of(-1, 1, 1, 5)!!.index)
        assertNull(ListPositions.of(-1, -1, -1, -1)) // danh sách nạp dần: số lượng chưa biết
        assertNull(ListPositions.of(0, 1, 0, 1)) // chỉ một mục
        assertNull(ListPositions.of(12, 12, 0, 1)) // chỉ số vượt số lượng
    }
}
