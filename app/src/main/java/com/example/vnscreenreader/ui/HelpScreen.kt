package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R
import com.example.vnscreenreader.menu.MenuLayout

@Composable
fun HelpScreen() {
    ScreenColumn(stringResource(R.string.hp_title)) {
        SectionHeading(stringResource(R.string.hp_basic_head))
        BodyText(stringResource(R.string.hp_basic_1))
        BodyText(stringResource(R.string.hp_basic_2))

        SectionHeading(stringResource(R.string.hp_controls_head))
        BodyText(stringResource(R.string.hp_controls_body))

        SectionHeading(stringResource(R.string.hp_std_head))
        BodyText(stringResource(R.string.hp_std_body))

        SectionHeading(stringResource(R.string.hp_swipes_head))
        BodyText(stringResource(R.string.hp_swipes_body))

        SectionHeading(stringResource(R.string.mn_title))
        BodyText(pluralStringResource(R.plurals.hp_menus_body, MenuLayout.MAX_ITEMS, MenuLayout.MAX_ITEMS))

        SectionHeading(stringResource(R.string.hp_keys_head))
        BodyText(stringResource(R.string.hp_keys_nav))
        BodyText(stringResource(R.string.hp_keys_global))
        BodyText(stringResource(R.string.hp_keys_vol))
        BodyText(stringResource(R.string.hp_keys_extra))

        SectionHeading(stringResource(R.string.hp_voice_head))
        BodyText(stringResource(R.string.hp_voice_body))

        SectionHeading(stringResource(R.string.hp_more_head))
        BodyText(stringResource(R.string.hp_more_body))

        // Hai đoạn giải thích trước đây nằm cuối màn "Chi tiết đọc nội dung", lẫn với các công tắc.
        SectionHeading(stringResource(R.string.rd_unlabeled_head))
        BodyText(stringResource(R.string.rd_unlabeled_intro))
        BodyText(stringResource(R.string.rd_unlabeled_1))
        BodyText(stringResource(R.string.rd_unlabeled_2))
        BodyText(stringResource(R.string.rd_unlabeled_3))
        BodyText(stringResource(R.string.rd_unlabeled_4))
        BodyText(stringResource(R.string.rd_unlabeled_5))

        SectionHeading(stringResource(R.string.rd_ai_head))
        BodyText(stringResource(R.string.rd_ai_body))

        SectionHeading(stringResource(R.string.hp_perm_head))
        BodyText(stringResource(R.string.hp_perm_body))

        SectionHeading(stringResource(R.string.hp_limits_head))
        BodyText(stringResource(R.string.hp_limit_ocr))
        BodyText(stringResource(R.string.hp_limit_describe))
        BodyText(stringResource(R.string.hp_limit_secure))
        BodyText(stringResource(R.string.hp_limit_edge))
        BodyText(stringResource(R.string.hp_translation_note))
    }
}
