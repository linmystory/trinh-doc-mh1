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

    @Test fun tapFallbackAlsoOnTheLockScreen_withoutChangingTheWebRules() {
        // Màn hình khóa đang hiện: nút của hệ thống (bàn phím số) có thể từ chối ACTION_CLICK nhưng nhận cú chạm thật.
        assertTrue(ClickTargetPolicy.shouldTapFallback(insideWeb = false, isWebRootItself = false, keyguardShowing = true))
        // Luật của web giữ nguyên, bất kể màn hình khóa.
        assertTrue(ClickTargetPolicy.shouldTapFallback(insideWeb = true, isWebRootItself = false, keyguardShowing = true))
        assertFalse(ClickTargetPolicy.shouldTapFallback(insideWeb = true, isWebRootItself = true, keyguardShowing = true))
        assertFalse(ClickTargetPolicy.shouldTapFallback(insideWeb = true, isWebRootItself = true, keyguardShowing = false))
        // Không nói gì về màn hình khóa = không khóa: gọi kiểu cũ vẫn cho kết quả cũ.
        assertFalse(ClickTargetPolicy.shouldTapFallback(false, false))
    }

    @Test fun neverClicksTheNeighbourOfAKeyOrOnTheLockScreen() {
        assertTrue(ClickTargetPolicy.mayClickNearby(isKey = false, keyguardShowing = false)) // ô tích cạnh dòng chữ: như trước
        assertFalse(ClickTargetPolicy.mayClickNearby(isKey = true, keyguardShowing = false)) // phím chữ/số: bên cạnh là phím khác
        assertFalse(ClickTargetPolicy.mayClickNearby(isKey = false, keyguardShowing = true)) // bàn phím số nhập mã mở khóa
        assertFalse(ClickTargetPolicy.mayClickNearby(isKey = true, keyguardShowing = true))
    }

    @Test fun theNeighbourPickerWouldHaveChosenTheWrongDigitOnANumberPad() {
        // Vì sao cần chặn: bàn phím số 3 cột (mỗi phím 300x200 px, sát nhau). Phím "5" không nhấn được thì bộ chọn "điều khiển bên cạnh" chọn phím "4"
        // (cùng hàng, cách 0 px, mã nhỏ hơn) và gõ sai chữ số. Chính sách mayClickNearby chặn đúng tình huống này.
        val five = NearbyControlPicker.Box(300, 200, 600, 400)
        val neighbours = listOf(
            NearbyControlPicker.Candidate(6, NearbyControlPicker.Box(600, 200, 900, 400), checkable = false, clickable = true),
            NearbyControlPicker.Candidate(4, NearbyControlPicker.Box(0, 200, 300, 400), checkable = false, clickable = true),
        )
        assertEquals(4, NearbyControlPicker.pick(five, neighbours))
        assertFalse(ClickTargetPolicy.mayClickNearby(isKey = false, keyguardShowing = true))
        assertFalse(ClickTargetPolicy.mayClickNearby(isKey = true, keyguardShowing = false))
    }
}
