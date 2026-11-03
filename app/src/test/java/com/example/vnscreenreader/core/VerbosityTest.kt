package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerbosityTest {
    private val button = NodeSnapshot(text = "Đăng nhập", className = "android.widget.Button", isClickable = true)
    private val link = NodeSnapshot(text = "Tin tức", className = "android.view.View", isClickable = true, isLink = true)
    private val position = ListPosition(3, 12)

    private fun build(node: NodeSnapshot, v: Verbosity, pos: ListPosition? = null) =
        SpeechTextBuilder.buildNodeDescription(node, SpeechDetail(v, pos))

    @Test fun defaultDetailKeepsOldBehaviour() {
        assertEquals("Đăng nhập, nút", SpeechTextBuilder.buildNodeDescription(button))
        assertEquals("Đăng nhập, nút", build(button, Verbosity.STANDARD))
    }

    @Test fun linkRoleIsRead() {
        assertEquals("Tin tức, liên kết", build(link, Verbosity.STANDARD))
    }

    @Test fun minimalDropsCommonRoleButKeepsLabelAndState() {
        assertEquals("Đăng nhập", build(button, Verbosity.MINIMAL))
        assertEquals("Tin tức", build(link, Verbosity.MINIMAL))
        val disabled = button.copy(isEnabled = false)
        assertEquals("Đăng nhập, ${t(S.SP_STATE_DISABLED)}", build(disabled, Verbosity.MINIMAL))
    }

    @Test fun minimalKeepsRolesThatChangeInteraction() {
        val edit = NodeSnapshot(text = "abc", className = "android.widget.EditText", isEditable = true)
        assertEquals("abc, ô nhập", build(edit, Verbosity.MINIMAL))
        val check = NodeSnapshot(text = "Đồng ý", className = "android.widget.CheckBox", isCheckable = true)
        assertEquals(build(check, Verbosity.STANDARD), build(check, Verbosity.MINIMAL))
    }

    @Test fun minimalStillNamesUnlabeledElement() {
        val unlabeled = NodeSnapshot(className = "android.widget.ImageButton", isClickable = true)
        assertEquals(build(unlabeled, Verbosity.STANDARD), build(unlabeled, Verbosity.MINIMAL))
    }

    @Test fun positionOnlyInDetailed() {
        assertEquals("Đăng nhập, nút, Mục 3 trên 12", build(button, Verbosity.DETAILED, position))
        assertEquals("Đăng nhập, nút", build(button, Verbosity.STANDARD, position))
        assertEquals("Đăng nhập", build(button, Verbosity.MINIMAL, position))
        assertEquals("Đăng nhập, nút", build(button, Verbosity.DETAILED, null))
    }

    @Test fun passwordNeverGetsPositionOrRole() {
        val pw = NodeSnapshot(text = "bimat", isPassword = true, isEditable = true)
        assertEquals(t(S.SP_PASSWORD_FIELD), build(pw, Verbosity.DETAILED, position))
    }

    @Test fun parseFallsBackToStandard() {
        assertEquals(Verbosity.DETAILED, Verbosity.parse("DETAILED"))
        assertEquals(Verbosity.STANDARD, Verbosity.parse("rác"))
        assertEquals(Verbosity.STANDARD, Verbosity.parse(null))
    }

    @Test fun actionHintKindPrecedence() {
        fun kind(editable: Boolean = false, link: Boolean = false, checkable: Boolean = false, actionable: Boolean = false, enabled: Boolean = true) =
            ActionHints.kindOf(editable, link, checkable, actionable, enabled)
        assertEquals(ActionHintKind.EDIT, kind(editable = true, actionable = true))
        assertEquals(ActionHintKind.OPEN_LINK, kind(link = true, actionable = true))
        assertEquals(ActionHintKind.TOGGLE, kind(checkable = true, actionable = true))
        assertEquals(ActionHintKind.ACTIVATE, kind(actionable = true))
        assertNull(kind())
        assertNull(kind(actionable = true, enabled = false))
    }

    @Test fun hintSentenceUsesGestureLabel() {
        assertEquals("Chạm hai lần để kích hoạt", ActionHints.sentence(ActionHintKind.ACTIVATE, "Chạm hai lần"))
        assertEquals("Chạm hai lần để mở liên kết", ActionHints.sentence(ActionHintKind.OPEN_LINK, "Chạm hai lần"))
    }
}
