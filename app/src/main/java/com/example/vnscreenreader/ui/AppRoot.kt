package com.example.vnscreenreader.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.SearchEntry
import com.example.vnscreenreader.core.SettingsDestination
import com.example.vnscreenreader.core.SettingsMenu
import com.example.vnscreenreader.core.SettingsNav
import com.example.vnscreenreader.core.SettingsPage
import com.example.vnscreenreader.core.SettingsSearch

private const val WIDE_LAYOUT_DP = 600

/** Cột danh sách chiếm tỉ lệ chiều rộng (có chặn min/max) nên chuỗi dài của ngôn ngữ khác vẫn xuống dòng, không tràn. */
private const val LIST_WIDTH_FRACTION = 0.35f

private const val MAX_QUERY_CHARS = 80

/**
 * Giao diện chính: cây cài đặt hai tầng menu → trang (cấu trúc và quy tắc Back nằm ở core/SettingsMenu.kt, có test JVM).
 * Bố cục thích ứng:
 *  - Màn hình hẹp: danh sách chính chỉ hiện các menu; mở một menu ra danh sách trang con; mở một trang ra toàn màn hình.
 *    Back (hoặc hàng "Quay lại" ở đầu) lùi từng tầng: trang → menu → danh sách chính. Menu chỉ có một trang thì mở thẳng trang.
 *  - Màn hình rộng (máy tính bảng, gập mở, xoay ngang): cột trái hiện cả cây (tên menu rồi các trang của nó), cột phải là
 *    trang đang chọn nên không cần Back.
 * Chỗ đang đứng được lưu bằng một khóa chuỗi ([SettingsNav.resolve]) để sống sót khi xoay màn hình.
 */
@Composable
fun AppRoot() {
    var navKey by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = SettingsNav.resolve(navKey)
    // Chiều rộng cửa sổ (dp) lấy từ LocalWindowInfo: đúng cả khi chia đôi màn hình/đổi cỡ cửa sổ (khuyến nghị của lint).
    val density = LocalDensity.current
    val windowWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val wide = windowWidthDp >= WIDE_LAYOUT_DP

    val goTo = { target: SettingsDestination -> navKey = SettingsNav.keyOf(target) }
    val openMenu = { menu: SettingsMenu -> goTo(SettingsNav.open(menu)) }
    val openPage = { page: SettingsPage -> goTo(SettingsDestination.Page(page)) }

    // Màn hình hẹp: Back lùi một tầng thay vì thoát ứng dụng; chỉ ở danh sách chính mới để hệ thống xử lý (thoát).
    BackHandler(enabled = !wide && destination != SettingsDestination.Root) { goTo(SettingsNav.back(destination)) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        if (wide) {
            val page = SettingsNav.pageForWide(destination)
            Row(modifier = Modifier.fillMaxSize()) {
                RootList(
                    wide = true,
                    selected = page,
                    onOpenMenu = openMenu,
                    onOpenPage = openPage,
                    modifier = Modifier.fillMaxWidth(LIST_WIDTH_FRACTION).widthIn(min = 260.dp, max = 380.dp).fillMaxHeight(),
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { PageContent(page) }
            }
        } else {
            when (destination) {
                SettingsDestination.Root -> RootList(
                    wide = false,
                    selected = null,
                    onOpenMenu = openMenu,
                    onOpenPage = openPage,
                    modifier = Modifier.fillMaxSize(),
                )
                is SettingsDestination.Menu -> Column(modifier = Modifier.fillMaxSize()) {
                    BackRow(stringResource(R.string.ui_back_to_list)) { goTo(SettingsNav.back(destination)) }
                    Box(modifier = Modifier.weight(1f)) { MenuPage(destination.menu, openPage) }
                }
                is SettingsDestination.Page -> {
                    val parent = SettingsNav.parentMenu(destination.page)
                    val backText = if (parent != null) {
                        stringResource(R.string.ui_back_to_menu, stringResource(parent.titleRes()))
                    } else {
                        stringResource(R.string.ui_back_to_list)
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        BackRow(backText) { goTo(SettingsNav.back(destination)) }
                        Box(modifier = Modifier.weight(1f)) { PageContent(destination.page) }
                    }
                }
            }
        }
    }
}

/** Hàng "Quay lại" ở đầu trang (cùng việc với nút Back của hệ thống, cho người dùng không dùng cử chỉ Back). */
@Composable
private fun BackRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = HighContrastYellow,
        fontSize = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    )
}

/**
 * Danh sách chính. Ô tìm kiếm luôn có; khi đang tìm thì hiện các TRANG khớp (mở thẳng trang). Khi không tìm: màn hình hẹp
 * hiện sáu menu, màn hình rộng hiện cả cây (tên menu làm tiêu đề, dưới là các trang của nó).
 */
@Composable
private fun RootList(
    wide: Boolean,
    selected: SettingsPage?,
    onOpenMenu: (SettingsMenu) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    var query by rememberSaveable { mutableStateOf("") }
    // Chỉ mục tính lại khi đổi ngôn ngữ; tìm kiếm không phân biệt hoa thường và dấu.
    val index = remember(config) { buildSearchIndex(context) }
    val matches = remember(query, index) { SettingsSearch.search(index, query) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), color = HighContrastYellow, fontSize = 28.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(MAX_QUERY_CHARS) },
            label = { Text(stringResource(R.string.ss_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isNotBlank()) {
            PrimaryButton(stringResource(R.string.ss_clear)) { query = "" }
            val found = matches.mapNotNull { key -> SettingsNav.pageForKey(key) }
            if (found.isEmpty()) {
                Text(stringResource(R.string.ss_none), color = Color.White, fontSize = 18.sp)
            }
            found.forEach { page -> PageCard(page, page == selected, onOpenPage) }
        } else if (wide) {
            SettingsMenu.entries.forEach { menu ->
                Text(
                    text = stringResource(menu.titleRes()),
                    color = HighContrastGreen,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                menu.pages.forEach { page -> PageCard(page, page == selected, onOpenPage) }
            }
        } else {
            SettingsMenu.entries.forEach { menu -> MenuCard(menu, onOpenMenu) }
        }
    }
}

/** Danh sách trang con của một menu (màn hình hẹp, menu có từ hai trang). */
@Composable
private fun MenuPage(menu: SettingsMenu, onOpenPage: (SettingsPage) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(menu.titleRes()),
            color = HighContrastYellow,
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        menu.pages.forEach { page -> PageCard(page, false, onOpenPage) }
    }
}

@Composable
private fun MenuCard(menu: SettingsMenu, onOpenMenu: (SettingsMenu) -> Unit) {
    NavCard(stringResource(menu.titleRes()), stringResource(menu.summaryRes()), false) { onOpenMenu(menu) }
}

@Composable
private fun PageCard(page: SettingsPage, isSelected: Boolean, onOpenPage: (SettingsPage) -> Unit) {
    NavCard(stringResource(page.titleRes()), stringResource(page.summaryRes()), isSelected) { onOpenPage(page) }
}

/** Thẻ bấm được: một vùng chạm lớn đọc ra "tên. tóm tắt" (thêm "đang mở" cho trang đang chọn ở bố cục rộng). */
@Composable
private fun NavCard(title: String, summary: String, isSelected: Boolean, onClick: () -> Unit) {
    val openState = stringResource(R.string.ui_state_open)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(2.dp, if (isSelected) HighContrastYellow else Color.DarkGray, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp)
            .semantics {
                contentDescription = "$title. $summary"
                if (isSelected) stateDescription = openState
            },
    ) {
        Text(title, color = Color.White, fontSize = 20.sp)
        Text(summary, color = Color.LightGray, fontSize = 15.sp)
    }
}

/** Chữ tên menu, tên và tóm tắt trang là chuỗi riêng; hàm `when` đầy đủ nên thêm menu/trang mới mà quên chuỗi sẽ không biên dịch. */
@StringRes
private fun SettingsMenu.titleRes(): Int = when (this) {
    SettingsMenu.START -> R.string.hub_start
    SettingsMenu.AUDIO -> R.string.hub_audio
    SettingsMenu.CONTENT -> R.string.hub_content
    SettingsMenu.CONTROL -> R.string.hub_control
    SettingsMenu.TOOLS -> R.string.hub_tools
    SettingsMenu.HELP -> R.string.hub_help
}

/** Menu chỉ có một trang mở thẳng trang đó nên mượn tóm tắt của chính trang. */
@StringRes
private fun SettingsMenu.summaryRes(): Int = when (this) {
    SettingsMenu.START -> R.string.sec_status_sum
    SettingsMenu.AUDIO -> R.string.hub_audio_sum
    SettingsMenu.CONTENT -> R.string.hub_content_sum
    SettingsMenu.CONTROL -> R.string.hub_control_sum
    SettingsMenu.TOOLS -> R.string.hub_tools_sum
    SettingsMenu.HELP -> R.string.sec_help_sum
}

@StringRes
private fun SettingsPage.titleRes(): Int = when (this) {
    SettingsPage.STATUS -> R.string.sec_status
    SettingsPage.VOICE -> R.string.sec_voice
    SettingsPage.FEEDBACK -> R.string.sec_feedback
    SettingsPage.READ_ELEMENTS -> R.string.sec_read_elements
    SettingsPage.FOCUS -> R.string.sec_focus
    SettingsPage.NOTIFICATIONS -> R.string.sec_notif
    SettingsPage.GESTURES -> R.string.sec_gestures
    SettingsPage.APP_GESTURES -> R.string.sec_appgest
    SettingsPage.GESTURE_TEST -> R.string.sec_gesttest
    SettingsPage.MENUS -> R.string.sec_menus
    SettingsPage.TOUCH_TYPING -> R.string.sec_touch_typing
    SettingsPage.HARDWARE_KEYS -> R.string.sec_hw_keys
    SettingsPage.LABELS -> R.string.sec_labels
    SettingsPage.PLUGINS -> R.string.sec_plugins
    SettingsPage.BACKUP -> R.string.sec_backup
    SettingsPage.LOG -> R.string.sec_log
    SettingsPage.HELP -> R.string.sec_help
}

@StringRes
private fun SettingsPage.summaryRes(): Int = when (this) {
    SettingsPage.STATUS -> R.string.sec_status_sum
    SettingsPage.VOICE -> R.string.sec_voice_sum
    SettingsPage.FEEDBACK -> R.string.sec_feedback_sum
    SettingsPage.READ_ELEMENTS -> R.string.sec_read_elements_sum
    SettingsPage.FOCUS -> R.string.sec_focus_sum
    SettingsPage.NOTIFICATIONS -> R.string.sec_notif_sum
    SettingsPage.GESTURES -> R.string.sec_gestures_sum
    SettingsPage.APP_GESTURES -> R.string.sec_appgest_sum
    SettingsPage.GESTURE_TEST -> R.string.sec_gesttest_sum
    SettingsPage.MENUS -> R.string.sec_menus_sum
    SettingsPage.TOUCH_TYPING -> R.string.sec_touch_typing_sum
    SettingsPage.HARDWARE_KEYS -> R.string.sec_hw_keys_sum
    SettingsPage.LABELS -> R.string.sec_labels_sum
    SettingsPage.PLUGINS -> R.string.sec_plugins_sum
    SettingsPage.BACKUP -> R.string.sec_backup_sum
    SettingsPage.LOG -> R.string.sec_log_sum
    SettingsPage.HELP -> R.string.sec_help_sum
}

/** Chỉ mục tìm kiếm theo TRANG: tên trang, tóm tắt, tên menu chứa trang (gõ "Điều khiển" ra mọi trang của menu) và các chuỗi trong [SETTINGS_INDEX]. */
private fun buildSearchIndex(context: Context): List<SearchEntry> = SettingsPage.entries.flatMap { page ->
    val menuTitle = context.getString(SettingsNav.menuOf(page).titleRes())
    buildList {
        add(SearchEntry(page.key, context.getString(page.titleRes()), true))
        add(SearchEntry(page.key, context.getString(page.summaryRes()), false))
        add(SearchEntry(page.key, menuTitle, false))
        SETTINGS_INDEX[page.key]?.forEach { add(SearchEntry(page.key, context.getString(it.id), it.isTitle)) }
    }
}

@Composable
private fun PageContent(page: SettingsPage) {
    when (page) {
        SettingsPage.STATUS -> StatusScreen()
        SettingsPage.VOICE -> VoiceScreen()
        SettingsPage.FEEDBACK -> FeedbackScreen()
        SettingsPage.READ_ELEMENTS -> ReadElementsScreen()
        SettingsPage.FOCUS -> FocusScreen()
        SettingsPage.NOTIFICATIONS -> NotificationScreen()
        SettingsPage.GESTURES -> GestureSettingsScreen()
        SettingsPage.APP_GESTURES -> AppGesturesScreen()
        SettingsPage.GESTURE_TEST -> GestureTestScreen()
        SettingsPage.MENUS -> MenusScreen()
        SettingsPage.TOUCH_TYPING -> TouchTypingScreen()
        SettingsPage.HARDWARE_KEYS -> HardwareKeysScreen()
        SettingsPage.LABELS -> LabelsScreen()
        SettingsPage.PLUGINS -> PluginManagerScreen()
        SettingsPage.BACKUP -> BackupScreen()
        SettingsPage.LOG -> LogScreen()
        SettingsPage.HELP -> HelpScreen()
    }
}
