package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionEffectTest {
    @Test
    fun click_changedWhenStateDiffersOrNodeGone_notWhenIdentical() {
        assertTrue(ClickEffect.changed("a|b", "a|c"))
        assertTrue(ClickEffect.changed("a|b", null))
        assertFalse(ClickEffect.changed("a|b", "a|b"))
    }

    @Test
    fun signature_detectsPageChange_andIgnoresIdenticalScreens() {
        val page1 = listOf("Zalo", "Chrome", "Máy ảnh")
        val page2 = listOf("Facebook", "Gmail")
        assertFalse(ScreenSignature.changed(ScreenSignature.of(page1), ScreenSignature.of(page1.toList())))
        assertTrue(ScreenSignature.changed(ScreenSignature.of(page1), ScreenSignature.of(page2)))
        assertEquals(ScreenSignature.of(emptyList()), ScreenSignature.of(emptyList()))
    }
}
