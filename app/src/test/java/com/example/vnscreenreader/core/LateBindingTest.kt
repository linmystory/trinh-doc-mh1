package com.example.vnscreenreader.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LateBindingTest {

    /** Hạn chờ cho các chốt đồng bộ: nếu hỏng thì bài thất bại thay vì treo cả bộ kiểm thử. */
    private val waitSeconds = 5L

    private val pools = mutableListOf<java.util.concurrent.ExecutorService>()

    @After
    fun stopPools() {
        pools.forEach { it.shutdownNow() }
    }

    /** Kho giả: mở được hay chưa do [available] quyết định, đếm số lần mở. */
    private class Source {
        @Volatile var available = false
        val opens = AtomicInteger()
        val resource = Any()
        fun open(): Any? {
            opens.incrementAndGet()
            return if (available) resource else null
        }
    }

    @Test
    fun nothingIsOpenedUntilBindIsCalled() {
        val source = Source()
        val binding = LateBinding { source.open() }
        assertNull(binding.current)
        assertEquals(0, source.opens.get())
    }

    @Test
    fun anUnavailableResourceStaysUnboundAndIsRetriedOnTheNextBind() {
        val source = Source()
        val binding = LateBinding { source.open() }

        assertNull(binding.bind())
        assertNull(binding.bind())
        assertNull(binding.current)
        assertEquals("mỗi lần chưa mở được là một lần thử lại", 2, source.opens.get())

        source.available = true
        assertSame(source.resource, binding.bind())
        assertSame(source.resource, binding.current)
    }

    @Test
    fun aBoundResourceIsOpenedOnlyOnceAndTheCallbackRunsOnlyOnce() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        val bound = mutableListOf<Any>()

        val first = binding.bind { bound += it }
        val second = binding.bind { bound += it }
        val third = binding.bind()

        assertSame(source.resource, first)
        assertSame(first, second)
        assertSame(first, third)
        assertEquals(1, source.opens.get())
        assertEquals(listOf(source.resource), bound)
    }

    @Test
    fun theCallbackRunsBeforeAnyoneCanSeeTheResource() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        var seenInsideCallback: Any? = "chưa đo"

        binding.bind { seenInsideCallback = binding.current }

        assertNull("khi onBound chạy thì chưa ai thấy tài nguyên qua current", seenInsideCallback)
        assertSame(source.resource, binding.current)
    }

    @Test
    fun aFailingCallbackLeavesTheResourceUnboundAndTheNextBindStartsOver() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        var attempts = 0

        assertThrows(IllegalStateException::class.java) {
            binding.bind { attempts++; throw IllegalStateException("nạp dở") }
        }
        assertNull(binding.current)

        assertSame(source.resource, binding.bind { attempts++ })
        assertEquals(2, attempts)
        assertEquals(2, source.opens.get())
    }

    @Test
    fun aFailingOpenPropagatesAndLeavesTheResourceUnbound() {
        var fail = true
        val binding = LateBinding { if (fail) throw IllegalStateException("kho khóa") else "kho" }

        assertThrows(IllegalStateException::class.java) { binding.bind() }
        assertNull(binding.current)

        fail = false
        assertEquals("kho", binding.bind())
    }

    @Test
    fun closeHandsTheBoundResourceBackExactlyOnce() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        binding.bind()
        val closed = mutableListOf<Any>()

        binding.close { closed += it }
        binding.close { closed += it }

        assertEquals(listOf(source.resource), closed)
        assertNull(binding.current)
    }

    @Test
    fun closingBeforeAnythingWasBoundHandsBackNothing() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        var called = false

        binding.close { called = true }

        assertFalse(called)
        assertEquals(0, source.opens.get())
    }

    @Test
    fun afterCloseNothingIsOpenedAgain() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        binding.close()

        assertNull(binding.bind { throw AssertionError("không được gắn sau khi đã đóng") })

        assertEquals("đã đóng thì không mở nữa, kẻo rò listener của phiên đã bỏ", 0, source.opens.get())
        assertNull(binding.current)
    }

    @Test
    fun afterCloseABoundResourceIsNoLongerReturned() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        binding.bind()
        binding.close()

        assertNull(binding.bind())
        assertEquals(1, source.opens.get())
    }

    @Test
    fun theCloseCallbackRunsOutsideTheLockSoItCannotBlockBind() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        binding.bind()
        val inCallback = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2).also { pools += it }

        pool.submit {
            binding.close {
                inCallback.countDown()
                // Việc đóng chậm, và chậm HƠN hạn chờ của luồng quan sát bên dưới: nếu bind() bị chặn bởi khóa thì luồng kia chắc chắn hết hạn trước, thay vì
                // tình cờ được thả đúng lúc hai hạn chờ trùng nhau.
                release.await(waitSeconds * 3, TimeUnit.SECONDS)
            }
        }
        assertTrue(inCallback.await(waitSeconds, TimeUnit.SECONDS))
        // Trong lúc việc đóng còn đang chạy, một lần bind ở luồng khác không được bị chặn.
        val other = pool.submit<Any?> { binding.bind() }
        val result = try {
            other.get(waitSeconds, TimeUnit.SECONDS)
        } finally {
            release.countDown()
        }
        assertNull(result)
    }

    @Test
    fun manyThreadsBindingAtOnceOpenAndInitialiseExactlyOnce() {
        val source = Source().also { it.available = true }
        val binding = LateBinding { source.open() }
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads).also { pools += it }
        val start = CountDownLatch(1)
        val initialised = AtomicInteger()

        val results = (1..threads).map {
            pool.submit<Any?> {
                start.await(waitSeconds, TimeUnit.SECONDS)
                binding.bind { initialised.incrementAndGet() }
            }
        }
        start.countDown()

        for (result in results) assertSame(source.resource, result.get(waitSeconds, TimeUnit.SECONDS))
        assertEquals(1, source.opens.get())
        assertEquals(1, initialised.get())
    }
}
