package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vị trí con trỏ thật khi vuốt xuống/lên trong ô nhập: tiến tới cuối đơn vị kế, lùi tới đầu đơn vị trước. */
class CaretMathTest {

    private val C = TextGranularity.CHARACTER
    private val W = TextGranularity.WORD
    private val S = TextGranularity.SENTENCE
    private val P = TextGranularity.PARAGRAPH

    private fun target(text: String, caret: Int, unit: TextGranularity, forward: Boolean) =
        CaretMath.target(text, caret, unit, forward)

    @Test
    fun character_forwardAndBackward() {
        assertEquals(1, target("abc", 0, C, true))
        assertEquals(2, target("abc", 1, C, true))
        assertEquals(3, target("abc", 2, C, true))
        assertNull(target("abc", 3, C, true)) // cuối chữ
        assertEquals(2, target("abc", 3, C, false))
        assertEquals(0, target("abc", 1, C, false))
        assertNull(target("abc", 0, C, false)) // đầu chữ
    }

    @Test
    fun word_forwardLandsOnTheEndOfTheNextWord_backwardOnTheStartOfThePreviousOne() {
        val text = "một hai ba"
        assertEquals(3, target(text, 0, W, true)) // cuối "một"
        assertEquals(7, target(text, 3, W, true)) // từ dấu cách tới cuối "hai"
        assertEquals(10, target(text, 7, W, true))
        assertNull(target(text, 10, W, true))
        assertEquals(8, target(text, 10, W, false)) // đầu "ba"
        assertEquals(4, target(text, 8, W, false)) // đầu "hai"
        assertEquals(0, target(text, 4, W, false))
        assertNull(target(text, 0, W, false))
    }

    @Test
    fun word_fromTheMiddleOfAWord_goesToItsEnd_orItsStart() {
        val text = "xin chào"
        assertEquals(3, target(text, 1, W, true)) // giữa "xin" -> cuối "xin"
        assertEquals(0, target(text, 1, W, false)) // giữa "xin" -> đầu "xin"
        assertEquals(8, target(text, 6, W, true)) // giữa "chào" -> cuối "chào"
        assertEquals(4, target(text, 6, W, false)) // giữa "chào" -> đầu "chào"
    }

    @Test
    fun sentence_andParagraph_moveByTheirOwnBoundaries() {
        val text = "Một hai. Ba bốn năm."
        assertEquals(8, target(text, 0, S, true)) // hết câu đầu (tính cả dấu chấm, chưa tính dấu cách)
        assertEquals(20, target(text, 8, S, true))
        assertEquals(9, target(text, 20, S, false)) // đầu câu hai (sau dấu cách)
        assertEquals(0, target(text, 9, S, false))
        // Đơn vị đã bỏ khoảng trắng hai đầu, nên dấu xuống dòng nằm NGOÀI đoạn: con trỏ dừng ngay trước nó.
        val paragraphs = "Dòng một\nDòng hai"
        assertEquals(8, target(paragraphs, 0, P, true)) // cuối đoạn một, trước dấu xuống dòng
        assertEquals(17, target(paragraphs, 8, P, true))
        assertEquals(17, target(paragraphs, 9, P, true))
        assertNull(target(paragraphs, 17, P, true))
        assertEquals(9, target(paragraphs, 17, P, false)) // đầu đoạn hai
        assertEquals(0, target(paragraphs, 9, P, false))
        assertEquals("Dòng hai", CaretMath.traversed(paragraphs, 17, 9))
    }

    @Test
    fun emptyText_hasNowhereToGo() {
        TextGranularity.entries.forEach {
            assertNull(target("", 0, it, true))
            assertNull(target("", 0, it, false))
        }
    }

    @Test
    fun outOfRangeCaret_isClampedInsteadOfCrashing() {
        assertEquals(1, target("abc", -5, C, true)) // âm: coi như đầu chữ
        assertNull(target("abc", -5, C, false))
        assertNull(target("abc", 99, C, true)) // quá cuối: coi như cuối chữ
        assertEquals(2, target("abc", 99, C, false))
    }

    @Test
    fun character_neverSplitsAGraphemeCluster() {
        val text = "áb" // "á" viết dạng tổ hợp (a + dấu sắc), rồi "b"
        assertEquals(2, target(text, 0, C, true)) // cả "á" là một ký tự
        assertEquals(3, target(text, 2, C, true))
        assertEquals(2, target(text, 3, C, false))
        assertEquals(0, target(text, 2, C, false))
        val emoji = "x😀y"
        assertEquals(1, target(emoji, 0, C, true))
        assertEquals(3, target(emoji, 1, C, true)) // emoji gồm hai đơn vị UTF-16, đi qua một lần
        assertEquals(1, target(emoji, 3, C, false))
    }

    @Test
    fun walkingForwardThenBackward_visitsEveryUnitAndComesBack() {
        val text = "Xin chào các bạn."
        for (unit in TextGranularity.entries) {
            val forward = ArrayList<Int>()
            var caret = 0
            while (true) {
                caret = target(text, caret, unit, true) ?: break
                forward += caret
            }
            assertEquals("${unit.name}: kết thúc ở cuối chữ", text.length, forward.last())
            // Đi ngược lại: mỗi bước lùi tới đầu một đơn vị và không bao giờ đứng yên (tránh vòng lặp vô hạn).
            var back = text.length
            var steps = 0
            while (true) {
                val next = target(text, back, unit, false) ?: break
                assertTrue("${unit.name}: phải lùi thật", next < back)
                back = next
                steps++
                assertTrue(steps <= text.length)
            }
            assertEquals("${unit.name}: kết thúc ở đầu chữ", 0, back)
        }
    }

    @Test
    fun traversed_isTheTextBetweenTwoPositions_inAnyOrder() {
        assertEquals("hai", CaretMath.traversed("một hai ba", 4, 7))
        assertEquals("hai", CaretMath.traversed("một hai ba", 7, 4))
        assertEquals(" hai", CaretMath.traversed("một hai ba", 3, 7))
        assertEquals("", CaretMath.traversed("abc", 2, 2))
        assertEquals("bc", CaretMath.traversed("abc", 1, 99)) // kẹp vào độ dài chữ
        assertEquals("a", CaretMath.traversed("abc", -3, 1))
    }

    @Test
    fun aMoveAlwaysTravelsAtLeastOneCharacter_soTheSpokenTextIsNeverEmpty() {
        val text = "ab cd\nef"
        for (unit in TextGranularity.entries) for (forward in listOf(true, false)) {
            for (caret in 0..text.length) {
                val to = target(text, caret, unit, forward) ?: continue
                assertTrue(CaretMath.traversed(text, caret, to).isNotEmpty())
            }
        }
    }
}
