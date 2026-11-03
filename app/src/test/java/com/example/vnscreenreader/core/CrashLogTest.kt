package com.example.vnscreenreader.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_800_000_000_000L
    private fun store(dir: File = File(tmp.root, "crash_logs")) = CrashLogStore(dir) { now }

    private fun boom(msg: String = "x") = IllegalStateException(msg)

    // ---- che thông tin ----
    @Test fun redactorMasksEmailUrlAndLongNumbers() {
        val r = LogRedactor.redact("gửi tới a.b+c@mail.com tại https://x.vn/p?id=1 số 0912 345 678 và mã 42")
        assertFalse(r.contains("@"))
        assertFalse(r.contains("https"))
        assertFalse(r.contains("0912"))
        assertTrue(r.contains("<email>") && r.contains("<url>") && r.contains("<num>"))
        assertTrue(r.endsWith("mã 42")) // số ngắn giữ nguyên
    }

    @Test fun redactorTruncatesCollapsesAndHandlesNull() {
        assertEquals("", LogRedactor.redact(null))
        assertEquals("a b", LogRedactor.redact("a \n\t b"))
        val long = LogRedactor.redact("x".repeat(500))
        assertEquals(CrashLogLimits.MAX_MESSAGE_CHARS + 1, long.length)
    }

    // ---- định dạng ----
    @Test fun formatterHasHeaderChainAndFrameLimit() {
        val text = CrashReportFormatter.format("FATAL", "T", "main", "app: 1", RuntimeException("lỗi ngoài", boom("bên trong")), 3, 10_000)
        assertTrue(text.startsWith("=== FATAL T ==="))
        assertTrue(text.contains("app: 1") && text.contains("thread: main"))
        assertTrue(text.contains("java.lang.RuntimeException: lỗi ngoài"))
        assertTrue(text.contains("Caused by: java.lang.IllegalStateException: bên trong"))
        assertEquals(2, Regex("\\.\\.\\. \\d+ more").findAll(text).count())
    }

    @Test fun formatterSurvivesCauseCycleAndCapsLength() {
        val a = RuntimeException("a")
        val b = RuntimeException("b")
        a.initCause(b)
        b.initCause(a)
        val text = CrashReportFormatter.format("FATAL", "T", "t", "", a, 30, 300)
        assertTrue(text.length <= 300 + "\n[truncated]\n".length)
    }

    // ---- xoay vòng ----
    @Test fun rotationKeepsNewestWithinCountAndBytes() {
        val e = listOf(
            LogRotation.Entry("old", 10, 1), LogRotation.Entry("mid", 10, 2),
            LogRotation.Entry("new", 10, 3), LogRotation.Entry("newer", 10, 4),
        )
        assertEquals(setOf("old"), LogRotation.toDelete(e, 3, 1_000).toSet())
        assertEquals(setOf("old", "mid", "new"), LogRotation.toDelete(e, 3, 15).toSet())
    }

    @Test fun rotationAlwaysKeepsNewest() {
        assertEquals(listOf("a"), LogRotation.toDelete(listOf(LogRotation.Entry("a", 5, 1), LogRotation.Entry("b", 999, 2)), 3, 10))
        assertTrue(LogRotation.toDelete(emptyList(), 3, 10).isEmpty())
    }

    // ---- chống tràn ----
    @Test fun throttleMergesRepeatsAndCapsPerHour() {
        var t = 0L
        val th = NonFatalThrottle({ t }, minIntervalMs = 60_000, maxPerHour = 3)
        assertTrue(th.allow("A"))
        assertFalse(th.allow("A")) // lặp trong 1 phút
        t += 61_000
        assertTrue(th.allow("A"))
        assertTrue(th.allow("B"))
        assertFalse(th.allow("C")) // đã đủ 3 lần trong giờ
        t += CrashLogLimits.HOUR_MS
        assertTrue(th.allow("C"))
    }

    @Test fun tailKeepsNewestWholeLines() {
        assertEquals("c\nd\n", TextTail.keep("a\nb\nc\nd\n", 5))
        assertEquals("abc", TextTail.keep("abc", 10))
    }

    // ---- kho ----
    @Test fun onlyNewestCrashReportsAreKept() {
        val s = store()
        repeat(6) {
            now += 1_000
            assertTrue(s.recordFatal("app: 1", "main", boom("lần $it")))
        }
        val names = File(tmp.root, "crash_logs").list()!!.sorted()
        assertEquals(CrashLogLimits.MAX_CRASH_FILES, names.size)
        assertEquals(CrashLogLimits.MAX_CRASH_FILES, s.summary().fileCount)
        val all = s.readAll(100_000)
        assertTrue(all.contains("lần 5") && all.contains("lần 4") && all.contains("lần 3"))
        assertFalse(all.contains("lần 2"))
        assertTrue(all.indexOf("lần 5") < all.indexOf("lần 3")) // mới nhất trước
    }

    @Test fun totalSizeStaysBounded() {
        val s = store()
        val deep = RuntimeException("m".repeat(10_000))
        repeat(20) {
            now += 61_000
            s.recordFatal("h".repeat(2_000), "t", deep)
            s.recordNonFatal("h", "tag", RuntimeException("e$it".repeat(50)))
        }
        val total = File(tmp.root, "crash_logs").listFiles()!!.sumOf { it.length() }
        assertTrue("tổng $total", total <= 80_000)
        assertTrue(s.summary().fileCount <= CrashLogLimits.MAX_CRASH_FILES + 1)
    }

    @Test fun eventsFileRollsAtCap() {
        val s = store()
        repeat(60) {
            now += 10 * 60_000L // cách xa nhau để không bị chống lặp
            s.recordNonFatal("app: 1", "tag$it", IllegalArgumentException("sự cố số $it"))
        }
        val events = File(tmp.root, "crash_logs/${CrashLogStore.EVENTS_FILE}")
        assertTrue(events.length() > 0)
        assertTrue(events.readText().length <= CrashLogLimits.MAX_EVENTS_CHARS)
        assertTrue(events.readText().contains("sự cố số 59")) // phần mới nhất còn
    }

    @Test fun clearRemovesEverythingAndEmptyReadsEmpty() {
        val s = store()
        assertEquals("", s.readAll(1000))
        s.recordFatal("", "t", boom())
        s.recordNonFatal("", "t", boom())
        assertTrue(s.clear())
        assertEquals(0, s.summary().fileCount)
        assertEquals("", s.readAll(1000))
    }

    @Test fun neverThrowsWhenDirectoryCannotBeCreated() {
        val blocker = tmp.newFile("not_a_dir")
        val s = store(File(blocker, "crash_logs")) // cha là một tệp thường: mkdirs thất bại
        assertFalse(s.recordFatal("", "t", boom()))
        assertFalse(s.recordNonFatal("", "t", boom()))
        assertEquals(0, s.summary().fileCount)
        assertEquals("", s.readAll(100))
    }
}
