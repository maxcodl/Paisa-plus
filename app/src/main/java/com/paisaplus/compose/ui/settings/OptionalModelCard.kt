package com.paisaplus.compose.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.paisaplus.compose.R
import com.paisaplus.compose.str
import com.paisaplus.compose.messages.optional.OptionalModelViewModel
import com.paisaplus.compose.ui.components.TintCard
import java.util.Locale

@Composable
fun OptionalModelCard(vm: OptionalModelViewModel = hiltViewModel()) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val work by vm.download.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val active = work?.state?.let { !it.isFinished } == true
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importPack) }
    // Download progress shows as a notification; ask first on Android 13+, then download either way.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.downloadModel() }
    val spec = vm.specification
    fun mb(bytes: Long) = String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
    TintCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(str(R.string.optional_model_title), style = MaterialTheme.typography.titleLarge)
            Text(str(R.string.optional_model_intro), style = MaterialTheme.typography.bodyMedium)
            Text(str(R.string.optional_model_size, mb(spec.bytes), mb(spec.installedBytes)), style = MaterialTheme.typography.bodySmall)
            Text(str(if (installed != null) R.string.optional_model_installed else R.string.optional_model_not_installed))
            if (active) {
                val bytes = work?.progress?.getLong("bytes", 0) ?: 0L
                val total = work?.progress?.getLong("total", spec.bytes) ?: spec.bytes
                LinearProgressIndicator(progress = { (bytes.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${work?.progress?.getString("stage") ?: str(R.string.optional_model_waiting)} · ${mb(bytes)} / ${mb(total)}")
                TextButton(vm::cancelDownload) { Text(str(R.string.optional_model_cancel_download)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (installed == null) Button({ if (android.os.Build.VERSION.SDK_INT >= 33) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) else vm.downloadModel() }, enabled = !busy) { Text(str(R.string.optional_model_download)) }
                    else OutlinedButton(vm::remove, enabled = !busy) { Text(str(R.string.optional_model_delete)) }
                    OutlinedButton({ pick.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) { Text(str(R.string.optional_model_import)) }
                }
            }
            if (work?.state == WorkInfo.State.FAILED && installed == null) {
                Text(work?.outputData?.getString("error") ?: str(R.string.optional_model_failed), color = MaterialTheme.colorScheme.error)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/maxcodl/Paisa-plus/releases/tag/models-v1"))) }) {
                Text(str(R.string.optional_model_release))
            }
            if (installed != null) {
                TextButton({ expanded = !expanded }) { Text(str(R.string.optional_model_try)) }
                if (expanded) {
                    Text(str(R.string.optional_model_warning), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it.take(4096) }, label = { Text(str(R.string.message_paste)) }, minLines = 2, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(vm::cancelTask) { Text(str(R.string.optional_model_cancel)) }
                    } else Button({ vm.test(text) }, enabled = text.isNotBlank() && !active) { Text(str(R.string.optional_model_generate)) }
                    draft?.let {
                        Text(str(R.string.optional_model_draft), style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(vm::cancelTask) { Text(str(R.string.optional_model_cancel)) }
            }
        }
    }
}
