package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMappingStore
import com.example.vnscreenreader.menu.MenuItem
import com.example.vnscreenreader.menu.Menus

/**
 * Giải thích các trình đơn nhanh. Cử chỉ mở trình đơn lấy từ cấu hình THỰC TẾ của người dùng
 * ([GestureMappingStore]), danh sách mục lấy thẳng từ mã nên không bao giờ lệch.
 */
@Composable
fun MenusScreen() {
    val context = LocalContext.current
    val mapping = remember { GestureMappingStore(context).load() }

    @Composable
    fun gestureText(action: GestureAction): String {
        val gestures = mapping.gesturesFor(action)
        return if (gestures.isEmpty()) stringResource(R.string.mn_gesture_none)
        else stringResource(R.string.mn_gesture_line, gestures.joinToString(", ") { it.label })
    }

    fun itemText(items: List<MenuItem>): String = items.mapIndexed { i, item -> "${i + 1}. ${item.label}" }.joinToString("\n")

    // Không tiêu đề, không phần giới thiệu: vào thẳng từng trình đơn. Mỗi trình đơn là MỘT khối chữ bắt đầu bằng tên của nó.
    ScreenColumn(stringResource(R.string.sec_menus), showTitle = false) {
        BodyText(
            stringResource(R.string.menu_global_title) + ". " + gestureText(GestureAction.OPEN_GLOBAL_MENU) + "\n" + itemText(Menus.global()),
        )
        BodyText(stringResource(R.string.menu_more_title) + ".\n" + itemText(Menus.globalMore()))
        BodyText(
            stringResource(R.string.mn_local_head) + ". " + gestureText(GestureAction.OPEN_LOCAL_MENU) + "\n" +
                itemText(Menus.local(emptyList(), editable = false)),
        )
        BodyText(stringResource(R.string.menu_edit_title) + ".\n" + itemText(Menus.localEdit()) + "\n" + stringResource(R.string.mn_local_extra))
    }
}
