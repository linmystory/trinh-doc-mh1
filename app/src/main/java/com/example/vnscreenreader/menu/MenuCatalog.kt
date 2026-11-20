package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.GestureAction

/**
 * Một nhóm trong danh mục chức năng của trình đơn. Mỗi nhóm là một trình đơn con của trình đơn chung ([kind]) và là một đề mục
 * trong hộp thoại chọn chức năng khi chỉnh trình đơn phần tử. Thứ tự khai báo là thứ tự nhóm trong cả hai nơi; thứ tự chức năng
 * trong mỗi nhóm là thứ tự nghe trong trình đơn con.
 *
 * Danh mục là nguồn duy nhất cho trình đơn chung, hộp thoại chọn và trang giải thích. MenuCatalogTest buộc mỗi [GestureAction]
 * thuộc đúng một nhóm hoặc nằm trong [MenuCatalog.EXCLUDED]: thêm chức năng mới mà quên xếp nhóm thì bài test hỏng, nên trình
 * đơn chung không bao giờ thiếu chức năng.
 */
enum class MenuGroup(val kind: MenuKind, val actions: List<GestureAction>) {
    READING(
        MenuKind.GROUP_READING,
        listOf(
            GestureAction.READ_FROM_TOP, GestureAction.READ_FROM_CURRENT, GestureAction.REPEAT_LAST,
            GestureAction.SPELL_FOCUSED, GestureAction.UNIT_NEXT, GestureAction.UNIT_PREVIOUS,
            GestureAction.CYCLE_GRANULARITY, GestureAction.CYCLE_VOICE_MODE,
            GestureAction.NEXT_READING_MODE, GestureAction.PREVIOUS_READING_MODE,
        ),
    ),
    BROWSE(
        MenuKind.GROUP_BROWSE,
        listOf(
            GestureAction.NEXT_HEADING, GestureAction.PREVIOUS_HEADING, GestureAction.NEXT_LINK, GestureAction.PREVIOUS_LINK,
            GestureAction.NEXT_CONTAINER, GestureAction.PREVIOUS_CONTAINER, GestureAction.NEXT_CONTROL, GestureAction.PREVIOUS_CONTROL,
            GestureAction.NEXT_WINDOW, GestureAction.PREVIOUS_WINDOW, GestureAction.JUMP_FIRST, GestureAction.JUMP_LAST,
        ),
    ),
    SCROLL(
        MenuKind.GROUP_SCROLL,
        listOf(
            GestureAction.SCROLL_FORWARD, GestureAction.SCROLL_BACKWARD, GestureAction.PAGE_NEXT, GestureAction.PAGE_PREVIOUS,
            GestureAction.SCROLL_TO_TOP, GestureAction.SCROLL_TO_BOTTOM,
        ),
    ),
    ELEMENT(
        MenuKind.GROUP_ELEMENT,
        listOf(
            GestureAction.ACTIVATE, GestureAction.LONG_CLICK_FOCUSED, GestureAction.COPY_FOCUSED, GestureAction.COPY_UNIT,
            GestureAction.LABEL_FOCUSED, GestureAction.DESCRIBE_FOCUSED, GestureAction.OCR_FOCUSED, GestureAction.DESCRIBE_TECHNICAL,
            GestureAction.SLIDER_UP, GestureAction.SLIDER_DOWN, GestureAction.SLIDER_TO_END,
        ),
    ),
    TEXT(
        MenuKind.GROUP_TEXT,
        listOf(
            GestureAction.PASTE_FOCUSED, GestureAction.CUT_FOCUSED, GestureAction.SELECT_ALL_FOCUSED,
            GestureAction.CURSOR_START, GestureAction.CURSOR_END, GestureAction.SELECT_TO_START, GestureAction.SELECT_TO_END,
            GestureAction.CLEAR_FIELD, GestureAction.RESTORE_CLEARED,
        ),
    ),
    SYSTEM(
        MenuKind.GROUP_SYSTEM,
        listOf(
            GestureAction.HOME, GestureAction.RECENTS, GestureAction.NOTIFICATIONS, GestureAction.QUICK_SETTINGS,
            GestureAction.MEDIA_PLAY_PAUSE, GestureAction.VOLUME_RAISE, GestureAction.VOLUME_LOWER,
            GestureAction.TOGGLE_KEYS_PAUSE, GestureAction.VOLUME_EXCLUDE_APP,
        ),
    ),
    TOOLS(
        MenuKind.GROUP_TOOLS,
        listOf(
            GestureAction.SEARCH_SCREEN, GestureAction.OCR_SCREEN, GestureAction.RUN_PLUGINS, GestureAction.OPEN_SETTINGS,
            GestureAction.CYCLE_FEEDBACK, GestureAction.TOGGLE_DETAILED_FOCUS, GestureAction.SPEAK_STATUS, GestureAction.SAY_LOCATION,
        ),
    );

    /** Tên nhóm theo ngôn ngữ hiện hành. */
    val title: String get() = kind.title

    companion object {
        private val byKind: Map<MenuKind, MenuGroup> = MenuGroup.entries.associateBy { it.kind }

        /** Nhóm ứng với một loại trình đơn con; null nếu [kind] không phải nhóm. */
        fun forKind(kind: MenuKind): MenuGroup? = byKind[kind]
    }
}

object MenuCatalog {

    /**
     * Chức năng KHÔNG đưa vào trình đơn: chính trình đơn dùng các cử chỉ này để chuyển mục, chạy mục, đóng hoặc lùi, hoặc chúng vô
     * nghĩa khi đã ở trong trình đơn (không làm gì, dừng đọc, mở trình đơn từ trong trình đơn).
     */
    val EXCLUDED: Set<GestureAction> = setOf(
        GestureAction.NONE,
        GestureAction.NEXT_ELEMENT, GestureAction.PREVIOUS_ELEMENT,
        GestureAction.NEXT_BY_CONTROL, GestureAction.PREVIOUS_BY_CONTROL,
        GestureAction.BACK, GestureAction.STOP_SPEECH,
        GestureAction.OPEN_GLOBAL_MENU, GestureAction.OPEN_LOCAL_MENU,
    )

    /** Mọi chức năng đưa được vào trình đơn, theo thứ tự nhóm rồi thứ tự trong nhóm. */
    val ALL: List<GestureAction> = MenuGroup.entries.flatMap { it.actions }

    private val groupByAction: Map<GestureAction, MenuGroup> =
        MenuGroup.entries.flatMap { group -> group.actions.map { action -> action to group } }.toMap()

    /** Nhóm chứa [action]; null nếu chức năng không có trong danh mục. */
    fun groupOf(action: GestureAction): MenuGroup? = groupByAction[action]

    /** [action] có đưa được vào trình đơn không. */
    fun isAvailable(action: GestureAction): Boolean = action in groupByAction
}
