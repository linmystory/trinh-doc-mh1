package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverPolicyTest {

    @Test
    fun emptyLayout_isNotRead() {
        assertFalse(HoverPolicy.isReadable(NodeSnapshot(className = "android.widget.LinearLayout")))
    }

    @Test
    fun blankLabels_areTreatedAsEmpty() {
        val node = NodeSnapshot(text = "  ", contentDescription = "", hintText = "\n")
        assertFalse(HoverPolicy.isReadable(node))
    }

    @Test
    fun anyLabel_isRead() {
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(text = "Gửi")))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(contentDescription = "Đóng")))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(hintText = "Nhập email")))
    }

    @Test
    fun interactiveWithoutLabel_isRead() {
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(isClickable = true)))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(isEditable = true)))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(isCheckable = true)))
    }

    @Test
    fun password_isReadableButNeverExposesContent() {
        val node = NodeSnapshot(text = "bimat123", isPassword = true, isEditable = true)
        assertTrue(HoverPolicy.isReadable(node))
        assertTrue(SpeechTextBuilder.buildNodeDescription(node) == "Ô nhập mật khẩu")
    }
}
