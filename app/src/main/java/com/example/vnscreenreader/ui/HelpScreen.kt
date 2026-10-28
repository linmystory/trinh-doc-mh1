package com.example.vnscreenreader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

@Composable
fun HelpScreen() {
    ScreenColumn(stringResource(R.string.hp_title)) {
        SectionHeading(stringResource(R.string.hp_basic_head))
        BodyText(stringResource(R.string.hp_basic_1))
        BodyText(stringResource(R.string.hp_basic_2))

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
