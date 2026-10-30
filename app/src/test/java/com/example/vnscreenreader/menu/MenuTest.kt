package com.example.vnscreenreader.menu

import com.example.vnscreenreader.core.FeedbackMode
import com.example.vnscreenreader.core.ScreenSearch
import com.example.vnscreenreader.core.SpeechFormats
import com.example.vnscreenreader.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuStateTest {
    private val items = listOf(
        MenuItem("A", GestureAction.HOME),
        MenuItem("B", GestureAction.BACK),
        MenuItem("C", customActionId = 0x7f010001),
    )

    @Test
    fun opensWithoutSelection_thenNextStartsAtFirst() {
        val s = MenuState.open(MenuKind.GLOBAL, items)
        assertNull(s.current)
        assertEquals("Trình đơn chung, 3 mục.", s.opening())
        assertEquals("A, 1 trên 3", s.next().describeCurrent())
    }

    @Test
    fun wrapsAroundBothWays() {
        val s = MenuState.open(MenuKind.LOCAL, items)
        assertEquals("C", s.previous().current!!.label)
        assertEquals("A", s.next().next().next().next().current!!.label)
        assertEquals("B", s.next().previous().previous().current!!.label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyMenu_isRejected() {
        MenuState.open(MenuKind.GLOBAL, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun itemNeedsExactlyOneTarget() {
        MenuItem("x")
    }

    @Test
    fun menus_haveExpectedContent_andOnlyUniqueActions() {
        val global = Menus.global()
        assertTrue(global.any { it.action == GestureAction.READ_FROM_TOP })
        assertTrue(Menus.globalMore().any { it.action == GestureAction.OPEN_SETTINGS })
        assertEquals(global.size, global.map { it.action }.toSet().size)
        val local = Menus.local(listOf(CustomAction(0x7f000001, "Xóa tin")))
        assertEquals("Nhấn", local.first().label)
        assertEquals(0x7f000001, local.last().customActionId)
    }

    @Test
    fun customActions_areCapped() {
        val many = (1..50).map { CustomAction(0x7f000000 + it, "a$it") }
        assertEquals(11 + Menus.MAX_CUSTOM_ACTIONS, Menus.local(many).size)
    }

    @Test
    fun customActionDetection() {
        assertTrue(Menus.isCustomAction(0x7f0a0001, "Xóa"))
        assertFalse(Menus.isCustomAction(0x7f0a0001, ""))
        assertFalse(Menus.isCustomAction(0x7f0a0001, null))
        assertFalse(Menus.isCustomAction(16, "Nhấn")) // ACTION_CLICK
        assertFalse(Menus.isCustomAction(0x01020036, "Cuộn")) // android.R.id.*
    }
}

class FeedbackModeTest {
    @Test
    fun mapsBooleansBothWays() {
        FeedbackMode.entries.forEach { assertEquals(it, FeedbackMode.of(it.haptic, it.sound)) }
        assertEquals(FeedbackMode.HAPTIC_ONLY, FeedbackMode.of(true, false))
        assertEquals(FeedbackMode.OFF, FeedbackMode.of(false, false))
    }

    @Test
    fun cyclesThroughAllFour() {
        assertEquals(FeedbackMode.entries.toSet(), generateSequence(FeedbackMode.BOTH) { it.next() }.take(4).toSet())
        assertEquals(FeedbackMode.BOTH, FeedbackMode.OFF.next())
    }
}

class ScreenSearchTest {
    private val d = listOf("Trang chủ, nút", "Tìm kiếm, ô nhập", "Đăng nhập, nút", "Tìm bạn bè, nút")

    @Test
    fun ignoresCaseAndDiacritics() {
        assertEquals(1, ScreenSearch.find(d, "tim kiem", -1))
        assertEquals(2, ScreenSearch.find(d, "DANG NHAP", -1))
        assertEquals(2, ScreenSearch.find(d, "đăng", -1))
    }

    @Test
    fun repeatedSearch_movesToNext_andWraps() {
        assertEquals(1, ScreenSearch.find(d, "tìm", 0))
        assertEquals(3, ScreenSearch.find(d, "tìm", 1))
        assertEquals(1, ScreenSearch.find(d, "tìm", 3))
    }

    @Test
    fun noMatch_orBlank_isNull() {
        assertNull(ScreenSearch.find(d, "xyz", 0))
        assertNull(ScreenSearch.find(d, "   ", 0))
        assertNull(ScreenSearch.find(emptyList(), "a", 0))
    }
}

class SpeechFormatsTest {
    @Test
    fun spell_and_limits() {
        assertEquals("X, i, n, dấu cách, c", SpeechFormats.spell("Xin c"))
        assertTrue(SpeechFormats.spell("a".repeat(500)).endsWith("và còn nữa"))
    }

    @Test
    fun timeAndBattery() {
        assertEquals("Bây giờ là 9 giờ", SpeechFormats.time(9, 0))
        assertEquals("Bây giờ là 21 giờ 5 phút", SpeechFormats.time(21, 5))
        assertEquals("Pin 80 phần trăm, đang sạc", SpeechFormats.battery(80, true))
        assertEquals("Pin 100 phần trăm", SpeechFormats.battery(250, false))
    }
}
