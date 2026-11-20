package com.example.vnscreenreader.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CoalescingReceiverTest {

    /** Sự kiện thử: "h..." là chạm (gộp được), còn lại thì không. */
    private fun isHover(s: String) = s.startsWith("h")

    private fun channelOf(vararg items: String): Channel<String> {
        val ch = Channel<String>(Channel.UNLIMITED)
        items.forEach { ch.trySend(it) }
        ch.close()
        return ch
    }

    /** Lấy hết sự kiện: danh sách đã giao và tổng số sự kiện bị gộp. */
    private fun drain(ch: Channel<String>): Pair<List<String>, Int> = runBlocking {
        val receiver = CoalescingReceiver(ch, ::isHover)
        val out = ArrayList<String>()
        var skipped = 0
        while (true) {
            val e = receiver.receive() ?: break
            out += e
            skipped += receiver.takeSkipped()
        }
        out to skipped
    }

    @Test
    fun consecutiveHovers_keepOnlyTheNewest() {
        val (out, skipped) = drain(channelOf("h1", "h2", "h3"))
        assertEquals(listOf("h3"), out)
        assertEquals(2, skipped)
    }

    @Test
    fun otherEvents_areNeverDropped_andKeepTheirOrder() {
        val (out, skipped) = drain(channelOf("h1", "h2", "x", "h3", "y"))
        assertEquals(listOf("h2", "x", "h3", "y"), out)
        assertEquals(1, skipped)
    }

    @Test
    fun hoversAreNeverMergedAcrossALift() {
        // Nhấc tay (x) chen giữa: hai lần đặt ngón tay khác nhau, mỗi lần được đọc.
        val (out, skipped) = drain(channelOf("h1", "x", "h2", "h3"))
        assertEquals(listOf("h1", "x", "h3"), out)
        assertEquals(1, skipped)
    }

    @Test
    fun singleEvent_isReturnedAsIs() {
        val (out, skipped) = drain(channelOf("h1"))
        assertEquals(listOf("h1"), out)
        assertEquals(0, skipped)
    }

    @Test
    fun emptyClosedChannel_returnsNull() {
        runBlocking {
            val receiver = CoalescingReceiver(channelOf(), ::isHover)
            assertNull(receiver.receive())
            assertEquals(0, receiver.takeSkipped())
        }
    }

    @Test
    fun carriedEvent_isDeliveredEvenThoughTheChannelIsAlreadyClosed() {
        val (out, _) = drain(channelOf("h1", "x"))
        assertEquals(listOf("h1", "x"), out)
    }

    @Test
    fun takeSkipped_resetsToZero() {
        runBlocking {
            val receiver = CoalescingReceiver(channelOf("h1", "h2", "h3"), ::isHover)
            assertEquals("h3", receiver.receive())
            assertEquals(2, receiver.takeSkipped())
            assertEquals(0, receiver.takeSkipped())
        }
    }

    @Test
    fun doesNotWaitForEventsThatHaveNotArrivedYet() {
        // Kênh còn mở nhưng đã hết sự kiện: trả ngay sự kiện chạm hiện có, không treo chờ cái mới.
        runBlocking {
            val ch = Channel<String>(Channel.UNLIMITED)
            ch.trySend("h1")
            val receiver = CoalescingReceiver(ch, ::isHover)
            assertEquals("h1", receiver.receive())
            ch.close()
        }
    }

    @Test
    fun randomStreams_keepEveryOtherEvent_andOnlyTheLastOfEachHoverRun() {
        val r = Random(20261118L)
        repeat(500) {
            val input = List(r.nextInt(40)) { if (r.nextInt(100) < 60) "h$it" else "x$it" }
            val (out, skipped) = drain(channelOf(*input.toTypedArray()))

            // Kỳ vọng: từ mỗi đoạn chạm liền nhau chỉ giữ cái cuối; mọi sự kiện khác giữ nguyên thứ tự.
            val expected = ArrayList<String>()
            for ((i, e) in input.withIndex()) {
                val nextIsHover = i + 1 < input.size && isHover(input[i + 1])
                if (!(isHover(e) && nextIsHover)) expected += e
            }
            assertEquals(expected, out)
            assertEquals(input.size, out.size + skipped)
            assertTrue(out.filterNot(::isHover) == input.filterNot(::isHover))
        }
    }
}
