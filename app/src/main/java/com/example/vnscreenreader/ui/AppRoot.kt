package com.example.vnscreenreader.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import com.example.vnscreenreader.core.SettingsSearch
import com.example.vnscreenreader.core.SearchEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.OutlinedTextField
import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Các mục cài đặt, sắp theo thứ tự quen thuộc: giọng nói → âm báo và rung → cử chỉ và trình đơn
 * → nâng cao → tiện ích. `key` được lưu khi xoay màn hình nên không đổi tên tùy tiện.
 */
private enum class Section(
    val key: String,
    @StringRes val group: Int,
    @StringRes val title: Int,
    @StringRes val summary: Int,
) {
    STATUS("status", R.string.grp_basic, R.string.sec_status, R.string.sec_status_sum),
    VOICE("voice", R.string.grp_voice, R.string.sec_voice, R.string.sec_voice_sum),
    FEEDBACK("feedback", R.string.grp_voice, R.string.sec_feedback, R.string.sec_feedback_sum),
    NOTIFICATIONS("notifications", R.string.grp_voice, R.string.sec_notif, R.string.sec_notif_sum),
    GESTURES("gestures", R.string.grp_control, R.string.sec_gestures, R.string.sec_gestures_sum),
    APP_GESTURES("appgestures", R.string.grp_control, R.string.sec_appgest, R.string.sec_appgest_sum),
    GESTURE_TEST("gesturetest", R.string.grp_control, R.string.sec_gesttest, R.string.sec_gesttest_sum),
    MENUS("menus", R.string.grp_control, R.string.sec_menus, R.string.sec_menus_sum),
    READING("reading", R.string.grp_advanced, R.string.sec_reading, R.string.sec_reading_sum),
    LABELS("labels", R.string.grp_advanced, R.string.sec_labels, R.string.sec_labels_sum),
    PERMISSIONS("permissions", R.string.grp_advanced, R.string.sec_perms, R.string.sec_perms_sum),
    LOG("log", R.string.grp_advanced, R.string.sec_log, R.string.sec_log_sum),
    BACKUP("backup", R.string.grp_advanced, R.string.sec_backup, R.string.sec_backup_sum),
    PLUGINS("plugins", R.string.grp_extend, R.string.sec_plugins, R.string.sec_plugins_sum),
    HELP("help", R.string.grp_help, R.string.sec_help, R.string.sec_help_sum);

    companion object {
        fun fromKey(key: String?): Section? = entries.firstOrNull { it.key == key }
    }
}

private const val WIDE_LAYOUT_DP = 600

/** Cột danh sách chiếm tỉ lệ chiều rộng (có chặn min/max) nên chuỗi dài của ngôn ngữ khác vẫn xuống dòng, không tràn. */
private const val LIST_WIDTH_FRACTION = 0.35f

/**
 * Bố cục thích ứng: màn hình rộng (máy tính bảng, gập mở, xoay ngang) hiện danh sách mục bên trái và nội dung bên
 * phải; màn hình hẹp hiện danh sách rồi mở từng mục toàn màn hình (nút Back quay về danh sách).
 */
@Composable
fun AppRoot() {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = Section.fromKey(selectedKey)
    // Chiều rộng cửa sổ (dp) lấy từ LocalWindowInfo: đúng cả khi chia đôi màn hình/đổi cỡ cửa sổ (khuyến nghị của lint).
    val density = LocalDensity.current
    val windowWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val wide = windowWidthDp >= WIDE_LAYOUT_DP

    // Màn hình hẹp: Back từ một mục quay về danh sách thay vì thoát ứng dụng.
    BackHandler(enabled = !wide && selected != null) { selectedKey = null }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                SectionList(
                    selected = selected ?: Section.STATUS,
                    onSelect = { selectedKey = it.key },
                    modifier = Modifier.fillMaxWidth(LIST_WIDTH_FRACTION).widthIn(min = 260.dp, max = 380.dp).fillMaxHeight(),
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SectionContent(selected ?: Section.STATUS)
                }
            }
        } else if (selected == null) {
            SectionList(selected = null, onSelect = { selectedKey = it.key }, modifier = Modifier.fillMaxSize())
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.ui_back_to_list),
                    color = HighContrastYellow,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Button) { selectedKey = null }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                )
                Box(modifier = Modifier.weight(1f)) { SectionContent(selected) }
            }
        }
    }
}

@Composable
private fun SectionList(selected: Section?, onSelect: (Section) -> Unit, modifier: Modifier) {
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
            val found = matches.mapNotNull { key -> Section.fromKey(key) }
            if (found.isEmpty()) {
                Text(stringResource(R.string.ss_none), color = Color.White, fontSize = 18.sp)
            }
            found.forEach { section -> SectionCard(section, section == selected, onSelect) }
        } else {
            var lastGroup: Int? = null
            Section.entries.forEach { section ->
                if (section.group != lastGroup) {
                    lastGroup = section.group
                    Text(
                        text = stringResource(section.group),
                        color = HighContrastGreen,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                SectionCard(section, section == selected, onSelect)
            }
        }
    }
}

@Composable
private fun SectionCard(section: Section, isSelected: Boolean, onSelect: (Section) -> Unit) {
    val title = stringResource(section.title)
    val summary = stringResource(section.summary)
    val openState = stringResource(R.string.ui_state_open)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(2.dp, if (isSelected) HighContrastYellow else Color.DarkGray, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onSelect(section) }
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

private const val MAX_QUERY_CHARS = 80

private fun buildSearchIndex(context: Context): List<SearchEntry> = Section.entries.flatMap { section ->
    buildList {
        add(SearchEntry(section.key, context.getString(section.title), true))
        add(SearchEntry(section.key, context.getString(section.summary), false))
        SETTINGS_INDEX[section.key]?.forEach { add(SearchEntry(section.key, context.getString(it.id), it.isTitle)) }
    }
}

@Composable
private fun SectionContent(section: Section) {
    when (section) {
        Section.STATUS -> StatusScreen()
        Section.VOICE -> VoiceScreen()
        Section.FEEDBACK -> FeedbackScreen()
        Section.MENUS -> MenusScreen()
        Section.GESTURE_TEST -> GestureTestScreen()
        Section.READING -> ReadingScreen()
        Section.LABELS -> LabelsScreen()
        Section.PERMISSIONS -> PermissionsScreen()
        Section.LOG -> LogScreen()
        Section.BACKUP -> BackupScreen()
        Section.NOTIFICATIONS -> NotificationScreen()
        Section.GESTURES -> GestureSettingsScreen()
        Section.APP_GESTURES -> AppGesturesScreen()
        Section.PLUGINS -> PluginManagerScreen()
        Section.HELP -> HelpScreen()
    }
}
