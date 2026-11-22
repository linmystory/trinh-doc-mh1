package com.example.vnscreenreader.menu

import com.example.vnscreenreader.core.EditOp
import com.example.vnscreenreader.core.EditPolicy
import com.example.vnscreenreader.core.SelectionMath
import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmenuAndEditTest {

    @Test fun globalMenuOrderAndLimit() {
        val g = Menus.global()
        val actions = g.mapNotNull { it.action }
        assertEquals(
            listOf(
                GestureAction.READ_FROM_TOP, GestureAction.READ_FROM_CURRENT, GestureAction.REPEAT_LAST,
                GestureAction.SEARCH_SCREEN, GestureAction.OCR_SCREEN, GestureAction.NOTIFICATIONS, GestureAction.HOME,
            ),
            actions,
        )
        assertEquals(Menus.MAX_GLOBAL_TOP_ACTIONS, actions.size)
        // Sau 7 mục quen thuộc: mỗi nhóm một mục mở trình đơn con, theo đúng thứ tự nhóm, và không còn gì khác.
        assertEquals(MenuGroup.entries.map { it.kind }, g.drop(actions.size).map { it.submenu })
    }

    @Test fun groupSubmenusListTheirFunctionsAndEndWithBack() {
        MenuGroup.entries.forEach { group ->
            val items = Menus.submenu(group.kind)!!
            assertTrue(group.name, items.last().isBack)
            assertEquals(group.name, group.actions, items.dropLast(1).map { it.action })
        }
        // Chỉ nhóm và "Chỉnh sửa văn bản" của phần tử là trình đơn con; trình đơn gốc thì không.
        assertNull(Menus.submenu(MenuKind.GLOBAL))
        assertNull(Menus.submenu(MenuKind.LOCAL))
        assertNotNull(Menus.submenu(MenuKind.LOCAL_EDIT))
    }

    @Test fun backReturnsToParentKeepingSelection() {
        val top = MenuState.open(MenuKind.GLOBAL, Menus.global())
        var s = top
        repeat(Menus.global().size) { s = s.next() } // dừng ở mục cuối: nhóm cuối cùng
        val lastGroup = MenuGroup.entries.last()
        assertEquals(lastGroup.kind, s.current!!.submenu)
        val child = s.child(lastGroup.kind, Menus.submenu(lastGroup.kind)!!)
        assertEquals(-1, child.index)
        assertEquals(s.index, child.back()!!.index)
        assertNull(top.back())
    }

    @Test fun editSubmenuOnlyForEditable() {
        assertNull(Menus.local(emptyList(), editable = false).firstOrNull { it.submenu != null })
        val editable = Menus.local(emptyList(), editable = true)
        assertEquals(MenuKind.LOCAL_EDIT, editable.single { it.submenu != null }.submenu)
        // Mục chỉnh sửa không còn nằm ở cấp đầu.
        val topActions = Menus.local(emptyList(), editable = true).mapNotNull { it.action }
        assertFalse(GestureAction.PASTE_FOCUSED in topActions || GestureAction.CUT_FOCUSED in topActions)
        val edit = Menus.localEdit()
        assertTrue(edit.last().isBack)
        assertTrue(edit.mapNotNull { it.action }.containsAll(
            listOf(GestureAction.PASTE_FOCUSED, GestureAction.CURSOR_START, GestureAction.SELECT_TO_END),
        ))
    }

    @Test fun editFieldDetection() {
        assertTrue(EditPolicy.isEditField(true, null))
        assertTrue(EditPolicy.isEditField(false, "android.widget.EditText"))
        assertTrue(EditPolicy.isEditField(false, "androidx.appcompat.widget.AppCompatEditText"))
        assertFalse(EditPolicy.isEditField(false, "android.widget.TextView"))
        assertFalse(EditPolicy.isEditField(false, null))
    }

    @Test fun openingTextHasCountAndPeriod() {
        val s = MenuState.open(MenuKind.GLOBAL, Menus.global())
        assertEquals("Trình đơn chung, ${s.items.size} mục.", s.opening())
    }

    @Test fun announcementGateBlocksConsecutiveRepeat() {
        val g = AnnouncementGate()
        assertTrue(g.shouldSpeak("hint"))
        assertFalse(g.shouldSpeak("hint"))
        g.record("Mục 1")
        assertTrue(g.shouldSpeak("hint")) // đã có thông báo khác xen giữa
        g.reset()
        assertTrue(g.shouldSpeak("hint"))
    }

    @Test fun selectionRangeIncludingReversed() {
        assertEquals(2 to 5, SelectionMath.selectedRange(2, 5, 10))
        assertEquals(2 to 5, SelectionMath.selectedRange(5, 2, 10)) // chọn ngược
        assertNull(SelectionMath.selectedRange(3, 3, 10))
        assertNull(SelectionMath.selectedRange(-1, -1, 10))
        assertNull(SelectionMath.selectedRange(-1, 4, 10))
        assertEquals(0 to 4, SelectionMath.selectedRange(0, 99, 4)) // chặn trong độ dài
    }

    @Test fun copyOnlySelectedPart() {
        assertEquals("llo", SelectionMath.selectedText("hello world", 2, 5))
        assertEquals("llo", SelectionMath.selectedText("hello world", 5, 2))
        assertNull(SelectionMath.selectedText("hello", 1, 1))
        assertNull(SelectionMath.selectedText("hello", -1, -1))
    }

    @Test fun cursorOperations() {
        assertEquals(0 to 0, SelectionMath.target(EditOp.CURSOR_START, 10, 4, 4))
        assertEquals(10 to 10, SelectionMath.target(EditOp.CURSOR_END, 10, 4, 4))
        assertEquals(4 to 0, SelectionMath.target(EditOp.SELECT_TO_START, 10, 4, 4))
        assertEquals(4 to 10, SelectionMath.target(EditOp.SELECT_TO_END, 10, 4, 4))
        assertEquals(0 to 10, SelectionMath.target(EditOp.SELECT_TO_END, 10, -1, -1))
        assertNull(SelectionMath.target(EditOp.PASTE, 10, 0, 0))
    }

    @Test fun cursorOperationsAlreadyAtTheGoalAreNoOps() {
        // TextView trả false cho ACTION_SET_SELECTION khi vùng chọn không đổi: những trường hợp này phải báo "xong", không gửi lại.
        assertTrue(SelectionMath.isNoOp(EditOp.CURSOR_START, 10, 0, 0))
        assertTrue(SelectionMath.isNoOp(EditOp.CURSOR_END, 10, 10, 10))
        assertTrue(SelectionMath.isNoOp(EditOp.SELECT_ALL, 10, 0, 10))
        assertTrue(SelectionMath.isNoOp(EditOp.SELECT_TO_START, 10, 4, 0)) // đã chọn từ điểm neo 4 về đầu
        assertTrue(SelectionMath.isNoOp(EditOp.SELECT_TO_END, 10, 4, 10))
        // Chưa ở đích: vẫn phải thực hiện.
        assertFalse(SelectionMath.isNoOp(EditOp.CURSOR_START, 10, 4, 4))
        assertFalse(SelectionMath.isNoOp(EditOp.CURSOR_END, 10, 0, 0))
        assertFalse(SelectionMath.isNoOp(EditOp.SELECT_ALL, 10, 0, 0))
        assertFalse(SelectionMath.isNoOp(EditOp.SELECT_ALL, 10, 3, 10))
        assertFalse(SelectionMath.isNoOp(EditOp.CURSOR_START, 10, -1, -1)) // chưa biết con trỏ ở đâu: cứ đặt
        // Ô trống (kể cả đang hiện gợi ý, khi đó độ dài chữ thật là 0): con trỏ vốn ở cả đầu lẫn cuối.
        EditOp.entries.filter { it != EditOp.PASTE && it != EditOp.CUT }.forEach {
            assertTrue("$it trên ô trống", SelectionMath.isNoOp(it, 0, -1, -1))
            assertTrue("$it trên ô trống", SelectionMath.isNoOp(it, 0, 0, 0))
        }
        // Dán và cắt không phải thao tác vùng chọn.
        assertFalse(SelectionMath.isNoOp(EditOp.PASTE, 10, 0, 0))
        assertFalse(SelectionMath.isNoOp(EditOp.CUT, 0, 0, 0))
    }

    @Test fun passwordOnlyAllowsPaste() {
        EditOp.entries.forEach { assertEquals(it == EditOp.PASTE, EditPolicy.allowedOnPassword(it)) }
    }

    @Test fun defaultsAndRealMappingLookup() {
        val m = GestureMapping()
        assertEquals(listOf(Gesture.DOWN_THEN_RIGHT, Gesture.THREE_FINGER_TAP), m.gesturesFor(GestureAction.OPEN_GLOBAL_MENU))
        assertNotNull(m.gesturesFor(GestureAction.OPEN_LOCAL_MENU).firstOrNull())
        // Người đã lưu cấu hình cũ giữ nguyên.
        val old = GestureMapping.parse("DOWN_THEN_RIGHT=OPEN_LOCAL_MENU;UP_THEN_RIGHT=OPEN_GLOBAL_MENU")
        assertEquals(GestureAction.OPEN_LOCAL_MENU, old.actionFor(Gesture.DOWN_THEN_RIGHT))
        // Chưa lưu gì: dùng mặc định mới.
        assertEquals(GestureAction.OPEN_GLOBAL_MENU, GestureMapping.parse(null).actionFor(Gesture.DOWN_THEN_RIGHT))
    }
}
