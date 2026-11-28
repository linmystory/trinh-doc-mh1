package com.example.vnscreenreader.core

import android.accessibilityservice.AccessibilityServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chế độ an toàn: không có phiên đọc chạy được thì phải trả màn hình về chạm thường (tắt khám phá bằng chạm), nếu không người dùng không nhập nổi mã mở khóa.
 * Các bài này khóa phần số học cờ; việc nối vào dịch vụ được khóa ở DirectBootSourceTest.
 */
class TouchExplorationPolicyTest {

    /** Mọi cờ khác mà dịch vụ khai/đặt lúc chạy: tắt hay bật khám phá bằng chạm đều không được đụng tới chúng. */
    private val others = listOf(
        AccessibilityServiceInfo.DEFAULT,
        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS,
        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS,
        AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES,
        AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP,
        AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS,
        AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH,
        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS,
    )

    @Test
    fun theBitMatchesTheSystemConstant() {
        // Khai lại hằng để lõi không phụ thuộc Android: lệch với hệ thống là bài này đỏ.
        assertEquals(AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE, TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION)
    }

    @Test
    fun disablingClearsOnlyTheTouchExplorationBit() {
        val all = others.fold(TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION) { acc, f -> acc or f }
        val off = TouchExplorationPolicy.withTouchExploration(all, enabled = false)
        assertFalse(TouchExplorationPolicy.isRequested(off))
        assertEquals(all and TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION.inv(), off)
        for (flag in others) assertTrue("mất cờ $flag", off and flag == flag)
    }

    @Test
    fun enablingSetsOnlyTheTouchExplorationBit() {
        val all = others.fold(0) { acc, f -> acc or f }
        val on = TouchExplorationPolicy.withTouchExploration(all, enabled = true)
        assertTrue(TouchExplorationPolicy.isRequested(on))
        assertEquals(all or TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION, on)
    }

    @Test
    fun bothDirectionsAreIdempotent() {
        for (start in listOf(0, TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION, others.fold(0) { a, f -> a or f })) {
            val on = TouchExplorationPolicy.withTouchExploration(start, true)
            val off = TouchExplorationPolicy.withTouchExploration(start, false)
            assertEquals(on, TouchExplorationPolicy.withTouchExploration(on, true))
            assertEquals(off, TouchExplorationPolicy.withTouchExploration(off, false))
            // Tắt rồi bật lại trả đúng cờ ban đầu (khi ban đầu đang bật): phiên chạy lại không làm mất cấu hình nào.
            assertEquals(on, TouchExplorationPolicy.withTouchExploration(off, true))
        }
    }

    @Test
    fun theSameResultWhenTheFlagIsAlreadyInTheWantedState() {
        // Dịch vụ chỉ đặt lại serviceInfo khi cờ đổi: bật khi đang bật, tắt khi đang tắt phải trả đúng giá trị cũ.
        val on = others.fold(TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION) { a, f -> a or f }
        val off = on and TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION.inv()
        assertEquals(on, TouchExplorationPolicy.withTouchExploration(on, true))
        assertEquals(off, TouchExplorationPolicy.withTouchExploration(off, false))
    }

    @Test
    fun everySubsetOfFlagsKeepsItsOtherBits() {
        // Mọi tổ hợp 2^8 cờ khác: cờ khác không bao giờ đổi.
        for (mask in 0 until (1 shl others.size)) {
            val base = others.indices.filter { mask shr it and 1 == 1 }.fold(0) { acc, i -> acc or others[i] }
            val bit = TouchExplorationPolicy.FLAG_TOUCH_EXPLORATION
            for (start in listOf(base, base or bit)) {
                assertEquals(base, TouchExplorationPolicy.withTouchExploration(start, false) and bit.inv())
                assertEquals(base, TouchExplorationPolicy.withTouchExploration(start, true) and bit.inv())
            }
        }
    }
}
