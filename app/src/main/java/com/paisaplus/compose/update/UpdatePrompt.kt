package com.paisaplus.compose.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.str

/**
 * GitHub release notes as plain text: the changelog part only (GitHub's generated "What's Changed" / "Full Changelog"
 * and the download section are dropped), headings and bullets made readable.
 */
fun releaseNotes(body: String): String = body
    .substringBefore("## What's Changed").substringBefore("**Full Changelog**").substringBefore("## Download")
    .lines().joinToString("\n") { l ->
        val t = l.trimEnd()
        when {
            t.startsWith("#") -> t.trimStart('#', ' ')
            t.startsWith("- ") || t.startsWith("* ") -> "• " + t.drop(2)
            else -> t
        }.replace("**", "").replace("`", "")
    }.trim()

/**
 * Checks once on opening (unless updates are Off) and, when a newer release exists that hasn't been offered yet,
 * shows its changelog with Install (downloads, verifies, then Android's installer) or Later (not offered again for
 * that version; Settings › About › App updates still has it).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdatePrompt(vm: UpdatesViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.checkOnOpen() }
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val release = prompt ?: return
    val ctx = LocalContext.current
    val downloading = state as? UpdateState.Downloading
    AlertDialog(
        onDismissRequest = { if (downloading == null) vm.later(release) },
        title = { Text(str(R.string.update_x_available, release.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(str(R.string.whats_new), style = MaterialTheme.typography.titleSmall)
                Text(
                    releaseNotes(release.notes).ifBlank { release.name },
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (downloading != null) {
                    LinearWavyProgressIndicator({ downloading.progress }, Modifier.fillMaxWidth())
                    Text(str(R.string.downloading_x, (downloading.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                }
                (state as? UpdateState.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (release.sha256 == null) Text(str(R.string.update_no_checksum), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button({
                // Android asks once to allow installs from Paisa+; send the user there first.
                if (!vm.canInstall()) ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                else vm.install(release)
            }, enabled = downloading == null && release.sha256 != null) { Text(str(R.string.install)) }
        },
        dismissButton = { TextButton({ vm.later(release) }, enabled = downloading == null) { Text(str(R.string.later)) } },
    )
}
