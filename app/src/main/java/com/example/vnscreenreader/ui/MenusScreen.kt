package com.example.vnscreenreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.R
import com.example.vnscreenreader.gesture.GestureAction
import com.example.vnscreenreader.gesture.GestureMappingStore
import com.example.vnscreenreader.menu.MenuGroup
import com.example.vnscreenreader.menu.MenuItem
import com.example.vnscreenreader.menu.MenuLayout
import com.example.vnscreenreader.menu.MenuLayoutStore
import com.example.vnscreenreader.menu.Menus

/** Hộp thoại chọn chức năng đang mở cho việc gì: thêm mới ([replacing] = null) hay thay cho một mục có sẵn. */
private data class PickerTarget(val replacing: GestureAction?)

/**
 * Trang "Trình đơn nhanh": trình đơn phần tử do người dùng tự sắp xếp (thêm, bớt, đổi thứ tự, thay chức năng) rồi đến phần giải
 * thích trình đơn chung. Cử chỉ mở trình đơn lấy từ cấu hình THỰC TẾ của người dùng ([GestureMappingStore]); danh sách trình đơn
 * chung lấy thẳng từ mã ([Menus]) nên không bao giờ lệch.
 *
 * Mọi thay đổi lưu ngay vào [MenuLayoutStore]; dịch vụ đọc bố cục mỗi lần mở trình đơn nên có hiệu lực tức thì, không cần khởi
 * động lại. Mỗi mục là MỘT điểm dừng của trình đọc màn hình; chạm vào mở hộp thoại chỉ gồm các thao tác áp dụng được.
 */
@Composable
fun MenusScreen() {
    val context = LocalContext.current
    // Chuỗi dựng lúc bấm phải lấy từ LocalResources (đổi theo ngôn ngữ/cấu hình), không từ LocalContext.current.getString().
    val resources = LocalResources.current
    val mapping = remember { GestureMappingStore(context).load() }
    val store = remember { MenuLayoutStore(context) }
    var layout by remember { mutableStateOf(store.load()) }
    var status by remember { mutableStateOf<String?>(null) }
    var itemDialog by remember { mutableStateOf<GestureAction?>(null) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    @Composable
    fun gestureText(action: GestureAction): String {
        val gestures = mapping.gesturesFor(action)
        return if (gestures.isEmpty()) stringResource(R.string.mn_gesture_none)
        else stringResource(R.string.mn_gesture_line, gestures.joinToString(", ") { it.label })
    }

    fun itemText(items: List<MenuItem>): String = items.mapIndexed { i, item -> "${i + 1}. ${item.label}" }.joinToString("\n")

    // Mọi thay đổi đi qua đây: lưu ngay, rồi báo kết quả bằng vùng thông báo trực tiếp (liveRegion).
    fun commit(newLayout: MenuLayout, message: String) {
        layout = newLayout
        store.save(newLayout)
        status = message
    }

    // Không tiêu đề trang: vào thẳng nội dung. Phần của người dùng (chỉnh được) đứng trước phần tra cứu.
    ScreenColumn(stringResource(R.string.sec_menus), showTitle = false) {
        SectionHeading(stringResource(R.string.mn_local_head))
        BodyText(
            pluralStringResource(R.plurals.mn_local_desc, MenuLayout.MAX_ITEMS, MenuLayout.MAX_ITEMS) + " " +
                gestureText(GestureAction.OPEN_LOCAL_MENU) + " " + stringResource(R.string.mn_local_extra),
        )
        BodyText(stringResource(R.string.mn_counter, layout.actions.size, MenuLayout.MAX_ITEMS))

        layout.actions.forEachIndexed { index, action ->
            LayoutRow(Menus.labelFor(action), index + 1, layout.actions.size) { itemDialog = action }
        }

        if (layout.isFull) {
            BodyText(pluralStringResource(R.plurals.mn_add_full, MenuLayout.MAX_ITEMS, MenuLayout.MAX_ITEMS))
        } else {
            PrimaryButton(stringResource(R.string.mn_add)) { picker = PickerTarget(null) }
        }
        status?.let {
            Text(text = it, color = Color.White, fontSize = 18.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (layout.isCustomized) {
            PrimaryButton(stringResource(R.string.mn_reset)) { confirmReset = true }
        }

        SectionHeading(stringResource(R.string.menu_global_title))
        BodyText(gestureText(GestureAction.OPEN_GLOBAL_MENU) + " " + stringResource(R.string.mn_general_desc))
        BodyText(itemText(Menus.global()))
        // Mỗi nhóm là MỘT khối chữ bắt đầu bằng tên nhóm, liệt kê đúng những gì người dùng nghe khi mở nhóm đó.
        MenuGroup.entries.forEach { group ->
            BodyText(group.title + ".\n" + itemText(Menus.submenu(group.kind).orEmpty()))
        }
    }

    val selected = itemDialog
    if (selected != null && selected in layout) {
        ItemDialog(
            label = Menus.labelFor(selected),
            canMoveToTop = layout.canMoveToTop(selected),
            canMoveUp = layout.canMoveUp(selected),
            canMoveDown = layout.canMoveDown(selected),
            canRemove = layout.canRemove(selected),
            onMoveToTop = {
                itemDialog = null
                val moved = layout.moveToTop(selected)
                commit(moved, resources.getString(R.string.mn_msg_moved, Menus.labelFor(selected), moved.positionOf(selected), moved.actions.size))
            },
            onMoveUp = {
                itemDialog = null
                val moved = layout.moveUp(selected)
                commit(moved, resources.getString(R.string.mn_msg_moved, Menus.labelFor(selected), moved.positionOf(selected), moved.actions.size))
            },
            onMoveDown = {
                itemDialog = null
                val moved = layout.moveDown(selected)
                commit(moved, resources.getString(R.string.mn_msg_moved, Menus.labelFor(selected), moved.positionOf(selected), moved.actions.size))
            },
            onReplace = {
                itemDialog = null
                picker = PickerTarget(selected)
            },
            onRemove = {
                itemDialog = null
                val removed = layout.remove(selected)
                if (removed !== layout) {
                    commit(removed, resources.getString(R.string.mn_msg_removed, Menus.labelFor(selected), removed.actions.size))
                }
            },
            onDismiss = { itemDialog = null },
        )
    }

    picker?.let { target ->
        val replacing = target.replacing
        FunctionPickerDialog(
            title = if (replacing == null) {
                stringResource(R.string.mn_pick_title)
            } else {
                stringResource(R.string.mn_pick_replace_title, Menus.labelFor(replacing))
            },
            layout = layout,
            onPick = { chosen ->
                picker = null
                if (replacing == null) {
                    val added = layout.add(chosen)
                    if (added !== layout) {
                        commit(added, resources.getString(R.string.mn_msg_added, Menus.labelFor(chosen), added.positionOf(chosen)))
                    }
                } else {
                    val replaced = layout.replace(replacing, chosen)
                    if (replaced !== layout) {
                        commit(replaced, resources.getString(R.string.mn_msg_replaced, Menus.labelFor(chosen)))
                    }
                }
            },
            onDismiss = { picker = null },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.mn_reset_title), fontSize = 20.sp) },
            text = { Text(stringResource(R.string.mn_reset_msg), fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    commit(layout.reset(), resources.getString(R.string.mn_msg_reset))
                }) { Text(stringResource(R.string.mn_reset_confirm), fontSize = 18.sp) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) } },
        )
    }
}

/** Một chức năng của trình đơn phần tử: MỘT điểm dừng, đọc "tên, vị trí trên tổng", chạm để mở hộp thoại thao tác. */
@Composable
private fun LayoutRow(label: String, position: Int, total: Int, onClick: () -> Unit) {
    // "Tên, vị trí trên tổng": cùng câu mà trình đơn thật đọc khi vuốt tới mục này.
    val description = stringResource(R.string.menu_position, label, position, total)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(BorderStroke(2.dp, HighContrastYellow), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(description, color = Color.White, fontSize = 20.sp)
    }
}

/** Một thao tác trong hộp thoại: hàng rộng, một điểm dừng, đọc "tên, nút". */
@Composable
private fun DialogOption(text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, fontSize = 18.sp)
    }
}

/**
 * Thao tác với một chức năng của trình đơn phần tử. Chỉ hiện thao tác áp dụng được ([MenuLayout.canMoveUp]...); mục cuối cùng
 * không xóa được thì có một dòng giải thích thay cho nút "Xóa".
 */
@Composable
private fun ItemDialog(
    label: String,
    canMoveToTop: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label, fontSize = 20.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (canMoveToTop) DialogOption(stringResource(R.string.mn_move_top), onMoveToTop)
                if (canMoveUp) DialogOption(stringResource(R.string.mn_move_up), onMoveUp)
                if (canMoveDown) DialogOption(stringResource(R.string.mn_move_down), onMoveDown)
                DialogOption(stringResource(R.string.mn_replace), onReplace)
                if (canRemove) {
                    DialogOption(stringResource(R.string.mn_remove), onRemove)
                } else {
                    Text(stringResource(R.string.mn_msg_last), fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) } },
    )
}

/**
 * Hộp thoại chọn chức năng để thêm hoặc để thay: danh sách CUỘN được, chia theo nhóm của trình đơn chung (mỗi nhóm một tiêu đề),
 * chỉ gồm chức năng CHƯA có trong trình đơn phần tử nên không thể chọn trùng.
 */
@Composable
private fun FunctionPickerDialog(
    title: String,
    layout: MenuLayout,
    onPick: (GestureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sections = remember(layout) {
        MenuGroup.entries.map { group -> group to group.actions.filter { it !in layout } }.filter { (_, actions) -> actions.isNotEmpty() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 20.sp) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                sections.forEach { (group, actions) ->
                    item(key = "group_${group.name}") {
                        Text(
                            text = group.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                    items(actions, key = { it.name }) { action ->
                        DialogOption(Menus.labelFor(action)) { onPick(action) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) } },
    )
}
