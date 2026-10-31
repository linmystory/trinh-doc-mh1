package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDeduplicatorTest {

    @Test
    fun firstEvent_isProcessed() {
        assertTrue(EventDeduplicator(100).shouldProcess("a", 1_000))
    }

    @Test
    fun sameKeyWithinWindow_isDropped() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 1_000))
        assertFalse(dedup.shouldProcess("a", 1_050))
    }

    @Test
    fun sameKeyAfterWindow_isProcessed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 1_000))
        assertTrue(dedup.shouldProcess("a", 1_101))
    }

    @Test
    fun differentKey_isProcessed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 1_000))
        assertTrue(dedup.shouldProcess("b", 1_010))
    }

    @Test
    fun continuousStorm_staysSuppressed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 0))
        assertFalse(dedup.shouldProcess("a", 80))
        assertFalse(dedup.shouldProcess("a", 160))
    }

    @Test
    fun timeGoingBackwards_isNotTreatedAsDuplicate() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 5_000))
        assertTrue(dedup.shouldProcess("a", 4_000))
    }
}

class SpeechQueuePolicyTest {

    @Test
    fun interrupt_alwaysFlushes() {
        assertEquals(SpeechQueuePolicy.Mode.FLUSH, SpeechQueuePolicy.modeFor(true, 0))
    }

    @Test
    fun shortQueue_addsWhenNotInterrupting() {
        assertEquals(SpeechQueuePolicy.Mode.ADD, SpeechQueuePolicy.modeFor(false, SpeechQueuePolicy.MAX_PENDING - 1))
    }

    @Test
    fun longQueue_flushesToDropStaleSpeech() {
        assertEquals(SpeechQueuePolicy.Mode.DROP, SpeechQueuePolicy.modeFor(false, SpeechQueuePolicy.MAX_PENDING))
        assertEquals(SpeechQueuePolicy.Mode.FLUSH, SpeechQueuePolicy.modeFor(true, SpeechQueuePolicy.MAX_PENDING))
    }
}

class EventDeduplicatorLruTest {

    @Test
    fun alternatingKeys_areBothSuppressed() {
        val dedup = EventDeduplicator(100)
        assertTrue(dedup.shouldProcess("a", 0))
        assertTrue(dedup.shouldProcess("b", 10))
        assertFalse(dedup.shouldProcess("a", 20))
        assertFalse(dedup.shouldProcess("b", 30))
    }

    @Test
    fun capacityIsBounded_oldestKeyForgotten() {
        val dedup = EventDeduplicator(1_000, capacity = 2)
        assertTrue(dedup.shouldProcess("a", 0))
        assertTrue(dedup.shouldProcess("b", 1))
        assertTrue(dedup.shouldProcess("c", 2)) // đẩy "a" ra
        assertTrue(dedup.shouldProcess("a", 3)) // "a" đã bị quên
    }
}

class PasswordAndHintsTest {
    @org.junit.Test
    fun passwordCharsHiddenByDefaultAndSpokenWhenEnabled() {
        val off = SpeechTextBuilder.describeTextChange("abc", 2, 1, 0, isPassword = true)
        org.junit.Assert.assertEquals("Đã nhập ký tự", off)
        val on = SpeechTextBuilder.describeTextChange("abc", 2, 1, 0, isPassword = true, speakPasswordChars = true)
        org.junit.Assert.assertEquals("c", on)
        val masked = SpeechTextBuilder.describeTextChange("•••", 2, 1, 0, isPassword = true, speakPasswordChars = true)
        org.junit.Assert.assertEquals("Đã nhập ký tự", masked)
    }

    @org.junit.Test
    fun menuHintsCanBeSwitchedOff() {
        val state = com.example.vnscreenreader.menu.MenuState.open(
            com.example.vnscreenreader.menu.MenuKind.GLOBAL,
            com.example.vnscreenreader.menu.Menus.global(),
        ).next()
        org.junit.Assert.assertTrue(state.describeCurrent(hints = true).contains("1 trên"))
        org.junit.Assert.assertEquals(state.current!!.label, state.describeCurrent(hints = false))
        org.junit.Assert.assertEquals("Trình đơn chung, ${state.items.size} mục.", state.opening())
    }
}

class AuditV20Test {
    @org.junit.Test
    fun fullQueue_dropsLowPriority_neverFlushesIt() {
        org.junit.Assert.assertEquals(SpeechQueuePolicy.Mode.DROP, SpeechQueuePolicy.modeFor(false, SpeechQueuePolicy.MAX_PENDING + 5))
    }

    @org.junit.Test
    fun tidy_stillCleansCommasAfterRegexConstants() {
        val out = EmojiText.forSpeech("Xin chào 😀 tên", VietnameseDictionary)
        org.junit.Assert.assertTrue(out, out.startsWith("Xin chào, "))
        org.junit.Assert.assertTrue(out, out.endsWith(", tên"))
        org.junit.Assert.assertFalse(out, out.contains(" ,") || out.contains(",,") || out.contains("  "))
    }
}
