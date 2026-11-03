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
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.Verbosity
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

@Composable
fun ReadingScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var notifications by remember { mutableStateOf(settings.readNotifications) }
    var passwords by remember { mutableStateOf(settings.speakPasswords) }
    var hints by remember { mutableStateOf(settings.speakHints) }
    var emoji by remember { mutableStateOf(settings.speakEmoji) }
    var autoOcr by remember { mutableStateOf(settings.autoOcrUnlabeled) }
    var detailed by remember { mutableStateOf(settings.detailedFocus) }
    var diagOn by remember { mutableStateOf(settings.diagnosticsEnabled) }
    var diagLines by remember { mutableStateOf(Diag.lineCount()) }
    var actionHints by remember { mutableStateOf(settings.speakActionHints) }
    var verbosity by remember { mutableStateOf(settings.verbosity) }

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
        val clearedText = stringResource(R.string.rd_diag_cleared)
        Button(onClick = {
            val text = Diag.text()
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("vnscreenreader-diag", text))
            diagLines = Diag.lineCount()
            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
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
