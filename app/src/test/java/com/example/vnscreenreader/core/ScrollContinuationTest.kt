package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Chọn phần tử kế tiếp sau khi tự cuộn một vùng. Vùng thử: trái 0, trên 200, phải 1000, dưới 1000 (cao 800; dải mép = 200).
 * Mỗi hàng cao 100 nếu không nói khác.
 */
class ScrollContinuationTest {
    private val box = intArrayOf(0, 200, 1000, 1000)

    private fun row(label: String, top: Int, height: Int = 100, left: Int = 0, right: Int = 1000) =
        ScrollItem(label, left, top, right, top + height)

    /** Các hàng liên tiếp nhãn [labels], hàng đầu ở [top]. */
    private fun rows(labels: List<String>, top: Int = 200): List<ScrollItem> =
        labels.mapIndexed { i, label -> row(label, top + i * 100) }

    private fun letters(from: Char, count: Int): List<String> = (0 until count).map { (from + it).toString() }

    @Test
    fun fullPageScrollPicksFirstNewForwardAndLastNewBackward() {
        val before = rows(letters('A', 8))
        val after = rows(letters('I', 8))
        assertEquals(0, ScrollContinuation.pick(before, after, box, forward = true))
        assertEquals(7, ScrollContinuation.pick(before, after, box, forward = false))
    }

    @Test
    fun partialScrollSkipsTheOverlapAndMeasuresTheShift() {
        val before = rows(letters('A', 8)) // A..H
        // Nội dung trượt lên 500: F, G, H còn thấy ở đầu vùng, I..M mới lộ ra.
        val after = rows(letters('F', 8))
        val match = ScrollContinuation.analyze(before, after, box, forward = true)
        assertEquals(500, match.shift)
        assertEquals(3, match.index) // "I"
        assertEquals(3, match.old)
        assertEquals(0, match.pinned)
    }

    @Test
    fun backwardScrollMirrorsForward() {
        val before = rows(letters('A', 8))
        // Nội dung trượt xuống 500: năm mục mới ở đầu vùng, A, B, C ở cuối.
        val after = rows(listOf("N1", "N2", "N3", "N4", "N5", "A", "B", "C"))
        val match = ScrollContinuation.analyze(before, after, box, forward = false)
        assertEquals(-500, match.shift)
        assertEquals(4, match.index) // mục mới sát phần đã thấy
        assertEquals(3, match.old)
    }

    @Test
    fun repeatedButtonLabelOfARowThatWasCutOffIsNotSkipped() {
        // Mỗi bài có tiêu đề riêng và một nút "Thích" dùng chung chữ. Bài 4 trước đó chỉ lộ tiêu đề (nút nằm dưới mép), sau khi
        // cuộn mới thấy nút của nó. Cách cũ chỉ so nhãn sẽ coi "Thích" là đã thấy và bỏ qua nút này.
        val before = listOf(
            row("Bài 1", 200), row("Thích", 300), row("Bài 2", 400), row("Thích", 500),
            row("Bài 3", 600), row("Thích", 700), row("Bài 4", 800),
        )
        val after = listOf(
            row("Bài 3", 200), row("Thích", 300), row("Bài 4", 400), row("Thích", 500),
            row("Bài 5", 600), row("Thích", 700), row("Bài 6", 800),
        )
        val match = ScrollContinuation.analyze(before, after, box, forward = true)
        assertEquals(400, match.shift)
        assertEquals(3, match.index) // nút "Thích" ở y=500 là của bài 4, lần đầu lộ ra
        assertEquals(3, match.old)
        assertEquals(0, match.pinned)
    }

    @Test
    fun duplicatePairsDoNotDistortTheShift() {
        val before = listOf(
            row("A", 200), row("B", 300), row("C", 400), row("x", 500), row("D", 600), row("x", 700), row("E", 800),
        )
        val after = listOf(
            row("C", 200), row("x", 300), row("D", 400), row("x", 500), row("E", 600), row("N1", 700), row("N2", 800),
        )
        val match = ScrollContinuation.analyze(before, after, box, forward = true)
        assertEquals(200, match.shift)
        assertEquals(5, match.index)
        assertEquals(5, match.old)
    }

    @Test
    fun stickyTopBarIsNeverPicked() {
        // Thanh "Menu" cao 50 đứng yên ở đầu vùng; nội dung bên dưới trượt lên 300.
        val before = listOf(
            row("Menu", 200, 50), row("A", 250), row("B", 350), row("C", 450),
            row("D", 550), row("E", 650), row("F", 750), row("G", 850),
        )
        val after = listOf(
            row("Menu", 200, 50), row("D", 250), row("E", 350), row("F", 450),
            row("G", 550), row("H", 650), row("I", 750), row("J", 850),
        )
        val match = ScrollContinuation.analyze(before, after, box, forward = true)
        assertEquals(300, match.shift)
        assertEquals(1, match.pinned)
        assertEquals(5, match.index) // "H", không phải "Menu"
    }

    @Test
    fun stickyBottomBarIsNotChosenGoingBackward() {
        // Lùi: mục mới lộ ra ở ĐẦU vùng; thanh dưới "Bar" đứng yên ở cuối thứ tự đọc và không được chọn thay mục mới cuối cùng.
        val before = listOf(
            row("A", 200), row("B", 300), row("C", 400), row("D", 500),
            row("E", 600), row("F", 700), row("G", 800), row("Bar", 900),
        )
        val after = listOf(
            row("N1", 200), row("N2", 300), row("N3", 400), row("A", 500),
            row("B", 600), row("C", 700), row("D", 800), row("Bar", 900),
        )
        val match = ScrollContinuation.analyze(before, after, box, forward = false)
        assertEquals(-300, match.shift)
        assertEquals(1, match.pinned)
        assertEquals(2, match.index) // "N3"
    }

    @Test
    fun clippedParagraphAtTheBottomEdgeIsAContinuationNotNew() {
        // Đoạn văn dài nằm cắt ở mép dưới; sau khi cuộn, phần còn lại của CHÍNH đoạn đó dính mép trên. Không đọc lại nó.
        val before = listOf(row("Mở đầu", 200), row("Đoạn dài", 300, 700))
        val after = listOf(row("Đoạn dài", 200, 300), row("Đoạn kế", 500))
        val match = ScrollContinuation.analyze(before, after, box, forward = true)
        assertNull(match.shift) // hai bản của đoạn văn khác chiều cao nên không dùng để đo
        assertEquals(1, match.old)
        assertEquals(1, match.index) // "Đoạn kế"
    }

    @Test
    fun clippedParagraphAtTheTopEdgeIsAContinuationGoingBackward() {
        val before = listOf(row("Đoạn dài", 200, 300), row("Đoạn kế", 500))
        val after = listOf(row("Mở đầu", 200), row("Đoạn dài", 300, 700))
        val match = ScrollContinuation.analyze(before, after, box, forward = false)
        assertEquals(1, match.old)
        assertEquals(0, match.index) // "Mở đầu"
    }

    @Test
    fun nothingMovedMeansNoNewElement() {
        val same = rows(letters('A', 8))
        val match = ScrollContinuation.analyze(same, same, box, forward = true)
        assertNull(match.index)
        assertEquals(8, match.pinned)
        assertNull(ScrollContinuation.pick(same, same, box, forward = false))
    }

    @Test
    fun aTickingClockIsNotAScroll() {
        val before = listOf(row("A", 200), row("B", 300), row("C", 400), row("12:00", 500))
        val after = listOf(row("A", 200), row("B", 300), row("C", 400), row("12:01", 500))
        assertNull(ScrollContinuation.pick(before, after, box, forward = true))
    }

    @Test
    fun unlabeledItemsAreNeverCountedAsSeen() {
        val before = listOf(row("", 200), row("", 300))
        val after = listOf(row("", 250), row("", 350))
        assertEquals(0, ScrollContinuation.pick(before, after, box, forward = true))
        assertEquals(1, ScrollContinuation.pick(before, after, box, forward = false))
    }

    @Test
    fun differentColumnsDoNotMatch() {
        // Cùng nhãn nhưng ở cột khác (lưới hai cột): không phải cùng một mục.
        val before = listOf(row("Ảnh", 200, left = 0, right = 500))
        val after = listOf(row("Ảnh", 300, left = 500, right = 1000))
        assertEquals(0, ScrollContinuation.pick(before, after, box, forward = true))
    }

    @Test
    fun emptyAndMalformedInputs() {
        assertNull(ScrollContinuation.pick(rows(letters('A', 3)), emptyList(), box, forward = true))
        assertEquals(0, ScrollContinuation.pick(emptyList(), rows(letters('A', 3)), box, forward = true))
        assertEquals(2, ScrollContinuation.pick(emptyList(), rows(letters('A', 3)), box, forward = false))
        assertEquals(ScrollMatch(null, null, 0, 0), ScrollContinuation.analyze(rows(letters('A', 3)), rows(letters('B', 3)), intArrayOf(0, 0), forward = true))
    }

    @Test
    fun largeListsAreHandledInOnePass() {
        val n = 3000
        val tall = intArrayOf(0, 0, 1000, 10 * n)
        val before = (0 until n).map { ScrollItem("L$it", 0, it * 10, 1000, it * 10 + 10) }
        // Trượt 15000px (1500 mục): nửa sau của "before" còn thấy, 1500 mục mới ở cuối.
        val after = (1500 until n + 1500).map { ScrollItem("L$it", 0, (it - 1500) * 10, 1000, (it - 1500) * 10 + 10) }
        val match = ScrollContinuation.analyze(before, after, tall, forward = true)
        assertEquals(15000, match.shift)
        assertEquals(1500, match.index) // "L3000"
        assertEquals(1500, match.old)
    }
}
