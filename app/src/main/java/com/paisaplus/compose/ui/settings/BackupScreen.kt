package com.paisaplus.compose.ui.settings

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.IconBadge
import com.paisaplus.compose.ui.components.PillButton
import com.paisaplus.compose.ui.components.TintCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val whenFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

/** Export / restore / daily folder backup. */
@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val dir by vm.backupDir.collectAsStateWithLifecycle()
    val last by vm.lastBackup.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportTo) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirm = it }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setBackupDir) }

    Scaffold(topBar = { BackTopBar(str(R.string.backup_restore), onBack) }) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Section(Icons.Rounded.Save, str(R.string.export), str(R.string.all_data_and_settings_in)) {
                    PillButton(str(R.string.export_backup), { export.launch("paisa-${LocalDate.now()}.json") })
                }
            }
            item {
                Section(Icons.Rounded.Restore, str(R.string.restore), str(R.string.replaces_all_data_paisa_or)) {
                    OutlinedButton({ restore.launch(arrayOf("application/json", "*/*")) }, Modifier.heightIn(min = 48.dp)) { Text(str(R.string.choose_backup_file)) }
                }
            }
            item {
                Section(
                    Icons.Rounded.Backup,
                    str(R.string.daily_backup),
                    if (dir == null) str(R.string.off)
                    else str(R.string.daily_keeps_last_x_n, com.paisaplus.compose.data.BackupManager.KEEP) +
                        str(R.string.last_backup) + (last?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(whenFmt) } ?: str(R.string.not_yet)),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ folder.launch(null) }, Modifier.heightIn(min = 48.dp)) { Text(if (dir == null) str(R.string.choose_folder) else str(R.string.change_folder)) }
                        if (dir != null) {
                            OutlinedButton(vm::backupNow, Modifier.heightIn(min = 48.dp)) { Text(str(R.string.back_up_now)) }
                            TextButton({ vm.setBackupDir(null) }, Modifier.heightIn(min = 48.dp)) { Text(str(R.string.turn_off)) }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(str(R.string.replace_all_data)) },
            text = { Text(str(R.string.replaces_all_data_export_first)) },
            confirmButton = { TextButton(onClick = { vm.import(uri); confirm = null }) { Text(str(R.string.replace)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(str(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, actions: @Composable () -> Unit) {
    TintCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon)
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.padding(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(6.dp))
            actions()
        }
    }
}
