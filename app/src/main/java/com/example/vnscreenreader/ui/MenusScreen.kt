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
    fun gestureLine(action: GestureAction) {
        val gestures = mapping.gesturesFor(action)
        BodyText(
            if (gestures.isEmpty()) stringResource(R.string.mn_gesture_none)
            else stringResource(R.string.mn_gesture_line, gestures.joinToString(", ") { it.label }),
        )
    }

    @Composable
    fun itemList(items: List<MenuItem>) = items.forEachIndexed { i, item -> BodyText("${i + 1}. ${item.label}") }

    ScreenColumn(stringResource(R.string.sec_menus)) {
        BodyText(stringResource(R.string.mn_intro))
        BodyText(stringResource(R.string.mn_when_open))

        SectionHeading(stringResource(R.string.menu_global_title))
        gestureLine(GestureAction.OPEN_GLOBAL_MENU)
        itemList(Menus.global())
        SectionHeading(stringResource(R.string.menu_more_title))
        itemList(Menus.globalMore())

        SectionHeading(stringResource(R.string.mn_local_head))
        gestureLine(GestureAction.OPEN_LOCAL_MENU)
        itemList(Menus.local(emptyList(), editable = false))
        SectionHeading(stringResource(R.string.menu_edit_title))
        itemList(Menus.localEdit())
        BodyText(stringResource(R.string.mn_local_extra))
    }
}
