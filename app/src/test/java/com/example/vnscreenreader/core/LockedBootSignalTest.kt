package com.example.vnscreenreader.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedBootSignalTest {

    private val waitSeconds = 5L

    @Test
    fun aListenerAttachedBeforeTheSignalIsToldWhenItArrives() {
        val signal = LockedBootSignal()
        var told = 0
        signal.attach { told++ }
        assertEquals(0, told)

        signal.arrive()

        assertEquals(1, told)
    }

    @Test
    fun aListenerAttachedAfterTheSignalIsToldAtOnce() {
        // Phiên dựng lại sau khi hệ thống đã phát tin: vẫn phải biết.
        val signal = LockedBootSignal()
        signal.arrive()
        var told = 0

        signal.attach { told++ }

        assertEquals(1, told)
    }

    @Test
    fun bothOrdersTellTheListenerExactlyOnce() {
        for (signalFirst in listOf(true, false)) {
            val signal = LockedBootSignal()
            var told = 0
            if (signalFirst) {
                signal.arrive()
                signal.attach { told++ }
            } else {
                signal.attach { told++ }
                signal.arrive()
            }
            assertEquals("signalFirst=$signalFirst", 1, told)
        }
    }

    @Test
    fun withNoListenerTheSignalIsRememberedNotLost() {
        val signal = LockedBootSignal()
        assertFalse(signal.hasArrived)
        signal.arrive()
        assertTrue(signal.hasArrived)
    }

    @Test
    fun aDetachedListenerIsNotToldAnymore() {
        val signal = LockedBootSignal()
        var told = 0
        val listener: () -> Unit = { told++ }
        signal.attach(listener)
        signal.detach(listener)

        signal.arrive()

        assertEquals(0, told)
    }

    @Test
    fun detachingAnOldListenerLeavesTheNewerSessionsListenerAttached() {
        val signal = LockedBootSignal()
        var oldTold = 0
        var newTold = 0
        val old: () -> Unit = { oldTold++ }
        val newer: () -> Unit = { newTold++ }
        signal.attach(old)
        signal.attach(newer) // phiên mới thay phiên cũ
        signal.detach(old) // phiên cũ đóng muộn

        signal.arrive()

        assertEquals(0, oldTold)
        assertEquals(1, newTold)
    }

    @Test
    fun expiredSignalDoesNotWakeALaterSession() {
        // Máy đã mở khóa: phiên dựng sau đó (dịch vụ nối lại) không được báo oan.
        val signal = LockedBootSignal()
        signal.arrive()
        signal.expire()
        var told = 0

        signal.attach { told++ }

        assertFalse(signal.hasArrived)
        assertEquals(0, told)
    }

    @Test
    fun aNewSignalAfterExpiryIsHonouredAgain() {
        val signal = LockedBootSignal()
        signal.arrive()
        signal.expire()
        var told = 0
        signal.attach { told++ }

        signal.arrive()

        assertEquals(1, told)
    }

    @Test
    fun aSecondSignalTellsTheListenerAgain() {
        val signal = LockedBootSignal()
        var told = 0
        signal.attach { told++ }
        signal.arrive()
        signal.arrive()
        assertEquals(2, told)
    }

    @Test
    fun aFailingListenerIsReportedAndNeverEscapesFromArrive() {
        val reported = mutableListOf<RuntimeException>()
        val signal = LockedBootSignal { reported += it }
        val boom = IllegalStateException("phiên hỏng")
        signal.attach { throw boom }

        signal.arrive() // không được ném ra onReceive

        assertEquals(1, reported.size)
        assertSame(boom, reported[0])
    }

    @Test
    fun anyKindOfRuntimeExceptionFromTheListenerIsContained() {
        // Phiên đọc có thể ném nhiều loại ngoại lệ thời gian chạy khác nhau, không chỉ một loại; loại nào cũng không được thoát ra onReceive.
        val errors = listOf<RuntimeException>(
            IllegalStateException("a"),
            IllegalArgumentException("b"),
            SecurityException("c"),
            UnsupportedOperationException("d"),
            NullPointerException("e"),
        )
        for (error in errors) {
            val reported = mutableListOf<RuntimeException>()
            val signal = LockedBootSignal { reported += it }
            signal.attach { throw error }

            signal.arrive()

            assertEquals(error.javaClass.simpleName, listOf(error), reported)
        }
    }

    @Test
    fun aFailingListenerAttachedAfterTheSignalIsAlsoContained() {
        val reported = mutableListOf<RuntimeException>()
        val signal = LockedBootSignal { reported += it }
        signal.arrive()

        signal.attach { throw IllegalStateException("phiên hỏng") }

        assertEquals(1, reported.size)
    }

    @Test
    fun aFailingErrorReporterNeverEscapes() {
        val signal = LockedBootSignal { throw IllegalArgumentException("báo lỗi cũng hỏng") }
        signal.attach { throw IllegalStateException("phiên hỏng") }
        signal.arrive()
    }

    @Test
    fun anErrorIsNotSwallowed() {
        // Lỗi nặng (hết bộ nhớ, lỗi liên kết...) không phải việc của chốt này.
        val signal = LockedBootSignal()
        signal.attach { throw LinkageError("giả lập") }
        try {
            signal.arrive()
            throw AssertionError("LinkageError phải đi tiếp")
        } catch (expected: LinkageError) {
            // đúng
        }
    }

    @Test
    fun theListenerRunsOutsideTheLockSoItMayAttachOrDetachAgain() {
        val signal = LockedBootSignal()
        var told = 0
        lateinit var listener: () -> Unit
        listener = {
            told++
            signal.detach(listener) // gọi ngược vào chốt từ chính người nghe: không được tự khóa chết
            signal.attach { told += 10 }
        }
        signal.attach(listener)

        signal.arrive()

        // listener chạy một lần, rồi người nghe mới gắn trong lúc đó được báo ngay vì tin đã tới (1 + 10).
        assertEquals(11, told)
    }

    @Test
    fun theListenerRunsOutsideTheLockSoAnotherThreadIsNotBlockedMeanwhile() {
        // Người nghe có thể chạy lâu (nối lại giọng đọc): trong lúc đó luồng khác gọi vào chốt không được bị chặn.
        val signal = LockedBootSignal()
        val pool = Executors.newSingleThreadExecutor()
        try {
            val otherWasServed = AtomicInteger(0)
            signal.attach {
                val other = pool.submit<Boolean> { signal.hasArrived }
                try {
                    other.get(waitSeconds, TimeUnit.SECONDS)
                    otherWasServed.set(1)
                } catch (e: java.util.concurrent.TimeoutException) {
                    otherWasServed.set(-1)
                }
            }

            signal.arrive()

            assertEquals("luồng khác bị chặn vì chốt còn khóa trong lúc gọi người nghe", 1, otherWasServed.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun signalAndAttachRacingFromTwoThreadsTellTheListenerExactlyOnce() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(200) {
                val signal = LockedBootSignal()
                val told = AtomicInteger()
                val start = CountDownLatch(1)
                val done = CountDownLatch(2)
                pool.execute {
                    start.await(waitSeconds, TimeUnit.SECONDS)
                    signal.arrive()
                    done.countDown()
                }
                pool.execute {
                    start.await(waitSeconds, TimeUnit.SECONDS)
                    signal.attach { told.incrementAndGet() }
                    done.countDown()
                }
                start.countDown()
                assertTrue(done.await(waitSeconds, TimeUnit.SECONDS))
                assertEquals("tin và người nghe tới đồng thời vẫn chỉ báo một lần", 1, told.get())
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
