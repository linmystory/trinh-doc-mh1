package com.example.vnscreenreader.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.KeywordList
import com.example.vnscreenreader.core.SettingsRepository

/**
 * Đọc thông báo: công tắc chính (trước đây nằm ở màn Đọc, nay chỉ ở đây để không có hai nơi cho một cài đặt), các hoàn cảnh
 * yên lặng và hai danh sách từ khóa. Quy tắc ưu tiên nằm ở [com.example.vnscreenreader.core.NotificationRules].
 */
@Composable
fun NotificationScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var master by remember { mutableStateOf(settings.readNotifications) }
    var toasts by remember { mutableStateOf(settings.readToasts) }
    var finger by remember { mutableStateOf(settings.notifQuietFinger) }
    var call by remember { mutableStateOf(settings.notifQuietCall) }
    var dnd by remember { mutableStateOf(settings.notifQuietDnd) }
    var media by remember { mutableStateOf(settings.notifQuietMedia) }
    var black by remember { mutableStateOf(settings.notifBlacklist) }
    var white by remember { mutableStateOf(settings.notifWhitelist) }

    ScreenColumn(stringResource(R.string.sec_notif)) {
        SettingSwitch(
            title = stringResource(R.string.rd_notif_title),
            description = stringResource(R.string.rd_notif_desc),
            checked = master,
            onCheckedChange = {
                master = it
                settings.readNotifications = it
            },
        )

        SettingSwitch(
            title = stringResource(R.string.rd_toast_title),
            description = stringResource(R.string.rd_toast_desc),
            checked = toasts,
            onCheckedChange = {
                toasts = it
                settings.readToasts = it
            },
        )

        SectionHeading(stringResource(R.string.nt_quiet_head))
        SettingSwitch(
            title = stringResource(R.string.nt_finger_title),
            description = stringResource(R.string.nt_finger_desc),
            checked = finger,
            onCheckedChange = {
                finger = it
                settings.notifQuietFinger = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.nt_call_title),
            description = stringResource(R.string.nt_call_desc),
            checked = call,
            onCheckedChange = {
                call = it
                settings.notifQuietCall = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.nt_dnd_title),
            description = stringResource(R.string.nt_dnd_desc),
            checked = dnd,
            onCheckedChange = {
                dnd = it
                settings.notifQuietDnd = it
            },
        )
        SettingSwitch(
            title = stringResource(R.string.nt_media_title),
            description = stringResource(R.string.nt_media_desc),
            checked = media,
            onCheckedChange = {
                media = it
                settings.notifQuietMedia = it
            },
        )

        OutlinedTextField(
            value = black,
            onValueChange = {
                black = it.take(KeywordList.MAX_RAW_CHARS)
                settings.notifBlacklist = black
            },
            label = { Text(stringResource(R.string.nt_black_title)) },
            supportingText = { Text(stringResource(R.string.nt_black_desc)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = white,
            onValueChange = {
                white = it.take(KeywordList.MAX_RAW_CHARS)
                settings.notifWhitelist = white
            },
            label = { Text(stringResource(R.string.nt_white_title)) },
            supportingText = { Text(stringResource(R.string.nt_white_desc)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
