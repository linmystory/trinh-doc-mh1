package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chạm chọn thẳng một mục ([MenuState.select]) và câu đọc có chữ "nhóm" cho mục mở nhóm. */
class MenuTouchSelectTest {
    private val items = listOf(
        MenuItem("Đọc", submenu = MenuKind.GROUP_READING),
        MenuItem("B", GestureAction.BACK),
        MenuItem("C", customActionId = 0x7f010001),
    )

    @Test
    fun selectJumpsStraightToTheTouchedItem() {
        val open = MenuState.open(MenuKind.GLOBAL, items).next()
        val touched = open.select(2)
        assertEquals(2, touched.index)
        assertEquals("C", touched.current!!.label)
        assertSame(open.items, touched.items) // cùng danh sách: lớp vẽ chỉ tô sáng, không dựng lại
        assertEquals("C, 3 trên 3", touched.describeCurrent())
    }

    @Test
    fun selectKeepsTheParentSoBackStillWorks() {
        val root = MenuState.open(MenuKind.GLOBAL, items).next()
        val child = root.child(MenuKind.GROUP_READING, items).next()
        assertSame(root, child.select(1).back())
    }

    @Test
    fun selectIgnoresInvalidOrUnchangedPositions() {
        val s = MenuState.open(MenuKind.GLOBAL, items).next().next()
        assertSame(s, s.select(s.index))
        assertSame(s, s.select(-1))
        assertSame(s, s.select(items.size))
    }

    @Test
    fun selectWorksFromTheOpeningStateToo() {
        val s = MenuState.open(MenuKind.GLOBAL, items) // chưa chọn mục nào
        assertEquals(1, s.select(1).index)
    }

    @Test
    fun touchThenNextContinuesFromTheTouchedItem() {
        val s = MenuState.open(MenuKind.GLOBAL, items).next().select(1)
        assertEquals("C", s.next().current!!.label)
        assertEquals("Đọc", s.previous().current!!.label)
    }

    @Test
    fun groupEntriesAreAnnouncedAsGroupsOnlyWithHints() {
        val s = MenuState.open(MenuKind.GLOBAL, items).next()
        assertEquals("Đọc, nhóm, 1 trên 3", s.describeCurrent())
        assertEquals("Đọc", s.describeCurrent(hints = false))
        assertEquals("B, 2 trên 3", s.next().describeCurrent()) // mục chức năng: không có "nhóm"
    }

    @Test
    fun everyGroupEntryOfTheRealGeneralMenuSaysGroupAndNoOtherEntryDoes() {
        val real = Menus.global()
        var state = MenuState.open(MenuKind.GLOBAL, real)
        var groups = 0
        repeat(real.size) {
            state = state.next()
            val isGroup = state.current!!.submenu != null
            if (isGroup) groups++
            assertEquals(state.describeCurrent(), isGroup, state.describeCurrent().contains(", nhóm, "))
        }
        assertEquals(7, groups)
        assertFalse(state.describeCurrent(hints = false).contains("nhóm,"))
    }

    @Test
    fun openingLineOfAGroupHasTitleAndCount() {
        val child = MenuState.open(MenuKind.GLOBAL, items).next().child(MenuKind.GROUP_READING, items)
        assertTrue(child.opening().endsWith(", 3 mục."))
        assertTrue(child.opening().startsWith(MenuKind.GROUP_READING.title))
    }
}
