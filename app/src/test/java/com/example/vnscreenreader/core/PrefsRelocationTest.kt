package com.example.vnscreenreader.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsRelocationTest {

    private val waitSeconds = 5L
    private val pools = mutableListOf<java.util.concurrent.ExecutorService>()

    @After
    fun stopPools() {
        pools.forEach { it.shutdownNow() }
    }

    /** Hai nơi lưu giả: tập tên kho đang có ở mỗi nơi, cùng nhật ký mọi thao tác. */
    private class FakeSite(
        var unlocked: Boolean = true,
        oldHas: Set<String> = emptySet(),
        newHas: Set<String> = emptySet(),
    ) : PrefsRelocation.Site {
        val old = oldHas.toMutableSet()
        val new = newHas.toMutableSet()
        val calls = mutableListOf<String>()
        var moveResult = true
        var discardResult = true
        var moveError: RuntimeException? = null

        /** Làm việc chuyển chậm để các luồng chồng lên nhau nếu bộ chuyển không tự khóa. */
        var moveDelayMillis = 0L

        override fun isUnlocked(): Boolean = unlocked.also { calls += "isUnlocked" }
        override fun oldExists(name: String): Boolean = (name in old).also { calls += "oldExists:$name" }
        override fun newExists(name: String): Boolean = (name in new).also { calls += "newExists:$name" }

        override fun move(name: String): Boolean {
            synchronized(calls) { calls += "move:$name" }
            if (moveDelayMillis > 0) Thread.sleep(moveDelayMillis)
            moveError?.let { throw it }
            if (moveResult) {
                old -= name
                new += name
            }
            return moveResult
        }

        override fun discardOld(name: String): Boolean {
            calls += "discardOld:$name"
            if (discardResult) old -= name
            return discardResult
        }

        fun count(prefix: String) = calls.count { it.startsWith(prefix) }
    }

    private val name = "custom_labels"

    @Test
    fun aLockedDeviceTouchesNothingAndIsNotMarkedDone() {
        val site = FakeSite(unlocked = false, oldHas = setOf(name))
        val relocation = PrefsRelocation()

        assertFalse(relocation.settle(name, site))

        assertEquals(listOf("isUnlocked"), site.calls)
        assertTrue("dữ liệu vẫn ở chỗ cũ", name in site.old)
    }

    @Test
    fun onceUnlockedTheStoreMovesThatWasSkippedWhileLocked() {
        val site = FakeSite(unlocked = false, oldHas = setOf(name))
        val relocation = PrefsRelocation()
        relocation.settle(name, site)

        site.unlocked = true

        assertTrue(relocation.settle(name, site))
        assertTrue(name in site.new)
        assertFalse(name in site.old)
    }

    @Test
    fun nothingAtTheOldPlaceIsDoneWithoutAnyMove() {
        val site = FakeSite()
        val relocation = PrefsRelocation()

        assertTrue(relocation.settle(name, site))

        assertEquals(0, site.count("move"))
        assertEquals(0, site.count("discardOld"))
    }

    @Test
    fun theOldCopyIsMovedAndNeverDiscardedWhenTheNewPlaceHasNone() {
        val site = FakeSite(oldHas = setOf(name))
        val relocation = PrefsRelocation()

        assertTrue(relocation.settle(name, site))

        assertEquals(1, site.count("move:$name"))
        assertEquals(0, site.count("discardOld"))
        assertTrue(name in site.new)
        assertFalse(name in site.old)
    }

    @Test
    fun whenBothPlacesHaveItTheNewCopyIsKeptAndNothingIsMovedOverIt() {
        val site = FakeSite(oldHas = setOf(name), newHas = setOf(name))
        val relocation = PrefsRelocation()

        assertTrue(relocation.settle(name, site))

        assertEquals("không được chuyển đè lên bản mới hơn", 0, site.count("move"))
        assertEquals(1, site.count("discardOld:$name"))
        assertTrue(name in site.new)
        assertFalse(name in site.old)
    }

    @Test
    fun aFinishedStoreIsNotInspectedAgain() {
        val site = FakeSite(oldHas = setOf(name))
        val relocation = PrefsRelocation()
        relocation.settle(name, site)
        site.calls.clear()

        assertTrue(relocation.settle(name, site))

        assertEquals("kho đã xong thì lần sau không tốn thao tác nào", emptyList<String>(), site.calls)
    }

    @Test
    fun aMoveThatReportsFailureIsNotMarkedDoneAndIsRetried() {
        val site = FakeSite(oldHas = setOf(name))
        site.moveResult = false
        val relocation = PrefsRelocation()

        assertFalse(relocation.settle(name, site))
        assertTrue("dữ liệu không được mất khi chuyển hỏng", name in site.old)

        site.moveResult = true
        assertTrue(relocation.settle(name, site))
        assertEquals(2, site.count("move:$name"))
        assertTrue(name in site.new)
    }

    @Test
    fun aDiscardThatReportsFailureIsRetried() {
        val site = FakeSite(oldHas = setOf(name), newHas = setOf(name))
        site.discardResult = false
        val relocation = PrefsRelocation()

        assertFalse(relocation.settle(name, site))
        site.discardResult = true
        assertTrue(relocation.settle(name, site))

        assertEquals(2, site.count("discardOld:$name"))
        assertEquals(0, site.count("move"))
    }

    @Test
    fun aRuntimeExceptionIsReportedNotThrownAndTheStoreIsRetriedLater() {
        val reported = mutableListOf<Pair<String, RuntimeException>>()
        val relocation = PrefsRelocation { n, e -> reported += n to e }
        val site = FakeSite(oldHas = setOf(name))
        val boom = IllegalStateException("đĩa đầy")
        site.moveError = boom

        assertFalse(relocation.settle(name, site)) // không ném ra ngoài

        assertEquals(1, reported.size)
        assertEquals(name, reported[0].first)
        assertSame(boom, reported[0].second)
        assertTrue("dữ liệu vẫn ở chỗ cũ", name in site.old)

        site.moveError = null
        assertTrue(relocation.settle(name, site))
        assertTrue(name in site.new)
    }

    @Test
    fun anyKindOfRuntimeExceptionIsContained() {
        // moveSharedPreferencesFrom và thao tác tệp có thể ném nhiều loại ngoại lệ thời gian chạy khác nhau, không chỉ một loại.
        val errors = listOf<RuntimeException>(
            IllegalStateException("a"),
            IllegalArgumentException("b"),
            SecurityException("c"),
            UnsupportedOperationException("d"),
            NullPointerException("e"),
        )
        for (error in errors) {
            val reported = mutableListOf<RuntimeException>()
            val relocation = PrefsRelocation { _, e -> reported += e }
            val site = FakeSite(oldHas = setOf(name))
            site.moveError = error

            assertFalse(error.javaClass.simpleName, relocation.settle(name, site))

            assertEquals(listOf(error), reported)
        }
    }

    @Test
    fun aFailingReporterNeverEscapes() {
        val relocation = PrefsRelocation { _, _ -> throw IllegalArgumentException("báo lỗi cũng hỏng") }
        val site = FakeSite(oldHas = setOf(name))
        site.moveError = IllegalStateException("đĩa đầy")

        assertFalse(relocation.settle(name, site))
    }

    @Test
    fun anErrorIsNotSwallowed() {
        val relocation = PrefsRelocation()
        val site = object : PrefsRelocation.Site {
            override fun isUnlocked() = true
            override fun oldExists(name: String): Boolean = throw LinkageError("giả lập")
            override fun newExists(name: String) = false
            override fun move(name: String) = true
            override fun discardOld(name: String) = true
        }
        assertThrows(LinkageError::class.java) { relocation.settle(name, site) }
    }

    @Test
    fun eachStoreIsSettledIndependently() {
        val site = FakeSite(oldHas = setOf("custom_labels", "plugin_prefs"))
        site.moveResult = true
        val relocation = PrefsRelocation()

        assertTrue(relocation.settle("custom_labels", site))
        // "plugin_prefs" chưa được xét: làm xong "custom_labels" không được coi là làm xong cả hai.
        assertTrue("plugin_prefs" in site.old)
        assertTrue(relocation.settle("plugin_prefs", site))
        assertEquals(setOf("custom_labels", "plugin_prefs"), site.new)
        assertTrue(site.old.isEmpty())
    }

    @Test
    fun aFailureOnOneStoreDoesNotBlockTheOther() {
        val site = FakeSite(oldHas = setOf("custom_labels", "plugin_prefs"))
        val relocation = PrefsRelocation()
        site.moveError = IllegalStateException("hỏng")
        assertFalse(relocation.settle("custom_labels", site))
        site.moveError = null

        assertTrue(relocation.settle("plugin_prefs", site))
        assertTrue("plugin_prefs" in site.new)
        assertTrue("custom_labels" in site.old)
    }

    @Test
    fun manyThreadsSettlingTheSameStoreMoveItExactlyOnce() {
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads).also { pools += it }
        val site = FakeSite(oldHas = setOf(name))
        site.moveDelayMillis = 50
        val relocation = PrefsRelocation()
        val start = CountDownLatch(1)

        val results = (1..threads).map {
            pool.submit<Boolean> {
                start.await(waitSeconds, TimeUnit.SECONDS)
                relocation.settle(name, site)
            }
        }
        start.countDown()

        for (result in results) assertTrue(result.get(waitSeconds, TimeUnit.SECONDS))
        assertEquals(1, site.count("move:$name"))
    }
}
