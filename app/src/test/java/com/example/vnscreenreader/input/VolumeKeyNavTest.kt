package com.example.vnscreenreader.input

import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.input.VolumeKeyNav.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyNavTest {
    @Test fun shortPressesReportTheirOwnTrigger() {
        val nav = VolumeKeyNav()
        assertNull(nav.onDown(Key.DOWN, 0, 0))
        assertEquals(Gesture.VOLUME_DOWN_SHORT, nav.onUp(Key.DOWN, 100))
        assertNull(nav.onDown(Key.UP, 0, 200))
        assertEquals(Gesture.VOLUME_UP_SHORT, nav.onUp(Key.UP, 300))
    }

    @Test fun longPressFiresOnceAndSuppressesTheShortPress() {
        val nav = VolumeKeyNav()
        nav.onDown(Key.DOWN, 0, 0)
        assertEquals(Gesture.VOLUME_DOWN_LONG, nav.onLongPress(Key.DOWN))
        assertNull(nav.onLongPress(Key.DOWN)) // chỉ một lần mỗi lần giữ
        assertNull(nav.onUp(Key.DOWN, 900)) // nhả sau khi đã giữ: không thêm bấm nhanh
        nav.onDown(Key.UP, 0, 1000)
        assertEquals(Gesture.VOLUME_UP_LONG, nav.onLongPress(Key.UP))
    }

    @Test fun bothKeysFireOnceAndNothingElse() {
        val nav = VolumeKeyNav()
        assertNull(nav.onDown(Key.DOWN, 0, 0))
        assertEquals(Gesture.VOLUME_BOTH, nav.onDown(Key.UP, 0, 50))
        assertNull(nav.onLongPress(Key.DOWN)) // hẹn giờ của phím thứ nhất không được chạy
        assertNull(nav.onUp(Key.DOWN, 100))
        assertNull(nav.onUp(Key.UP, 120))
        // Sau khi nhả hết, bấm nhanh mới lại bình thường.
        nav.onDown(Key.DOWN, 0, 500)
        assertEquals(Gesture.VOLUME_DOWN_SHORT, nav.onUp(Key.DOWN, 550))
    }

    @Test fun chordDoesNotRepeatWhileOneKeyStaysHeld() {
        val nav = VolumeKeyNav()
        nav.onDown(Key.DOWN, 0, 0)
        assertEquals(Gesture.VOLUME_BOTH, nav.onDown(Key.UP, 0, 10))
        nav.onUp(Key.UP, 20)
        assertNull(nav.onDown(Key.UP, 0, 30)) // chưa nhả hết nên chưa tính tổ hợp mới
    }

    @Test fun autoRepeatIsIgnored() {
        val nav = VolumeKeyNav()
        nav.onDown(Key.DOWN, 0, 0)
        assertNull(nav.onDown(Key.DOWN, 1, 50))
        assertNull(nav.onDown(Key.DOWN, 2, 100))
        assertEquals(Gesture.VOLUME_DOWN_SHORT, nav.onUp(Key.DOWN, 150))
    }

    @Test fun releaseWithoutPressIsNotConsumed() {
        val nav = VolumeKeyNav()
        assertFalse(nav.isHeld(Key.UP))
        assertNull(nav.onUp(Key.UP, 0))
        nav.onDown(Key.UP, 0, 10)
        assertTrue(nav.isHeld(Key.UP))
    }

    @Test fun lostReleaseIsRecoveredByIdleExpiry() {
        val nav = VolumeKeyNav()
        nav.onDown(Key.DOWN, 0, 0)
        nav.expireIfIdle(VolumeKeyNav.MAX_IDLE_MS + 1)
        assertFalse(nav.isHeld(Key.DOWN))
        assertNull(nav.onDown(Key.UP, 0, VolumeKeyNav.MAX_IDLE_MS + 10)) // không bị coi là nhấn cả hai
        val other = VolumeKeyNav()
        other.onDown(Key.DOWN, 0, 0)
        other.expireIfIdle(VolumeKeyNav.MAX_IDLE_MS - 1)
        assertTrue(other.isHeld(Key.DOWN))
    }

    @Test fun nearSimultaneousChordIsSwallowedWhenGapIsRequired() {
        val nav = VolumeKeyNav(minChordGapMs = 64)
        assertNull(nav.onDown(Key.DOWN, 0, 1_000))
        assertNull(nav.onDown(Key.UP, 0, 1_020)) // cách 20 ms: nhường cho hệ thống
        assertNull(nav.onUp(Key.UP, 1_100)) // và không biến thành bấm nhanh khi nhả
        assertNull(nav.onUp(Key.DOWN, 1_120))
    }

    @Test fun chordWithEnoughGapStillFires() {
        val nav = VolumeKeyNav(minChordGapMs = 64)
        assertNull(nav.onDown(Key.UP, 0, 1_000))
        assertEquals(Gesture.VOLUME_BOTH, nav.onDown(Key.DOWN, 0, 1_200))
    }

    @Test fun downWithoutPriorUpResetsStaleState() {
        val nav = VolumeKeyNav()
        nav.onDown(Key.DOWN, 0, 0)
        assertNull(nav.onDown(Key.DOWN, 0, 5_000)) // lỡ mất sự kiện nhả: bắt đầu lại sạch
        assertEquals(Gesture.VOLUME_DOWN_SHORT, nav.onUp(Key.DOWN, 5_050))
    }
}
