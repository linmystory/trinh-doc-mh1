package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlabeledNamerTest {
    @Test
    fun translatesMeaningfulIds() {
        assertEquals("đóng", UnlabeledNamer.guess("com.app:id/btn_close"))
        assertEquals("tìm kiếm", UnlabeledNamer.guess("com.app:id/ivSearch"))
        assertEquals("quay lại", UnlabeledNamer.guess("com.app:id/ic_back_icon"))
        assertEquals("chia sẻ", UnlabeledNamer.guess("share_button"))
    }

    @Test
    fun combinesTwoWords() {
        assertEquals("tải xuống ảnh", UnlabeledNamer.guess("com.app:id/btnDownloadPhoto"))
    }

    @Test
    fun unknownOrMeaninglessIds_giveNull() {
        assertNull(UnlabeledNamer.guess("com.app:id/btn_xyz"))   // một từ lạ => không đoán
        assertNull(UnlabeledNamer.guess("com.app:id/view_1"))
        assertNull(UnlabeledNamer.guess("com.app:id/btn_close_xyz")) // chỉ cần một từ không dịch được => bỏ
        assertNull(UnlabeledNamer.guess(null))
        assertNull(UnlabeledNamer.guess(""))
    }

    @Test
    fun tooManyWords_isRejected() {
        assertNull(UnlabeledNamer.guess("a:id/close_open_edit_save"))
    }
}

class CustomLabelsTest {
    @Test
    fun setFindRemove() {
        val labels = CustomLabels().with("com.app", "com.app:id/x", "  Gửi tin  ")
        assertEquals("Gửi tin", labels.find("com.app", "com.app:id/x"))
        assertNull(labels.find("other", "com.app:id/x"))
        assertNull(labels.without("com.app", "com.app:id/x").find("com.app", "com.app:id/x"))
    }

    @Test
    fun blankLabel_removesExisting() {
        val labels = CustomLabels().with("p", "v", "a").with("p", "v", "   ")
        assertEquals(0, labels.size)
    }

    @Test
    fun serializeParse_roundTrips_andIgnoresGarbage() {
        val labels = CustomLabels().with("p1", "v1", "Một").with("p2", "v2", "Hai")
        assertEquals(labels.entries(), CustomLabels.parse(labels.serialize()).entries())
        assertEquals(0, CustomLabels.parse("không phải json").size)
        assertEquals(0, CustomLabels.parse(null).size)
        assertEquals(0, CustomLabels.parse("""{"khongcodauphancach":"x","p|v":""}""").size)
    }

    @Test
    fun longLabel_isTruncated_andCapacityIsBounded() {
        val long = CustomLabels().with("p", "v", "x".repeat(500))
        assertEquals(CustomLabels.MAX_LABEL_LENGTH, long.find("p", "v")!!.length)
        var full = CustomLabels()
        repeat(CustomLabels.MAX_LABELS) { full = full.with("p", "v$it", "a") }
        assertEquals(CustomLabels.MAX_LABELS, full.with("p", "extra", "b").size)
        assertEquals("c", full.with("p", "v0", "c").find("p", "v0")) // sửa nhãn có sẵn vẫn được
    }
}

class LabelResolverTest {
    private val bare = NodeSnapshot(className = "android.widget.ImageButton", isClickable = true, viewId = "a:id/btn_close", packageName = "a")

    @Test
    fun appLabelWins_andNothingElseIsComputed() {
        val node = bare.copy(contentDescription = "Đóng cửa sổ")
        val out = LabelResolver.resolve(node, { error("không được gọi") }, { error("không được gọi") })
        assertEquals(node, out)
    }

    @Test
    fun labeledByAndTooltip_countAsAppLabels() {
        assertEquals("Email, ô nhập", SpeechTextBuilder.buildNodeDescription(NodeSnapshot(labeledByText = "Email", isEditable = true)))
        assertEquals("Gửi, nút", SpeechTextBuilder.buildNodeDescription(NodeSnapshot(tooltip = "Gửi", className = "Button")))
    }

    @Test
    fun priority_custom_thenChildren_thenGuess() {
        assertEquals("Nút của tôi", LabelResolver.resolve(bare, { "Nút của tôi" }, { "Con" }).text)
        assertEquals("Con", LabelResolver.resolve(bare, { null }, { "Con" }).text)
        val guessed = LabelResolver.resolve(bare, { null }, { "" })
        assertEquals("đóng", guessed.text)
        assertTrue(guessed.labelIsGuess)
        assertEquals("đóng, nút, nhãn đoán", SpeechTextBuilder.buildNodeDescription(guessed))
    }

    @Test
    fun noInformation_staysUnlabeled_andPasswordIsUntouched() {
        val none = bare.copy(viewId = "a:id/zzz")
        assertEquals(none, LabelResolver.resolve(none, { null }, { "" }))
        assertEquals("Nút không có nhãn", SpeechTextBuilder.buildNodeDescription(none))
        val pw = NodeSnapshot(isPassword = true, viewId = "a:id/btn_close")
        assertEquals(pw, LabelResolver.resolve(pw, { "x" }, { "y" }))
    }
}
