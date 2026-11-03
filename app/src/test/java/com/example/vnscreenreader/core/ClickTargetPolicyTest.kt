package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickTargetPolicyTest {

    private fun probe(
        clickable: Boolean = false,
        action: Boolean = false,
        scrollable: Boolean = false,
        web: Boolean = false,
        link: Boolean = false,
    ) = ClickProbe(clickable, action, scrollable, web, link)

    @Test fun selfClickableIsChosen() {
        assertEquals(0, ClickTargetPolicy.ancestorToClick(listOf(probe(clickable = true), probe(clickable = true))))
    }

    @Test fun linkThatOnlyDeclaresClickActionIsFoundFromTextLeaf() {
        // Chữ trong thẻ <a>: lá không bấm được, thẻ cha chỉ báo ACTION_CLICK (cờ clickable = false), rồi tới WebView.
        val chain = listOf(probe(), probe(action = true, link = true), probe(web = true, clickable = true))
        assertEquals(1, ClickTargetPolicy.ancestorToClick(chain))
    }

    @Test fun neverClimbsIntoWebViewRootOrScrollArea() {
        assertNull(ClickTargetPolicy.ancestorToClick(listOf(probe(), probe(), probe(web = true, clickable = true))))
        assertNull(ClickTargetPolicy.ancestorToClick(listOf(probe(), probe(scrollable = true), probe(clickable = true))))
    }

    @Test fun nothingClickable() {
        assertNull(ClickTargetPolicy.ancestorToClick(listOf(probe(), probe())))
        assertNull(ClickTargetPolicy.ancestorToClick(emptyList()))
    }

    @Test fun descendantPrefersLinkThenShallowestThenFirst() {
        val candidates = listOf(
            DescendantProbe(probe(clickable = true), depth = 1),
            DescendantProbe(probe(action = true, link = true), depth = 3),
            DescendantProbe(probe(action = true, link = true), depth = 2),
            DescendantProbe(probe(clickable = true), depth = 1),
        )
        assertEquals(2, ClickTargetPolicy.descendantToClick(candidates))
        assertEquals(0, ClickTargetPolicy.descendantToClick(candidates.filterNot { it.probe.link }))
        assertNull(ClickTargetPolicy.descendantToClick(emptyList()))
    }

    @Test fun webContainerClassDetection() {
        assertTrue(ClickTargetPolicy.isWebContainerClass("android.webkit.WebView"))
        assertTrue(ClickTargetPolicy.isWebContainerClass("org.mozilla.geckoview.GeckoView"))
        assertFalse(ClickTargetPolicy.isWebContainerClass("android.widget.TextView"))
        assertFalse(ClickTargetPolicy.isWebContainerClass(null))
    }

    @Test fun tapFallbackOnlyInsideWebAndNotOnRoot() {
        assertTrue(ClickTargetPolicy.shouldTapFallback(insideWeb = true, isWebRootItself = false))
        assertFalse(ClickTargetPolicy.shouldTapFallback(insideWeb = true, isWebRootItself = true))
        assertFalse(ClickTargetPolicy.shouldTapFallback(insideWeb = false, isWebRootItself = false))
    }
}
