package com.example.vnscreenreader.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class HoverPolicyTest {

    @Test
    fun emptyLayout_isNotRead() {
        assertFalse(HoverPolicy.isReadable(NodeSnapshot(className = "android.widget.LinearLayout")))
    }

    @Test
    fun blankLabels_areTreatedAsEmpty() {
        val node = NodeSnapshot(text = "  ", contentDescription = "", hintText = "\n")
        assertFalse(HoverPolicy.isReadable(node))
    }

    @Test
    fun anyLabel_isRead() {
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(text = "Gửi")))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(contentDescription = "Đóng")))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(hintText = "Nhập email")))
    }

    @Test
    fun interactiveWithoutLabel_isRead() {
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(isClickable = true)))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(isEditable = true)))
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(isCheckable = true)))
    }

    @Test
    fun password_isReadableButNeverExposesContent() {
        val node = NodeSnapshot(text = "bimat123", isPassword = true, isEditable = true)
        assertTrue(HoverPolicy.isReadable(node))
        assertTrue(SpeechTextBuilder.buildNodeDescription(node) == "Ô nhập mật khẩu")
    }

    // ---- Khớp với điểm dừng khi vuốt ----

    @Test
    fun rangeWithoutLabel_isRead() {
        // Thanh trượt/thanh tiến trình không nhãn: vuốt tới được ([StopSelection]) thì chạm vào cũng phải nghe được.
        val slider = NodeSnapshot(className = "android.widget.SeekBar", hasRange = true, rangePercent = 40)
        assertTrue(HoverPolicy.isReadable(slider))
        assertTrue(SpeechTextBuilder.buildNodeDescription(slider).isNotBlank())
        // Khoảng giá trị rỗng (rangePercent null) vẫn là một thanh có giá trị.
        assertTrue(HoverPolicy.isReadable(NodeSnapshot(className = "android.widget.ProgressBar", hasRange = true)))
    }

    @Test
    fun screenReaderFocusableWithoutLabel_needsMergedTextCheck() {
        assertTrue(HoverPolicy.needsMergedText(NodeSnapshot(isScreenReaderFocusable = true)))
        // Đã có chữ/thao tác/khoảng giá trị thì đọc thẳng, không cần tra chữ gộp.
        assertFalse(HoverPolicy.needsMergedText(NodeSnapshot(isScreenReaderFocusable = true, text = "Thẻ")))
        assertFalse(HoverPolicy.needsMergedText(NodeSnapshot(isScreenReaderFocusable = true, isClickable = true)))
        assertFalse(HoverPolicy.needsMergedText(NodeSnapshot(isScreenReaderFocusable = true, hasRange = true)))
        // Khung thường không phải khối đọc.
        assertFalse(HoverPolicy.needsMergedText(NodeSnapshot(className = "android.widget.LinearLayout")))
    }

    @Test
    fun ancestor_isOnlyAClickableOne() {
        assertTrue(HoverPolicy.isReadableAncestor(NodeSnapshot(isClickable = true)))
        // Khung có nhãn nhưng không bấm được, khối đọc, thanh trượt: không đọc thay cho con không nhãn (tránh đọc nhãn cả danh sách
        // mỗi lần ngón tay lướt qua khoảng trống).
        assertFalse(HoverPolicy.isReadableAncestor(NodeSnapshot(contentDescription = "Danh sách")))
        assertFalse(HoverPolicy.isReadableAncestor(NodeSnapshot(isScreenReaderFocusable = true)))
        assertFalse(HoverPolicy.isReadableAncestor(NodeSnapshot(hasRange = true)))
    }

    @Test
    fun layoutOnlyNodes_stayQuiet() {
        // Không chữ, không thao tác, không khoảng giá trị: khoảng trống giữa các phần tử không được đọc ra "không có nhãn".
        val layout = NodeSnapshot(className = "android.widget.FrameLayout")
        assertFalse(HoverPolicy.isReadable(layout))
        assertFalse(HoverPolicy.needsMergedText(layout))
    }

    // ---- Tính chất trên cây ngẫu nhiên ----

    private fun randomFacts(r: Random): StopFacts {
        val range = r.nextInt(100) < 5
        return StopFacts(
            hasLabel = r.nextInt(100) < 35,
            hasAuxLabel = r.nextInt(100) < 5,
            clickable = r.nextInt(100) < 20,
            screenReaderFocusable = r.nextInt(100) < 8,
            checkable = r.nextInt(100) < 5,
            editable = r.nextInt(100) < 4,
            range = range,
            adjustable = range && r.nextBoolean(),
            structural = r.nextInt(100) < 10,
        )
    }

    /** Ảnh chụp mà ngón tay thấy khi chạm vào đúng nút có sự thật [f] (nhãn/gợi ý/thao tác/khoảng giá trị/khối đọc). */
    private fun snapshotOf(f: StopFacts) = NodeSnapshot(
        text = if (f.hasLabel) "nhãn" else null,
        hintText = if (f.hasAuxLabel) "gợi ý" else null,
        isClickable = f.clickable,
        isCheckable = f.checkable,
        isEditable = f.editable,
        hasRange = f.range,
        isScreenReaderFocusable = f.screenReaderFocusable,
    )

    private class Flat(val parent: IntArray, val facts: List<StopFacts>)

    /** Cây ngẫu nhiên theo tiền thứ tự: cha của nút i là một tổ tiên (hoặc chính) của nút i-1, nên `parent[i] < i`. */
    private fun randomFlat(r: Random, size: Int): Flat {
        val parent = IntArray(size)
        parent[0] = -1
        for (i in 1 until size) {
            var p = i - 1
            var up = r.nextInt(4)
            while (up > 0 && parent[p] >= 0) {
                p = parent[p]
                up--
            }
            parent[i] = p
        }
        return Flat(parent, List(size) { randomFacts(r) })
    }

    @Test
    fun everySwipeStop_canAlsoBeReachedByTouch() {
        val r = Random(20261118L)
        var stopsChecked = 0
        repeat(3_000) {
            val tree = randomFlat(r, 1 + r.nextInt(40))
            for (i in StopSelection.select(tree.parent, tree.facts)) {
                val snap = snapshotOf(tree.facts[i])
                assertTrue(
                    "điểm dừng ${tree.facts[i]} vuốt tới được nhưng chạm vào thì im lặng",
                    HoverPolicy.isReadable(snap) || HoverPolicy.needsMergedText(snap),
                )
                stopsChecked++
            }
        }
        // Bảo đảm phép thử thật sự có việc làm (không rỗng vì lỗi sinh cây).
        assertTrue("chỉ kiểm được $stopsChecked điểm dừng", stopsChecked > 5_000)
    }
}
