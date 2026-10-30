package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.vnscreenreader.core.FeedbackMode
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.feedback.EarconPlayer
import com.example.vnscreenreader.feedback.HapticPlayer
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

@Composable
fun FeedbackScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var mode by remember { mutableStateOf(settings.feedbackMode) }
    var volume by remember { mutableFloatStateOf(settings.soundVolume) }
    val hapticPlayer = remember { HapticPlayer(context) }
    val earcon = remember { EarconPlayer() }
    DisposableEffect(Unit) { onDispose { earcon.release() } }

    // Mọi thay đổi ghi ngay vào cài đặt; dịch vụ đọc lại mỗi lần phát nên có hiệu lực tức thì, giọng đọc không bị đụng tới.
    fun update(newMode: FeedbackMode) {
        mode = newMode
        settings.feedbackMode = newMode
    }

    ScreenColumn(stringResource(R.string.sec_feedback)) {
        BodyText(
            stringResource(R.string.fbk_intro),
        )

        SettingSwitch(
            title = stringResource(R.string.fbk_haptic_title),
            description = stringResource(R.string.fbk_haptic_desc),
            checked = mode.haptic,
            onCheckedChange = { update(FeedbackMode.of(it, mode.sound)) },
        )
        SettingSwitch(
            title = stringResource(R.string.fbk_sound_title),
            description = stringResource(R.string.fbk_sound_desc),
            checked = mode.sound,
            onCheckedChange = { update(FeedbackMode.of(mode.haptic, it)) },
        )
        LabeledSlider(
            title = stringResource(R.string.fbk_volume),
            value = volume,
            valueRange = SettingsRepository.MIN_SOUND_VOLUME..1f,
            steps = 8,
            onValueChange = { volume = it },
            onValueChangeFinished = { settings.soundVolume = volume },
        )
        PrimaryButton(stringResource(R.string.fbk_test_haptic)) { hapticPlayer.play(HapticPlayer.Kind.CLICK) }
        PrimaryButton(stringResource(R.string.fbk_test_sound)) {
            earcon.setVolume(volume)
            earcon.play(EarconPlayer.Kind.ACTIVATE)
        }
        BodyText(stringResource(R.string.fbk_quick))
    }
}
