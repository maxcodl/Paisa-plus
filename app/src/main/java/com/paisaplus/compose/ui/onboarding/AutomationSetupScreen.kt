package com.paisaplus.compose.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.paisaplus.compose.R
import com.paisaplus.compose.str
import com.paisaplus.compose.messages.optional.OptionalModelViewModel
import java.util.Locale

/** One-time, device-local introduction; skipping never starts a model download. */
@Composable
fun AutomationSetupScreen(onDone: () -> Unit, vm: OptionalModelViewModel = hiltViewModel()) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val work by vm.download.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val active = work?.state?.let { !it.isFinished } == true
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importPack) }
    val spec = vm.specification
    BackHandler(onBack = onDone)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(str(R.string.auto_setup_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(str(R.string.auto_setup_title), style = MaterialTheme.typography.headlineLarge)
                Text(str(R.string.auto_setup_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SetupFeature(Icons.AutoMirrored.Rounded.ReceiptLong, str(R.string.auto_setup_builtin))
                SetupFeature(Icons.Rounded.AutoAwesome, str(R.string.auto_setup_optional))
                SetupFeature(Icons.Rounded.Lock, str(R.string.auto_setup_private))
                Text(str(R.string.auto_setup_size, String.format(Locale.ROOT, "%.1f", spec.bytes / 1_000_000.0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (installed != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text(str(R.string.optional_model_installed))
                    }
                }
                if (active) {
                    val downloaded = work?.progress?.getLong("bytes", 0) ?: 0L
                    LinearProgressIndicator(progress = { (downloaded.toFloat() / spec.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(work?.progress?.getString("stage") ?: str(R.string.optional_model_waiting), style = MaterialTheme.typography.bodyMedium)
                    TextButton(vm::cancelDownload) { Text(str(R.string.optional_model_cancel_download)) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (work?.state == WorkInfo.State.FAILED && installed == null) {
                    Text(work?.outputData?.getString("error") ?: str(R.string.optional_model_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/maxcodl/Paisa-plus/releases/tag/models-v1"))) }) { Text(str(R.string.optional_model_release)) }
                }
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = if (installed != null) onDone else vm::downloadModel,
                    enabled = !active && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(str(if (installed != null) R.string.auto_setup_continue else R.string.optional_model_download)) }
                if (installed == null && !active) TextButton({ pick.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) {
                    Text(str(R.string.optional_model_import))
                }
                TextButton(onDone, Modifier.heightIn(min = 48.dp)) {
                    Text(str(if (active || busy) R.string.auto_setup_background else R.string.auto_setup_skip))
                }
            }
        }
    }
}

@Composable
private fun SetupFeature(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
