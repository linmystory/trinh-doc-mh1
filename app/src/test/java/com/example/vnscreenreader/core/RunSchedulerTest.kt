package com.example.vnscreenreader.core

import com.example.vnscreenreader.core.LanguageRuns.Run
import com.example.vnscreenreader.core.LanguageRuns.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSchedulerTest {
    private fun run(text: String, side: Side = Side.PRIMARY) = Run(text, side)

    @Test
    fun playsOneRunAtATime_inOrder() {
        val s = RunScheduler()
        assertTrue(s.submit(listOf(run("a"), run("b", Side.SECOND)), interrupt = false))
        val (first, t1) = s.next()!!
        assertEquals("a", first.text)
        assertNull("đang phát thì chưa lấy đoạn kế", s.next())
        assertTrue(s.finished(t1))
        val (second, t2) = s.next()!!
        assertEquals("b", second.text)
        assertEquals(Side.SECOND, second.side)
        assertTrue(s.finished(t2))
        assertNull(s.next())
    }

    @Test
    fun interruptDropsQueue_andStaleFinishIsIgnored() {
        val s = RunScheduler()
        s.submit(listOf(run("old1"), run("old2")), false)
        val (_, oldToken) = s.next()!!
        s.submit(listOf(run("new")), interrupt = true)
        assertFalse("báo xong muộn của đoạn cũ phải bị bỏ", s.finished(oldToken))
        assertEquals("new", s.next()!!.first.text)
    }

    @Test
    fun lowPriorityIsDropped_whenQueueIsFull() {
        val s = RunScheduler(maxPending = 2)
        assertTrue(s.submit(listOf(run("1")), false))
        s.next()
        assertTrue(s.submit(listOf(run("2")), false))
        assertFalse(s.submit(listOf(run("3")), false))
        assertTrue("ngắt thì luôn nhận", s.submit(listOf(run("4")), true))
    }

    @Test
    fun emptySubmissionIsRejected_andClearStopsEverything() {
        val s = RunScheduler()
        assertFalse(s.submit(emptyList(), false))
        s.submit(listOf(run("a")), false)
        val (_, t) = s.next()!!
        s.clear()
        assertFalse(s.finished(t))
        assertNull(s.next())
        assertEquals(0, s.pendingMessages())
    }
}
