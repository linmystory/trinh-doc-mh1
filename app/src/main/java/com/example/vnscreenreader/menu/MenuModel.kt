package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Một mục trình đơn: hoặc một [GestureAction] có sẵn, hoặc một hành động tùy chỉnh của chính phần tử (id hệ thống). */
data class MenuItem(val label: String, val action: GestureAction? = null, val customActionId: Int? = null) {
    init {
        require((action == null) != (customActionId == null)) { "Mục phải có đúng một trong action hoặc customActionId" }
    }
}

enum class MenuKind(private val titleKey: S) {
    GLOBAL(S.MENU_GLOBAL_TITLE),
    LOCAL(S.MENU_LOCAL_TITLE);

    /** Tiêu đề theo ngôn ngữ hiện hành. */
    val title: String get() = t(titleKey)
}

/** Hành động tùy chỉnh do ứng dụng khai báo cho một phần tử (ví dụ "Xóa tin nhắn"). */
data class CustomAction(val id: Int, val label: String)

object Menus {

    /** Trình đơn chung: thao tác với cả màn hình/hệ thống. Thứ tự theo mức dùng phổ biến. */
    fun global(): List<MenuItem> = listOf(
        MenuItem(t(S.GA_READ_FROM_TOP), GestureAction.READ_FROM_TOP),
        MenuItem(t(S.GA_READ_FROM_CURRENT), GestureAction.READ_FROM_CURRENT),
        MenuItem(t(S.GA_REPEAT_LAST), GestureAction.REPEAT_LAST),
        MenuItem(t(S.GA_SEARCH_SCREEN), GestureAction.SEARCH_SCREEN),
        MenuItem(t(S.GA_OCR_SCREEN), GestureAction.OCR_SCREEN),
        MenuItem(t(S.GA_CYCLE_FEEDBACK), GestureAction.CYCLE_FEEDBACK),
        MenuItem(t(S.GA_SPEAK_STATUS), GestureAction.SPEAK_STATUS),
        MenuItem(t(S.MSG_NOTIFICATIONS), GestureAction.NOTIFICATIONS),
        MenuItem(t(S.MSG_QUICK_SETTINGS), GestureAction.QUICK_SETTINGS),
        MenuItem(t(S.MSG_HOME), GestureAction.HOME),
        MenuItem(t(S.MSG_RECENTS), GestureAction.RECENTS),
        MenuItem(t(S.GA_RUN_PLUGINS), GestureAction.RUN_PLUGINS),
        MenuItem(t(S.GA_OPEN_SETTINGS), GestureAction.OPEN_SETTINGS),
    )

    /** Trình đơn của phần tử đang chọn; cuối danh sách là các hành động tùy chỉnh của chính phần tử đó. */
    fun local(custom: List<CustomAction>): List<MenuItem> = buildList {
        add(MenuItem(t(S.MENU_ACTIVATE), GestureAction.ACTIVATE))
        add(MenuItem(t(S.MENU_LONG_CLICK), GestureAction.LONG_CLICK_FOCUSED))
        add(MenuItem(t(S.MENU_COPY), GestureAction.COPY_FOCUSED))
        add(MenuItem(t(S.MENU_SPELL), GestureAction.SPELL_FOCUSED))
        add(MenuItem(t(S.MENU_LABEL), GestureAction.LABEL_FOCUSED))
        add(MenuItem(t(S.MENU_DESCRIBE), GestureAction.DESCRIBE_FOCUSED))
        custom.take(MAX_CUSTOM_ACTIONS).forEach { add(MenuItem(it.label, customActionId = it.id)) }
    }

    const val MAX_CUSTOM_ACTIONS = 20

    /**
     * Hành động tùy chỉnh = có nhãn và id nằm ngoài vùng id của khung hệ thống (cờ bit < 0x01000000 và
     * `android.R.id.*` 0x0102xxxx). Đây là quy tắc gần đúng: hành động chuẩn không có nhãn nên cũng bị loại.
     */
    fun isCustomAction(id: Int, label: CharSequence?): Boolean =
        !label.isNullOrBlank() && id > FRAMEWORK_ID_MAX

    private const val FRAMEWORK_ID_MAX = 0x0102FFFF
}

/**
 * Trạng thái một trình đơn đang mở, đọc bằng giọng nói và điều khiển bằng cử chỉ (vuốt chuyển mục, chạm hai lần
 * chọn). Bất biến: mỗi thao tác trả về trạng thái mới. Chuyển mục theo vòng tròn như TalkBack.
 */
class MenuState private constructor(
    val kind: MenuKind,
    val items: List<MenuItem>,
    val index: Int,
) {
    init {
        require(items.isNotEmpty()) { "Trình đơn phải có ít nhất một mục" }
        require(index in -1 until items.size) { "index ngoài phạm vi" }
    }

    val current: MenuItem? get() = items.getOrNull(index)

    fun next(): MenuState = MenuState(kind, items, if (index < 0) 0 else (index + 1) % items.size)

    fun previous(): MenuState =
        MenuState(kind, items, if (index < 0) items.size - 1 else (index - 1 + items.size) % items.size)

    fun opening(): String = t(S.MENU_OPENING, kind.title, items.size)

    fun describeCurrent(): String {
        val item = current ?: return opening()
        return t(S.MENU_POSITION, item.label, index + 1, items.size)
    }

    companion object {
        fun open(kind: MenuKind, items: List<MenuItem>): MenuState = MenuState(kind, items, -1)
    }
}
