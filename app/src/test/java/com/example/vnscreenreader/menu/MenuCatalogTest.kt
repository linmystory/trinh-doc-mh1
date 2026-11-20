package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Danh mục chức năng là nguồn duy nhất của trình đơn chung, hộp thoại chọn chức năng và trang giải thích. Các bài test ở đây giữ lời
 * hứa "trình đơn chung chứa mọi chức năng của ứng dụng": thêm một [GestureAction] mới mà quên xếp nhóm là bài test hỏng.
 */
class MenuCatalogTest {

    @Test
    fun everyActionIsInExactlyOneGroupOrExcluded() {
        val seen = mutableMapOf<GestureAction, MenuGroup>()
        for (group in MenuGroup.entries) {
            for (action in group.actions) {
                assertNull("${action.name} nằm ở hai nhóm: ${seen[action]} và $group", seen.put(action, group))
            }
        }
        for (action in GestureAction.entries) {
            val inGroup = action in seen
            val excluded = action in MenuCatalog.EXCLUDED
            assertTrue("${action.name} phải thuộc một nhóm hoặc nằm trong MenuCatalog.EXCLUDED", inGroup || excluded)
            assertFalse("${action.name} vừa thuộc nhóm vừa bị loại", inGroup && excluded)
        }
    }

    @Test
    fun catalogListMatchesTheGroups() {
        assertEquals(GestureAction.entries.size - MenuCatalog.EXCLUDED.size, MenuCatalog.ALL.size)
        assertEquals(MenuCatalog.ALL.size, MenuCatalog.ALL.toSet().size)
        MenuCatalog.ALL.forEach {
            assertTrue(it.name, MenuCatalog.isAvailable(it))
            assertNotNull(it.name, MenuCatalog.groupOf(it))
        }
        MenuCatalog.EXCLUDED.forEach {
            assertFalse(it.name, MenuCatalog.isAvailable(it))
            assertNull(it.name, MenuCatalog.groupOf(it))
        }
    }

    @Test
    fun excludedCoverEverythingTheMenuUsesToNavigate() {
        // Cử chỉ trình đơn dùng để chuyển mục, đóng, lùi, mở trình đơn không được thành mục trình đơn. "Nhấn" (ACTIVATE) vừa là cử chỉ
        // chạy mục vừa là một chức năng hợp lệ: chạm vào phần tử đang chọn, nên không bị loại.
        val navigation = MenuGestures.HANDLED - GestureAction.ACTIVATE
        assertEquals(emptySet<GestureAction>(), navigation - MenuCatalog.EXCLUDED)
        assertTrue(GestureAction.ACTIVATE in MenuCatalog.ALL)
        assertTrue(GestureAction.NONE in MenuCatalog.EXCLUDED)
    }

    @Test
    fun groupKindsAreOneToOneWithTheGroupMenuKinds() {
        val groupKinds = MenuKind.entries.filter { it.isGroup }
        assertEquals(MenuGroup.entries.size, groupKinds.size)
        assertEquals(groupKinds.toSet(), MenuGroup.entries.map { it.kind }.toSet())
        MenuGroup.entries.forEach { assertSame(it, MenuGroup.forKind(it.kind)) }
        listOf(MenuKind.GLOBAL, MenuKind.LOCAL, MenuKind.LOCAL_EDIT).forEach {
            assertFalse(it.name, it.isGroup)
            assertNull(it.name, MenuGroup.forKind(it))
        }
    }

    @Test
    fun groupsAreSmallEnoughToSwipeThrough() {
        MenuGroup.entries.forEach { assertTrue("${it.name}: ${it.actions.size} mục", it.actions.size in 1..12) }
    }

    @Test
    fun everyTitleAndLabelIsReadable() {
        MenuKind.entries.forEach { assertTrue(it.name, it.title.isNotBlank()) }
        // Mỗi nhóm có tên riêng, để người nghe phân biệt được (nhóm văn bản dùng chung tên với trình đơn con "Chỉnh sửa văn bản").
        assertEquals(MenuGroup.entries.size, MenuGroup.entries.map { it.title }.toSet().size)
        MenuCatalog.ALL.forEach { assertTrue(it.name, Menus.labelFor(it).isNotBlank()) }
    }

    @Test
    fun globalMenuReachesEveryCatalogFunction() {
        assertEquals(MenuCatalog.ALL.toSet(), Menus.globalActions())
        // Qua đường đi thật của người dùng: mục ở cấp đầu hoặc mục trong đúng một trình đơn con.
        val top = Menus.global()
        val reachable = top.mapNotNull { it.action }.toMutableSet()
        top.mapNotNull { it.submenu }.forEach { kind -> Menus.submenu(kind)!!.mapNotNullTo(reachable) { it.action } }
        assertEquals(MenuCatalog.ALL.toSet(), reachable)
    }

    @Test
    fun shortcutsKeepTheirPlaceAndAreAlsoInTheirGroups() {
        assertEquals(Menus.MAX_GLOBAL_TOP_ACTIONS, Menus.GLOBAL_SHORTCUTS.size)
        assertEquals(Menus.GLOBAL_SHORTCUTS.size, Menus.GLOBAL_SHORTCUTS.toSet().size)
        Menus.GLOBAL_SHORTCUTS.forEach { assertNotNull(it.name, MenuCatalog.groupOf(it)) }
        assertEquals(Menus.GLOBAL_SHORTCUTS, Menus.global().take(Menus.GLOBAL_SHORTCUTS.size).map { it.action })
    }

    @Test
    fun theLocalEditSubmenuIsTheTextGroup() {
        assertEquals(MenuGroup.TEXT.actions, Menus.localEdit().dropLast(1).map { it.action })
        assertTrue(Menus.localEdit().last().isBack)
    }
}
