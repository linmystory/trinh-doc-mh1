package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.Verbosity

/*
 * Màn "Chi tiết đọc nội dung" cũ (một trang cuộn dài 21 công tắc) đã được tách theo chủ đề; mỗi hàm dưới đây là một trang
 * của cây menu (core/SettingsMenu.kt, ui/AppRoot.kt). Các cài đặt đã chuyển chỗ nhưng KHÔNG đổi khóa lưu hay giá trị:
 *   - đọc người gọi, nói vị trí, báo giờ khi bật/tắt màn hình  → NotificationScreen
 *   - nhật ký chẩn đoán                                         → LogScreen
 *   - hai đoạn giải thích (phần tử không nhãn, chức năng gán vào cử chỉ) → HelpScreen
 */

/** Trang "Cách đọc phần tử": đọc những gì và chi tiết đến đâu cho mỗi phần tử khi duyệt. */
@Composable
fun ReadElementsScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var emoji by remember { mutableStateOf(settings.speakEmoji) }
    var autoOcr by remember { mutableStateOf(settings.autoOcrUnlabeled) }
    var listRange by remember { mutableStateOf(settings.speakListRange) }
    var hints by remember { mutableStateOf(settings.speakHints) }
    var actionHints by remember { mutableStateOf(settings.speakActionHints) }
    var verbosity by remember { mutableStateOf(settings.verbosity) }

    ScreenColumn(stringResource(R.string.sec_read_elements)) {
        SettingSwitch(
            title = stringResource(R.string.rd_emoji_title),
            description = stringResource(R.string.rd_emoji_desc),
            checked = emoji,
            onCheckedChange = {
                emoji = it
                settings.speakEmoji = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_autoocr_title),
            description = stringResource(R.string.rd_autoocr_desc),
            checked = autoOcr,
            onCheckedChange = {
                autoOcr = it
                settings.autoOcrUnlabeled = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_listrange_title),
            description = stringResource(R.string.rd_listrange_desc),
            checked = listRange,
            onCheckedChange = {
                listRange = it
                settings.speakListRange = it
            },
        )

        SectionHeading(stringResource(R.string.rd_g_hints))
        SettingSwitch(
            title = stringResource(R.string.rd_hint_title),
            description = stringResource(R.string.rd_hint_desc),
            checked = hints,
            onCheckedChange = {
                hints = it
                settings.speakHints = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_action_hint_title),
            description = stringResource(R.string.rd_action_hint_desc),
            checked = actionHints,
            onCheckedChange = {
                actionHints = it
                settings.speakActionHints = it
            },
        )
        SettingChoice(
            title = stringResource(R.string.rd_verbosity_title),
            description = stringResource(R.string.rd_verbosity_desc),
            options = Verbosity.entries.map { it.label },
            selectedIndex = verbosity.ordinal,
            onSelect = {
                verbosity = Verbosity.entries[it]
                settings.verbosity = verbosity
            },
        )
    }
}

/** Trang "Tiêu điểm": cách tiêu điểm di chuyển khi mở hộp thoại và khi quay lại một cửa sổ. */
@Composable
fun FocusScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var detailed by remember { mutableStateOf(settings.detailedFocus) }
    var dialogFocus by remember { mutableStateOf(settings.focusDialogs) }
    var focusMemory by remember { mutableStateOf(settings.rememberFocus) }

    ScreenColumn(stringResource(R.string.sec_focus)) {
        SettingSwitch(
            title = stringResource(R.string.rd_detailed_title),
            description = stringResource(R.string.rd_detailed_desc),
            checked = detailed,
            onCheckedChange = {
                detailed = it
                settings.detailedFocus = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_dialog_focus_title),
            description = stringResource(R.string.rd_dialog_focus_desc),
            checked = dialogFocus,
            onCheckedChange = {
                dialogFocus = it
                settings.focusDialogs = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_focus_memory_title),
            description = stringResource(R.string.rd_focus_memory_desc),
            checked = focusMemory,
            onCheckedChange = {
                focusMemory = it
                settings.rememberFocus = it
            },
        )
    }
}

/** Trang "Chạm và gõ phím": độ trễ khi lướt ngón tay, nhập phím bằng nhấc tay và những gì được đọc khi gõ. */
@Composable
fun TouchTypingScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var liftToType by remember { mutableStateOf(settings.liftToType) }
    var touchDelay by remember { mutableFloatStateOf(settings.touchDelayMs.toFloat()) }
    var sliders by remember { mutableStateOf(settings.scrollAdjustsSliders) }
    var echoChars by remember { mutableStateOf(settings.echoChars) }
    var echoWords by remember { mutableStateOf(settings.echoWords) }
    var capitals by remember { mutableStateOf(settings.speakCapitals) }
    var passwords by remember { mutableStateOf(settings.speakPasswords) }
    val msTemplate = stringResource(R.string.rd_touch_delay_value)

    ScreenColumn(stringResource(R.string.sec_touch_typing)) {
        SettingSwitch(
            title = stringResource(R.string.rd_lift_title),
            description = stringResource(R.string.rd_lift_desc),
            checked = liftToType,
            onCheckedChange = {
                liftToType = it
                settings.liftToType = it
            },
        )
        LabeledSlider(
            title = stringResource(R.string.rd_touch_delay_title),
            value = touchDelay,
            valueRange = 0f..SettingsRepository.MAX_TOUCH_DELAY_MS.toFloat(),
            steps = SettingsRepository.MAX_TOUCH_DELAY_MS / 50 - 1,
            onValueChange = { touchDelay = it },
            onValueChangeFinished = { settings.touchDelayMs = touchDelay.toInt() },
            valueText = { String.format(msTemplate, it.toInt()) },
        )
        BodyText(stringResource(R.string.rd_touch_delay_desc))
        SettingSwitch(
            title = stringResource(R.string.rd_slider_title),
            description = stringResource(R.string.rd_slider_desc),
            checked = sliders,
            onCheckedChange = {
                sliders = it
                settings.scrollAdjustsSliders = it
            },
        )

        SettingSwitch(
            title = stringResource(R.string.rd_echo_chars_title),
            description = stringResource(R.string.rd_echo_chars_desc),
            checked = echoChars,
            onCheckedChange = {
                echoChars = it
                settings.echoChars = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_echo_words_title),
            description = stringResource(R.string.rd_echo_words_desc),
            checked = echoWords,
            onCheckedChange = {
                echoWords = it
                settings.echoWords = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_caps_title),
            description = stringResource(R.string.rd_caps_desc),
            checked = capitals,
            onCheckedChange = {
                capitals = it
                settings.speakCapitals = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_pw_title),
            description = stringResource(R.string.rd_pw_desc),
            checked = passwords,
            onCheckedChange = {
                passwords = it
                settings.speakPasswords = it
            },
        )
    }
}

/** Trang "Phím cứng và nút tai nghe": bật các nguồn phím; hành động của từng phím gán ở "Cử chỉ tùy chỉnh". */
@Composable
fun HardwareKeysScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var keyShortcuts by remember { mutableStateOf(settings.keyboardShortcuts) }
    var volumeNav by remember { mutableStateOf(settings.volumeKeyNav) }
    var headset by remember { mutableStateOf(settings.headsetKey) }

    ScreenColumn(stringResource(R.string.sec_hw_keys)) {
        SettingSwitch(
            title = stringResource(R.string.rd_keys_title),
            description = stringResource(R.string.rd_keys_desc),
            checked = keyShortcuts,
            onCheckedChange = {
                keyShortcuts = it
                settings.keyboardShortcuts = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_vol_title),
            description = stringResource(R.string.rd_vol_desc),
            checked = volumeNav,
            onCheckedChange = {
                volumeNav = it
                settings.volumeKeyNav = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_headset_title),
            description = stringResource(R.string.rd_headset_desc),
            checked = headset,
            onCheckedChange = {
                headset = it
                settings.headsetKey = it
            },
        )
    }
}
