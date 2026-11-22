package com.example.vnscreenreader.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.CrashLogger
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.Diag
import com.example.vnscreenreader.core.SettingsRepository

/** Số ký tự tối đa hiện trên màn hình (giữ giao diện nhẹ) và khi chia sẻ (đủ chứa toàn bộ nhật ký, xem CrashLogLimits). */
private const val VIEW_MAX_CHARS = 20_000
private const val SHARE_MAX_CHARS = 90_000

/**
 * Số ký tự nhật ký chẩn đoán chép vào clipboard: lấy đoạn CUỐI (mới nhất). Khung chat nhận tối đa khoảng 20.000 ký tự khi dán và khi
 * quá dài thì giữ phần ĐẦU, bỏ phần CUỐI, tức là bỏ đúng phần vừa thao tác; nên chép vừa dưới giới hạn đó để gửi đi không mất dòng mới nhất.
 */
private const val DIAG_COPY_MAX_CHARS = 18_000

/**
 * Trang "Nhật ký và hỗ trợ", gồm hai phần cùng mục đích là nhờ người khác tìm lỗi giúp:
 *  - Nhật ký lỗi: bật/tắt ghi, xem, chia sẻ (hộp chia sẻ của hệ thống, gửi dạng văn bản nên không cần quyền hay tệp công
 *    khai) và xóa (có hỏi lại). Chỉ nằm trong bộ nhớ riêng của ứng dụng.
 *  - Nhật ký chẩn đoán: bật/tắt, sao chép và xóa (trước đây nằm lẫn trong màn Đọc nội dung, dưới tiêu đề "Phím").
 */
@Composable
fun LogScreen() {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val store = remember { CrashLogger.store(context) }
    var enabled by remember { mutableStateOf(settings.crashLogEnabled) }
    var summary by remember { mutableStateOf(store.summary()) }
    var shown by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val clearedText = stringResource(R.string.log_cleared)
    var diagOn by remember { mutableStateOf(settings.diagnosticsEnabled) }
    var diagLines by remember { mutableIntStateOf(Diag.lineCount()) }
    val diagCopiedText = stringResource(R.string.rd_diag_copied)
    val diagCopyFailedText = stringResource(R.string.rd_diag_copy_failed)
    val diagClearedText = stringResource(R.string.rd_diag_cleared)

    ScreenColumn(stringResource(R.string.sec_log)) {
        SectionHeading(stringResource(R.string.log_errors_head))
        SettingSwitch(
            title = stringResource(R.string.log_enable_title),
            description = stringResource(R.string.log_enable_desc),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                settings.crashLogEnabled = it
            },
        )
        BodyText(stringResource(R.string.log_privacy_note))

        if (summary.fileCount == 0) {
            BodyText(stringResource(R.string.log_empty))
        } else {
            val kb = ((summary.totalBytes + 1023) / 1024).toInt()
            BodyText(stringResource(R.string.log_summary, summary.fileCount, kb))
            PrimaryButton(stringResource(if (shown == null) R.string.log_view else R.string.log_hide)) {
                shown = if (shown == null) store.readAll(VIEW_MAX_CHARS) else null
            }
            shown?.let {
                Text(text = it, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
            }
            PrimaryButton(stringResource(R.string.log_share)) { shareLog(context, store.readAll(SHARE_MAX_CHARS)) }
            PrimaryButton(stringResource(R.string.log_clear)) { confirmClear = true }
        }
        status?.let {
            Text(text = it, color = Color.White, fontSize = 18.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        SectionHeading(stringResource(R.string.log_diag_head))
        SettingSwitch(
            title = stringResource(R.string.rd_diag_title),
            description = stringResource(R.string.rd_diag_desc),
            checked = diagOn,
            onCheckedChange = {
                diagOn = it
                settings.diagnosticsEnabled = it
            },
        )
        BodyText(stringResource(R.string.rd_diag_lines, diagLines))
        PrimaryButton(stringResource(R.string.rd_diag_copy)) {
            val copied = try {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("vnscreenreader-diag", Diag.text(DIAG_COPY_MAX_CHARS)))
                clipboard != null
            } catch (e: RuntimeException) {
                false // clipboard từ chối (quá lớn, bị hệ thống chặn...): báo thật, không sập
            }
            diagLines = Diag.lineCount()
            Toast.makeText(context, if (copied) diagCopiedText else diagCopyFailedText, Toast.LENGTH_SHORT).show()
        }
        PrimaryButton(stringResource(R.string.rd_diag_clear)) {
            Diag.clear()
            diagLines = 0
            Toast.makeText(context, diagClearedText, Toast.LENGTH_SHORT).show()
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.log_clear_title), fontSize = 20.sp) },
            text = { Text(stringResource(R.string.log_clear_msg), fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    shown = null
                    store.clear()
                    summary = store.summary()
                    status = clearedText
                }) { Text(stringResource(R.string.log_clear_confirm), fontSize = 18.sp) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) } },
        )
    }
}

private fun shareLog(context: Context, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_share_subject))
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.log_share_title))
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        // Máy không có ứng dụng nào nhận văn bản: người dùng vẫn xem được nhật ký ngay trên màn hình.
    }
}
