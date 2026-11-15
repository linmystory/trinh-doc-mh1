package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material3.Button
import com.example.vnscreenreader.core.Diag
import androidx.compose.material3.Text
import com.example.vnscreenreader.core.PermissionFeatures
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.Verbosity
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

/** Số ký tự nhật ký tối đa chép vào clipboard (clipboard đi qua Binder nên có giới hạn dung lượng). */
private const val DIAG_COPY_MAX_CHARS = 100_000

@Composable
fun ReadingScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var passwords by remember { mutableStateOf(settings.speakPasswords) }
    var hints by remember { mutableStateOf(settings.speakHints) }
    var emoji by remember { mutableStateOf(settings.speakEmoji) }
    var autoOcr by remember { mutableStateOf(settings.autoOcrUnlabeled) }
    var detailed by remember { mutableStateOf(settings.detailedFocus) }
    var diagOn by remember { mutableStateOf(settings.diagnosticsEnabled) }
    var diagLines by remember { mutableStateOf(Diag.lineCount()) }
    var actionHints by remember { mutableStateOf(settings.speakActionHints) }
    var verbosity by remember { mutableStateOf(settings.verbosity) }
    var liftToType by remember { mutableStateOf(settings.liftToType) }
    var keyShortcuts by remember { mutableStateOf(settings.keyboardShortcuts) }
    var echoChars by remember { mutableStateOf(settings.echoChars) }
    var echoWords by remember { mutableStateOf(settings.echoWords) }
    var capitals by remember { mutableStateOf(settings.speakCapitals) }
    var listRange by remember { mutableStateOf(settings.speakListRange) }
    var dialogFocus by remember { mutableStateOf(settings.focusDialogs) }
    var focusMemory by remember { mutableStateOf(settings.rememberFocus) }
    var screenState by remember { mutableStateOf(settings.announceScreen) }
    var volumeNav by remember { mutableStateOf(settings.volumeKeyNav) }
    var headset by remember { mutableStateOf(settings.headsetKey) }
    var sliders by remember { mutableStateOf(settings.scrollAdjustsSliders) }
    var callAnnounce by remember { mutableStateOf(settings.announceCalls) }
    var location by remember { mutableStateOf(settings.locationEnabled) }
    // Chỉ xin quyền khi người dùng BẬT tính năng; từ chối thì công tắc giữ nguyên tắt.
    // Một công tắc "đọc người gọi": quyền cuộc gọi là bắt buộc; quyền danh bạ là tùy chọn (không cấp thì chỉ đọc số).
    val askContacts = rememberPermissionRequest(PermissionFeatures.contacts) { }
    val askCaller = rememberPermissionRequest(PermissionFeatures.caller) {
        callAnnounce = true
        settings.announceCalls = true
        askContacts()
    }
    val askLocation = rememberPermissionRequest(PermissionFeatures.location) {
        location = true
        settings.locationEnabled = true
    }
    var touchDelay by remember { mutableStateOf(settings.touchDelayMs.toFloat()) }
    val msTemplate = stringResource(R.string.rd_touch_delay_value)

    ScreenColumn(stringResource(R.string.rd_title)) {
        SectionHeading(stringResource(R.string.rd_g_content))
        SettingSwitch(
            title = stringResource(R.string.rd_pw_title),
            description = stringResource(R.string.rd_pw_desc),
            checked = passwords,
            onCheckedChange = {
                passwords = it
                settings.speakPasswords = it
            },
        )
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
            title = stringResource(R.string.rd_detailed_title),
            description = stringResource(R.string.rd_detailed_desc),
            checked = detailed,
            onCheckedChange = {
                detailed = it
                settings.detailedFocus = it
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
        SettingSwitch(
            title = stringResource(R.string.rd_screen_title),
            description = stringResource(R.string.rd_screen_desc),
            checked = screenState,
            onCheckedChange = {
                screenState = it
                settings.announceScreen = it
            },
        )

        SettingSwitch(
            title = stringResource(R.string.rd_slider_title),
            description = stringResource(R.string.rd_slider_desc),
            checked = sliders,
            onCheckedChange = {
                sliders = it
                settings.scrollAdjustsSliders = it
            },
        )

        SectionHeading(stringResource(R.string.rd_g_more))
        SettingSwitch(
            title = stringResource(R.string.rd_call_title),
            description = stringResource(R.string.rd_call_desc),
            checked = callAnnounce,
            onCheckedChange = { on ->
                if (on) {
                    askCaller()
                } else {
                    callAnnounce = false
                    settings.announceCalls = false
                }
            },
        )
        SettingSwitch(
            title = stringResource(R.string.rd_location_title),
            description = stringResource(R.string.rd_location_desc),
            checked = location,
            onCheckedChange = { on ->
                if (on) {
                    askLocation()
                } else {
                    location = false
                    settings.locationEnabled = false
                }
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

        SectionHeading(stringResource(R.string.rd_g_touch))
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

        SectionHeading(stringResource(R.string.rd_g_keys))
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

        SettingSwitch(
            title = stringResource(R.string.rd_diag_title),
            description = stringResource(R.string.rd_diag_desc),
            checked = diagOn,
            onCheckedChange = {
                diagOn = it
                settings.diagnosticsEnabled = it
            },
        )
        BodyText(stringResource(R.string.rd_diag_lines, diagLines))
        val copiedText = stringResource(R.string.rd_diag_copied)
        val copyFailedText = stringResource(R.string.rd_diag_copy_failed)
        val clearedText = stringResource(R.string.rd_diag_cleared)
        Button(onClick = {
            val copied = try {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("vnscreenreader-diag", Diag.text(DIAG_COPY_MAX_CHARS)))
                clipboard != null
            } catch (e: RuntimeException) {
                false // clipboard từ chối (quá lớn, bị hệ thống chặn...): báo thật, không sập
            }
            diagLines = Diag.lineCount()
            Toast.makeText(context, if (copied) copiedText else copyFailedText, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.rd_diag_copy)) }
        Button(onClick = {
            Diag.clear()
            diagLines = 0
            Toast.makeText(context, clearedText, Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.rd_diag_clear)) }

        SectionHeading(stringResource(R.string.rd_unlabeled_head))
        BodyText(stringResource(R.string.rd_unlabeled_intro))
        BodyText(stringResource(R.string.rd_unlabeled_1))
        BodyText(stringResource(R.string.rd_unlabeled_2))
        BodyText(stringResource(R.string.rd_unlabeled_3))
        BodyText(stringResource(R.string.rd_unlabeled_4))
        BodyText(stringResource(R.string.rd_unlabeled_5))

        SectionHeading(stringResource(R.string.rd_ai_head))
        BodyText(
            stringResource(R.string.rd_ai_body),
        )
    }
}
