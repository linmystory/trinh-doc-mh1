package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlanTest {

    private fun say(text: String) = SpeechPart.Say(text)
    private fun pause(ms: Int) = SpeechPart.Pause(ms)

    @Test
    fun normalize_dropsEdgePauses_mergesPauses_andDropsBlankSay() {
        val parts = listOf(pause(100), say("a"), pause(100), pause(300), say(" "), say("b"), pause(50))
        assertEquals(listOf(say("a"), pause(300), say("b")), SpeechPlan.normalize(parts))
    }

    @Test
    fun single_ofBlankText_isEmpty() {
        assertTrue(SpeechPlan.single("   ").isEmpty())
        assertEquals(listOf(say("xin chào")), SpeechPlan.single("xin chào"))
    }

    @Test
    fun plainText_joinsSayPartsWithCommas() {
        assertEquals("a, b", SpeechPlan.plainText(listOf(say("a "), pause(200), say(" b"))))
    }

    @Test
    fun chunk_splitsLongSay_andKeepsPauses() {
        val parts = SpeechPlan.chunk(listOf(say("word ".repeat(20)), pause(200), say("tail")), 30)
        val says = parts.filterIsInstance<SpeechPart.Say>()
        assertTrue(says.size >= 4)
        assertTrue(says.all { it.text.length <= 30 })
        assertEquals(1, parts.count { it is SpeechPart.Pause })
        assertEquals(say("tail"), parts.last())
    }

    @Test
    fun scaledPause_followsSpeechRate_andIsClamped() {
        assertEquals(160, SpeechPlan.scaledPause(160, 1f))
        assertEquals(80, SpeechPlan.scaledPause(160, 2f))
        assertEquals(SpeechPlan.MIN_PAUSE_MS, SpeechPlan.scaledPause(160, 4f))
        assertEquals(SpeechPlan.MAX_PAUSE_MS, SpeechPlan.scaledPause(500, 0.5f))
        assertEquals(160, SpeechPlan.scaledPause(160, 0f)) // tốc độ không hợp lệ coi như 1
    }

    @Test
    fun emojiRunPolicy_gaps() {
        assertEquals(EmojiRunPolicy.ENTRY_GAP_MS, EmojiRunPolicy.gapAfter(0))
        assertEquals(EmojiRunPolicy.ENTRY_GAP_MS, EmojiRunPolicy.gapAfter(2))
        assertEquals(EmojiRunPolicy.GROUP_GAP_MS, EmojiRunPolicy.gapAfter(EmojiRunPolicy.GROUP_SIZE - 1))
        assertEquals(EmojiRunPolicy.GROUP_GAP_MS, EmojiRunPolicy.gapAfter(2 * EmojiRunPolicy.GROUP_SIZE - 1))
        assertTrue(EmojiRunPolicy.GROUP_GAP_MS > EmojiRunPolicy.ENTRY_GAP_MS)
    }
}

class SpeechPendingTrackerTest {

    @Test
    fun aMessageWithManyParts_countsAsOneMessage() {
        val tracker = SpeechPendingTracker()
        for (i in 1..9) tracker.register("m1", "u$i")
        assertEquals(1, tracker.pendingMessages())
        assertEquals(9, tracker.pendingUtterances())
        // Một câu emoji dài không làm hàng đợi trông "đầy" nên lời kế tiếp không cắt ngang nó.
        assertEquals(SpeechQueuePolicy.Mode.ADD, SpeechQueuePolicy.modeFor(false, tracker.pendingMessages()))
    }

    @Test
    fun messageFinishes_whenItsLastPartCompletes() {
        val tracker = SpeechPendingTracker()
        tracker.register("m1", "a")
        tracker.register("m1", "b")
        tracker.complete("a")
        assertEquals(1, tracker.pendingMessages())
        tracker.complete("b")
        assertEquals(0, tracker.pendingMessages())
        assertEquals(0, tracker.pendingUtterances())
    }

    @Test
    fun completingTwiceOrUnknown_isHarmless() {
        val tracker = SpeechPendingTracker()
        tracker.register("m1", "a")
        tracker.complete("a")
        tracker.complete("a")
        tracker.complete("khong-co")
        assertEquals(0, tracker.pendingMessages())
    }

    @Test
    fun manyPendingMessages_flushTheQueue() {
        val tracker = SpeechPendingTracker()
        for (i in 1..SpeechQueuePolicy.MAX_PENDING) tracker.register("m$i", "u$i")
        assertEquals(SpeechQueuePolicy.Mode.DROP, SpeechQueuePolicy.modeFor(false, tracker.pendingMessages()))
    }

    @Test
    fun clear_forgetsEverything() {
        val tracker = SpeechPendingTracker()
        tracker.register("m1", "a")
        tracker.clear()
        assertEquals(0, tracker.pendingMessages())
        tracker.complete("a") // callback đến muộn sau khi clear
        assertEquals(0, tracker.pendingMessages())
    }
}
