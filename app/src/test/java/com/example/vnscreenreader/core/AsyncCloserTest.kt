package com.example.vnscreenreader.core

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AsyncCloserTest {

    /** Hạn chờ cho các chốt đồng bộ: đủ rộng cho máy chậm, nhưng nếu hỏng thì bài thất bại thay vì treo mãi. */
    private val waitSeconds = 5L

    private val toStop = mutableListOf<ExecutorService>()

    @After
    fun stopPools() {
        toStop.forEach { it.shutdownNow() }
    }

    private fun pool(prefix: String = "vn-test", maxThreads: Int = AsyncCloser.DEFAULT_MAX_THREADS) =
        AsyncCloser.daemonPool(prefix, maxThreads).also { toStop += it }

    @Test
    fun theActionRunsOnABackgroundDaemonThread_notOnTheCaller() {
        val closer = AsyncCloser(pool("vn-test"))
        val ran = CountDownLatch(1)
        var runner: Thread? = null
        closer.close("x") {
            runner = Thread.currentThread()
            ran.countDown()
        }
        assertTrue(ran.await(waitSeconds, TimeUnit.SECONDS))
        val thread = runner!!
        assertNotSame(Thread.currentThread(), thread)
        assertTrue("luồng đóng phải là daemon để không giữ tiến trình sống", thread.isDaemon)
        assertTrue(thread.name.startsWith("vn-test-"))
    }

    @Test
    fun closeReturnsAtOnce_evenWhileTheActionIsStillStuck() {
        // Hình dung engine TTS không trả lời lúc khởi động máy: việc đóng kẹt hàng chục giây nhưng luồng gọi (luồng chính) không được kẹt theo.
        val closer = AsyncCloser(pool())
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        // Lời gọi chạy trên luồng riêng có hạn chờ: nếu close() chặn thì bài thất bại rõ ràng thay vì treo cả bộ test.
        val caller = Executors.newSingleThreadExecutor().also { toStop += it }
        val call = caller.submit {
            closer.close("stuck") {
                started.countDown()
                // Chờ có hạn: nếu ai đó làm close() chạy đồng bộ thì bài thất bại sau vài giây thay vì treo mãi cả bộ kiểm thử (và cả CI).
                release.await(waitSeconds, TimeUnit.SECONDS)
                finished.countDown()
            }
        }
        try {
            call.get(waitSeconds, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            release.countDown()
            fail("close() chặn luồng gọi khi việc đóng bị kẹt")
        }
        assertTrue(started.await(waitSeconds, TimeUnit.SECONDS))
        assertEquals("việc đóng vẫn đang kẹt", 1L, finished.count)
        release.countDown()
        assertTrue(finished.await(waitSeconds, TimeUnit.SECONDS))
    }

    @Test
    fun aStuckClose_doesNotHoldBackAnotherClose() {
        // Lần dừng tiếng của engine cũ phải chạy ngay dù một lần đóng trước đó đang kẹt.
        val closer = AsyncCloser(pool(maxThreads = 2))
        val stuckStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        closer.close("stuck") {
            stuckStarted.countDown()
            // Chờ có hạn vì lý do như bài trên: close() mà chạy đồng bộ thì lời gọi này nằm ngay trên luồng của bài kiểm thử.
            release.await(waitSeconds, TimeUnit.SECONDS)
        }
        assertTrue(stuckStarted.await(waitSeconds, TimeUnit.SECONDS))
        val secondDone = CountDownLatch(1)
        closer.close("second") { secondDone.countDown() }
        val ranWhileStuck = secondDone.await(waitSeconds, TimeUnit.SECONDS)
        release.countDown()
        assertTrue("lần đóng sau phải chạy dù lần trước đang kẹt", ranWhileStuck)
    }

    @Test
    fun onASingleThread_closesKeepTheirQueueOrder() {
        val closer = AsyncCloser(pool(maxThreads = 1))
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val done = CountDownLatch(20)
        repeat(20) { i ->
            closer.close("n$i") {
                order.add(i)
                done.countDown()
            }
        }
        assertTrue(done.await(waitSeconds, TimeUnit.SECONDS))
        assertEquals((0 until 20).toList(), order.toList())
    }

    @Test
    fun aRuntimeExceptionInTheAction_isReportedWithItsLabel_andNeverThrown() {
        val reported = mutableListOf<Pair<String, RuntimeException>>()
        val closer = AsyncCloser(Executor { it.run() }) { label, error -> reported += label to error }
        val boom = IllegalStateException("dịch vụ TTS đã chết")
        closer.close("đóng TextToSpeech") { throw boom }
        assertEquals(1, reported.size)
        assertEquals("đóng TextToSpeech", reported[0].first)
        assertSame(boom, reported[0].second)
    }

    @Test
    fun aSuccessfulAction_reportsNothing() {
        var reports = 0
        var ran = false
        val closer = AsyncCloser(Executor { it.run() }) { _, _ -> reports++ }
        closer.close("x") { ran = true }
        assertTrue(ran)
        assertEquals(0, reports)
    }

    @Test
    fun aFailingErrorReporter_neverEscapes() {
        val closer = AsyncCloser(Executor { it.run() }) { _, _ -> throw IllegalArgumentException("báo lỗi cũng hỏng") }
        closer.close("x") { throw IllegalStateException("đóng hỏng") }
    }

    @Test
    fun aRejectingExecutor_closesInlineRatherThanLeakingTheResource() {
        val reported = mutableListOf<String>()
        val closer = AsyncCloser(Executor { throw RejectedExecutionException("đã dừng") }) { label, _ -> reported += label }
        var ran = false
        closer.close("a") { ran = true }
        assertTrue("bộ thực thi từ chối thì phải đóng tại chỗ", ran)
        closer.close("b") { throw IllegalStateException("đóng hỏng") }
        assertEquals("lỗi khi đóng tại chỗ vẫn được báo, không ném", listOf("b"), reported)
    }

    @Test
    fun anError_isNotSwallowed() {
        // Lỗi nặng (hết bộ nhớ, lỗi liên kết...) không phải việc của lớp này: giống lúc đóng thẳng trên luồng gọi.
        val closer = AsyncCloser(Executor { it.run() })
        assertThrows(LinkageError::class.java) { closer.close("x") { throw LinkageError("giả lập") } }
    }

    @Test
    fun poolThreadsAreNamedAfterThePrefix_andNumbered() {
        val names = Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(1)
        AsyncCloser(pool("vn-name", maxThreads = 1)).close("x") {
            names.add(Thread.currentThread().name)
            done.countDown()
        }
        assertTrue(done.await(waitSeconds, TimeUnit.SECONDS))
        assertEquals(listOf("vn-name-1"), names.toList())
    }

    @Test
    fun theDaemonPool_isBoundedAndReleasesIdleThreads() {
        val pool = AsyncCloser.daemonPool("cfg", maxThreads = 3, idleSeconds = 7).also { toStop += it }
        assertEquals(3, pool.corePoolSize)
        assertEquals(3, pool.maximumPoolSize)
        assertEquals(7L, pool.getKeepAliveTime(TimeUnit.SECONDS))
        assertTrue("luồng nhàn rỗi phải tự thoát, không giữ luồng thừa suốt đời tiến trình", pool.allowsCoreThreadTimeOut())
        assertFalse(pool.isShutdown)
    }

    @Test
    fun theDaemonPool_rejectsNonsenseSizes() {
        assertThrows(IllegalArgumentException::class.java) { AsyncCloser.daemonPool("x", maxThreads = 0) }
        assertThrows(IllegalArgumentException::class.java) { AsyncCloser.daemonPool("x", idleSeconds = 0) }
    }
}
