package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftToTypeTest {
    private var now = 0L
    private fun policy() = LiftToTypePolicy({ now }, maxAgeMs = 1_000)

    @Test fun liftingOnAKeyTypesOnceEvenWithTwoEndEvents() {
        val p = policy()
        p.onHover(isKey = true)
        now += 300
        assertTrue(p.onTouchEnd(enabled = true))
        assertFalse(p.onTouchEnd(enabled = true)) // sự kiện kết thúc thứ hai của cùng một lần chạm
    }

    @Test fun disabledNeverTypes() {
        val p = policy()
        p.onHover(isKey = true)
        assertFalse(p.onTouchEnd(enabled = false))
    }

    @Test fun slidingOffTheKeyboardCancels() {
        val p = policy()
        p.onHover(isKey = true)
        p.onHover(isKey = false)
        assertFalse(p.onTouchEnd(enabled = true))
    }

    @Test fun gestureStartCancels() {
        val p = policy()
        p.onHover(isKey = true)
        p.onGestureStart()
        assertFalse(p.onTouchEnd(enabled = true))
    }

    @Test fun restingTooLongDoesNotType() {
        val p = policy()
        p.onHover(isKey = true)
        now += 1_001
        assertFalse(p.onTouchEnd(enabled = true))
    }

    @Test fun nothingTouchedNothingTyped() {
        assertFalse(policy().onTouchEnd(enabled = true))
    }

    @Test fun eachNewTouchCanTypeAgain() {
        val p = policy()
        p.onHover(true)
        assertTrue(p.onTouchEnd(true))
        now += 500
        p.onHover(true)
        assertTrue(p.onTouchEnd(true))
    }
}
