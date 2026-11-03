package com.example.vnscreenreader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.PermissionFeature
import com.example.vnscreenreader.core.PermissionFlow
import com.example.vnscreenreader.core.PermissionStep
import com.example.vnscreenreader.core.t

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

internal fun Context.isGranted(permissions: List<String>): Boolean =
    PermissionFlow.allGranted(permissions.map { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED })

/** Mở đúng màn hình quyền của ứng dụng trong Cài đặt hệ thống. */
internal fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

/**
 * Luồng xin quyền cho một tính năng: có quyền thì làm ngay; chưa có thì ĐỌC lời giải thích rồi mới hiện hộp thoại hệ
 * thống; bị từ chối thì thông báo và dừng (không hỏi lại trong cùng một lần bấm); "không hỏi lại" thì kèm nút mở Cài
 * đặt. Trả về hàm để gọi khi người dùng bật/bấm tính năng. Chỉ xin khi tính năng được bật ([featureEnabled]).
 */
@Composable
fun rememberPermissionRequest(
    feature: PermissionFeature,
    featureEnabled: Boolean = true,
    onGranted: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val latestOnGranted by rememberUpdatedState(onGranted)
    var step by remember { mutableStateOf<PermissionStep?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = feature.permissions.all { result[it] == true }
        val activity = context.findActivity()
        val shouldShow = activity != null &&
            feature.permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        val next = PermissionFlow.afterSystemDialog(granted, shouldShow)
        if (next == PermissionStep.Proceed) {
            step = null
            latestOnGranted()
        } else {
            step = next
        }
    }

    step?.let { current ->
        PermissionDialog(
            step = current,
            onContinue = {
                step = null
                launcher.launch(feature.permissions.toTypedArray())
            },
            onDismiss = { step = null },
            onOpenSettings = {
                step = null
                context.openAppSettings()
            },
        )
    }

    return {
        val next = PermissionFlow.start(featureEnabled, context.isGranted(feature.permissions), feature.rationale)
        if (next == PermissionStep.Proceed) {
            latestOnGranted()
        } else if (next != PermissionStep.NotRequested) {
            step = next
        }
    }
}

@Composable
private fun PermissionDialog(step: PermissionStep, onContinue: () -> Unit, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val message = PermissionFlow.message(step) ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(t(message)) } },
        confirmButton = {
            when {
                step is PermissionStep.Explain ->
                    TextButton(onClick = onContinue) { Text(stringResource(R.string.perm_continue), fontSize = 18.sp) }
                PermissionFlow.offersOpenSettings(step) ->
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.perm_open_settings), fontSize = 18.sp) }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) }
        },
    )
}
