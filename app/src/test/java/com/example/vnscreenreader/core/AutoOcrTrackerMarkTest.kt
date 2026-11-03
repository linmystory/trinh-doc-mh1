package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tự đọc ảnh: chỉ coi là "đã đọc" sau khi người dùng dừng đủ lâu; vuốt nhanh qua ảnh không làm mất lần đọc. */
class AutoOcrTrackerMarkTest {

    @Test
    fun isDone_doesNotRecordAnything() {
        val tracker = AutoOcrTracker()
        assertFalse(tracker.isDone("w1", "a"))
        assertFalse(tracker.isDone("w1", "a")) // gọi lại vẫn chưa đọc: chỉ ĐỌC trạng thái
        assertTrue(tracker.tryStart("w1", "a")) // nên lần đầu thật sự vẫn được phép
    }

    @Test
    fun cancelledQuickPass_leavesTheElementReadable() {
        val tracker = AutoOcrTracker()
        // Chọn ảnh: kiểm tra chưa đọc -> bắt đầu chờ -> bị hủy (vuốt đi) => KHÔNG gọi markDone.
        assertFalse(tracker.isDone("w1", "photo"))
        // Chọn lại: vẫn được phép đọc.
        assertFalse(tracker.isDone("w1", "photo"))
        tracker.markDone("w1", "photo") // lần này người dùng dừng đủ lâu
        assertTrue(tracker.isDone("w1", "photo"))
    }

    @Test
    fun markDone_isPerWindow_andClearForgetsEverything() {
        val tracker = AutoOcrTracker()
        tracker.markDone("w1", "a")
        assertTrue(tracker.isDone("w1", "a"))
        assertFalse(tracker.isDone("w2", "a")) // cửa sổ khác
        tracker.clear() // đổi màn hình/cửa sổ
        assertFalse(tracker.isDone("w1", "a"))
    }

    @Test
    fun switchingWindow_forgetsTheOldOnes() {
        val tracker = AutoOcrTracker()
        tracker.markDone("w1", "a")
        tracker.markDone("w2", "b")
        assertFalse(tracker.isDone("w1", "a"))
        assertTrue(tracker.isDone("w2", "b"))
    }

    @Test
    fun memoryIsBounded() {
        val tracker = AutoOcrTracker()
        for (i in 1..500) tracker.markDone("w", "k$i")
        assertFalse(tracker.isDone("w", "k1")) // khóa cũ nhất bị bỏ
        assertTrue(tracker.isDone("w", "k500"))
    }
}
