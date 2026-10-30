package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.vnscreenreader.core.SettingsRepository
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

@Composable
fun ReadingScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var notifications by remember { mutableStateOf(settings.readNotifications) }
    var passwords by remember { mutableStateOf(settings.speakPasswords) }
    var hints by remember { mutableStateOf(settings.speakHints) }
    var autoOcr by remember { mutableStateOf(settings.autoOcrUnlabeled) }

    ScreenColumn(stringResource(R.string.rd_title)) {
        SettingSwitch(
            title = stringResource(R.string.rd_notif_title),
            description = stringResource(R.string.rd_notif_desc),
            checked = notifications,
            onCheckedChange = {
                notifications = it
                settings.readNotifications = it
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
            title = stringResource(R.string.rd_autoocr_title),
            description = stringResource(R.string.rd_autoocr_desc),
            checked = autoOcr,
            onCheckedChange = {
                autoOcr = it
                settings.autoOcrUnlabeled = it
            },
        )

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
