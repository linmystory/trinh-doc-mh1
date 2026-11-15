package com.example.vnscreenreader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGestureOverridesTest {
    private val global = GestureMapping()

    @Test fun diffOfIdenticalMappingsIsEmpty() {
        assertEquals("", AppGestureOverrides.diff(global, global))
        assertEquals(0, AppGestureOverrides.count(""))
    }

    @Test fun diffKeepsOnlyChangedGestures() {
        val edited = global.with(Gesture.SWIPE_UP, GestureAction.HOME)
        val raw = AppGestureOverrides.diff(global, edited)
        assertEquals("SWIPE_UP=HOME", raw)
        assertEquals(1, AppGestureOverrides.count(raw))
    }

    @Test fun applyRoundTripsAndLeavesOthersToGlobal() {
        val edited = global.with(Gesture.SWIPE_UP, GestureAction.HOME).with(Gesture.TWO_FINGER_SWIPE_LEFT, GestureAction.NEXT_LINK)
        val applied = AppGestureOverrides.apply(global, AppGestureOverrides.diff(global, edited))
        assertEquals(edited.serialize(), applied.serialize())
    }

    @Test fun globalChangesStillReachGesturesTheAppDidNotOverride() {
        val raw = "SWIPE_UP=HOME"
        val newGlobal = global.with(Gesture.SWIPE_DOWN, GestureAction.BACK)
        val applied = AppGestureOverrides.apply(newGlobal, raw)
        assertEquals(GestureAction.HOME, applied.actionFor(Gesture.SWIPE_UP))
        assertEquals(GestureAction.BACK, applied.actionFor(Gesture.SWIPE_DOWN))
    }

    @Test fun garbageIsIgnoredAndNullIsSafe() {
        assertEquals(global.serialize(), AppGestureOverrides.apply(global, null).serialize())
        assertEquals(global.serialize(), AppGestureOverrides.apply(global, "x=y;;SWIPE_UP;NOPE=HOME;SWIPE_UP=NOPE").serialize())
        assertEquals(0, AppGestureOverrides.count("x=y;;SWIPE_UP"))
    }

    @Test fun limitIsReasonable() {
        assertTrue(AppGestureOverrides.MAX_APPS in 10..200)
    }
}
