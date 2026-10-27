package com.example.vnscreenreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.AccessibilityStatus
import com.example.vnscreenreader.core.SettingsRepository
import com.example.vnscreenreader.core.SpeechEngine
import java.util.Locale
import kotlin.math.roundToInt

private const val SLIDER_STEPS = 14 // 0.5 → 2.0, mỗi bước 0.1

@Composable
fun GeneralSettingsScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var serviceEnabled by remember { mutableStateOf(AccessibilityStatus.isServiceEnabled(context)) }
    var rate by remember { mutableFloatStateOf(settings.speechRate) }
    var pitch by remember { mutableFloatStateOf(settings.pitch) }

    var settingsError by remember { mutableStateOf<String?>(null) }
    val settingsUnavailableText = stringResource(R.string.accessibility_settings_unavailable)

    // Cập nhật trạng thái công tắc mỗi khi quay lại từ màn hình Cài đặt hệ thống.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceEnabled = AccessibilityStatus.isServiceEnabled(context)
    }

    // Engine đọc thử chỉ được tạo khi người dùng bấm "Đọc thử" lần đầu (trong sự kiện, KHÔNG phải trong
    // lúc compose) và luôn được giải phóng khi rời màn hình: tránh rò rỉ kết nối TTS khi compose bị hủy
    // và tránh bind dịch vụ TTS khi người dùng chỉ vào bật/tắt dịch vụ.
    var previewEngine by remember { mutableStateOf<SpeechEngine?>(null) }
    DisposableEffect(Unit) { onDispose { previewEngine?.shutdown() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = HighContrastYellow,
            fontSize = 32.sp,
            style = MaterialTheme.typography.headlineLarge,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .toggleable(
                    value = serviceEnabled,
                    role = Role.Switch,
                    onValueChange = {
                        settingsError = if (openAccessibilitySettings(context)) null else settingsUnavailableText
                    },
                )
                .semantics { stateDescription = if (serviceEnabled) "Đang bật" else "Đang tắt" }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Dịch vụ đọc màn hình",
                fontSize = 24.sp,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = serviceEnabled,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 16.dp).scale(1.6f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = HighContrastYellow,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.DarkGray,
                    uncheckedBorderColor = Color.White,
                ),
            )
        }

        Text(
            text = stringResource(R.string.service_toggle_hint, stringResource(R.string.service_label)),
            fontSize = 18.sp,
            color = Color.White,
        )

        settingsError?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                color = HighContrastGreen,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        VoiceSlider(
            title = "Tốc độ đọc",
            value = rate,
            onValueChange = {
                rate = it
                previewEngine?.updateVoice(rate, pitch)
            },
            onValueChangeFinished = { settings.speechRate = rate },
        )

        VoiceSlider(
            title = "Cao độ giọng",
            value = pitch,
            onValueChange = {
                pitch = it
                previewEngine?.updateVoice(rate, pitch)
            },
            onValueChangeFinished = { settings.pitch = pitch },
        )

        Button(
            onClick = {
                val engine = previewEngine ?: SpeechEngine(context, rate, pitch).also { previewEngine = it }
                engine.speak("Xin chào, đây là giọng đọc tiếng Việt.")
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HighContrastYellow,
                contentColor = Color.Black,
            ),
        ) {
            Text("Đọc thử giọng nói", fontSize = 22.sp)
        }

        Text(
            text = "Vào thẻ \"Cử chỉ\" để gán chức năng cho từng cử chỉ, và thẻ \"Tiện ích\" để bật/tắt hoặc nạp thêm script.",
            fontSize = 18.sp,
            color = HighContrastGreen,
        )
    }
}

@Composable
private fun VoiceSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val display = String.format(Locale.getDefault(), "%.1f", value)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "$title: $display", fontSize = 22.sp, color = Color.White)
        Slider(
            value = value,
            onValueChange = { onValueChange((it * 10).roundToInt() / 10f) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = SettingsRepository.MIN_VALUE..SettingsRepository.MAX_VALUE,
            steps = SLIDER_STEPS,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = display
                },
        )
    }
}

/** @return false nếu thiết bị không có màn hình Cài đặt Hỗ trợ tiếp cận (một số ROM/TV). */
private fun openAccessibilitySettings(context: Context): Boolean =
    try {
        // Chạy trong task của ứng dụng (không NEW_TASK) để nút Back quay về đúng màn hình này.
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
