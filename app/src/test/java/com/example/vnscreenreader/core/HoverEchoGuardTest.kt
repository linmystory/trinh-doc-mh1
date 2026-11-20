package com.example.vnscreenreader.core

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverEchoGuardTest {

    @Test
    fun firstTouch_isRead() {
        assertTrue(HoverEchoGuard(250).shouldRead("A", 1_000))
    }

    @Test
    fun sameTargetRightAfter_isAnEcho() {
        val guard = HoverEchoGuard(250)
        assertTrue(guard.shouldRead("A", 1_000))
        assertFalse(guard.shouldRead("A", 1_040))
    }

    @Test
    fun windowEdge_isInclusive() {
        val guard = HoverEchoGuard(250)
        assertTrue(guard.shouldRead("A", 1_000))
        assertFalse(guard.shouldRead("A", 1_250))
        assertTrue(HoverEchoGuard(250).apply { shouldRead("A", 1_000) }.shouldRead("A", 1_251))
    }

    @Test
    fun differentTargets_areAlwaysRead_evenWhenSlidingFast() {
        // Trường hợp lỗi gốc: lướt nhanh qua nhiều phần tử (mỗi phần tử 30 ms), phần tử nào cũng phải được đọc.
        val guard = HoverEchoGuard(250)
        for (i in 0 until 10) assertTrue("phần tử $i bị bỏ", guard.shouldRead("item$i", i * 30L))
    }

    @Test
    fun alternatingTargets_areAllRead() {
        // A,B,A,B trong vài chục ms (ngón tay run ở ranh giới): mỗi lần đổi phần tử là một lần đọc mới; chỉ lặp lại CHÍNH phần tử vừa đọc mới là vọng.
        val guard = HoverEchoGuard(250)
        val order = listOf("A", "B", "A", "B", "A")
        order.forEachIndexed { i, key -> assertTrue("lần $i ($key) bị bỏ", guard.shouldRead(key, i * 20L)) }
    }

    @Test
    fun droppingAnEcho_doesNotExtendTheWindow() {
        // Bản lọc trùng chung làm mới mốc cả khi bỏ sự kiện nên ngón tay dừng rồi quay về vẫn bị nuốt; ở đây mốc chỉ ghi khi thật sự đọc.
        val guard = HoverEchoGuard(250)
        assertTrue(guard.shouldRead("A", 0))
        assertFalse(guard.shouldRead("A", 200))
        assertTrue(guard.shouldRead("A", 300))
    }

    @Test
    fun lifting_forgetsTheLastTarget() {
        val guard = HoverEchoGuard(250)
        assertTrue(guard.shouldRead("A", 0))
        guard.onLift()
        assertTrue(guard.shouldRead("A", 10))
    }

    @Test
    fun timeGoingBackwards_isNotAnEcho() {
        val guard = HoverEchoGuard(250)
        assertTrue(guard.shouldRead("A", 5_000))
        assertTrue(guard.shouldRead("A", 4_000))
    }

    @Test
    fun nodeLikeKeys_compareByEquality_notIdentity() {
        // AccessibilityNodeInfo.equals so theo nút nguồn: mỗi sự kiện mang MỘT BẢN SAO của cùng nút, vẫn phải nhận ra là cùng phần tử.
        data class FakeNode(val id: Long)
        val guard = HoverEchoGuard(250)
        assertTrue(guard.shouldRead(FakeNode(7), 0))
        assertFalse(guard.shouldRead(FakeNode(7), 30))
        assertTrue(guard.shouldRead(FakeNode(8), 60))
    }
}

class HoverStatsTest {

    private val line = Regex("^[a-z_]+=\\d+( [a-z_]+=\\d+)*$")

    @Test
    fun onlyReads_producesNoLine() {
        val stats = HoverStats()
        repeat(5) { stats.record(HoverOutcome.READ) }
        assertNull(stats.takeSummary())
    }

    @Test
    fun nothingRecorded_producesNoLine() {
        assertNull(HoverStats().takeSummary())
    }

    @Test
    fun silentOutcomes_areCountedInOrder() {
        val stats = HoverStats()
        repeat(3) { stats.record(HoverOutcome.READ) }
        repeat(2) { stats.record(HoverOutcome.UNREADABLE) }
        stats.record(HoverOutcome.ECHO)
        stats.record(HoverOutcome.NO_SOURCE)
        assertEquals("read=3 echo=1 unreadable=2 hidden=0 no_source=1 coalesced=0", stats.takeSummary())
    }

    @Test
    fun taking_resetsTheCounters() {
        val stats = HoverStats()
        stats.record(HoverOutcome.HIDDEN)
        assertEquals("read=0 echo=0 unreadable=0 hidden=1 no_source=0 coalesced=0", stats.takeSummary())
        assertNull(stats.takeSummary())
        stats.record(HoverOutcome.READ)
        assertNull(stats.takeSummary())
    }

    @Test
    fun coalescedEvents_areLogged() {
        // Lướt nhanh hơn tốc độ xử lý: số lần chạm bị bỏ vì đã có chạm mới hơn phải hiện ra để biết nguyên nhân "mục không được đọc".
        val stats = HoverStats()
        repeat(4) { stats.record(HoverOutcome.COALESCED) }
        stats.record(HoverOutcome.READ)
        assertEquals("read=1 echo=0 unreadable=0 hidden=0 no_source=0 coalesced=4", stats.takeSummary())
    }

    @Test
    fun summary_isNumbersOnly() {
        // Nhật ký chẩn đoán KHÔNG được chứa chữ trên màn hình: chỉ "tên=số".
        val stats = HoverStats()
        HoverOutcome.entries.forEach { stats.record(it) }
        val summary = stats.takeSummary()
        assertTrue(summary, summary != null && line.matches(summary))
    }
}

class EventAdmissionTest {

    // Khóa y như sự kiện chạm của Compose/web/ứng dụng tự vẽ: không id view, chữ sự kiện rỗng (xem ReaderEvent.dedupKey).
    private val noIdKey = "${AccessibilityEvent.TYPE_VIEW_HOVER_ENTER}|null||-1|-1|0|0"

    @Test
    fun hoverEvents_withIdenticalKeys_areAllAdmitted() {
        // Lỗi gốc: lướt nhanh (mỗi phần tử 50 ms, dưới cửa sổ 120 ms) chỉ phần tử đầu được xử lý; ngón tay dừng ở phần tử bị nuốt.
        val dedup = EventDeduplicator(120)
        for (i in 0 until 10) {
            assertTrue("sự kiện chạm thứ $i bị rơi", dedup.admit(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER, noIdKey, i * 50L))
        }
    }

    @Test
    fun otherEvents_withIdenticalKeys_areStillDeduplicated() {
        val dedup = EventDeduplicator(120)
        val key = "${AccessibilityEvent.TYPE_VIEW_FOCUSED}|null||-1|-1|0|0"
        assertTrue(dedup.admit(AccessibilityEvent.TYPE_VIEW_FOCUSED, key, 0))
        assertFalse(dedup.admit(AccessibilityEvent.TYPE_VIEW_FOCUSED, key, 50))
        assertFalse(dedup.admit(AccessibilityEvent.TYPE_VIEW_FOCUSED, key, 100))
    }

    @Test
    fun hover_doesNotDisturbTheMemoryOfOtherEvents() {
        val dedup = EventDeduplicator(120)
        val key = "${AccessibilityEvent.TYPE_TOUCH_INTERACTION_END}|null||-1|-1|0|0"
        assertTrue(dedup.admit(AccessibilityEvent.TYPE_TOUCH_INTERACTION_END, key, 0))
        // Cả một loạt sự kiện chạm xen vào (kể cả cùng chuỗi khóa) không làm mới hay xóa mốc của sự kiện khác.
        for (i in 1..30) dedup.admit(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER, key, i * 3L)
        assertFalse(dedup.admit(AccessibilityEvent.TYPE_TOUCH_INTERACTION_END, key, 100))
        assertTrue(dedup.admit(AccessibilityEvent.TYPE_TOUCH_INTERACTION_END, key, 300))
    }

    @Test
    fun onlyHoverEnter_isExempt() {
        assertTrue(EventDedupPolicy.exempt(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER))
        val others = listOf(
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
        )
        for (type in others) assertFalse("loại $type không được miễn lọc trùng", EventDedupPolicy.exempt(type))
    }
}
