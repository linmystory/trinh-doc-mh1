package com.example.vnscreenreader.menu

import com.example.vnscreenreader.core.BackupCodec
import com.example.vnscreenreader.core.BackupEntry
import com.example.vnscreenreader.core.BackupSchema
import com.example.vnscreenreader.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuLayoutTest {
    private val default = MenuLayout.DEFAULT

    @Test
    fun defaultIsTheTwelveItemsOfEarlierVersions() {
        assertEquals(
            listOf(
                GestureAction.ACTIVATE, GestureAction.LONG_CLICK_FOCUSED, GestureAction.UNIT_NEXT, GestureAction.UNIT_PREVIOUS,
                GestureAction.CYCLE_GRANULARITY, GestureAction.COPY_UNIT, GestureAction.COPY_FOCUSED, GestureAction.SPELL_FOCUSED,
                GestureAction.LABEL_FOCUSED, GestureAction.DESCRIBE_FOCUSED, GestureAction.OCR_FOCUSED, GestureAction.DESCRIBE_TECHNICAL,
            ),
            default.actions,
        )
        assertFalse(default.isCustomized)
        assertNull(default.serialize())
        assertEquals(12, MenuLayout.MAX_ITEMS)
        assertEquals(MenuLayout.MAX_ITEMS, default.actions.size)
        assertTrue(default.isFull)
        assertTrue(MenuLayout.DEFAULT_ACTIONS.all { MenuCatalog.isAvailable(it) })
    }

    @Test
    fun addAppendsAtTheEnd_andRefusesWhenFullDuplicateOrNotInTheCatalog() {
        assertSame(default, default.add(GestureAction.HOME)) // đã đủ 12
        val eleven = default.remove(GestureAction.DESCRIBE_TECHNICAL)
        assertEquals(11, eleven.actions.size)
        assertFalse(eleven.isFull)
        val twelve = eleven.add(GestureAction.HOME)
        assertEquals(GestureAction.HOME, twelve.actions.last())
        assertEquals(12, twelve.actions.size)
        assertTrue(twelve.isFull)
        assertSame(twelve, twelve.add(GestureAction.VOLUME_RAISE)) // lại đủ
        assertSame(eleven, eleven.add(GestureAction.ACTIVATE)) // đã có
        assertSame(eleven, eleven.add(GestureAction.BACK)) // bị loại khỏi danh mục
        assertSame(eleven, eleven.add(GestureAction.NONE))
    }

    @Test
    fun removeKeepsAtLeastOneItem() {
        var layout = default
        for (action in default.actions.dropLast(1)) layout = layout.remove(action)
        assertEquals(listOf(GestureAction.DESCRIBE_TECHNICAL), layout.actions)
        assertTrue(layout.isCustomized)
        assertSame(layout, layout.remove(GestureAction.DESCRIBE_TECHNICAL)) // mục cuối cùng: giữ lại
        assertSame(default, default.remove(GestureAction.HOME)) // không có trong danh sách
    }

    @Test
    fun movesAreOneStepAndStopAtTheEnds() {
        val first = default.actions.first()
        val last = default.actions.last()
        assertSame(default, default.moveUp(first))
        assertSame(default, default.moveToTop(first))
        assertSame(default, default.moveDown(last))

        val down = default.moveDown(first)
        assertEquals(default.actions[1], down.actions[0])
        assertEquals(first, down.actions[1])
        assertEquals(default.actions.size, down.actions.size)

        val fifth = default.actions[5]
        assertEquals(listOf(fifth) + default.actions.filter { it != fifth }, default.moveToTop(fifth).actions)
        val movedDown = default.moveDown(fifth).actions
        assertEquals(default.actions[6], movedDown[5])
        assertEquals(fifth, movedDown[6])
        val movedUp = default.moveUp(fifth).actions
        assertEquals(fifth, movedUp[4])
        assertEquals(default.actions[4], movedUp[5])

        // Chuyển xuống rồi chuyển lên là về đúng mặc định: không còn coi là đã tùy chỉnh.
        val back = default.moveDown(first).moveUp(first)
        assertEquals(default, back)
        assertFalse(back.isCustomized)

        // Mục không có trong danh sách: không đổi.
        assertSame(default, default.moveUp(GestureAction.HOME))
        assertSame(default, default.moveDown(GestureAction.HOME))
        assertSame(default, default.moveToTop(GestureAction.HOME))
    }

    @Test
    fun replaceKeepsThePositionAndRejectsBadChoices() {
        val old = default.actions[3]
        val replaced = default.replace(old, GestureAction.HOME)
        assertEquals(GestureAction.HOME, replaced.actions[3])
        assertEquals(12, replaced.actions.size)
        assertTrue(replaced.isCustomized)
        assertFalse(old in replaced.actions)
        assertSame(default, default.replace(old, default.actions[0])) // đã có trong danh sách
        assertSame(default, default.replace(old, old))
        assertSame(default, default.replace(old, GestureAction.BACK)) // không có trong danh mục
        assertSame(default, default.replace(GestureAction.HOME, GestureAction.VOLUME_RAISE)) // mục cũ không có
    }

    @Test
    fun positionIsOneBasedAndZeroWhenAbsent() {
        assertEquals(1, default.positionOf(default.actions.first()))
        assertEquals(12, default.positionOf(default.actions.last()))
        assertEquals(0, default.positionOf(GestureAction.HOME))
    }

    @Test
    fun theUiOnlyOffersWhatMakesSense() {
        val first = default.actions[0]
        val second = default.actions[1]
        val third = default.actions[2]
        val last = default.actions.last()
        // Đầu danh sách: không có gì ở trên.
        assertFalse(default.canMoveUp(first))
        assertFalse(default.canMoveToTop(first))
        assertTrue(default.canMoveDown(first))
        // Vị trí 2: "Chuyển lên" đã đúng việc của "Chuyển lên đầu", nên chỉ hiện một nút.
        assertTrue(default.canMoveUp(second))
        assertFalse(default.canMoveToTop(second))
        // Từ vị trí 3 trở đi mới có cả hai.
        assertTrue(default.canMoveUp(third))
        assertTrue(default.canMoveToTop(third))
        // Cuối danh sách: không có gì ở dưới.
        assertFalse(default.canMoveDown(last))
        assertTrue(default.canMoveUp(last))
        // Mục không có trong danh sách: không thao tác nào.
        listOf(
            default.canMoveUp(GestureAction.HOME), default.canMoveDown(GestureAction.HOME),
            default.canMoveToTop(GestureAction.HOME), default.canRemove(GestureAction.HOME),
        ).forEach { assertFalse(it) }
        // Xóa được khi còn từ hai mục; mục cuối cùng thì không.
        assertTrue(default.canRemove(first))
        var single = default
        for (action in default.actions.dropLast(1)) single = single.remove(action)
        assertFalse(single.canRemove(last))
        assertFalse(single.canMoveUp(last) || single.canMoveDown(last) || single.canMoveToTop(last))
    }

    @Test
    fun whatTheUiOffersAlwaysChangesTheLayout_andWhatItHidesNeverDoes() {
        val layouts = listOf(
            default,
            default.remove(GestureAction.ACTIVATE),
            MenuLayout.of(listOf(GestureAction.HOME)),
            MenuLayout.of(listOf(GestureAction.HOME, GestureAction.RECENTS)),
        )
        for (layout in layouts) {
            for (action in layout.actions + GestureAction.BACK) {
                val label = "${layout.actions.size} mục, ${action.name}"
                assertEquals("lên: $label", layout.canMoveUp(action), layout.moveUp(action) !== layout)
                assertEquals("xuống: $label", layout.canMoveDown(action), layout.moveDown(action) !== layout)
                assertEquals("xóa: $label", layout.canRemove(action), layout.remove(action) !== layout)
                if (layout.canMoveToTop(action)) {
                    assertTrue("lên đầu: $label", layout.moveToTop(action) !== layout)
                    // Đã hiện "Chuyển lên đầu" thì nó phải khác "Chuyển lên".
                    assertTrue("lên đầu khác lên: $label", layout.moveToTop(action) != layout.moveUp(action))
                }
            }
        }
    }

    @Test
    fun resetGoesBackToDefault() {
        val custom = default.replace(GestureAction.ACTIVATE, GestureAction.HOME)
        assertTrue(custom.isCustomized)
        assertSame(MenuLayout.DEFAULT, custom.reset())
    }

    @Test
    fun serializeAndParseRoundTrip() {
        val layout = default.replace(GestureAction.ACTIVATE, GestureAction.HOME).moveDown(GestureAction.HOME).remove(GestureAction.OCR_FOCUSED)
        val raw = layout.serialize()
        assertTrue(raw != null && raw.startsWith("LONG_CLICK_FOCUSED,HOME,"))
        assertEquals(layout, MenuLayout.parse(raw))
    }

    @Test
    fun parseToleratesGarbage() {
        listOf(null, "", "   ", ",,,", "NOPE,ALSO_NOPE", "\n\t").forEach {
            assertSame("[$it]", MenuLayout.DEFAULT, MenuLayout.parse(it))
        }
        // Lẫn mục đúng, mục lạ, trùng, khoảng trắng, mục bị loại và chữ thường: chỉ giữ mục dùng được, theo thứ tự đầu tiên gặp.
        val mixed = MenuLayout.parse(" HOME , NOPE,HOME,BACK,volume_raise,VOLUME_RAISE ,NONE")
        assertEquals(listOf(GestureAction.HOME, GestureAction.VOLUME_RAISE), mixed.actions)
        // Quá 12 mục: cắt còn 12, giữ thứ tự.
        val many = MenuLayout.parse(MenuCatalog.ALL.joinToString(",") { it.name })
        assertEquals(MenuCatalog.ALL.take(MenuLayout.MAX_ITEMS), many.actions)
        // Chuỗi khổng lồ không làm sập và không tốn thời gian.
        assertEquals(listOf(GestureAction.HOME), MenuLayout.parse("HOME,".repeat(100_000)).actions)
    }

    @Test
    fun aLayoutEqualToTheDefaultIsNotCustomized() {
        assertSame(MenuLayout.DEFAULT, MenuLayout.of(MenuLayout.DEFAULT_ACTIONS))
        assertSame(MenuLayout.DEFAULT, MenuLayout.parse(MenuLayout.DEFAULT_ACTIONS.joinToString(",") { it.name }))
        assertSame(MenuLayout.DEFAULT, MenuLayout.of(emptyList()))
        assertEquals(MenuLayout.MAX_ITEMS, MenuLayout.of(MenuCatalog.ALL).actions.size) // không bao giờ vượt giới hạn
    }

    @Test
    fun storeFileAndKeyAreInTheBackupSchema() {
        assertTrue(BackupSchema.MENU_LAYOUT in BackupSchema.FILES)
        assertEquals(MenuLayoutStore.PREFS_NAME, BackupSchema.MENU_LAYOUT)
        assertEquals('s', BackupSchema.typeOf(BackupSchema.MENU_LAYOUT, MenuLayoutStore.KEY_LOCAL))
        assertNull(BackupSchema.typeOf(BackupSchema.MENU_LAYOUT, "other"))
    }

    @Test
    fun theLayoutSurvivesABackupAndRestore() {
        val layout = default.replace(GestureAction.ACTIVATE, GestureAction.HOME).moveToTop(GestureAction.SPELL_FOCUSED)
        val entry = BackupEntry(BackupSchema.MENU_LAYOUT, MenuLayoutStore.KEY_LOCAL, 's', layout.serialize()!!)
        val decoded = BackupCodec.decode(BackupCodec.encode(listOf(entry)))
        assertTrue(decoded.valid)
        assertEquals(1, decoded.entries.size)
        assertEquals(layout, MenuLayout.parse(decoded.entries.single().value))
    }
}

class LocalMenuLayoutTest {

    @Test
    fun defaultLayoutGivesTheSameMenuAsEarlierVersions() {
        val items = Menus.local(emptyList())
        assertEquals(MenuLayout.DEFAULT_ACTIONS, items.map { it.action })
        assertEquals(
            listOf(
                "Nhấn", "Nhấn giữ", "Đơn vị kế tiếp", "Đơn vị trước", "Đổi đơn vị đọc", "Sao chép đơn vị",
                "Sao chép chữ", "Đọc từng ký tự", "Gắn hoặc sửa nhãn", "Mô tả hình ảnh", "Đọc chữ trong ảnh", "Thông tin kỹ thuật",
            ),
            items.map { it.label },
        )
    }

    @Test
    fun theGeneralMenuKeepsItsFamiliarLabels() {
        assertEquals(
            listOf(
                "Đọc từ đầu màn hình", "Đọc từ phần tử đang chọn", "Lặp lại câu vừa đọc", "Tìm chữ trên màn hình",
                "Đọc chữ trên màn hình (OCR)", "Thanh thông báo", "Màn hình chính",
            ),
            Menus.global().take(Menus.MAX_GLOBAL_TOP_ACTIONS).map { it.label },
        )
        assertEquals("Đọc", MenuKind.GROUP_READING.title)
        assertEquals("Chỉnh sửa văn bản", MenuKind.GROUP_TEXT.title)
    }

    @Test
    fun customLayoutDecidesTheOrder_andTheFixedItemsStayAtTheEnd() {
        val layout = MenuLayout.DEFAULT.replace(GestureAction.ACTIVATE, GestureAction.HOME).moveToTop(GestureAction.DESCRIBE_FOCUSED)
        val items = Menus.local(listOf(CustomAction(0x7f010001, "Xóa tin")), editable = true, layout = layout)
        assertEquals(layout.actions, items.take(layout.actions.size).map { it.action })
        assertEquals(MenuKind.LOCAL_EDIT, items[layout.actions.size].submenu)
        assertEquals(0x7f010001, items.last().customActionId)
        assertEquals(layout.actions.size + 2, items.size)
    }

    @Test
    fun theImageRuleOnlyAppliesWhileTheMenuIsNotCustomized() {
        val image = Menus.local(emptyList(), imageLike = true)
        assertEquals(listOf(GestureAction.DESCRIBE_FOCUSED, GestureAction.OCR_FOCUSED), image.take(2).map { it.action })
        assertEquals(MenuLayout.DEFAULT_ACTIONS.size, image.size)
        // Đã tùy chỉnh: thứ tự của người dùng được giữ nguyên, kể cả với ảnh.
        val custom = MenuLayout.DEFAULT.moveToTop(GestureAction.LABEL_FOCUSED)
        assertEquals(custom.actions, Menus.local(emptyList(), imageLike = true, layout = custom).map { it.action })
    }

    @Test
    fun theSmallestLayoutStillMakesAValidMenu() {
        var layout = MenuLayout.DEFAULT
        for (action in MenuLayout.DEFAULT_ACTIONS.dropLast(1)) layout = layout.remove(action)
        val items = Menus.local(emptyList(), layout = layout)
        assertEquals(1, items.size)
        MenuState.open(MenuKind.LOCAL, items) // không ném lỗi: trình đơn có ít nhất một mục
    }

    @Test
    fun customActionsAreStillCappedWhateverTheLayout() {
        val many = (1..50).map { CustomAction(0x7f000000 + it, "a$it") }
        val layout = MenuLayout.DEFAULT.remove(GestureAction.ACTIVATE)
        assertEquals(layout.actions.size + Menus.MAX_CUSTOM_ACTIONS, Menus.local(many, layout = layout).size)
    }
}
