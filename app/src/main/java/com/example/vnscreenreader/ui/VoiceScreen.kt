package com.example.vnscreenreader.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.SpeechEngine
import com.example.vnscreenreader.core.TtsEngines
import kotlin.math.roundToInt

private const val SLIDER_STEPS = 14 // 0.5 → 2.0, mỗi bước 0.1

@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var rate by remember { mutableFloatStateOf(settings.speechRate) }
    var pitch by remember { mutableFloatStateOf(settings.pitch) }
    var enginePackage by remember { mutableStateOf(settings.ttsEngine) }
    val engines = remember { TtsEngines.list(context) }
    val systemDefaultLabel = stringResource(R.string.vo_system_default)
    val testPhrase = stringResource(R.string.vo_test_phrase)
    val languageUnavailable = stringResource(R.string.vo_lang_unavailable)
    var languageMessage by remember { mutableStateOf<String?>(null) }

    // Engine đọc thử chỉ được tạo khi bấm "Đọc thử" lần đầu (trong sự kiện, không phải lúc compose) và luôn
    // được giải phóng khi rời màn hình: tránh rò rỉ kết nối TTS.
    var previewEngine by remember { mutableStateOf<SpeechEngine?>(null) }
    DisposableEffect(Unit) { onDispose { previewEngine?.shutdown() } }

    ScreenColumn(stringResource(R.string.vo_title)) {
        LabeledSlider(
            title = stringResource(R.string.vo_rate),
            value = rate,
            valueRange = SettingsRepository.MIN_VALUE..SettingsRepository.MAX_VALUE,
            steps = SLIDER_STEPS,
            onValueChange = {
                rate = (it * 10).roundToInt() / 10f
                previewEngine?.updateVoice(rate, pitch)
            },
            onValueChangeFinished = { settings.speechRate = rate },
        )
        LabeledSlider(
            title = stringResource(R.string.vo_pitch),
            value = pitch,
            valueRange = SettingsRepository.MIN_VALUE..SettingsRepository.MAX_VALUE,
            steps = SLIDER_STEPS,
            onValueChange = {
                pitch = (it * 10).roundToInt() / 10f
                previewEngine?.updateVoice(rate, pitch)
            },
            onValueChangeFinished = { settings.pitch = pitch },
        )

        SectionHeading(stringResource(R.string.vo_engine_head))
        val currentLabel = engines.firstOrNull { it.packageName == enginePackage }?.label ?: systemDefaultLabel
        var expanded by remember { mutableStateOf(false) }
        val engineDescription = stringResource(R.string.vo_engine_desc, currentLabel)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics { contentDescription = engineDescription },
                border = BorderStroke(2.dp, HighContrastYellow),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastYellow),
            ) {
                Text(currentLabel, fontSize = 18.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(systemDefaultLabel, fontSize = 18.sp) },
                    onClick = {
                        expanded = false
                        enginePackage = ""
                        settings.ttsEngine = ""
                    },
                )
                engines.forEach { engine ->
                    DropdownMenuItem(
                        text = { Text(engine.label, fontSize = 18.sp) },
                        onClick = {
                            expanded = false
                            enginePackage = engine.packageName
                            settings.ttsEngine = engine.packageName
                        },
                    )
                }
            }
        }
        BodyText(stringResource(R.string.vo_engine_note))

        PrimaryButton(stringResource(R.string.vo_test)) {
            previewEngine?.shutdown()
            previewEngine = SpeechEngine(context, rate, pitch, enginePackage).also {
                it.speak(testPhrase)
            }
        }

        SectionHeading(stringResource(R.string.vo_lang_head))
        BodyText(stringResource(R.string.vo_lang_body))
        PrimaryButton(stringResource(R.string.vo_lang_button)) {
            // Android 13+: màn hình chọn ngôn ngữ riêng cho từng ứng dụng của hệ thống.
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
            languageMessage = try {
                context.startActivity(intent)
                null
            } catch (e: ActivityNotFoundException) {
                languageUnavailable
            }
        }
        languageMessage?.let { BodyText(it) }
    }
}
