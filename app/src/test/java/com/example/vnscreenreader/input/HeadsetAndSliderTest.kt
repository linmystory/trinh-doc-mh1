package com.example.vnscreenreader.input

import com.example.vnscreenreader.core.SliderMath
import com.example.vnscreenreader.core.VolumeStreamPolicy
import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetKeyNavTest {
    @Test fun singlePressIsConfirmedOnlyAfterTheDoubleWindow() {
        val nav = HeadsetKeyNav()
        nav.onDown()
        assertNull(nav.onUp(100)) // chưa biết một hay hai lần
        assertEquals(Gesture.HEADSET_SHORT, nav.onSingleTimeout())
        assertNull(nav.onSingleTimeout()) // chỉ một lần
    }

    @Test fun secondQuickPressIsADouble() {
        val nav = HeadsetKeyNav()
        nav.onDown(); assertNull(nav.onUp(100))
        nav.onDown(); assertEquals(Gesture.HEADSET_DOUBLE, nav.onUp(300))
        assertNull(nav.onSingleTimeout()) // bấm một lần đã bị thay bằng bấm hai lần
    }

    @Test fun slowSecondPressIsTwoSinglesNotADouble() {
        val nav = HeadsetKeyNav()
        nav.onDown(); assertNull(nav.onUp(100))
        assertEquals(Gesture.HEADSET_SHORT, nav.onSingleTimeout())
        nav.onDown(); assertNull(nav.onUp(1_000))
        assertEquals(Gesture.HEADSET_SHORT, nav.onSingleTimeout())
    }

    @Test fun holdFiresOnceAndSuppressesTheRest() {
        val nav = HeadsetKeyNav()
        nav.onDown()
        assertEquals(Gesture.HEADSET_LONG, nav.onLongPress())
        assertNull(nav.onLongPress())
        assertNull(nav.onUp(900))
        assertNull(nav.onSingleTimeout())
    }

    @Test fun releaseWithoutPressIsIgnored() {
        val nav = HeadsetKeyNav()
        assertFalse(nav.isHeld())
        assertNull(nav.onUp(0))
        nav.onDown()
        assertTrue(nav.isHeld())
        nav.reset()
        assertFalse(nav.isHeld())
    }
}

class SliderMathTest {
    @Test fun floatSliderMovesAboutFivePercent() {
        assertEquals(0.05f, SliderMath.next(0f, 0f, 1f, 1, isInt = false), 0.0001f)
        assertEquals(0.45f, SliderMath.next(0.5f, 0f, 1f, -1, isInt = false), 0.0001f)
    }

    @Test fun integerSliderAlwaysMovesAtLeastOneAndStaysInteger() {
        assertEquals(6f, SliderMath.next(5f, 0f, 10f, 1, isInt = true), 0f) // 5% của 10 < 1 → nhích 1
        assertEquals(20f, SliderMath.next(0f, 0f, 400f, 1, isInt = true), 0f) // 5% của 400 = 20
        assertEquals(4f, SliderMath.next(5f, 0f, 10f, -1, isInt = true), 0f)
    }

    @Test fun neverLeavesTheRange() {
        assertEquals(1f, SliderMath.next(0.99f, 0f, 1f, 1, isInt = false), 0f)
        assertEquals(0f, SliderMath.next(0.01f, 0f, 1f, -1, isInt = false), 0f)
        assertEquals(10f, SliderMath.next(10f, 0f, 10f, 1, isInt = true), 0f)
        assertEquals(5f, SliderMath.next(5f, 5f, 5f, 1, isInt = true), 0f) // khoảng rỗng: giữ nguyên giá trị nhỏ nhất
    }

    @Test fun percentIsClampedAndSafeForEmptyRange() {
        assertEquals(50, SliderMath.percent(5f, 0f, 10f))
        assertEquals(100, SliderMath.percent(50f, 0f, 10f))
        assertEquals(0, SliderMath.percent(1f, 3f, 3f))
    }

    @Test fun onlyEnabledNodesWithAnAdjustActionAreAdjustable() {
        val setProgress = 0x0102_0038
        val forward = 0x1000
        val backward = 0x2000
        fun adjustable(enabled: Boolean, vararg ids: Int) = SliderMath.isAdjustable(enabled, ids.toList(), setProgress, forward, backward)
        assertTrue(adjustable(true, setProgress)) // thanh chuẩn / Compose Slider
        assertTrue(adjustable(true, forward)) // thanh tự vẽ chỉ có cuộn tới
        assertTrue(adjustable(true, backward)) // đang ở đầu thanh: chỉ còn cuộn lùi
        assertTrue(adjustable(true, 0x10, setProgress, forward, backward))
        // Thanh tiến trình chỉ-đọc (tải xuống, mức pin): có khoảng giá trị nhưng không có hành động chỉnh nào.
        assertFalse(adjustable(true))
        assertFalse(adjustable(true, 0x10, 0x20)) // chỉ có hành động khác (nhấn, sao chép...)
        // Thanh đã bị vô hiệu hóa: không chỉnh được dù còn hành động.
        assertFalse(adjustable(false, setProgress, forward, backward))
    }

    @Test fun dragTargetStopsShortOfTheTrackEndAndSkipsWhenAlreadyThere() {
        assertEquals(1000 - 20 - 2, SliderMath.dragEndX(handleCenterX = 100, handleWidth = 40, trackRight = 1000))
        assertNull(SliderMath.dragEndX(handleCenterX = 978, handleWidth = 40, trackRight = 1000)) // đã ở cuối
        assertNull(SliderMath.dragEndX(handleCenterX = 975, handleWidth = 40, trackRight = 1000)) // còn dưới ngưỡng nhích
    }
}

class VolumeStreamPolicyTest {
    @Test fun callsUseTheCallStream() {
        assertEquals(VolumeStreamPolicy.STREAM_VOICE_CALL, VolumeStreamPolicy.choose(VolumeStreamPolicy.MODE_IN_CALL, speaking = true, touching = true))
        assertEquals(VolumeStreamPolicy.STREAM_VOICE_CALL, VolumeStreamPolicy.choose(VolumeStreamPolicy.MODE_IN_COMMUNICATION, speaking = false, touching = false))
    }

    @Test fun speakingOrTouchingUsesTheAccessibilityStream() {
        assertEquals(VolumeStreamPolicy.STREAM_ACCESSIBILITY, VolumeStreamPolicy.choose(0, speaking = true, touching = false))
        assertEquals(VolumeStreamPolicy.STREAM_ACCESSIBILITY, VolumeStreamPolicy.choose(0, speaking = false, touching = true))
    }

    @Test fun otherwiseMedia() {
        assertEquals(VolumeStreamPolicy.STREAM_MUSIC, VolumeStreamPolicy.choose(0, speaking = false, touching = false))
    }

    @Test fun percentHandlesZeroMax() {
        assertEquals(60, VolumeStreamPolicy.percent(9, 15))
        assertEquals(0, VolumeStreamPolicy.percent(3, 0))
        assertEquals(100, VolumeStreamPolicy.percent(20, 15))
    }
}

class KeyTriggerDefaultsTest {
    private val m = GestureMapping()

    @Test fun shortPressStillChangesVolumeAndBothKeysIsUnassigned() {
        assertEquals(GestureAction.VOLUME_RAISE, m.actionFor(Gesture.VOLUME_UP_SHORT))
        assertEquals(GestureAction.VOLUME_LOWER, m.actionFor(Gesture.VOLUME_DOWN_SHORT))
        // Lối thoát khẩn cấp đã bỏ ở 2.18.1: hai phím cùng lúc không gán sẵn (gán được ở Cài đặt cử chỉ).
        assertEquals(GestureAction.NONE, m.actionFor(Gesture.VOLUME_BOTH))
    }

    @Test fun holdMovesAndHeadsetHasSensibleDefaults() {
        assertEquals(GestureAction.PREVIOUS_ELEMENT, m.actionFor(Gesture.VOLUME_UP_LONG))
        assertEquals(GestureAction.NEXT_ELEMENT, m.actionFor(Gesture.VOLUME_DOWN_LONG))
        assertEquals(GestureAction.NEXT_ELEMENT, m.actionFor(Gesture.HEADSET_SHORT))
        assertEquals(GestureAction.ACTIVATE, m.actionFor(Gesture.HEADSET_DOUBLE))
    }

    @Test fun keyTriggersNeverCollideWithSystemGestureIds() {
        val keys = Gesture.entries.filter { it.fingers == 0 }
        assertTrue(keys.all { it.systemId < 0 })
        assertTrue(Gesture.entries.filter { it.fingers > 0 }.all { it.systemId > 0 })
        keys.forEach { assertNull(it.name, Gesture.fromSystemId(it.systemId)) } // hệ thống không bao giờ gửi mã âm này
    }
}
