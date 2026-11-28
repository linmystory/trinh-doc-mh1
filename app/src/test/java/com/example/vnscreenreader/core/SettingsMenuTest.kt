package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuTest {

    /** Mười lăm khóa của danh sách phẳng ở bản 2.20.x: khóa này có thể còn nằm trong trạng thái đã lưu. */
    private val flatListKeys = listOf(
        "status", "voice", "feedback", "notifications", "gestures", "appgestures", "gesturetest", "menus",
        "reading", "labels", "permissions", "log", "backup", "plugins", "help",
    )

    // ---------- cấu trúc cây ----------

    @Test
    fun everyPageBelongsToExactlyOneMenu() {
        val listed = SettingsMenu.entries.flatMap { it.pages }
        assertEquals("có trang nằm ở nhiều menu", listed.size, listed.toSet().size)
        assertEquals("có trang không thuộc menu nào", SettingsPage.entries.toSet(), listed.toSet())
        SettingsPage.entries.forEach { assertTrue(it in SettingsNav.menuOf(it).pages) }
    }

    @Test
    fun noMenuIsEmpty() {
        SettingsMenu.entries.forEach { assertTrue("menu ${it.key} rỗng", it.pages.isNotEmpty()) }
    }

    @Test
    fun designHasSixMenusAndSeventeenPages() {
        assertEquals(6, SettingsMenu.entries.size)
        // Bản 2.23.0 bỏ trang "Kiểm tra cử chỉ" (16); bản 2.29.0 tách "Trình đơn nhanh" thành hai trang (17).
        assertEquals(17, SettingsPage.entries.size)
        assertEquals(17, SettingsMenu.entries.sumOf { it.pages.size })
    }

    @Test
    fun menusAndPagesAreInTheDesignedOrder() {
        // Thứ tự này là thứ tự người dùng nghe khi vuốt: đổi thì phải đổi cả README (bảng "mục cũ → vị trí mới").
        val actual = SettingsMenu.entries.associate { menu -> menu.key to menu.pages.map { it.key } }
        val expected = linkedMapOf(
            "hub_start" to listOf("status"),
            "hub_audio" to listOf("voice", "feedback"),
            "hub_content" to listOf("read_elements", "focus", "notifications"),
            "hub_control" to listOf("gestures", "appgestures", "menus_general", "menus_quick", "touch_typing", "hardware_keys"),
            "hub_tools" to listOf("labels", "plugins", "backup", "log"),
            "hub_help" to listOf("help"),
        )
        assertEquals(expected.toList(), actual.toList())
    }

    @Test
    fun keysAreUniqueAcrossMenusAndPages() {
        // Một khóa chuỗi duy nhất lưu "đang ở đâu": khóa menu và khóa trang mà trùng thì không phân biệt được.
        val keys = SettingsMenu.entries.map { it.key } + SettingsPage.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        val shape = Regex("[a-z][a-z_]*")
        keys.forEach { assertTrue("khóa lạ: $it", shape.matches(it)) }
    }

    @Test
    fun onlyTheSinglePageMenusOpenDirectly() {
        assertEquals(
            setOf(SettingsMenu.START, SettingsMenu.HELP),
            SettingsMenu.entries.filter { it.directPage != null }.toSet(),
        )
        assertSame(SettingsPage.STATUS, SettingsMenu.START.directPage)
        assertNull(SettingsMenu.CONTROL.directPage)
    }

    // ---------- khóa đã lưu ----------

    @Test
    fun everyKeyFromTheFlatListStillOpensAPage() {
        flatListKeys.forEach { key ->
            val destination = SettingsNav.resolve(key)
            assertTrue("khóa cũ '$key' không mở được trang nào", destination is SettingsDestination.Page)
        }
    }

    @Test
    fun splitAndMergedPagesMapToTheirNewHomes() {
        assertEquals(SettingsDestination.Page(SettingsPage.READ_ELEMENTS), SettingsNav.resolve("reading"))
        assertEquals(SettingsDestination.Page(SettingsPage.STATUS), SettingsNav.resolve("permissions"))
        // Các trang còn nguyên khóa cũ.
        assertEquals(SettingsDestination.Page(SettingsPage.NOTIFICATIONS), SettingsNav.resolve("notifications"))
        assertEquals(SettingsDestination.Page(SettingsPage.LOG), SettingsNav.resolve("log"))
        assertEquals(SettingsDestination.Page(SettingsPage.GESTURES), SettingsNav.resolve("gestures"))
    }

    @Test
    fun theOldQuickMenusKeyOpensTheEditablePage() {
        // Trang "Trình đơn nhanh" đã tách làm hai: khóa cũ còn nằm trong trạng thái đã lưu phải mở trang chỉnh được.
        assertEquals(SettingsDestination.Page(SettingsPage.MENUS_QUICK), SettingsNav.resolve("menus"))
        assertEquals(SettingsPage.MENUS_QUICK, SettingsNav.pageForKey("menus"))
        // Hai trang mới nằm cạnh nhau trong menu Điều khiển, đúng thứ tự: chung rồi hành động nhanh.
        val pages = SettingsMenu.CONTROL.pages
        assertEquals(pages.indexOf(SettingsPage.MENUS_GENERAL) + 1, pages.indexOf(SettingsPage.MENUS_QUICK))
        assertEquals(SettingsMenu.CONTROL, SettingsNav.menuOf(SettingsPage.MENUS_GENERAL))
        assertEquals(SettingsMenu.CONTROL, SettingsNav.menuOf(SettingsPage.MENUS_QUICK))
    }

    @Test
    fun nullAndUnknownKeysGoToTheRoot() {
        assertEquals(SettingsDestination.Root, SettingsNav.resolve(null))
        assertEquals(SettingsDestination.Root, SettingsNav.resolve(""))
        assertEquals(SettingsDestination.Root, SettingsNav.resolve("khong_co_khoa_nay"))
        assertNull(SettingsNav.pageForKey(null))
        assertNull(SettingsNav.pageForKey("hub_control")) // khóa menu không phải khóa trang
    }

    @Test
    fun everyDestinationSurvivesTheSaveAndRestoreRoundTrip() {
        val all = buildList<SettingsDestination> {
            add(SettingsDestination.Root)
            SettingsMenu.entries.filter { it.directPage == null }.forEach { add(SettingsDestination.Menu(it)) }
            SettingsPage.entries.forEach { add(SettingsDestination.Page(it)) }
        }
        all.forEach { assertEquals(it, SettingsNav.resolve(SettingsNav.keyOf(it))) }
    }

    @Test
    fun aSavedKeyOfASinglePageMenuResolvesToItsPage() {
        assertEquals(SettingsDestination.Page(SettingsPage.STATUS), SettingsNav.resolve("hub_start"))
        assertEquals(SettingsDestination.Page(SettingsPage.HELP), SettingsNav.resolve("hub_help"))
        assertEquals(SettingsDestination.Menu(SettingsMenu.CONTROL), SettingsNav.resolve("hub_control"))
    }

    // ---------- mở và Back ----------

    @Test
    fun openingAMenuShowsItsListUnlessItHasOnePage() {
        assertEquals(SettingsDestination.Menu(SettingsMenu.CONTROL), SettingsNav.open(SettingsMenu.CONTROL))
        assertEquals(SettingsDestination.Menu(SettingsMenu.AUDIO), SettingsNav.open(SettingsMenu.AUDIO))
        assertEquals(SettingsDestination.Page(SettingsPage.STATUS), SettingsNav.open(SettingsMenu.START))
        assertEquals(SettingsDestination.Page(SettingsPage.HELP), SettingsNav.open(SettingsMenu.HELP))
    }

    @Test
    fun backGoesUpExactlyOneLevel() {
        val page = SettingsDestination.Page(SettingsPage.TOUCH_TYPING)
        val menu = SettingsNav.back(page)
        assertEquals(SettingsDestination.Menu(SettingsMenu.CONTROL), menu)
        assertEquals(SettingsDestination.Root, SettingsNav.back(menu))
        assertEquals(SettingsDestination.Root, SettingsNav.back(SettingsDestination.Root))
    }

    @Test
    fun backFromAPageOfASinglePageMenuSkipsTheMenu() {
        assertEquals(SettingsDestination.Root, SettingsNav.back(SettingsDestination.Page(SettingsPage.STATUS)))
        assertEquals(SettingsDestination.Root, SettingsNav.back(SettingsDestination.Page(SettingsPage.HELP)))
    }

    @Test
    fun backFromAnyDestinationReachesTheRootInAtMostTwoSteps() {
        // Không có ngõ cụt: dù đứng đâu, Back tối đa hai lần là về danh sách chính (rồi mới thoát ứng dụng).
        SettingsPage.entries.forEach { page ->
            var at: SettingsDestination = SettingsDestination.Page(page)
            var steps = 0
            while (at != SettingsDestination.Root) {
                at = SettingsNav.back(at)
                steps++
                assertTrue("Back từ ${page.key} không về được danh sách chính", steps <= 2)
            }
        }
    }

    @Test
    fun parentMenuMatchesBackTarget() {
        SettingsPage.entries.forEach { page ->
            val parent = SettingsNav.parentMenu(page)
            val back = SettingsNav.back(SettingsDestination.Page(page))
            if (parent == null) assertEquals(SettingsDestination.Root, back) else assertEquals(SettingsDestination.Menu(parent), back)
        }
    }

    // ---------- bố cục rộng ----------

    @Test
    fun wideLayoutAlwaysHasAPageToShow() {
        assertEquals(SettingsPage.STATUS, SettingsNav.defaultPage)
        assertEquals(SettingsPage.STATUS, SettingsNav.pageForWide(SettingsDestination.Root))
        assertEquals(SettingsPage.GESTURES, SettingsNav.pageForWide(SettingsDestination.Menu(SettingsMenu.CONTROL)))
        assertEquals(SettingsPage.FOCUS, SettingsNav.pageForWide(SettingsDestination.Page(SettingsPage.FOCUS)))
        SettingsMenu.entries.forEach { assertNotNull(SettingsNav.pageForWide(SettingsNav.open(it))) }
    }
}
