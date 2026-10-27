package com.example.vnscreenreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping
import com.example.vnscreenreader.gesture.GestureMappingStore

@Composable
fun GestureSettingsScreen() {
    val context = LocalContext.current
    val store = remember { GestureMappingStore(context) }
    var mapping by remember { mutableStateOf(store.load()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gán chức năng cho cử chỉ", color = HighContrastYellow, fontSize = 28.sp)
                Text(
                    "Chọn một cử chỉ, rồi chọn chức năng trong danh sách. Thay đổi có hiệu lực ngay.",
                    color = Color.White,
                    fontSize = 18.sp,
                )
            }
        }
        items(Gesture.entries.toList(), key = { it.name }) { gesture ->
            GestureRow(
                gesture = gesture,
                action = mapping.actionFor(gesture),
                onSelected = { selected ->
                    mapping = mapping.with(gesture, selected)
                    store.save(mapping)
                },
            )
        }
        item {
            Button(
                onClick = {
                    store.reset()
                    mapping = GestureMapping()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HighContrastYellow,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Khôi phục cài đặt mặc định", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun GestureRow(
    gesture: Gesture,
    action: GestureAction,
    onSelected: (GestureAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val suffix = if (gesture.isTwoStep) " (hai bước)" else ""

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(gesture.label + suffix, color = Color.White, fontSize = 20.sp)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics {
                        contentDescription =
                            "${gesture.label}, hiện là ${action.label}. Chạm để đổi chức năng"
                    },
                border = BorderStroke(2.dp, HighContrastYellow),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastYellow),
            ) {
                Text(action.label, fontSize = 18.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GestureAction.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, fontSize = 18.sp) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}
