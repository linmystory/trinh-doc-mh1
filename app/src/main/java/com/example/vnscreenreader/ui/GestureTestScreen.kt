package com.example.vnscreenreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.GestureTestEvent
import com.example.vnscreenreader.core.GestureTestMode

/**
 * Màn hình "Kiểm tra cử chỉ": trong lúc mở, dịch vụ chỉ đọc tên mọi cử chỉ nhận được và không chạy chức năng đã
 * gán. Chế độ luôn tắt khi thoát (nút, Back), khi màn hình bị che/ẩn hoặc bị hủy; không lưu lịch sử.
 */
@Composable
fun GestureTestScreen() {
    var active by remember { mutableStateOf(GestureTestMode.active) }

    fun dispatch(event: GestureTestEvent) {
        GestureTestMode.dispatch(event)
        active = GestureTestMode.active
    }

    BackHandler(enabled = active) { dispatch(GestureTestEvent.BACK) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { dispatch(GestureTestEvent.SCREEN_LEFT) }
    DisposableEffect(Unit) { onDispose { GestureTestMode.dispatch(GestureTestEvent.SCREEN_LEFT) } }
    // Chế độ tự tắt khi không hoạt động: đồng bộ lại hiển thị.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            active = GestureTestMode.active
        }
    }

    ScreenColumn(stringResource(R.string.sec_gesttest)) {
        BodyText(stringResource(R.string.gt_intro))
        Text(
            text = stringResource(if (active) R.string.gt_active else R.string.gt_inactive),
            color = HighContrastGreen,
            fontSize = 20.sp,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (active) {
            PrimaryButton(stringResource(R.string.gt_stop)) { dispatch(GestureTestEvent.EXIT_BUTTON) }
        } else {
            PrimaryButton(stringResource(R.string.gt_start)) { dispatch(GestureTestEvent.ENTER) }
        }
    }
}
