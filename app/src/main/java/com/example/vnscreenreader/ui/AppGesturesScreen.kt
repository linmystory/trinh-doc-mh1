package com.example.vnscreenreader.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.R
import com.example.vnscreenreader.gesture.AppGestureOverrides
import com.example.vnscreenreader.gesture.AppGestureSource
import com.example.vnscreenreader.gesture.AppGestureStore
import com.example.vnscreenreader.gesture.GestureMappingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cử chỉ riêng cho từng ứng dụng: danh sách ứng dụng đã có cử chỉ riêng (sửa/xóa), thêm ứng dụng mới từ danh sách ứng dụng
 * có biểu tượng khởi chạy, và sửa bằng đúng màn hình cử chỉ chung. Chỉ phần khác bản đồ chung được lưu.
 */
@Composable
fun AppGesturesScreen() {
    val context = LocalContext.current
    val store = remember { AppGestureStore(context) }
    val global = remember { GestureMappingStore(context) }
    val pm = context.packageManager
    var selected by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var packages by remember { mutableStateOf(store.packages()) }

    fun labelOf(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg // ứng dụng đã gỡ hoặc không nhìn thấy được: hiện tên gói
    } catch (e: RuntimeException) {
        pkg // lỗi lạ từ trình quản lý gói: vẫn hiện được tên gói thay vì sập
    }

    // Duyệt danh sách ứng dụng và nạp nhãn từng ứng dụng có thể mất vài trăm mili giây: làm ở luồng nền để màn hình không đứng.
    val launchable by produceState(initialValue = emptyList<Pair<String, String>>(), picking) {
        value = if (!picking) {
            emptyList<Pair<String, String>>()
        } else {
            withContext(Dispatchers.Default) {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                        .distinctBy { it.first }
                        .filter { it.first != context.packageName }
                        .sortedBy { it.second.lowercase() }
                } catch (e: RuntimeException) {
                    emptyList<Pair<String, String>>()
                }
            }
        }
    }

    val current = selected
    if (current != null) {
        val source = remember(current) { AppGestureSource(store, global, current) }
        var rawTap by remember(current) { mutableStateOf(store.isRawTap(current)) }
        Column(Modifier.fillMaxSize()) {
            PrimaryButton(stringResource(R.string.ag_back)) {
                selected = null
                packages = store.packages()
            }
            SettingSwitch(
                title = stringResource(R.string.ag_rawtap_title),
                description = stringResource(R.string.ag_rawtap_desc),
                checked = rawTap,
                onCheckedChange = { rawTap = if (store.setRawTap(current, it)) it else rawTap },
            )
            Box(Modifier.weight(1f)) {
                GestureSettingsScreen(source = source, heading = stringResource(R.string.ag_edit_title, labelOf(current)))
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.sec_appgest),
                    color = HighContrastYellow,
                    fontSize = 28.sp,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.ag_intro), color = Color.White, fontSize = 18.sp)
            }
        }
        if (packages.isEmpty()) {
            item { Text(stringResource(R.string.ag_none), color = Color.White, fontSize = 18.sp) }
        }
        items(packages, key = { "app_$it" }) { pkg ->
            val label = labelOf(pkg)
            val count = AppGestureOverrides.count(store.overridesFor(pkg))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.ag_row, label, count)) { selected = pkg }
                PrimaryButton(stringResource(R.string.ag_remove, label)) {
                    store.remove(pkg)
                    packages = store.packages()
                }
            }
        }
        item {
            PrimaryButton(stringResource(if (picking) R.string.ag_close_picker else R.string.ag_add)) { picking = !picking }
        }
        if (picking) {
            item {
                Text(
                    stringResource(R.string.ag_pick_title),
                    color = HighContrastGreen,
                    fontSize = 22.sp,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(launchable, key = { "pick_${it.first}" }) { (pkg, label) ->
                PrimaryButton(label) {
                    selected = pkg
                    picking = false
                }
            }
        }
    }
}
