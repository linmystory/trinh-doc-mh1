package com.example.vnscreenreader.core

/**
 * Một trang cài đặt: màn hình người dùng thật sự chỉnh. [key] được lưu qua lần xoay màn hình (rememberSaveable) và dùng làm
 * khóa chỉ mục tìm kiếm nên không đổi tên tùy tiện; khóa của bản trước khi có menu vẫn mở được (xem [SettingsNav.resolve]).
 * Thứ tự khai báo ở đây KHÔNG phải thứ tự hiển thị: thứ tự hiển thị là thứ tự trong [SettingsMenu.pages].
 */
enum class SettingsPage(val key: String) {
    STATUS("status"),
    VOICE("voice"),
    FEEDBACK("feedback"),
    READ_ELEMENTS("read_elements"),
    FOCUS("focus"),
    NOTIFICATIONS("notifications"),
    GESTURES("gestures"),
    APP_GESTURES("appgestures"),
    GESTURE_TEST("gesturetest"),
    MENUS("menus"),
    TOUCH_TYPING("touch_typing"),
    HARDWARE_KEYS("hardware_keys"),
    LABELS("labels"),
    PLUGINS("plugins"),
    BACKUP("backup"),
    LOG("log"),
    HELP("help"),
}

/**
 * Một menu: nhóm các trang cùng chủ đề, theo đúng thứ tự hiển thị. Danh sách chính chỉ hiện các menu; mở một menu mới thấy
 * các trang con của nó.
 */
enum class SettingsMenu(val key: String, val pages: List<SettingsPage>) {
    START("hub_start", listOf(SettingsPage.STATUS)),
    AUDIO("hub_audio", listOf(SettingsPage.VOICE, SettingsPage.FEEDBACK)),
    CONTENT("hub_content", listOf(SettingsPage.READ_ELEMENTS, SettingsPage.FOCUS, SettingsPage.NOTIFICATIONS)),
    CONTROL(
        "hub_control",
        listOf(
            SettingsPage.GESTURES,
            SettingsPage.APP_GESTURES,
            SettingsPage.GESTURE_TEST,
            SettingsPage.MENUS,
            SettingsPage.TOUCH_TYPING,
            SettingsPage.HARDWARE_KEYS,
        ),
    ),
    TOOLS("hub_tools", listOf(SettingsPage.LABELS, SettingsPage.PLUGINS, SettingsPage.BACKUP, SettingsPage.LOG)),
    HELP("hub_help", listOf(SettingsPage.HELP));

    /** Menu chỉ có một trang thì mở thẳng trang đó, không bắt người dùng qua một danh sách một mục. */
    val directPage: SettingsPage? get() = pages.singleOrNull()
}

/** Nơi người dùng đang đứng trong cây cài đặt. */
sealed interface SettingsDestination {
    /** Danh sách chính (các menu). */
    data object Root : SettingsDestination

    /** Danh sách trang con của một menu (chỉ dùng cho menu có từ hai trang trở lên). */
    data class Menu(val menu: SettingsMenu) : SettingsDestination

    /** Một trang cài đặt. */
    data class Page(val page: SettingsPage) : SettingsDestination
}

/**
 * Điều hướng trong cây menu: đổi khóa đã lưu thành [SettingsDestination] và ngược lại, nút Back lùi từng tầng, và trang
 * hiển thị ở bố cục rộng. Toàn bộ là hàm thuần để kiểm thử (SettingsMenuTest); giao diện chỉ gọi lại.
 */
object SettingsNav {
    /**
     * Khóa của các trang ở bản trước khi có menu mà nay đã tách hoặc gộp: "Chi tiết đọc" (reading) tách thành nhiều trang,
     * lấy trang đầu; "Quyền" (permissions) đã gộp vào "Trạng thái và quyền".
     */
    private val LEGACY_PAGE_KEYS: Map<String, SettingsPage> = mapOf(
        "reading" to SettingsPage.READ_ELEMENTS,
        "permissions" to SettingsPage.STATUS,
    )

    private val pageByKey: Map<String, SettingsPage> = SettingsPage.entries.associateBy { it.key }
    private val menuByKey: Map<String, SettingsMenu> = SettingsMenu.entries.associateBy { it.key }
    private val menuByPage: Map<SettingsPage, SettingsMenu> =
        SettingsMenu.entries.flatMap { menu -> menu.pages.map { page -> page to menu } }.toMap()

    /** Trang hiển thị ở bố cục rộng khi chưa chọn gì: trang đầu của menu đầu. */
    val defaultPage: SettingsPage = SettingsMenu.entries.first().pages.first()

    /** Menu chứa [page] (mỗi trang đúng một menu, được kiểm thử). */
    fun menuOf(page: SettingsPage): SettingsMenu = checkNotNull(menuByPage[page]) { "Trang ${page.key} không thuộc menu nào" }

    /** Menu mà nút "Quay lại" của [page] đưa về; null nghĩa là về thẳng danh sách chính (menu chỉ có một trang). */
    fun parentMenu(page: SettingsPage): SettingsMenu? = menuOf(page).takeIf { it.directPage == null }

    /** Trang ứng với [key] (khóa hiện tại hoặc khóa cũ); null nếu không có. */
    fun pageForKey(key: String?): SettingsPage? = key?.let { pageByKey[it] ?: LEGACY_PAGE_KEYS[it] }

    /** Khóa đã lưu → nơi đứng; null hoặc khóa lạ về danh sách chính nên dữ liệu lưu hỏng không làm sập giao diện. */
    fun resolve(key: String?): SettingsDestination {
        if (key == null) return SettingsDestination.Root
        menuByKey[key]?.let { return open(it) }
        pageForKey(key)?.let { return SettingsDestination.Page(it) }
        return SettingsDestination.Root
    }

    /** Nơi đứng → khóa để lưu; danh sách chính là null. */
    fun keyOf(destination: SettingsDestination): String? = when (destination) {
        SettingsDestination.Root -> null
        is SettingsDestination.Menu -> destination.menu.key
        is SettingsDestination.Page -> destination.page.key
    }

    /** Chọn một menu ở danh sách chính: menu một trang mở thẳng trang đó, còn lại mở danh sách trang con. */
    fun open(menu: SettingsMenu): SettingsDestination =
        menu.directPage?.let { SettingsDestination.Page(it) } ?: SettingsDestination.Menu(menu)

    /** Back lùi đúng một tầng: trang → menu của nó (hoặc danh sách chính nếu menu chỉ có một trang) → danh sách chính. */
    fun back(destination: SettingsDestination): SettingsDestination = when (destination) {
        SettingsDestination.Root -> SettingsDestination.Root
        is SettingsDestination.Menu -> SettingsDestination.Root
        is SettingsDestination.Page ->
            parentMenu(destination.page)?.let { SettingsDestination.Menu(it) } ?: SettingsDestination.Root
    }

    /**
     * Bố cục rộng luôn có một trang ở khung nội dung (cây menu nằm cố định bên trái): trang đang mở, hoặc trang đầu của menu
     * đang mở, hoặc trang mặc định khi đang ở danh sách chính.
     */
    fun pageForWide(destination: SettingsDestination): SettingsPage = when (destination) {
        SettingsDestination.Root -> defaultPage
        is SettingsDestination.Menu -> destination.menu.pages.first()
        is SettingsDestination.Page -> destination.page
    }
}
