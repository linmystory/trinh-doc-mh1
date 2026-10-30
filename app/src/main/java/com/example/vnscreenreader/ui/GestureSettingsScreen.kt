package com.example.vnscreenreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.gesture.Gesture
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMapping
import com.example.vnscreenreader.gesture.GestureMappingStore
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

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
                Text(stringResource(R.string.gs_title), color = HighContrastYellow, fontSize = 28.sp)
                Text(
                    stringResource(R.string.gs_intro),
                    color = Color.White,
                    fontSize = 18.sp,
                )
            }
        }
        Gesture.entries.groupBy { it.fingers }.forEach { (fingers, gestures) ->
            item(key = "heading_$fingers") {
                Text(
                    text = if (fingers == 1) stringResource(R.string.gs_one_finger) else pluralStringResource(R.plurals.gs_n_fingers, fingers, fingers),
                    color = HighContrastGreen,
                    fontSize = 22.sp,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (fingers > 1 && fingers == 2) {
                item(key = "note_multi") {
                    Text(
                        stringResource(R.string.gs_multi_note),
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
            items(gestures, key = { it.name }) { gesture ->
                GestureRow(
                    gesture = gesture,
                    action = mapping.actionFor(gesture),
                    onSelected = { selected ->
                        mapping = mapping.with(gesture, selected)
                        store.save(mapping)
                    },
                )
            }
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
                Text(stringResource(R.string.gs_reset), fontSize = 20.sp)
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
    val suffix = if (gesture.isTwoStep) " " + stringResource(R.string.gs_two_step) else ""
    val rowDescription = stringResource(R.string.gs_row_desc, gesture.label, action.label)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(gesture.label + suffix, color = Color.White, fontSize = 20.sp)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .semantics {
                        contentDescription = rowDescription
                    },
                border = BorderStroke(2.dp, HighContrastYellow),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastYellow),
            ) {
                Text(action.label, fontSize = 18.sp)
            }
        }
    }

    if (expanded) {
        ActionPickerDialog(
            title = gesture.label + suffix,
            selected = action,
            onDismiss = { expanded = false },
            onPick = {
                expanded = false
                onSelected(it)
            },
        )
    }
}

/**
 * Hộp thoại chọn chức năng: danh sách CUỘN được, hiện đủ mọi chức năng (menu thả xuống kiểu cũ bị cắt trên máy
 * nhỏ/chữ lớn), mỗi hàng là một nút chọn rộng, đánh dấu chức năng đang dùng.
 */
@Composable
private fun ActionPickerDialog(
    title: String,
    selected: GestureAction,
    onDismiss: () -> Unit,
    onPick: (GestureAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 20.sp) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(GestureAction.entries.toList(), key = { it.name }) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton) { onPick(option) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(option.label, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) } },
    )
}
