package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingModeTest {
    @Test
    fun cycle_wrapsBothWays() {
        assertEquals(ReadingMode.HEADING, ReadingMode.ELEMENT.next())
        assertEquals(ReadingMode.IMAGE, ReadingMode.ELEMENT.previous())
        assertEquals(ReadingMode.ELEMENT, ReadingMode.IMAGE.next())
    }

    @Test
    fun matches_perMode() {
        val heading = NodeSnapshot(text = "Tin", isHeading = true)
        val link = NodeSnapshot(text = "Đọc tiếp", roleDescription = "Liên kết", isClickable = true)
        val button = NodeSnapshot(text = "Gửi", className = "android.widget.Button", isClickable = true)
        val edit = NodeSnapshot(isEditable = true, className = "android.widget.EditText")
        val box = NodeSnapshot(isCheckable = true, className = "android.widget.CheckBox")
        val img = NodeSnapshot(className = "android.widget.ImageView")
        assertTrue(ReadingMode.HEADING.matches(heading)); assertFalse(ReadingMode.HEADING.matches(button))
        assertTrue(ReadingMode.LINK.matches(link)); assertFalse(ReadingMode.LINK.matches(button))
        assertTrue(ReadingMode.CONTROL.matches(button)); assertTrue(ReadingMode.CONTROL.matches(edit)); assertFalse(ReadingMode.CONTROL.matches(heading))
        assertFalse(ReadingMode.CONTROL.matches(button.copy(isEnabled = false)))
        assertTrue(ReadingMode.EDIT.matches(edit)); assertTrue(ReadingMode.CHECKABLE.matches(box))
        assertTrue(ReadingMode.IMAGE.matches(img)); assertTrue(ReadingMode.ELEMENT.matches(heading))
    }

    @Test
    fun search_forwardAndBackward_skipsNonMatching_andStopsAtEnds() {
        val m = listOf(false, true, false, false, true, false)
        assertEquals(1, ReadingModeSearch.next(m, -1, true))
        assertEquals(4, ReadingModeSearch.next(m, 1, true))
        assertNull(ReadingModeSearch.next(m, 4, true))
        assertEquals(4, ReadingModeSearch.next(m, -1, false))
        assertEquals(1, ReadingModeSearch.next(m, 4, false))
        assertNull(ReadingModeSearch.next(m, 1, false))
        assertNull(ReadingModeSearch.next(emptyList(), 0, true))
        assertEquals(4, ReadingModeSearch.next(m, 99, false)) // chỉ số vượt biên không văng lỗi
    }

    @Test
    fun containers_forwardJumpsToNextRun_backwardGoesToRunStartThenPreviousRun() {
        val keys = listOf(0, 0, 7, 7, 7, 9)
        assertEquals(2, ContainerNav.target(keys, 0, true))
        assertEquals(5, ContainerNav.target(keys, 3, true))
        assertNull(ContainerNav.target(keys, 5, true))
        assertEquals(2, ContainerNav.target(keys, 4, false)) // giữa vùng: về đầu vùng
        assertEquals(0, ContainerNav.target(keys, 2, false)) // đầu vùng: về đầu vùng trước
        assertEquals(0, ContainerNav.target(keys, 1, false)) // giữa vùng đầu: về đầu vùng
        assertNull(ContainerNav.target(keys, 0, false)) // đã ở đầu vùng đầu tiên
        assertEquals(0, ContainerNav.target(keys, -1, true))
        assertEquals(5, ContainerNav.target(keys, -1, false))
        assertNull(ContainerNav.target(emptyList(), 0, true))
    }

    @Test
    fun windows_cycleAndFallbacks() {
        val ids = listOf(10, 20, 30)
        assertEquals(20, WindowCycle.next(ids, 10, true))
        assertEquals(10, WindowCycle.next(ids, 30, true))
        assertEquals(30, WindowCycle.next(ids, 10, false))
        assertEquals(10, WindowCycle.next(ids, 99, true))
        assertEquals(30, WindowCycle.next(ids, null, false))
        assertNull(WindowCycle.next(listOf(10), 10, true))
        assertNull(WindowCycle.next(emptyList(), 10, true))
    }
}
