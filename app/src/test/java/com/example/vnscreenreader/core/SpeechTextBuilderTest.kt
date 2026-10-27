package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechTextBuilderTest {

    @Test
    fun button_withDescription_readsLabelAndRole() {
        val node = NodeSnapshot(contentDescription = "Gửi", className = "android.widget.Button")
        assertEquals("Gửi, nút", SpeechTextBuilder.buildNodeDescription(node))
    }

    @Test
    fun contentDescription_hasPriorityOverText() {
        val node = NodeSnapshot(text = "abc", contentDescription = "Mô tả", className = "android.widget.TextView")
        assertEquals("Mô tả", SpeechTextBuilder.buildNodeDescription(node))
    }

    @Test
    fun editText_readsTextAndRole() {
        val node = NodeSnapshot(text = "abc", className = "android.widget.EditText", isEditable = true)
        assertEquals("abc, ô nhập", SpeechTextBuilder.buildNodeDescription(node))
    }

    @Test
    fun hint_usedWhenNoTextOrDescription() {
        val node = NodeSnapshot(hintText = "Nhập email", className = "android.widget.EditText", isEditable = true)
        assertEquals("Nhập email, ô nhập", SpeechTextBuilder.buildNodeDescription(node))
    }

    @Test
    fun password_neverReadsContent() {
        val node = NodeSnapshot(text = "bimat123", isPassword = true, isEditable = true)
        assertEquals("Ô nhập mật khẩu", SpeechTextBuilder.buildNodeDescription(node))
    }

    @Test
    fun checkbox_announcesState() {
        val checked = NodeSnapshot(
            text = "Nhớ tôi",
            className = "android.widget.CheckBox",
            isCheckable = true,
            checkedState = CheckedState.CHECKED,
        )
        val unchecked = checked.copy(checkedState = CheckedState.UNCHECKED)
        assertEquals("Nhớ tôi, hộp kiểm, đã chọn", SpeechTextBuilder.buildNodeDescription(checked))
        assertEquals("Nhớ tôi, hộp kiểm, chưa chọn", SpeechTextBuilder.buildNodeDescription(unchecked))
    }

    @Test
    fun checkbox_partialState_isAnnounced() {
        val partial = NodeSnapshot(
            text = "Chọn tất cả",
            className = "android.widget.CheckBox",
            isCheckable = true,
            checkedState = CheckedState.PARTIAL,
        )
        assertEquals("Chọn tất cả, hộp kiểm, chọn một phần", SpeechTextBuilder.buildNodeDescription(partial))
    }

    @Test
    fun nonCheckableNode_neverAnnouncesState() {
        val node = NodeSnapshot(text = "Gửi", className = "android.widget.Button", checkedState = CheckedState.CHECKED)
        assertEquals("Gửi, nút", SpeechTextBuilder.buildNodeDescription(node))
    }

    @Test
    fun unlabeledElement_usesFallback() {
        assertEquals("Phần tử không có nhãn", SpeechTextBuilder.buildNodeDescription(NodeSnapshot()))
        assertEquals(
            "Nút không có nhãn",
            SpeechTextBuilder.buildNodeDescription(NodeSnapshot(text = "  ", className = "android.widget.Button")),
        )
    }

    @Test
    fun clickableImage_isReadAsButton() {
        val node = NodeSnapshot(contentDescription = "Menu", className = "android.widget.ImageView", isClickable = true)
        assertEquals("Menu, nút", SpeechTextBuilder.buildNodeDescription(node))
    }

    @Test
    fun textChange_readsAddedCharacter() {
        assertEquals("c", SpeechTextBuilder.describeTextChange("abc", 2, 1, 0, false))
    }

    @Test
    fun textChange_space_readsSpaceWord() {
        assertEquals("dấu cách", SpeechTextBuilder.describeTextChange("a b", 1, 1, 0, false))
    }

    @Test
    fun textChange_removal_readsDeleted() {
        assertEquals("Đã xóa", SpeechTextBuilder.describeTextChange("ab", 2, 0, 1, false))
    }

    @Test
    fun textChange_password_neverLeaksCharacters() {
        assertEquals("Đã nhập ký tự", SpeechTextBuilder.describeTextChange("secret", 5, 1, 0, true))
        assertEquals("Đã xóa ký tự", SpeechTextBuilder.describeTextChange("secre", 5, 0, 1, true))
    }

    @Test
    fun textChange_invalidIndex_doesNotCrash() {
        assertNull(SpeechTextBuilder.describeTextChange("ab", 99, 1, 0, false))
        assertNull(SpeechTextBuilder.describeTextChange("ab", -1, 1, 0, false))
        assertNull(SpeechTextBuilder.describeTextChange("ab", 0, 0, 0, false))
    }
}
