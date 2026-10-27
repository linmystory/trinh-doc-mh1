package com.example.vnscreenreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TAB_TITLES = listOf("Chung", "Cử chỉ", "Tiện ích")

@Composable
fun AppRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Black,
            contentColor = HighContrastYellow,
        ) {
            TAB_TITLES.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.heightIn(min = 64.dp),
                    text = { Text(title, fontSize = 20.sp) },
                    selectedContentColor = HighContrastYellow,
                    unselectedContentColor = Color.White,
                )
            }
        }
        when (selectedTab) {
            0 -> GeneralSettingsScreen()
            1 -> GestureSettingsScreen()
            else -> PluginManagerScreen()
        }
    }
}
