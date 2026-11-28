package com.example.vnscreenreader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

/** Khung chuẩn của mọi màn hình cài đặt: tiêu đề (đánh dấu heading cho trình đọc màn hình) + nội dung cuộn được. */
@Composable
fun ScreenColumn(title: String, showTitle: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (showTitle) {
            Text(
                text = title,
                color = HighContrastYellow,
                fontSize = 30.sp,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

@Composable
fun SectionHeading(text: String) {
    Text(
        text = text,
        color = HighContrastGreen,
        fontSize = 22.sp,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
fun BodyText(text: String) {
    Text(text = text, color = Color.White, fontSize = 18.sp)
}

/** Hàng công tắc: cả hàng là một vùng chạm lớn, đọc ra "tên, mô tả, đang bật/tắt". */
@Composable
fun SettingSwitch(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val onText = stringResource(R.string.ui_state_on)
    val offText = stringResource(R.string.ui_state_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics {
                contentDescription = if (description.isNullOrBlank()) title else "$title. $description"
                stateDescription = if (checked) onText else offText
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontSize = 22.sp, color = Color.White)
            if (description != null) Text(text = description, fontSize = 16.sp, color = Color.LightGray)
        }
        Switch(checked = checked, onCheckedChange = null, colors = highContrastSwitchColors())
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HighContrastYellow, contentColor = Color.Black),
    ) {
        Text(text, fontSize = 20.sp)
    }
}

/** Thanh trượt có nhãn đọc được ("Tên: giá trị") và hiển thị giá trị theo ngôn ngữ hiện tại. */
@Composable
fun LabeledSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueText: ((Float) -> String)? = null,
) {
    // LocalLocale.current là state quan sát được (Locale.getDefault() bị lint NonObservableLocale chặn).
    val locale = LocalLocale.current.platformLocale
    val display = valueText?.invoke(value) ?: String.format(locale, "%.1f", value)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "$title: $display", fontSize = 22.sp, color = Color.White)
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = display
                },
        )
    }
}

/**
 * Chọn một trong NHIỀU lựa chọn mà không dàn cả danh sách giữa màn hình: trang chỉ có tiêu đề, mô tả và MỘT nút ghi lựa chọn hiện
 * tại (đọc ra "tên: lựa chọn"); chạm vào mở hộp thoại danh sách cuộn được, mỗi hàng là một nút chọn đọc "tên, đang chọn/không chọn".
 * Chọn xong hộp thoại tự đóng. Dùng khi có từ khoảng 5 lựa chọn trở lên (ví dụ ngôn ngữ giọng đọc), hoặc khi muốn trang gọn (ví dụ
 * phần công cụ giọng nói: số công cụ, công cụ đang cài đặt, đọc theo từ hay câu); còn lại dùng [SettingChoice].
 */
@Composable
fun SettingPicker(
    title: String,
    description: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.getOrElse(selectedIndex) { "" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, fontSize = 22.sp, color = Color.White, modifier = Modifier.semantics { heading() })
        if (description != null) Text(text = description, fontSize = 16.sp, color = Color.LightGray)
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics { contentDescription = "$title: $current" },
            border = BorderStroke(2.dp, HighContrastYellow),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastYellow),
        ) {
            Text(current, fontSize = 18.sp)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, fontSize = 20.sp) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(options) { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(selected = index == selectedIndex, role = Role.RadioButton) {
                                    open = false
                                    onSelect(index)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = index == selectedIndex, onClick = null)
                            Text(text = label, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) } },
        )
    }
}

/**
 * Nhóm lựa chọn một trong nhiều (nút chọn): mỗi hàng là một vùng chạm lớn, đọc ra "tên lựa chọn, đang chọn/không chọn".
 * [title]/[description] đứng trên nhóm. Dùng cho cài đặt có vài mức (ví dụ mức độ chi tiết khi duyệt); nhiều lựa chọn thì dùng [SettingPicker].
 */
@Composable
fun SettingChoice(
    title: String,
    description: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, fontSize = 22.sp, color = Color.White, modifier = Modifier.semantics { heading() })
        if (description != null) Text(text = description, fontSize = 16.sp, color = Color.LightGray)
        options.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .selectable(selected = index == selectedIndex, role = Role.RadioButton) { onSelect(index) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(selected = index == selectedIndex, onClick = null)
                Text(text = label, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
            }
        }
    }
}
