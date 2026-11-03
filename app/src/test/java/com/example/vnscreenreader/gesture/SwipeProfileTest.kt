package com.example.vnscreenreader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeProfileTest {

    @Test fun defaultsAreDetectedPerGroup() {
        assertEquals(SwipeProfile.SCROLL_AND_PAGE, SwipeProfile.detect(GestureMapping(), 2))
        assertEquals(SwipeProfile.TEXT_UNITS, SwipeProfile.detect(GestureMapping(), 3))
    }

    @Test fun defaultProfileIsFirstInEachGroup() {
        assertEquals(SwipeProfile.SCROLL_AND_PAGE, SwipeProfile.forFingers(2).first())
        assertEquals(SwipeProfile.TEXT_UNITS, SwipeProfile.forFingers(3).first())
        assertTrue(SwipeProfile.forFingers(1).isEmpty())
        assertTrue(SwipeProfile.forFingers(4).isEmpty())
    }

    @Test fun applyingProfileTouchesOnlyThatGroupsSwipes() {
        val before = GestureMapping()
        val after = before.withSwipeProfile(2, SwipeProfile.QUICK_NAV)
        Gesture.entries.filterNot { it.fingers == 2 && GestureAnalyzer.swipeDirection(it) != null }.forEach {
            assertEquals(it.name, before.actionFor(it), after.actionFor(it))
        }
        assertEquals(GestureAction.NEXT_HEADING, after.actionFor(Gesture.TWO_FINGER_SWIPE_UP))
        assertEquals(GestureAction.PREVIOUS_HEADING, after.actionFor(Gesture.TWO_FINGER_SWIPE_DOWN))
        assertEquals(GestureAction.NEXT_LINK, after.actionFor(Gesture.TWO_FINGER_SWIPE_RIGHT))
        assertEquals(GestureAction.PREVIOUS_LINK, after.actionFor(Gesture.TWO_FINGER_SWIPE_LEFT))
        assertEquals(SwipeProfile.QUICK_NAV, SwipeProfile.detect(after, 2))
        assertEquals(SwipeProfile.TEXT_UNITS, SwipeProfile.detect(after, 3)) // nhóm ba ngón không bị đụng tới
    }

    @Test fun threeFingerGroupAcceptsProfilesToo() {
        val m = GestureMapping().withSwipeProfile(3, SwipeProfile.FULL_PAGE)
        assertEquals(GestureAction.SCROLL_TO_BOTTOM, m.actionFor(Gesture.THREE_FINGER_SWIPE_UP))
        assertEquals(GestureAction.SCROLL_TO_TOP, m.actionFor(Gesture.THREE_FINGER_SWIPE_DOWN))
        assertEquals(GestureAction.JUMP_FIRST, m.actionFor(Gesture.THREE_FINGER_SWIPE_LEFT))
        assertEquals(GestureAction.JUMP_LAST, m.actionFor(Gesture.THREE_FINGER_SWIPE_RIGHT))
        assertEquals(SwipeProfile.SCROLL_AND_PAGE, SwipeProfile.detect(m, 2))
    }

    @Test fun individualEditMeansCustom() {
        val m = GestureMapping().withSwipeProfile(2, SwipeProfile.QUICK_NAV).with(Gesture.TWO_FINGER_SWIPE_UP, GestureAction.HOME)
        assertNull(SwipeProfile.detect(m, 2))
        assertEquals(GestureAction.HOME, m.actionFor(Gesture.TWO_FINGER_SWIPE_UP))
    }

    @Test fun anyActionCanBeAssignedToAnyMultiFingerSwipe() {
        // "Tự do": mọi chức năng gán được cho mọi cử chỉ hai/ba ngón, và lưu/đọc lại đúng.
        val swipes = GestureAnalyzer.PROFILE_FINGER_GROUPS.flatMap { GestureAnalyzer.swipes(it) }
        assertEquals(8, swipes.size)
        swipes.forEach { gesture ->
            GestureAction.entries.forEach { action ->
                val m = GestureMapping().with(gesture, action)
                assertEquals(action, GestureMapping.parse(m.serializeOverrides()).actionFor(gesture))
            }
        }
    }

    @Test fun profileSurvivesSaveAndLoadAsOverridesOnly() {
        val m = GestureMapping().withSwipeProfile(2, SwipeProfile.FULL_PAGE)
        val raw = m.serializeOverrides()
        assertEquals(4, raw.split(';').size)
        assertEquals(SwipeProfile.FULL_PAGE, SwipeProfile.detect(GestureMapping.parse(raw), 2))
        assertEquals("", GestureMapping().withSwipeProfile(2, SwipeProfile.SCROLL_AND_PAGE).serializeOverrides())
        assertEquals("", GestureMapping().withSwipeProfile(3, SwipeProfile.TEXT_UNITS).serializeOverrides())
    }

    @Test fun everyProfileHasLabelsForAllActions() {
        SwipeProfile.entries.forEach { profile ->
            assertTrue(profile.label.isNotBlank())
            SwipeDirection.entries.forEach { assertTrue(profile.actionFor(it).label.isNotBlank()) }
        }
    }

    @Test fun analyzerGroupsSwipesByFingersAndDirection() {
        assertEquals(SwipeDirection.UP, GestureAnalyzer.swipeDirection(Gesture.TWO_FINGER_SWIPE_UP))
        assertEquals(SwipeDirection.RIGHT, GestureAnalyzer.swipeDirection(Gesture.THREE_FINGER_SWIPE_RIGHT))
        assertEquals(SwipeDirection.LEFT, GestureAnalyzer.swipeDirection(Gesture.SWIPE_LEFT))
        assertNull(GestureAnalyzer.swipeDirection(Gesture.DOUBLE_TAP))
        assertNull(GestureAnalyzer.swipeDirection(Gesture.UP_THEN_RIGHT))
        assertNull(GestureAnalyzer.swipeDirection(Gesture.THREE_FINGER_TAP))
        listOf(1, 2, 3).forEach { fingers ->
            assertEquals(4, GestureAnalyzer.swipes(fingers).size)
            assertTrue(GestureAnalyzer.swipes(fingers).all { it.fingers == fingers })
            SwipeDirection.entries.forEach { assertEquals(it, GestureAnalyzer.swipeDirection(GestureAnalyzer.swipe(fingers, it)!!)) }
        }
        assertTrue(GestureAnalyzer.swipes(4).isEmpty())
        assertTrue(GestureAnalyzer.isMultiFingerSwipe(Gesture.TWO_FINGER_SWIPE_DOWN))
        assertTrue(GestureAnalyzer.isMultiFingerSwipe(Gesture.THREE_FINGER_SWIPE_UP))
        assertFalse(GestureAnalyzer.isMultiFingerSwipe(Gesture.SWIPE_UP))
        assertFalse(GestureAnalyzer.isMultiFingerSwipe(Gesture.TWO_FINGER_TAP))
    }

    @Test fun newActionsDoNotChangeExistingDefaults() {
        val m = GestureMapping()
        assertEquals(GestureAction.PAGE_NEXT, m.actionFor(Gesture.TWO_FINGER_SWIPE_LEFT))
        assertEquals(GestureAction.SCROLL_FORWARD, m.actionFor(Gesture.TWO_FINGER_SWIPE_UP))
        assertEquals(GestureAction.UNIT_PREVIOUS, m.actionFor(Gesture.THREE_FINGER_SWIPE_UP))
        assertTrue(m.gesturesFor(GestureAction.NEXT_HEADING).isEmpty())
    }
}
