package com.example.vnscreenreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.vnscreenreader.core.CustomLabelStore
import com.example.vnscreenreader.core.CustomLabels
import androidx.compose.ui.res.stringResource
import com.example.vnscreenreader.R

@Composable
fun LabelsScreen() {
    val context = LocalContext.current
    val store = remember { CustomLabelStore(context) }
    DisposableEffect(Unit) { onDispose { store.close() } }
    var entries by remember { mutableStateOf(store.load().entries()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { entries = store.load().entries() }

    ScreenColumn(stringResource(R.string.sec_labels)) {
        BodyText(
            stringResource(R.string.lb_intro),
        )
        BodyText(stringResource(R.string.lb_limits, CustomLabels.MAX_LABELS))
        if (entries.isEmpty()) {
            BodyText(stringResource(R.string.lb_empty))
        }
        entries.forEach { entry ->
            val deleteDescription = stringResource(R.string.lb_delete_desc, entry.label)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.label, fontSize = 22.sp, color = Color.White)
                Text("${entry.packageName} · ${entry.viewId.substringAfter(":id/")}", fontSize = 14.sp, color = Color.LightGray)
                OutlinedButton(
                    onClick = {
                        store.remove(entry.packageName, entry.viewId)
                        entries = store.load().entries()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = deleteDescription },
                    border = BorderStroke(2.dp, HighContrastYellow),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastYellow),
                ) {
                    Text(stringResource(R.string.lb_delete_this), fontSize = 18.sp)
                }
            }
        }
    }
}
