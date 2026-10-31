package com.example.vnscreenreader.core

import com.example.vnscreenreader.core.NearbyControlPicker.Box
import com.example.vnscreenreader.core.NearbyControlPicker.Candidate
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.menu.Menus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechInfoTest {
    @Test
    fun describesAHiddenCheckbox() {
        assertEquals(
            "Lớp CheckBox. Bấm được: có. Tích được: có. Quan trọng với trợ năng: không. Mã: terms_cb",
            TechInfo.describe("android.widget.CheckBox", true, true, false, "com.epay:id/terms_cb"),
        )
    }

    @Test
    fun missingValues_areSpokenAsNone() {
        assertEquals("không có", TechInfo.shortClass(null))
        assertTrue(TechInfo.describe(null, false, false, true, null).endsWith("Mã: không có"))
    }
}

class NearbyControlPickerTest {
    private val label = Box(200, 1600, 900, 1700) // dòng chữ "Tôi đồng ý với điều khoản"

    @Test
    fun picksTheCheckboxOnTheSameRow() {
        val box = Candidate(1, Box(80, 1610, 180, 1690), checkable = true, clickable = true)
        val farRow = Candidate(2, Box(80, 2000, 180, 2100), checkable = true, clickable = true)
        assertEquals(1, NearbyControlPicker.pick(label, listOf(farRow, box)))
    }

    @Test
    fun ignoresControlsOnOtherRowsOrTooFarAway() {
        assertNull(NearbyControlPicker.pick(label, listOf(Candidate(1, Box(80, 1900, 180, 2000), true, true))))
        assertNull(NearbyControlPicker.pick(label, listOf(Candidate(1, Box(0, 1610, 20, 1690), true, true)), maxGap = 100))
    }

    @Test
    fun prefersCheckableOverPlainClickable_thenNearest() {
        val button = Candidate(1, Box(190, 1610, 199, 1690), checkable = false, clickable = true)
        val check = Candidate(2, Box(100, 1610, 150, 1690), checkable = true, clickable = false)
        assertEquals(2, NearbyControlPicker.pick(label, listOf(button, check)))
        val nearer = Candidate(3, Box(120, 1610, 190, 1690), checkable = true, clickable = false)
        assertEquals(3, NearbyControlPicker.pick(label, listOf(check, nearer)))
    }

    @Test
    fun nothingActionable_returnsNull() {
        assertNull(NearbyControlPicker.pick(label, listOf(Candidate(1, Box(80, 1610, 180, 1690), false, false))))
    }
}

class DetailedFocusWiringTest {
    @Test
    fun newActionsHaveLabels_andAreInMenus() {
        assertNotNull(GestureAction.TOGGLE_DETAILED_FOCUS.label.takeIf { it.isNotBlank() })
        assertNotNull(GestureAction.DESCRIBE_TECHNICAL.label.takeIf { it.isNotBlank() })
        assertTrue(Menus.globalMore().any { it.action == GestureAction.TOGGLE_DETAILED_FOCUS })
        assertTrue(Menus.local(emptyList()).any { it.action == GestureAction.DESCRIBE_TECHNICAL })
    }
}
