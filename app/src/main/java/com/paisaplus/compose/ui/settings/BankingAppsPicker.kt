package com.paisaplus.compose.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paisaplus.compose.R
import com.paisaplus.compose.data.MessagePrefs
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.components.LinkRow

private data class InstalledApp(val pkg: String, val label: String)

/** Settings › Sources › Banking apps: pick which installed apps' notifications are read as bank alerts. */
@Composable
fun BankingAppsRow(prefs: MessagePrefs, save: (MessagePrefs) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LinkRow(Icons.Rounded.AccountBalance, 6, str(R.string.banking_apps), str(R.string.banking_apps_x, prefs.apps.size)) { open = true }
    if (open) BankingAppsDialog(prefs.apps, { open = false }) { save(prefs.copy(apps = it)) }
}

@Composable
private fun BankingAppsDialog(selected: Set<String>, dismiss: () -> Unit, save: (Set<String>) -> Unit) {
    val pm = LocalContext.current.packageManager
    val apps = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
    var picked by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    // Saved apps that aren't launchable here (uninstalled, or not yet installed) stay listed by package so they can be unticked.
    val shown = (apps + selected.filter { s -> apps.none { it.pkg == s } }.map { InstalledApp(it, it) })
        .filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(str(R.string.banking_apps)) },
        text = {
            Column {
                Text(str(R.string.banking_apps_summary))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true, placeholder = { Text(str(R.string.search)) })
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.pkg }) { a ->
                        val on = a.pkg in picked
                        Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - a.pkg else picked + a.pkg }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, null)
                            Text(a.label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ save(picked); dismiss() }) { Text(str(R.string.ok)) } },
        dismissButton = { TextButton(dismiss) { Text(str(R.string.cancel)) } },
    )
}
