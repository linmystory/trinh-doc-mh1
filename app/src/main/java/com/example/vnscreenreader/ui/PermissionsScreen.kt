package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.PermissionFeatures
import com.example.vnscreenreader.core.PermissionFlow
import com.example.vnscreenreader.core.t

/**
 * Mục "Quyền": mỗi tính năng một dòng (tên, trạng thái) và nút mở đúng màn hình quyền của ứng dụng. Trạng thái được
 * làm mới khi quay lại từ Cài đặt hệ thống. Mở mục này KHÔNG bao giờ tự xin quyền.
 */
@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    val features = PermissionFeatures.all
    var granted by remember { mutableStateOf(features.associate { it.id to context.isGranted(it.permissions) }) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = features.associate { it.id to context.isGranted(it.permissions) }
    }

    ScreenColumn(stringResource(R.string.sec_perms)) {
        BodyText(stringResource(R.string.perm_settings_intro))
        if (features.isEmpty()) BodyText(stringResource(R.string.perm_none))
        features.forEach { feature ->
            BodyText("${t(feature.name)}: ${t(PermissionFlow.statusLabel(granted[feature.id] == true))}")
            PrimaryButton(stringResource(R.string.perm_open_settings)) { context.openAppSettings() }
        }
    }
}
