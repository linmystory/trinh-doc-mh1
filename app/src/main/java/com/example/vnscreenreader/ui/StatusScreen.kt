package com.example.vnscreenreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.AccessibilityStatus

@Composable
fun StatusScreen() {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(AccessibilityStatus.isServiceEnabled(context)) }
    var ignoringBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val unavailableText = stringResource(R.string.accessibility_settings_unavailable)
    val onText = stringResource(R.string.ui_state_on)
    val offText = stringResource(R.string.ui_state_off)

    // Cập nhật khi quay lại từ màn hình Cài đặt hệ thống.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceEnabled = AccessibilityStatus.isServiceEnabled(context)
        ignoringBattery = isIgnoringBatteryOptimizations(context)
    }

    ScreenColumn(stringResource(R.string.sec_status)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .toggleable(
                    value = serviceEnabled,
                    role = Role.Switch,
                    onValueChange = {
                        message = if (openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)) null else unavailableText
                    },
                )
                .semantics { stateDescription = if (serviceEnabled) onText else offText },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.st_service_title), fontSize = 24.sp, color = Color.White, modifier = Modifier.weight(1f))
            Switch(
                checked = serviceEnabled,
                onCheckedChange = null,
                modifier = Modifier.padding(horizontal = 20.dp).scale(1.6f), // chừa chỗ vì scale không đổi kích thước layout
                colors = highContrastSwitchColors(),
            )
        }
        BodyText(stringResource(R.string.service_toggle_hint, stringResource(R.string.service_label)))
        message?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                color = HighContrastGreen,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        SectionHeading(stringResource(R.string.st_restricted_head))
        BodyText(stringResource(R.string.st_restricted_body, stringResource(R.string.app_name)))
        BodyText(stringResource(R.string.st_talkback))

        SectionHeading(stringResource(R.string.st_stable_head))
        BodyText(stringResource(R.string.st_stable_body))
        BodyText(stringResource(if (ignoringBattery) R.string.st_battery_unrestricted else R.string.st_battery_optimized))
        PrimaryButton(stringResource(R.string.st_battery_button)) {
            message = if (openSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) null else unavailableText
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** @return false nếu thiết bị không có màn hình cài đặt đó (một số ROM/TV). */
private fun openSettings(context: Context, action: String): Boolean =
    try {
        // Không dùng NEW_TASK để nút Back quay về đúng màn hình này.
        context.startActivity(Intent(action))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
