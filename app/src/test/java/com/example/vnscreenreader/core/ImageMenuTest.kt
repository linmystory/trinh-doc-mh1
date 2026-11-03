package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMenuTest {
    private fun actions(items: List<com.example.vnscreenreader.menu.MenuItem>) = items.mapNotNull { it.action }

    @Test
    fun imageElement_putsDescribeAndOcrFirst_withoutDuplicatesOrExtraItems() {
        val plain = com.example.vnscreenreader.menu.Menus.local(emptyList())
        val image = com.example.vnscreenreader.menu.Menus.local(emptyList(), imageLike = true)
        val acts = actions(image)
        assertEquals(com.example.vnscreenreader.gesture.GestureAction.DESCRIBE_FOCUSED, acts[0])
        assertEquals(com.example.vnscreenreader.gesture.GestureAction.OCR_FOCUSED, acts[1])
        assertEquals(com.example.vnscreenreader.gesture.GestureAction.ACTIVATE, acts[2])
        assertEquals(plain.size, image.size)
        assertEquals(acts.size, acts.toSet().size)
    }

    @Test
    fun anyImage_includesChromeWebImage_butNotButtonsOrText() {
        assertTrue(AutoOcrPolicy.isAnyImage("android.widget.Image"))
        assertTrue(AutoOcrPolicy.isAnyImage("android.widget.ImageView"))
        assertFalse(AutoOcrPolicy.isAnyImage("android.widget.Button"))
        assertFalse(AutoOcrPolicy.isAnyImage(null))
    }
}
