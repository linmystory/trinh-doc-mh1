package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/**
 * Một mục trình đơn: đúng MỘT trong bốn loại — [GestureAction] có sẵn, hành động tùy chỉnh của phần tử (id hệ
 * thống), mở trình đơn con ([submenu]) hoặc quay lại trình đơn cha ([isBack]).
 */
data class MenuItem(
    val label: String,
    val action: GestureAction? = null,
    val customActionId: Int? = null,
    val submenu: MenuKind? = null,
    val isBack: Boolean = false,
) {
    init {
        val kinds = listOf(action != null, customActionId != null, submenu != null, isBack).count { it }
        require(kinds == 1) { "Mục phải có đúng một loại: action, customActionId, submenu hoặc isBack" }
    }
}

enum class MenuKind(private val titleKey: S) {
    GLOBAL(S.MENU_GLOBAL_TITLE),
    LOCAL(S.MENU_LOCAL_TITLE),
    GLOBAL_MORE(S.MENU_MORE_TITLE),
    LOCAL_EDIT(S.MENU_EDIT_TITLE);

    /** Tiêu đề theo ngôn ngữ hiện hành. */
    val title: String get() = t(titleKey)
}

/** Hành động tùy chỉnh do ứng dụng khai báo cho một phần tử (ví dụ "Xóa tin nhắn"). */
data class CustomAction(val id: Int, val label: String)

object Menus {

    /** Số mục hành động tối đa ở cấp đầu của trình đơn chung (chưa tính mục mở trình đơn con "Khác"). */
    const val MAX_GLOBAL_TOP_ACTIONS = 7

    private fun back() = MenuItem(t(S.MENU_BACK), isBack = true)

    /** Trình đơn chung, cấp đầu: 7 mục dùng nhiều nhất + "Khác". Phần còn lại nằm trong [globalMore]. */
    fun global(): List<MenuItem> = listOf(
        MenuItem(t(S.GA_READ_FROM_TOP), GestureAction.READ_FROM_TOP),
        MenuItem(t(S.GA_READ_FROM_CURRENT), GestureAction.READ_FROM_CURRENT),
        MenuItem(t(S.GA_REPEAT_LAST), GestureAction.REPEAT_LAST),
        MenuItem(t(S.GA_SEARCH_SCREEN), GestureAction.SEARCH_SCREEN),
        MenuItem(t(S.GA_OCR_SCREEN), GestureAction.OCR_SCREEN),
        MenuItem(t(S.MSG_NOTIFICATIONS), GestureAction.NOTIFICATIONS),
        MenuItem(t(S.MSG_HOME), GestureAction.HOME),
        MenuItem(t(S.MENU_MORE_TITLE), submenu = MenuKind.GLOBAL_MORE),
    )

    /** Trình đơn con "Khác" của trình đơn chung; mục cuối là "Quay lại". */
    fun globalMore(): List<MenuItem> = listOf(
        MenuItem(t(S.MSG_QUICK_SETTINGS), GestureAction.QUICK_SETTINGS),
        MenuItem(t(S.MSG_RECENTS), GestureAction.RECENTS),
        MenuItem(t(S.GA_CYCLE_FEEDBACK), GestureAction.CYCLE_FEEDBACK),
        MenuItem(t(S.GA_SPEAK_STATUS), GestureAction.SPEAK_STATUS),
        MenuItem(t(S.GA_RUN_PLUGINS), GestureAction.RUN_PLUGINS),
        MenuItem(t(S.GA_OPEN_SETTINGS), GestureAction.OPEN_SETTINGS),
        back(),
    )

    /**
     * Trình đơn của phần tử đang chọn. Mục chỉnh sửa văn bản chỉ nằm trong trình đơn con [localEdit] và chỉ hiện
     * khi [editable]; cuối danh sách là các hành động tùy chỉnh của chính phần tử đó.
     */
    fun local(custom: List<CustomAction>, editable: Boolean = false): List<MenuItem> = buildList {
        add(MenuItem(t(S.MENU_ACTIVATE), GestureAction.ACTIVATE))
        add(MenuItem(t(S.MENU_LONG_CLICK), GestureAction.LONG_CLICK_FOCUSED))
        add(MenuItem(t(S.MENU_UNIT_NEXT), GestureAction.UNIT_NEXT))
        add(MenuItem(t(S.MENU_UNIT_PREV), GestureAction.UNIT_PREVIOUS))
        add(MenuItem(t(S.MENU_GRANULARITY), GestureAction.CYCLE_GRANULARITY))
        add(MenuItem(t(S.MENU_COPY_UNIT), GestureAction.COPY_UNIT))
        add(MenuItem(t(S.MENU_COPY), GestureAction.COPY_FOCUSED))
        add(MenuItem(t(S.MENU_SPELL), GestureAction.SPELL_FOCUSED))
        add(MenuItem(t(S.MENU_LABEL), GestureAction.LABEL_FOCUSED))
        add(MenuItem(t(S.MENU_DESCRIBE), GestureAction.DESCRIBE_FOCUSED))
        add(MenuItem(t(S.MENU_OCR_FOCUSED), GestureAction.OCR_FOCUSED))
        if (editable) add(MenuItem(t(S.MENU_EDIT_TITLE), submenu = MenuKind.LOCAL_EDIT))
        custom.take(MAX_CUSTOM_ACTIONS).forEach { add(MenuItem(it.label, customActionId = it.id)) }
    }

    /** Trình đơn con "Chỉnh sửa văn bản"; mục cuối là "Quay lại". */
    fun localEdit(): List<MenuItem> = listOf(
        MenuItem(t(S.MENU_PASTE), GestureAction.PASTE_FOCUSED),
        MenuItem(t(S.MENU_CUT), GestureAction.CUT_FOCUSED),
        MenuItem(t(S.MENU_SELECT_ALL), GestureAction.SELECT_ALL_FOCUSED),
        MenuItem(t(S.GA_CURSOR_START), GestureAction.CURSOR_START),
        MenuItem(t(S.GA_CURSOR_END), GestureAction.CURSOR_END),
        MenuItem(t(S.GA_SELECT_TO_START), GestureAction.SELECT_TO_START),
        MenuItem(t(S.GA_SELECT_TO_END), GestureAction.SELECT_TO_END),
        back(),
    )

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
    /** Trình đơn cha (trạng thái lúc mở trình đơn con); null ở cấp đầu. */
    val parent: MenuState? = null,
) {
    init {
        require(items.isNotEmpty()) { "Trình đơn phải có ít nhất một mục" }
        require(index in -1 until items.size) { "index ngoài phạm vi" }
    }

    val current: MenuItem? get() = items.getOrNull(index)

    fun next(): MenuState = MenuState(kind, items, if (index < 0) 0 else (index + 1) % items.size, parent)

    fun previous(): MenuState =
        MenuState(kind, items, if (index < 0) items.size - 1 else (index - 1 + items.size) % items.size, parent)

    /** Mở trình đơn con; [back] của nó trả về đúng trạng thái này (giữ nguyên mục đang chọn). */
    fun child(kind: MenuKind, items: List<MenuItem>): MenuState = MenuState(kind, items, -1, this)

    /** Trạng thái trình đơn cha, hoặc null nếu đang ở cấp đầu. */
    fun back(): MenuState? = parent

    /** "Tên, N mục." — luôn đọc như nhau, bất kể công tắc đọc hướng dẫn. */
    fun opening(): String = t(S.MENU_OPENING, kind.title, items.size)

    fun describeCurrent(hints: Boolean = true): String {
        val item = current ?: return opening()
        return if (hints) t(S.MENU_POSITION, item.label, index + 1, items.size) else item.label
    }

    companion object {
        fun open(kind: MenuKind, items: List<MenuItem>): MenuState = MenuState(kind, items, -1)
    }
}

/**
 * Chặn đọc lặp liên tiếp cùng một thông báo trong trình đơn (ví dụ người dùng vuốt sai nhiều lần chỉ nghe
 * lời nhắc một lần). Thuần hàm để kiểm thử.
 */
class AnnouncementGate {
    private var last: String? = null

    /** Ghi nhận một thông báo vừa đọc (không bị chặn): chuyển mục, mở trình đơn... */
    fun record(text: String) { last = text }

    /** Lời nhắc có nên đọc không: false nếu trùng thông báo vừa đọc. Nếu đọc thì ghi nhận luôn. */
    fun shouldSpeak(text: String): Boolean {
        if (text == last) return false
        last = text
        return true
    }

    fun reset() { last = null }
}
