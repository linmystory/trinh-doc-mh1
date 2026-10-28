package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import com.example.vnscreenreader.menu.Menus
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

/** Giải thích hai trình đơn nhanh (chung và của phần tử) giống TalkBack/Jieshuo, lấy danh sách mục thẳng từ mã. */
@Composable
fun MenusScreen() {
    ScreenColumn(stringResource(R.string.sec_menus)) {
        BodyText(
            stringResource(R.string.mn_intro),
        )
        BodyText(stringResource(R.string.mn_when_open))

        SectionHeading(stringResource(R.string.menu_global_title))
        Menus.global().forEachIndexed { i, item -> BodyText("${i + 1}. ${item.label}") }

        SectionHeading(stringResource(R.string.mn_local_head))
        Menus.local(emptyList()).forEachIndexed { i, item -> BodyText("${i + 1}. ${item.label}") }
        BodyText(stringResource(R.string.mn_local_extra))
    }
}
