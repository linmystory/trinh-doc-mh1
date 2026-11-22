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

/**
 * Các trình đơn: hai trình đơn gốc ([GLOBAL], [LOCAL]) và các trình đơn con của chúng. Trình đơn con của trình đơn chung là các
 * nhóm của [MenuCatalog] ([isGroup]); trình đơn con của phần tử là [LOCAL_EDIT].
 */
enum class MenuKind(private val titleKey: S, val isGroup: Boolean = false) {
    GLOBAL(S.MENU_GLOBAL_TITLE),
    LOCAL(S.MENU_LOCAL_TITLE),
    LOCAL_EDIT(S.MENU_EDIT_TITLE),
    GROUP_READING(S.MENU_GROUP_READING, isGroup = true),
    GROUP_BROWSE(S.MENU_GROUP_BROWSE, isGroup = true),
    GROUP_SCROLL(S.MENU_GROUP_SCROLL, isGroup = true),
    GROUP_ELEMENT(S.MENU_GROUP_ELEMENT, isGroup = true),
    GROUP_TEXT(S.MENU_EDIT_TITLE, isGroup = true),
    GROUP_SYSTEM(S.MENU_GROUP_SYSTEM, isGroup = true),
    GROUP_TOOLS(S.MENU_GROUP_TOOLS, isGroup = true);

    /** Tiêu đề theo ngôn ngữ hiện hành. */
    val title: String get() = t(titleKey)
}

/** Hành động tùy chỉnh do ứng dụng khai báo cho một phần tử (ví dụ "Xóa tin nhắn"). */
data class CustomAction(val id: Int, val label: String)

object Menus {

    /** Số mục hành động ở đầu trình đơn chung (chưa tính các mục mở nhóm). */
    const val MAX_GLOBAL_TOP_ACTIONS = 7

    /**
     * Hành động đứng đầu trình đơn chung, theo thứ tự: 7 mục dùng nhiều nhất, giữ nguyên vị trí như các bản trước để không phá thói
     * quen vuốt. Mỗi mục cũng có mặt trong nhóm của nó, nên mỗi nhóm đủ chức năng của chủ đề đó.
     */
    val GLOBAL_SHORTCUTS: List<GestureAction> = listOf(
        GestureAction.READ_FROM_TOP, GestureAction.READ_FROM_CURRENT, GestureAction.REPEAT_LAST,
        GestureAction.SEARCH_SCREEN, GestureAction.OCR_SCREEN, GestureAction.NOTIFICATIONS, GestureAction.HOME,
    )

    /** Số hành động tùy chỉnh của ứng dụng tối đa ở cuối trình đơn phần tử. */
    const val MAX_CUSTOM_ACTIONS = 20

    /**
     * Một số chức năng có tên ngắn riêng khi đọc trong trình đơn (nghe nhanh hơn tên đầy đủ dùng ở mục Cử chỉ tùy chỉnh); phần còn lại
     * dùng tên chung của [GestureAction].
     */
    private val MENU_LABELS: Map<GestureAction, S> = mapOf(
        GestureAction.ACTIVATE to S.MENU_ACTIVATE,
        GestureAction.LONG_CLICK_FOCUSED to S.MENU_LONG_CLICK,
        GestureAction.UNIT_NEXT to S.MENU_UNIT_NEXT,
        GestureAction.UNIT_PREVIOUS to S.MENU_UNIT_PREV,
        GestureAction.CYCLE_GRANULARITY to S.MENU_GRANULARITY,
        GestureAction.COPY_UNIT to S.MENU_COPY_UNIT,
        GestureAction.COPY_FOCUSED to S.MENU_COPY,
        GestureAction.SPELL_FOCUSED to S.MENU_SPELL,
        GestureAction.LABEL_FOCUSED to S.MENU_LABEL,
        GestureAction.DESCRIBE_FOCUSED to S.MENU_DESCRIBE,
        GestureAction.OCR_FOCUSED to S.MENU_OCR_FOCUSED,
        GestureAction.DESCRIBE_TECHNICAL to S.MENU_TECH_INFO,
        GestureAction.PASTE_FOCUSED to S.MENU_PASTE,
        GestureAction.CUT_FOCUSED to S.MENU_CUT,
        GestureAction.SELECT_ALL_FOCUSED to S.MENU_SELECT_ALL,
        GestureAction.NOTIFICATIONS to S.MSG_NOTIFICATIONS,
        GestureAction.HOME to S.MSG_HOME,
        GestureAction.QUICK_SETTINGS to S.MSG_QUICK_SETTINGS,
        GestureAction.RECENTS to S.MSG_RECENTS,
        GestureAction.TOGGLE_DETAILED_FOCUS to S.MENU_DETAILED_FOCUS,
    )

    /** Tên của một chức năng khi đọc trong trình đơn (và khi liệt kê trong trình chỉnh sửa trình đơn). */
    fun labelFor(action: GestureAction): String = MENU_LABELS[action]?.let { t(it) } ?: action.label

    private fun actionItem(action: GestureAction) = MenuItem(labelFor(action), action)

    private fun back() = MenuItem(t(S.MENU_BACK), isBack = true)

    /**
     * Trình đơn chung, cấp đầu: [GLOBAL_SHORTCUTS] rồi mỗi nhóm của [MenuGroup] là một mục mở trình đơn con. Không sửa được, nên
     * luôn đủ mọi chức năng của [MenuCatalog] (xem [submenu]).
     */
    fun global(): List<MenuItem> = buildList {
        GLOBAL_SHORTCUTS.forEach { add(actionItem(it)) }
        MenuGroup.entries.forEach { add(MenuItem(it.title, submenu = it.kind)) }
    }

    /** Mọi chức năng gọi được từ trình đơn chung (cấp đầu và các nhóm), không trùng. */
    fun globalActions(): Set<GestureAction> = buildSet {
        addAll(GLOBAL_SHORTCUTS)
        MenuGroup.entries.forEach { addAll(it.actions) }
    }

    /**
     * Danh sách của một trình đơn con: một nhóm của trình đơn chung hoặc "Chỉnh sửa văn bản" của phần tử; null nếu [kind] không
     * phải trình đơn con. Mục cuối luôn là "Quay lại".
     */
    fun submenu(kind: MenuKind): List<MenuItem>? {
        if (kind == MenuKind.LOCAL_EDIT) return localEdit()
        val group = MenuGroup.forKind(kind) ?: return null
        return group.actions.map { actionItem(it) } + back()
    }

    /**
     * Trình đơn của phần tử đang chọn: các chức năng của [layout] (mặc định là 12 mục như các bản trước), rồi mục "Chỉnh sửa văn bản"
     * khi [editable] (chỉ ở ô nhập), rồi các hành động tùy chỉnh của chính phần tử đó. Hai loại sau luôn nằm cuối và không tính vào
     * giới hạn của [layout].
     *
     * Với ảnh ([imageLike]), nếu người dùng CHƯA tùy chỉnh thì các mục đọc ảnh (mô tả, đọc chữ) lên ĐẦU để mở trình đơn rồi chạm hai
     * lần là xong. Đã tùy chỉnh thì thứ tự của người dùng được giữ nguyên.
     */
    fun local(
        custom: List<CustomAction>,
        editable: Boolean = false,
        imageLike: Boolean = false,
        layout: MenuLayout = MenuLayout.DEFAULT,
    ): List<MenuItem> = buildList {
        var actions = layout.actions
        if (imageLike && !layout.isCustomized) {
            val imageFirst = actions.filter { it == GestureAction.DESCRIBE_FOCUSED || it == GestureAction.OCR_FOCUSED }
            actions = imageFirst + actions.filter { it !in imageFirst }
        }
        actions.forEach { add(actionItem(it)) }
        if (editable) add(MenuItem(t(S.MENU_EDIT_TITLE), submenu = MenuKind.LOCAL_EDIT))
        custom.take(MAX_CUSTOM_ACTIONS).forEach { add(MenuItem(it.label, customActionId = it.id)) }
    }

    /** Trình đơn con "Chỉnh sửa văn bản" của phần tử: đúng các chức năng của nhóm cùng tên; mục cuối là "Quay lại". */
    fun localEdit(): List<MenuItem> = MenuGroup.TEXT.actions.map { actionItem(it) } + back()

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
 * chọn). Bất biến: mỗi thao tác trả về trạng thái mới. Chuyển mục theo vòng tròn.
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

    /** Chọn thẳng mục ở vị trí [position] (khi người dùng chạm vào hàng đó); giữ nguyên nếu [position] ngoài phạm vi hoặc đang chọn rồi. */
    fun select(position: Int): MenuState =
        if (position in items.indices && position != index) MenuState(kind, items, position, parent) else this

    /** Mở trình đơn con; [back] của nó trả về đúng trạng thái này (giữ nguyên mục đang chọn). */
    fun child(kind: MenuKind, items: List<MenuItem>): MenuState = MenuState(kind, items, -1, this)

    /** Trạng thái trình đơn cha, hoặc null nếu đang ở cấp đầu. */
    fun back(): MenuState? = parent

    /** "Tên, N mục." — luôn đọc như nhau, bất kể công tắc đọc hướng dẫn. */
    fun opening(): String = t(S.MENU_OPENING, kind.title, items.size)

    /**
     * Câu đọc cho mục đang chọn: "tên, i trên N" (bật gợi ý) hoặc chỉ tên (tắt gợi ý). Mục mở một nhóm/trình đơn con đọc thêm "nhóm"
     * ([S.MENU_POSITION_GROUP]) để người dùng biết chạm hai lần sẽ mở một danh sách chứ không chạy chức năng.
     */
    fun describeCurrent(hints: Boolean = true): String {
        val item = current ?: return opening()
        if (!hints) return item.label
        return t(if (item.submenu != null) S.MENU_POSITION_GROUP else S.MENU_POSITION, item.label, index + 1, items.size)
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
