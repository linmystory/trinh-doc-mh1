package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StopSelectionTest {

    /** Nút của cây thử: tên (để so kết quả), sự thật cục bộ, các con theo thứ tự đọc. */
    private class N(val name: String, val facts: StopFacts, val kids: List<N> = emptyList())

    private class Flat(val names: List<String>, val parent: IntArray, val facts: List<StopFacts>)

    private fun flatten(root: N): Flat {
        val names = ArrayList<String>()
        val parents = ArrayList<Int>()
        val facts = ArrayList<StopFacts>()
        fun walk(n: N, p: Int) {
            val i = names.size
            names += n.name
            parents += p
            facts += n.facts
            for (k in n.kids) walk(k, i)
        }
        walk(root, -1)
        return Flat(names, parents.toIntArray(), facts)
    }

    private fun stops(root: N): List<String> {
        val f = flatten(root)
        return StopSelection.select(f.parent, f.facts).map { f.names[it] }
    }

    /** Chữ gộp vào nhãn của [root] khi nó là gốc của cây con (đúng cách [NodeCollector.mergedLabel] dựng). */
    private fun merged(root: N, everything: Boolean = false): List<String> {
        val f = flatten(root)
        return StopSelection.mergeParts(f.parent, f.facts, everything).map { f.names[it] }
    }

    private fun layout(name: String, vararg kids: N) = N(name, StopFacts(), kids.toList())
    private fun text(name: String) = N(name, StopFacts(hasLabel = true))
    private fun group(name: String, vararg kids: N) = N(name, StopFacts(hasLabel = true), kids.toList())
    private fun card(name: String, vararg kids: N) = N(name, StopFacts(clickable = true), kids.toList())
    private fun button(name: String, vararg kids: N) = N(name, StopFacts(hasLabel = true, clickable = true), kids.toList())
    private fun icon(name: String) = N(name, StopFacts(clickable = true))
    private fun list(name: String, vararg kids: N) = N(name, StopFacts(structural = true), kids.toList())
    private fun labeledList(name: String, vararg kids: N) = N(name, StopFacts(hasLabel = true, structural = true), kids.toList())
    private fun unit(name: String, vararg kids: N) = N(name, StopFacts(screenReaderFocusable = true), kids.toList())
    private fun labeledUnit(name: String, vararg kids: N) = N(name, StopFacts(hasLabel = true, screenReaderFocusable = true), kids.toList())
    private fun slider(name: String) = N(name, StopFacts(range = true, adjustable = true))
    private fun progress(name: String) = N(name, StopFacts(range = true))
    private fun hintOnly(name: String) = N(name, StopFacts(hasAuxLabel = true))
    private fun input(name: String) = N(name, StopFacts(editable = true, clickable = true))
    private fun box(name: String) = N(name, StopFacts(checkable = true, clickable = true))

    private fun texts(count: Int): Array<N> = Array(count) { text("t${it + 1}") }

    // ---- Quy tắc cơ bản ----

    @Test
    fun emptyAndSingleTrees() {
        assertEquals(emptyList<Int>(), StopSelection.select(IntArray(0), emptyList()).toList())
        assertEquals(emptyList<String>(), stops(layout("root")))
        assertEquals(listOf("root"), stops(text("root")))
        assertEquals(emptyList<String>(), merged(layout("root")))
    }

    @Test
    fun plainLayoutIsNotAStopButItsTextsAre() {
        assertEquals(listOf("a", "b"), stops(layout("root", layout("row", text("a")), text("b"))))
    }

    // ---- Lỗi chính: khung có chữ nhưng không thao tác làm mất chữ bên dưới ----

    @Test
    fun labeledNonActionableContainerKeepsItsChildren() {
        // contentDescription trên khung bố cục/danh sách/WebView: trước đây nuốt hết chữ của các con.
        val root = layout("root", group("section", text("a"), text("b")))
        assertEquals(listOf("section", "a", "b"), stops(root))
    }

    @Test
    fun labeledListDoesNotHideItsRows() {
        val root = layout("root", labeledList("songs", layout("row1", text("a")), layout("row2", text("b"))))
        assertEquals(listOf("songs", "a", "b"), stops(root))
    }

    @Test
    fun deepLabeledNestingKeepsEveryText() {
        val root = layout("root", group("g1", group("g2", text("a")), text("b")), text("c"))
        assertEquals(listOf("g1", "g2", "a", "b", "c"), stops(root))
    }

    // ---- Khung bấm được không nhãn: gộp chữ, nhưng không mất ----

    @Test
    fun unlabeledClickableCardMergesItsTexts() {
        val root = layout("root", card("card", layout("col", text("t1"), text("t2"))))
        assertEquals(listOf("card"), stops(root))
        assertEquals(listOf("t1", "t2"), merged(card("card", layout("col", text("t1"), text("t2")))))
    }

    @Test
    fun cardKeepsNestedLabeledButtonAsItsOwnStopAndDoesNotReadItTwice() {
        val tree = card("card", text("t1"), button("like"))
        assertEquals(listOf("card", "like"), stops(layout("root", tree)))
        assertEquals(listOf("t1"), merged(tree))
        // Mặc định chỉ gộp phần thẻ đọc thay cho mình; "mọi chữ" (vùng thông báo) thì lấy cả nút.
        assertEquals(listOf("t1", "like"), merged(tree, everything = true))
    }

    @Test
    fun nestedClickableWrapperWithTextIsReachable() {
        // Nút "Thích" kiểu LinearLayout bấm được > [biểu tượng, chữ] nằm trong thẻ bấm được: phải tới được, không bị thẻ nuốt.
        val like = card("like", icon2(), text("Thích"))
        val tree = card("card", text("t1"), like)
        assertEquals(listOf("card", "like"), stops(layout("root", tree)))
        assertEquals(listOf("t1"), merged(tree))
        assertEquals(listOf("Thích"), merged(like))
    }

    private fun icon2() = N("img", StopFacts())

    @Test
    fun nestedUnlabeledIconButtonIsReachable() {
        val tree = card("card", text("t1"), icon("more"))
        assertEquals(listOf("card", "more"), stops(layout("root", tree)))
    }

    @Test
    fun unlabeledButtonWithNothingInsideIsAStop() {
        assertEquals(listOf("btn"), stops(layout("root", icon("btn"))))
    }

    @Test
    fun clickableWrapperAroundButtonsIsOnlyLayout() {
        val root = layout("root", card("wrapper", button("a"), button("b")))
        assertEquals(listOf("a", "b"), stops(root))
    }

    @Test
    fun clickableWrapperAroundAnInputIsOnlyLayout() {
        assertEquals(listOf("field"), stops(layout("root", card("wrapper", input("field")))))
    }

    @Test
    fun clickableWrapperAroundLabeledListIsOnlyLayout() {
        // Khung bấm được bọc một danh sách có chữ: không có chữ gộp được, nhưng bên dưới có nội dung nên không phải "nút không nhãn".
        val root = layout("root", card("wrapper", list("list", text("a"))))
        assertEquals(listOf("a"), stops(root))
    }

    @Test
    fun hugeClickableRootIsOnlyLayout() {
        // Khung gốc bấm được cả màn hình: trước đây chỉ đọc 6 chữ đầu rồi mất phần còn lại.
        val root = card("root", *texts(20))
        assertEquals((1..20).map { "t$it" }, stops(root))
    }

    @Test
    fun mergeLimitBoundary() {
        val atLimit = card("card", *texts(StopSelection.MERGE_MAX_PARTS))
        assertEquals(listOf("card"), stops(layout("root", atLimit)))
        val over = card("card", *texts(StopSelection.MERGE_MAX_PARTS + 1))
        assertEquals((1..StopSelection.MERGE_MAX_PARTS + 1).map { "t$it" }, stops(layout("root", over)))
    }

    @Test
    fun overflowPropagatesToOuterWrappers() {
        val root = layout("root", card("outer", layout("mid", card("inner", *texts(13)))))
        assertEquals((1..13).map { "t$it" }, stops(root))
    }

    @Test
    fun cardsKeepTheirOwnTextsInsideAHugeRoot() {
        val rows = Array(5) { i -> card("row$i", text("a$i"), text("b$i")) }
        // Chữ của các hàng con (điểm dừng riêng) không tính vào khung gốc: 10 chữ lẻ còn gộp được thành một điểm dừng...
        assertEquals(listOf("root") + (0..4).map { "row$it" }, stops(card("root", *rows, *texts(10))))
        // ...còn 13 chữ lẻ thì khung gốc chỉ là bố cục; các hàng vẫn giữ nguyên là điểm dừng riêng.
        assertEquals((0..4).map { "row$it" } + (1..13).map { "t$it" }, stops(card("root", *rows, *texts(13))))
    }

    @Test
    fun labeledClickableContainerIsOneUnit() {
        // Khung bấm được CÓ nhãn là một khối: chữ không thao tác bên dưới không đọc riêng (giống nút thường).
        val root = layout("root", button("buy", text("Mua"), text("ngay")))
        assertEquals(listOf("buy"), stops(root))
    }

    @Test
    fun labeledClickableContainerStillExposesNestedControls() {
        val root = layout("root", button("row", text("x"), button("del"), box("tick")))
        assertEquals(listOf("row", "del", "tick"), stops(root))
    }

    // ---- Ô nhập, công tắc, thanh trượt, tiến trình, gợi ý ----

    @Test
    fun editableAndCheckableAreAlwaysReachableEvenInsideACard() {
        val root = layout("root", card("card", text("t1"), input("field"), box("tick")))
        assertEquals(listOf("card", "field", "tick"), stops(root))
    }

    @Test
    fun scrollingMultilineInputIsStillReachable() {
        val field = N("field", StopFacts(editable = true, structural = true))
        assertEquals(listOf("field"), stops(layout("root", field)))
    }

    @Test
    fun unlabeledSliderIsReachableAtTopLevelAndInsideACard() {
        assertEquals(listOf("vol"), stops(layout("root", slider("vol"))))
        assertEquals(listOf("card", "vol"), stops(layout("root", card("card", text("Âm lượng"), slider("vol")))))
    }

    @Test
    fun progressBarIsReadOnlyWhenNotMergedIntoACard() {
        assertEquals(listOf("bar"), stops(layout("root", progress("bar"))))
        assertEquals(listOf("card"), stops(layout("root", card("card", text("Tải xuống"), progress("bar")))))
    }

    @Test
    fun hintOrTooltipOnlyViewIsAStopWhenNotMerged() {
        assertEquals(listOf("hint"), stops(layout("root", hintOnly("hint"))))
        assertEquals(listOf("card"), stops(layout("root", card("card", text("t1"), hintOnly("hint")))))
    }

    // ---- Ranh giới cuộn / danh sách ----

    @Test
    fun clickableScrollContainerIsNeverAConsumer() {
        val big = N("list", StopFacts(hasLabel = true, clickable = true, structural = true), listOf(text("a"), text("b")))
        assertEquals(listOf("list", "a", "b"), stops(layout("root", big)))
        val unlabeled = N("list", StopFacts(clickable = true, structural = true), listOf(text("a"), text("b")))
        assertEquals(listOf("a", "b"), stops(layout("root", unlabeled)))
    }

    @Test
    fun scrollContainerInsideACardIsAnIndependentBoundary() {
        val tree = card("card", text("t1"), list("carousel", text("c1"), text("c2")))
        assertEquals(listOf("card", "c1", "c2"), stops(layout("root", tree)))
        assertEquals(listOf("t1"), merged(tree))
    }

    @Test
    fun labeledScrollTextInsideACardIsItsOwnStop() {
        val tree = card("card", text("t1"), labeledList("desc"))
        assertEquals(listOf("card", "desc"), stops(layout("root", tree)))
    }

    // ---- Khối tự khai báo (screen-reader-focusable) ----

    @Test
    fun labeledUnitHidesItsPlainTextsButNotItsControls() {
        val root = layout("root", labeledUnit("post", text("a"), text("b"), button("share")))
        assertEquals(listOf("post", "share"), stops(root))
    }

    @Test
    fun unlabeledUnitMergesItsTexts() {
        val tree = unit("post", text("a"), layout("col", text("b")))
        assertEquals(listOf("post"), stops(layout("root", tree)))
        assertEquals(listOf("a", "b"), merged(tree))
    }

    @Test
    fun unitInsideACardIsMergedNotAStop() {
        val root = layout("root", card("card", unit("post", text("a")), text("b")))
        assertEquals(listOf("card"), stops(root))
    }

    @Test
    fun emptyUnitAroundNothingIsAnUnlabeledStopButAroundControlsIsLayout() {
        assertEquals(listOf("u"), stops(layout("root", unit("u"))))
        assertEquals(listOf("go"), stops(layout("root", unit("u", button("go")))))
    }

    @Test
    fun hugeUnitIsOnlyLayout() {
        assertEquals((1..13).map { "t$it" }, stops(layout("root", unit("u", *texts(13)))))
    }

    // ---- Gộp chữ ----

    @Test
    fun labeledNonInteractiveChildInsideACardIsOnePartAndHidesItsOwnChildren() {
        val tree = card("card", group("price", text("10"), text("đ")), text("name"))
        assertEquals(listOf("price", "name"), merged(tree))
        assertEquals(listOf("card"), stops(layout("root", tree)))
    }

    @Test
    fun mergeKeepsReadingOrder() {
        val tree = card("card", layout("a", text("1"), layout("b", text("2"))), text("3"))
        assertEquals(listOf("1", "2", "3"), merged(tree))
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

    private fun randomTree(r: Random, budget: IntArray, depth: Int): N {
        budget[0]--
        val kids = ArrayList<N>()
        if (depth < 6) {
            val count = r.nextInt(5)
            for (i in 0 until count) if (budget[0] > 0) kids += randomTree(r, budget, depth + 1)
        }
        return N("n", randomFacts(r), kids)
    }

    private fun subtreeEnd(parent: IntArray, s: Int): Int {
        var j = s + 1
        while (j < parent.size && parent[j] >= s) j++
        return j
    }

    private fun slice(f: Flat, s: Int): Flat {
        val e = subtreeEnd(f.parent, s)
        val parent = IntArray(e - s) { k -> if (k == 0) -1 else f.parent[s + k] - s }
        return Flat(f.names.subList(s, e), parent, f.facts.subList(s, e))
    }

    @Test
    fun randomTreesNeverLoseOrDuplicateText() {
        val r = Random(20261117L)
        repeat(4000) { round ->
            val f = flatten(randomTree(r, intArrayOf(80), 0))
            val n = f.facts.size
            val stops = StopSelection.select(f.parent, f.facts).toList()
            val message = "vòng $round, parent=${f.parent.toList()}"

            // 1. Chỉ số tăng dần, không trùng, nằm trong cây.
            assertEquals(message, stops.sorted().distinct(), stops)
            assertTrue(message, stops.all { it in 0 until n })

            // 2. Mỗi khối gộp (điểm dừng thao tác/tự khai báo, không phải ranh giới) lấy phần chữ của nó; phần đó không trùng điểm dừng
            //    nào và không trùng phần của khối khác (không đọc một chữ hai lần).
            val covered = HashSet<Int>(stops)
            val parts = HashSet<Int>()
            for (s in stops) {
                val fs = f.facts[s]
                if (fs.structural || !(fs.interactive || fs.screenReaderFocusable)) continue
                val sub = slice(f, s)
                val picks = StopSelection.mergeParts(sub.parent, sub.facts)
                if (!fs.hasLabel) {
                    assertTrue("$message: khối không nhãn nhiều chữ quá giới hạn (s=$s)", picks.size <= StopSelection.MERGE_MAX_PARTS)
                }
                for (k in picks) {
                    val global = s + k
                    assertTrue("$message: chữ $global vừa là điểm dừng vừa bị gộp", global !in stops)
                    assertTrue("$message: chữ $global bị gộp hai lần", parts.add(global))
                }
            }
            covered += parts

            // 3. Không mất chữ vô cớ: nút có chữ phải là điểm dừng, hoặc được gộp, hoặc nằm dưới một KHỐI có nhãn đã được đọc
            //    (nút thao tác có nhãn, khối tự khai báo, hoặc nhóm có nhãn đã bị khối cha gộp làm một mảnh). Một nút chữ thường
            //    (không thao tác, không phải khối) làm điểm dừng thì KHÔNG được che chữ của con.
            for (x in 0 until n) {
                if (!f.facts[x].hasLabel || x in covered) continue
                var p = f.parent[x]
                var explained = false
                while (p >= 0) {
                    val fp = f.facts[p]
                    val isUnit = fp.interactive || fp.screenReaderFocusable || p in parts
                    if (fp.hasLabel && !fp.structural && p in covered && isUnit) {
                        explained = true
                        break
                    }
                    p = f.parent[p]
                }
                assertTrue("$message: mất chữ ở nút $x", explained)
            }

            // 4. Nút nhập/công tắc/thanh trượt, và mọi nút thao tác có nhãn, luôn là điểm dừng.
            for (x in 0 until n) {
                val fx = f.facts[x]
                if ((fx.editable || fx.checkable || fx.adjustable) || (fx.interactive && fx.hasLabel)) {
                    assertTrue("$message: nút thao tác $x không phải điểm dừng", x in stops)
                }
            }

            // 5. "Mọi chữ" luôn là tập cha của phần chữ mặc định.
            if (n > 1) {
                val some = StopSelection.mergeParts(f.parent, f.facts, everything = false).toSet()
                val all = StopSelection.mergeParts(f.parent, f.facts, everything = true).toSet()
                assertTrue(message, all.containsAll(some))
            }
        }
    }
}
