package com.example.vnscreenreader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hai ngón vuốt do Android xử lý (như các trình đọc màn hình lớn) và việc gỡ lối thoát khẩn cấp ở 2.18.1. */
class SystemGestureTest {

    @Test fun onlyTheFourTwoFingerSwipesArePassedToTheSystem() {
        val passed = Gesture.entries.filter { it.passedToSystem }.toSet()
        assertEquals(
            setOf(
                Gesture.TWO_FINGER_SWIPE_UP, Gesture.TWO_FINGER_SWIPE_DOWN,
                Gesture.TWO_FINGER_SWIPE_LEFT, Gesture.TWO_FINGER_SWIPE_RIGHT,
            ),
            passed,
        )
        assertTrue(passed.all { it.fingers == 2 && GestureAnalyzer.swipeDirection(it) != null })
    }

    @Test fun twoFingerTapsAndOtherGesturesStayConfigurable() {
        val twoFingerNonSwipes = Gesture.entries.filter { it.fingers == 2 && GestureAnalyzer.swipeDirection(it) == null }
        assertTrue(twoFingerNonSwipes.isNotEmpty())
        assertTrue(twoFingerNonSwipes.none { it.passedToSystem })
        assertTrue(Gesture.entries.filter { it.fingers != 2 }.none { it.passedToSystem })
    }

    @Test fun swipeProfilesAreOnlyOfferedWhereTheServiceReceivesTheSwipes() {
        assertFalse(GestureAnalyzer.hasProfile(1))
        assertFalse(GestureAnalyzer.hasProfile(2))
        assertTrue(GestureAnalyzer.hasProfile(3))
        assertFalse(GestureAnalyzer.hasProfile(4))
    }

    @Test fun fallbackDefaultsStillScrollIfADeviceSendsTheseGestures() {
        val m = GestureMapping()
        assertEquals(GestureAction.SCROLL_FORWARD, m.actionFor(Gesture.TWO_FINGER_SWIPE_UP))
        assertEquals(GestureAction.SCROLL_BACKWARD, m.actionFor(Gesture.TWO_FINGER_SWIPE_DOWN))
        assertEquals(GestureAction.PAGE_NEXT, m.actionFor(Gesture.TWO_FINGER_SWIPE_LEFT))
        assertEquals(GestureAction.PAGE_PREVIOUS, m.actionFor(Gesture.TWO_FINGER_SWIPE_RIGHT))
    }

    @Test fun theEmergencyExitIsGone() {
        assertEquals(GestureAction.NONE, GestureMapping().actionFor(Gesture.VOLUME_BOTH))
        assertTrue(GestureAction.entries.none { it.name.contains("TOUCH_EXPLORATION") })
    }

    @Test fun savedMappingsThatMentionTheRemovedActionAreIgnoredWithoutLosingTheRest() {
        val m = GestureMapping.parse("VOLUME_BOTH=TOGGLE_TOUCH_EXPLORATION;SWIPE_UP=HOME")
        assertEquals(GestureAction.NONE, m.actionFor(Gesture.VOLUME_BOTH))
        assertEquals(GestureAction.HOME, m.actionFor(Gesture.SWIPE_UP))
        val perApp = AppGestureOverrides.apply(GestureMapping(), "VOLUME_UP_LONG=TOGGLE_TOUCH_EXPLORATION;SWIPE_DOWN=BACK")
        assertEquals(GestureMapping().actionFor(Gesture.VOLUME_UP_LONG), perApp.actionFor(Gesture.VOLUME_UP_LONG))
        assertEquals(GestureAction.BACK, perApp.actionFor(Gesture.SWIPE_DOWN))
    }
}
