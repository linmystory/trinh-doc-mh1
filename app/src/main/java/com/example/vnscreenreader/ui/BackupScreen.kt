package com.example.vnscreenreader.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.R
import com.example.vnscreenreader.core.BackupCodec
import com.example.vnscreenreader.core.SettingsBackupIO
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Giới hạn byte đọc từ tệp sao lưu (BackupSchema.MAX_TOTAL_CHARS ký tự, tối đa 4 byte mỗi ký tự UTF-8). */
private const val MAX_BACKUP_BYTES = 1_700_000

/**
 * Sao lưu/khôi phục bằng bộ chọn tệp của hệ thống (không cần quyền lưu trữ, người dùng tự chọn nơi lưu nên ứng dụng không để
 * lại tệp nào ngoài ý muốn). Khôi phục có hỏi lại và chỉ nhận khóa/kiểu hợp lệ theo [com.example.vnscreenreader.core.BackupSchema].
 */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupCodec.Decoded?>(null) }
    val scope = rememberCoroutineScope()

    // Đọc/ghi tệp qua nhà cung cấp tài liệu (có thể là ổ đám mây, chậm) nên chạy ngoài luồng chính để màn hình không bị treo.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            scope.launch { status = withContext(Dispatchers.IO) { exportTo(context, uri) } }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    val text = readLimited(context, uri) ?: return@withContext null
                    try {
                        BackupCodec.decode(text)
                    } catch (e: RuntimeException) {
                        BackupCodec.Decoded(emptyList(), 0, false) // tệp lạ/hỏng: coi như không hợp lệ, không sập
                    }
                }
                when {
                    decoded == null -> status = context.getString(R.string.bk_io_failed)
                    !decoded.valid || decoded.entries.isEmpty() -> status = context.getString(R.string.bk_failed)
                    else -> pending = decoded
                }
            }
        }
    }

    ScreenColumn(stringResource(R.string.sec_backup)) {
        BodyText(stringResource(R.string.bk_intro))
        PrimaryButton(stringResource(R.string.bk_export)) { exportLauncher.launch("vnscreenreader-backup.txt") }
        PrimaryButton(stringResource(R.string.bk_import)) { importLauncher.launch(arrayOf("text/plain", "*/*")) }
        status?.let {
            Text(text = it, color = Color.White, fontSize = 18.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }

    val toRestore = pending
    if (toRestore != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.bk_confirm_title), fontSize = 20.sp) },
            text = { Text(stringResource(R.string.bk_confirm_msg), fontSize = 18.sp) },
            confirmButton = {
                TextButton(onClick = {
                    status = try {
                        val applied = SettingsBackupIO.apply(context, toRestore.entries)
                        context.getString(R.string.bk_restored, applied, toRestore.skipped + (toRestore.entries.size - applied))
                    } catch (e: RuntimeException) {
                        context.getString(R.string.bk_io_failed)
                    }
                    pending = null
                }) { Text(stringResource(R.string.bk_confirm_ok), fontSize = 18.sp) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) } },
        )
    }
}

private fun exportTo(context: Context, uri: Uri): String = try {
    val entries = SettingsBackupIO.collect(context)
    val bytes = BackupCodec.encode(entries).toByteArray(Charsets.UTF_8)
    val out = context.contentResolver.openOutputStream(uri)
    if (out == null) {
        context.getString(R.string.bk_io_failed)
    } else {
        out.use { it.write(bytes) }
        context.getString(R.string.bk_saved, entries.size)
    }
} catch (e: IOException) {
    context.getString(R.string.bk_io_failed)
} catch (e: RuntimeException) { // gồm SecurityException và lỗi lạ từ nhà cung cấp tài liệu
    context.getString(R.string.bk_io_failed)
}

/** Đọc tối đa [MAX_BACKUP_BYTES] byte; vượt quá hoặc lỗi I/O thì trả null (không nạp tệp khổng lồ vào bộ nhớ). */
private fun readLimited(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BACKUP_BYTES) return@use null
            out.write(buffer, 0, n)
        }
        out.toString(Charsets.UTF_8.name())
    }
} catch (e: IOException) {
    null
} catch (e: RuntimeException) {
    null
}
