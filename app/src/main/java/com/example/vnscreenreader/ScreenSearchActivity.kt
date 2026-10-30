package com.example.vnscreenreader

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
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
import com.example.vnscreenreader.service.ScreenSearchBridge
import com.example.vnscreenreader.ui.HighContrastTheme
import com.example.vnscreenreader.ui.HighContrastYellow
import com.example.vnscreenreader.ui.PrimaryButton
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

/**
 * Ô nhập từ khóa để tìm phần tử trên màn hình ứng dụng đang dùng. Đóng ngay khi gửi để cửa sổ ứng dụng quay lại
 * trước khi dịch vụ chạy tìm kiếm (xem [ScreenSearchBridge]). Không exported.
 */
class ScreenSearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HighContrastTheme {
                SearchForm(
                    onSearch = { query ->
                        finish()
                        ScreenSearchBridge.submit(query)
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun SearchForm(onSearch: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.search_title),
            color = HighContrastYellow,
            fontSize = 28.sp,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_QUERY_LENGTH) },
            label = { Text(stringResource(R.string.ss_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        PrimaryButton(stringResource(R.string.ss_find)) { if (text.isNotBlank()) onSearch(text) }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.ui_cancel), fontSize = 18.sp)
        }
    }
}

private const val MAX_QUERY_LENGTH = 100
