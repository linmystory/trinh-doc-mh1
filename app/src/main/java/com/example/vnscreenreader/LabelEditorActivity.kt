package com.example.vnscreenreader

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.core.CustomLabelStore
import com.example.vnscreenreader.core.CustomLabels
import com.example.vnscreenreader.service.ActionDispatcher
import com.example.vnscreenreader.ui.HighContrastTheme
import com.example.vnscreenreader.ui.HighContrastYellow
import com.example.vnscreenreader.ui.PrimaryButton
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

/**
 * Màn hình nhỏ để người dùng gõ nhãn cho phần tử đang chọn. Được dịch vụ mở với tên gói và mã định danh view;
 * lưu vào [CustomLabelStore] rồi đóng. Không exported nên ứng dụng khác không mở được.
 */
class LabelEditorActivity : ComponentActivity() {

    private lateinit var store: CustomLabelStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(ActionDispatcher.EXTRA_PACKAGE)
        val viewId = intent.getStringExtra(ActionDispatcher.EXTRA_VIEW_ID)
        if (pkg.isNullOrEmpty() || viewId.isNullOrEmpty()) {
            finish()
            return
        }
        store = CustomLabelStore(this)
        setContent {
            HighContrastTheme {
                LabelEditor(
                    viewName = viewId.substringAfter(":id/"),
                    initial = store.load().find(pkg, viewId).orEmpty(),
                    onSave = { text ->
                        store.set(pkg, viewId, text)
                        finish()
                    },
                    onDelete = {
                        store.remove(pkg, viewId)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        if (::store.isInitialized) store.close()
        super.onDestroy()
    }
}

@Composable
private fun LabelEditor(
    viewName: String,
    initial: String,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.label_editor_title), color = HighContrastYellow, fontSize = 28.sp, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.le_element, viewName), color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(CustomLabels.MAX_LABEL_LENGTH) },
            label = { Text(stringResource(R.string.le_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        PrimaryButton(stringResource(R.string.le_save)) { onSave(text) }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.le_clear), fontSize = 18.sp)
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.ui_cancel), fontSize = 18.sp)
        }
    }
}
