package com.example.vnscreenreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun ScreenColumn(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = title,
            color = HighContrastYellow,
            fontSize = 30.sp,
            modifier = Modifier.semantics { heading() },
        )
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
            .semantics { stateDescription = if (checked) onText else offText },
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
) {
    // LocalLocale.current là state quan sát được (Locale.getDefault() bị lint NonObservableLocale chặn).
    val display = String.format(LocalLocale.current.platformLocale, "%.1f", value)
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
