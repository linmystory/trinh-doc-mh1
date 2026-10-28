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
