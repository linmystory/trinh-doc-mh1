package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaMenuTest {

    private fun actions(items: List<MenuItem>) = items.mapNotNull { it.action }

    @Test fun plainElementKeepsTheOldMenuWithoutCaptchaItem() {
        val local = Menus.local(emptyList())
        assertFalse(GestureAction.READ_CAPTCHA in actions(local))
        assertEquals(12, local.size) // 11 mục cũ + "thông tin kỹ thuật"; khớp MenuTest
        assertEquals(GestureAction.ACTIVATE, local.first().action)
    }

    @Test fun captchaImageHasReadCodeOnTop() {
        val local = Menus.local(emptyList(), imageLike = true, captchaLike = true)
        val acts = actions(local)
        assertEquals(GestureAction.READ_CAPTCHA, acts[0])
        assertEquals(GestureAction.DESCRIBE_FOCUSED, acts[1])
        assertEquals(GestureAction.OCR_FOCUSED, acts[2])
        assertEquals(GestureAction.ACTIVATE, acts[3])
    }

    @Test fun otherImagesGetImageActionsFirstAndReadCodeThird() {
        val acts = actions(Menus.local(emptyList(), imageLike = true))
        assertEquals(
            listOf(GestureAction.DESCRIBE_FOCUSED, GestureAction.OCR_FOCUSED, GestureAction.READ_CAPTCHA, GestureAction.ACTIVATE),
            acts.take(4),
        )
    }

    @Test fun captchaLikeAloneStillTreatedAsImage() {
        assertEquals(GestureAction.READ_CAPTCHA, Menus.local(emptyList(), captchaLike = true).first().action)
    }

    @Test fun noActionIsDuplicatedInAnyCombination() {
        for (editable in listOf(false, true)) for (image in listOf(false, true)) for (captcha in listOf(false, true)) {
            val acts = actions(Menus.local(emptyList(), editable = editable, imageLike = image, captchaLike = captcha))
            assertEquals("editable=$editable image=$image captcha=$captcha", acts.size, acts.toSet().size)
        }
    }

    @Test fun imageMenuIsOneItemLongerAndCustomActionsStayLast() {
        val custom = listOf(CustomAction(0x7f000001, "Xóa tin"))
        val plain = Menus.local(custom)
        val image = Menus.local(custom, imageLike = true)
        assertEquals(plain.size + 1, image.size)
        assertEquals(0x7f000001, image.last().customActionId)
    }

    @Test fun globalMoreStartsWithReadCodeAndGlobalTopStaysWithinLimit() {
        assertEquals(GestureAction.READ_CAPTCHA, Menus.globalMore().first().action)
        assertTrue(Menus.globalMore().last().isBack)
        val top = actions(Menus.global())
        assertFalse(GestureAction.READ_CAPTCHA in top)
        assertTrue(top.size <= Menus.MAX_GLOBAL_TOP_ACTIONS)
        val all = top + actions(Menus.globalMore())
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun newActionHasNoDefaultGestureAndAClearLabel() {
        assertTrue(GestureMapping.defaults().values.none { it == GestureAction.READ_CAPTCHA })
        assertTrue(GestureAction.READ_CAPTCHA.label.contains("mã xác thực"))
        assertNull(GestureMapping().toMap().entries.firstOrNull { it.value == GestureAction.READ_CAPTCHA })
    }
}
