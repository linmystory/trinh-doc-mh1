package com.example.vnscreenreader.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vnscreenreader.plugin.PluginInfo
import com.example.vnscreenreader.plugin.PluginRegistry
import com.example.vnscreenreader.plugin.PluginScriptStore
import com.example.vnscreenreader.plugin.ScriptParser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

@Composable
fun PluginManagerScreen() {
    val context = LocalContext.current
    val registry = remember { PluginRegistry(context) }
    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<PluginInfo?>(null) }
    // Script không khai báo "packages" sẽ chạy ở MỌI ứng dụng: phải được người dùng xác nhận trước khi cài.
    var pendingAllApps by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        plugins = withContext(Dispatchers.IO) { registry.listInfo() }
    }

    LaunchedEffect(Unit) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) { readScript(context, uri) }
                if (json == null) {
                    status = context.getString(R.string.pl_read_failed)
                } else if (ScriptParser.parse(json).getOrNull()?.packages?.isEmpty() == true) {
                    pendingAllApps = json
                } else {
                    status = withContext(Dispatchers.IO) { installScript(context, registry, json) }
                    reload()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pl_title), color = HighContrastYellow, fontSize = 28.sp)
                Text(
                    stringResource(R.string.pl_intro),
                    color = Color.White,
                    fontSize = 18.sp,
                )
            }
        }
        items(plugins, key = { it.id }) { info ->
            PluginRow(
                info = info,
                onToggle = { enabled ->
                    registry.setEnabled(info.id, enabled)
                    plugins = plugins.map { if (it.id == info.id) it.copy(enabled = enabled) else it }
                },
                onDelete = { pendingDelete = info },
            )
        }
        item {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HighContrastYellow,
                    contentColor = Color.Black,
                ),
            ) {
                Text(stringResource(R.string.pl_load), fontSize = 20.sp)
            }
        }
        item {
            status?.let {
                Text(
                    text = it,
                    color = HighContrastGreen,
                    fontSize = 18.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    pendingAllApps?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingAllApps = null },
            title = { Text(stringResource(R.string.pl_allapps_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.pl_allapps_msg)) } },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAllApps = null
                        scope.launch {
                            status = withContext(Dispatchers.IO) { installScript(context, registry, json) }
                            reload()
                        }
                    },
                ) { Text(stringResource(R.string.pl_allapps_confirm), fontSize = 18.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAllApps = null }) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) }
            },
        )
    }

    pendingDelete?.let { target ->
        val deletedText = stringResource(R.string.pl_deleted, target.name)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.pl_delete_q)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.pl_delete_msg, target.name)) } },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) { registry.removeScript(target.id) }
                            status = deletedText
                            reload()
                        }
                    },
                ) { Text(stringResource(R.string.ui_delete), fontSize = 18.sp) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.ui_cancel), fontSize = 18.sp) }
            },
        )
    }
}

@Composable
private fun PluginRow(info: PluginInfo, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .toggleable(value = info.enabled, role = Role.Switch, onValueChange = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.name, color = Color.White, fontSize = 22.sp)
                if (info.description.isNotBlank()) {
                    Text(info.description, color = Color.LightGray, fontSize = 16.sp)
                }
                Text(
                    if (info.isBuiltIn) stringResource(R.string.pl_builtin) else stringResource(R.string.pl_external),
                    color = HighContrastGreen,
                    fontSize = 14.sp,
                )
            }
            Switch(
                checked = info.enabled,
                onCheckedChange = null,
                modifier = Modifier.padding(horizontal = 16.dp).scale(1.4f), // chừa chỗ vì scale không đổi kích thước layout
                colors = highContrastSwitchColors(),
            )
        }
        if (!info.isBuiltIn) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                border = BorderStroke(2.dp, Color(0xFFFF8A80)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
            ) {
                Text(stringResource(R.string.pl_delete_this), fontSize = 18.sp)
            }
        }
    }
}

private fun readScript(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use {
        PluginScriptStore.readLimited(it, PluginScriptStore.MAX_BYTES)
    }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
}

private fun installScript(context: Context, registry: PluginRegistry, json: String): String =
    registry.installScript(json).fold(
        onSuccess = { context.getString(R.string.pl_installed, it.name) },
        onFailure = { context.getString(R.string.pl_error, it.message ?: context.getString(R.string.pl_unknown)) },
    )
