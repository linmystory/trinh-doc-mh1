package com.example.vnscreenreader.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
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
import com.example.vnscreenreader.core.TtsEngineInfo
import com.example.vnscreenreader.core.TtsEngines
import com.example.vnscreenreader.core.SplitMode
import com.example.vnscreenreader.core.VoiceMode
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
    val voiceInstallUnavailable = stringResource(R.string.vo_voice_install_unavailable)
    var voiceMessage by remember { mutableStateOf<String?>(null) }

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

        // Một công cụ duy nhất, hoặc cả hai công cụ (mỗi công cụ một ngôn ngữ).
        val modes = VoiceMode.entries
        val modeLabels = listOf(
            stringResource(R.string.vo2_mode_single),
            stringResource(R.string.vo2_mode_both),
        )
        var voiceMode by remember { mutableStateOf(settings.voiceMode) }
        SettingChoice(
            title = stringResource(R.string.vo2_mode_title),
            description = stringResource(R.string.vo2_mode_desc),
            options = modeLabels,
            selectedIndex = modes.indexOf(voiceMode),
            onSelect = {
                voiceMode = modes[it]
                settings.voiceMode = voiceMode
            },
        )

        SectionHeading(
            stringResource(if (voiceMode == VoiceMode.BOTH) R.string.vo2_first_head else R.string.vo_engine_head),
        )
        EnginePicker(
            currentPackage = enginePackage,
            engines = engines,
            systemDefaultLabel = systemDefaultLabel,
        ) {
            enginePackage = it
            settings.ttsEngine = it
        }
        // Ngôn ngữ công cụ thứ nhất: mặc định theo ngôn ngữ ứng dụng.
        val voiceLanguageTags = listOf("en", "vi", "fr", "es", "de", "it", "pt", "zh", "ru", "ar", "ja", "ko")
        val voiceLanguageNames = voiceLanguageTags.map { tag ->
            java.util.Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }
                .replaceFirstChar { c -> c.titlecase() }
        }
        var firstLanguage by remember { mutableStateOf(settings.firstLanguage) }
        SettingChoice(
            title = stringResource(R.string.vo2_first_lang_title),
            description = stringResource(R.string.vo2_first_lang_desc),
            options = listOf(stringResource(R.string.vo2_first_lang_auto)) + voiceLanguageNames,
            selectedIndex = if (firstLanguage.isBlank()) 0 else voiceLanguageTags.indexOf(firstLanguage) + 1,
            onSelect = {
                firstLanguage = if (it == 0) "" else voiceLanguageTags[it - 1]
                settings.firstLanguage = firstLanguage
            },
        )
        BodyText(stringResource(R.string.vo_engine_note))

        if (voiceMode == VoiceMode.BOTH) {
            var secondPackage by remember { mutableStateOf(settings.secondEngine) }
            var secondLanguage by remember { mutableStateOf(settings.secondLanguage) }
            SectionHeading(stringResource(R.string.vo2_engine_head))
            EnginePicker(
                currentPackage = secondPackage,
                engines = engines,
                systemDefaultLabel = systemDefaultLabel,
            ) {
                secondPackage = it
                settings.secondEngine = it
            }
            val languageTags = voiceLanguageTags
            val languageNames = voiceLanguageNames
            SettingChoice(
                title = stringResource(R.string.vo2_lang_title),
                description = stringResource(R.string.vo2_lang_desc),
                options = languageNames,
                selectedIndex = languageTags.indexOf(secondLanguage),
                onSelect = {
                    secondLanguage = languageTags[it]
                    settings.secondLanguage = secondLanguage
                },
            )
            if (voiceMode == VoiceMode.BOTH) {
                val splits = SplitMode.entries
                var voiceSplit by remember { mutableStateOf(settings.voiceSplit) }
                SettingChoice(
                    title = stringResource(R.string.vo2_split_title),
                    description = stringResource(R.string.vo2_split_desc),
                    options = listOf(stringResource(R.string.vo2_split_word), stringResource(R.string.vo2_split_sentence)),
                    selectedIndex = splits.indexOf(voiceSplit),
                    onSelect = {
                        voiceSplit = splits[it]
                        settings.voiceSplit = voiceSplit
                    },
                )
            }
            BodyText(stringResource(R.string.vo2_note))
        }

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

        // Dịch vụ ghi nhận engine TTS chưa có giọng cho ngôn ngữ ứng dụng: chỉ rõ nguyên nhân và cho cài dữ liệu giọng.
        if (settings.ttsVoiceMissing) {
            SectionHeading(stringResource(R.string.vo_voice_missing_head))
            BodyText(stringResource(R.string.vo_voice_missing_body))
            PrimaryButton(stringResource(R.string.vo_voice_install)) {
                voiceMessage = try {
                    context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    null
                } catch (e: ActivityNotFoundException) {
                    voiceInstallUnavailable
                }
            }
            voiceMessage?.let { BodyText(it) }
        }
    }
}

/** Nút chọn công cụ TTS (menu thả xuống); gói rỗng = mặc định hệ thống. */
@Composable
private fun EnginePicker(
    currentPackage: String,
    engines: List<TtsEngineInfo>,
    systemDefaultLabel: String,
    onPick: (String) -> Unit,
) {
    val currentLabel = engines.firstOrNull { it.packageName == currentPackage }?.label ?: systemDefaultLabel
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.vo_engine_desc, currentLabel)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics { contentDescription = description },
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
                    onPick("")
                },
            )
            engines.forEach { engine ->
                DropdownMenuItem(
                    text = { Text(engine.label, fontSize = 18.sp) },
                    onClick = {
                        expanded = false
                        onPick(engine.packageName)
                    },
                )
            }
        }
    }
}
