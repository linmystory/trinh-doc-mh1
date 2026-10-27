package com.example.vnscreenreader.gesture

import android.accessibilityservice.AccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureAnalyzerTest {

    @Test
    fun fourDirections_areRecognized() {
        assertEquals(Gesture.SWIPE_UP, GestureAnalyzer.analyze(AccessibilityService.GESTURE_SWIPE_UP))
        assertEquals(Gesture.SWIPE_DOWN, GestureAnalyzer.analyze(AccessibilityService.GESTURE_SWIPE_DOWN))
        assertEquals(Gesture.SWIPE_LEFT, GestureAnalyzer.analyze(AccessibilityService.GESTURE_SWIPE_LEFT))
        assertEquals(Gesture.SWIPE_RIGHT, GestureAnalyzer.analyze(AccessibilityService.GESTURE_SWIPE_RIGHT))
    }

    @Test
    fun twoStepGestures_areRecognized() {
        assertEquals(
            Gesture.DOWN_THEN_RIGHT,
            GestureAnalyzer.analyze(AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT),
        )
        assertEquals(
            Gesture.UP_THEN_LEFT,
            GestureAnalyzer.analyze(AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT),
        )
        assertTrue(GestureAnalyzer.isTwoStep(Gesture.DOWN_THEN_RIGHT))
        assertFalse(GestureAnalyzer.isTwoStep(Gesture.SWIPE_UP))
        assertFalse(GestureAnalyzer.isTwoStep(Gesture.DOUBLE_TAP))
    }

    @Test
    fun unknownId_returnsNull() {
        assertNull(GestureAnalyzer.analyze(-999))
        assertNull(GestureAnalyzer.analyze(0))
    }

    @Test
    fun twoStepFlag_isConsistentWithNaming_andReadme() {
        Gesture.entries.forEach { assertEquals(it.name, it.name.contains("_THEN_"), it.isTwoStep) }
        assertEquals(12, Gesture.entries.count { it.isTwoStep })
        assertEquals(17, Gesture.entries.size)
    }

    @Test
    fun systemIds_areUnique() {
        assertEquals(Gesture.entries.size, Gesture.entries.map { it.systemId }.toSet().size)
    }
}

class GestureMappingTest {

    @Test
    fun defaults_coverEveryGesture_andMatchSpec() {
        val mapping = GestureMapping()
        assertEquals(Gesture.entries.toSet(), mapping.toMap().keys)
        assertEquals(GestureAction.NOTIFICATIONS, mapping.actionFor(Gesture.DOWN_THEN_RIGHT))
        assertEquals(GestureAction.BACK, mapping.actionFor(Gesture.UP_THEN_LEFT))
        assertEquals(GestureAction.NEXT_ELEMENT, mapping.actionFor(Gesture.SWIPE_RIGHT))
        assertEquals(GestureAction.NONE, mapping.actionFor(Gesture.LEFT_THEN_UP))
    }

    @Test
    fun with_returnsNewMapping_andKeepsOriginalUnchanged() {
        val original = GestureMapping()
        val changed = original.with(Gesture.SWIPE_UP, GestureAction.HOME)
        assertEquals(GestureAction.HOME, changed.actionFor(Gesture.SWIPE_UP))
        assertEquals(GestureAction.SCROLL_FORWARD, original.actionFor(Gesture.SWIPE_UP))
    }

    @Test
    fun serialize_thenParse_roundTrips() {
        val mapping = GestureMapping()
            .with(Gesture.SWIPE_UP, GestureAction.RECENTS)
            .with(Gesture.RIGHT_THEN_UP, GestureAction.RUN_PLUGINS)
        assertEquals(mapping.toMap(), GestureMapping.parse(mapping.serialize()).toMap())
    }

    @Test
    fun parse_nullOrBlank_givesDefaults() {
        assertEquals(GestureMapping().toMap(), GestureMapping.parse(null).toMap())
        assertEquals(GestureMapping().toMap(), GestureMapping.parse("   ").toMap())
    }

    @Test
    fun parse_ignoresGarbageAndUnknownNames() {
        val parsed = GestureMapping.parse("SWIPE_UP=HOME;BOGUS=BACK;SWIPE_DOWN=NOT_AN_ACTION;;x;a=b=c")
        assertEquals(GestureAction.HOME, parsed.actionFor(Gesture.SWIPE_UP))
        assertEquals(GestureAction.SCROLL_BACKWARD, parsed.actionFor(Gesture.SWIPE_DOWN))
    }
}
