package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bám vết tiêu điểm khi giao diện vẽ lại / nạp thêm dữ liệu: không được quay về mốc 0. */
class FocusTraceTest {

    private class Item(
        val label: String,
        val box: IntArray = intArrayOf(0, 0, 100, 50),
        val uid: String? = null,
        val viewId: String? = null,
        val index: Int = FocusTrace.NO_INDEX,
    )

    private fun trace(label: String, box: IntArray = intArrayOf(0, 0, 100, 50), uid: String? = null, viewId: String? = null, index: Int = -1, window: Int = 1) =
        FocusTrace(window, uid, viewId, label, box, index)

    private fun locate(traces: List<FocusTrace>, items: List<Item>): Int = TraceLocator.locate(
        traces = traces,
        count = items.size,
        uniqueIdOf = { items[it].uid },
        viewIdOf = { items[it].viewId },
        labelOf = { items[it].label },
        boxOf = { items[it].box },
        itemIndexOf = { items[it].index },
    )

    @Test fun uniqueIdBeatsLabel() {
        val items = listOf(Item("Bài 7", uid = "b"), Item("Khác", uid = "a"))
        assertEquals(1, locate(listOf(trace("Bài 7", uid = "a")), items))
    }

    @Test fun sameLabelFoundAfterListShifts() {
        // Nạp thêm 3 mục phía trên: "Bài 7" từ vị trí 4 xuống vị trí 7, hộp tọa độ đã khác hẳn.
        val items = (0 until 10).map { Item("Bài $it", intArrayOf(0, it * 60, 100, it * 60 + 50)) }
        assertEquals(7, locate(listOf(trace("Bài 7", intArrayOf(0, 240, 100, 290))), items))
    }

    @Test fun duplicateLabelsPickNearestBox() {
        val items = listOf(
            Item("Thêm vào giỏ", intArrayOf(0, 0, 100, 50)),
            Item("Thêm vào giỏ", intArrayOf(0, 500, 100, 550)),
        )
        assertEquals(1, locate(listOf(trace("Thêm vào giỏ", intArrayOf(0, 490, 100, 540))), items))
    }

    @Test fun duplicateLabelsPreferSameListIndex() {
        val items = listOf(
            Item("Thêm vào giỏ", intArrayOf(0, 0, 100, 50), index = 3),
            Item("Thêm vào giỏ", intArrayOf(0, 500, 100, 550), index = 4),
        )
        // Hộp cũ gần mục đầu hơn, nhưng chỉ số danh sách cũ là 4: chỉ số có ưu tiên cao hơn khoảng cách.
        assertEquals(1, locate(listOf(trace("Thêm vào giỏ", intArrayOf(0, 10, 100, 60), index = 4)), items))
    }

    @Test fun blankLabelFallsBackToViewId() {
        val items = listOf(Item("", viewId = "a:id/x"), Item("", viewId = "a:id/menu"))
        assertEquals(1, locate(listOf(trace("", viewId = "a:id/menu")), items))
    }

    @Test fun noStrongMatchReturnsMinusOne() {
        assertEquals(-1, locate(listOf(trace("Đã biến mất")), listOf(Item("A"), Item("B"))))
        assertEquals(-1, locate(emptyList(), listOf(Item("A"))))
    }

    @Test fun olderTraceUsedWhenNewestVanished() {
        val traces = listOf(trace("Đã biến mất"), trace("B"))
        assertEquals(1, locate(traces, listOf(Item("A"), Item("B"))))
    }

    @Test fun bufferReplacesSameElementInsteadOfDuplicating() {
        val buffer = FocusTraceBuffer()
        buffer.push(trace("A", intArrayOf(0, 0, 10, 10)))
        buffer.push(trace("A", intArrayOf(0, 90, 10, 100))) // cùng phần tử, đã cuộn
        assertEquals(1, buffer.size)
        assertEquals(90, buffer.latest()!!.box[1])
    }

    @Test fun bufferKeepsOnlyCapacityNewestFirst() {
        val buffer = FocusTraceBuffer(capacity = 3)
        listOf("A", "B", "C", "D", "E").forEach { buffer.push(trace(it)) }
        assertEquals(listOf("E", "D", "C"), buffer.newestFirst().map { it.label })
    }

    @Test fun bufferDropsOtherWindows() {
        val buffer = FocusTraceBuffer()
        buffer.push(trace("A", window = 1))
        buffer.push(trace("B", window = 2))
        buffer.keepWindow(2)
        assertEquals(listOf("B"), buffer.newestFirst().map { it.label })
        buffer.clear()
        assertNull(buffer.latest())
    }
}
