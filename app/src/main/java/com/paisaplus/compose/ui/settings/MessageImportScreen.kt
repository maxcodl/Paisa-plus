package com.paisaplus.compose.ui.settings

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.str
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.messages.*
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.ChoiceChips
import com.paisaplus.compose.ui.components.TintCard
import java.text.DateFormat
import java.util.Date

@Composable
fun MessageImportScreen(app: AppViewModel, onBack: () -> Unit, vm: MessageInboxViewModel = hiltViewModel()) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val data by app.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    fun accessGranted() = ctx.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(ctx, BankNotificationListener::class.java))
    var access by remember { mutableStateOf(accessGranted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = accessGranted() }
    // Message text is intentionally not persisted in saved instance state.
    var text by remember { mutableStateOf("") }
    Scaffold(topBar = { BackTopBar(str(R.string.message_import_title), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(str(R.string.message_import_intro))
                Text(str(R.string.message_import_limits), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text(str(if (access) R.string.message_access_enabled else R.string.message_enable_access))
                }
            }
            // Android 13+ greys out notification access for sideloaded apps until "Allow restricted settings" is on.
            if (!access && android.os.Build.VERSION.SDK_INT >= 33) item {
                TintCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str(R.string.restricted_settings_title), style = MaterialTheme.typography.titleMedium)
                        Text(str(R.string.restricted_settings_steps), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))) }) {
                            Text(str(R.string.open_app_info))
                        }
                    }
                }
            }
            item {
                val prefs by app.messagePrefs.collectAsStateWithLifecycle()
                com.paisaplus.compose.ui.components.ListHeader(str(R.string.sources))
                com.paisaplus.compose.ui.components.ListGroup {
                    item { com.paisaplus.compose.ui.components.SwitchRow(androidx.compose.material.icons.Icons.Rounded.Sms, 1, "SMS", str(R.string.source_sms_summary), prefs.sms) { app.setMessagePrefs(prefs.copy(sms = it)) } }
                    item { com.paisaplus.compose.ui.components.SwitchRow(androidx.compose.material.icons.Icons.Rounded.Mail, 3, "Gmail", str(R.string.source_gmail_summary), prefs.gmail) { app.setMessagePrefs(prefs.copy(gmail = it)) } }
                    item { com.paisaplus.compose.ui.components.SwitchRow(androidx.compose.material.icons.Icons.Rounded.Wifi, 5, str(R.string.wifi_only_download), str(R.string.wifi_only_download_summary), prefs.wifiOnly) { app.setMessagePrefs(prefs.copy(wifiOnly = it)) } }
                }
            }
            item {
                OutlinedTextField(text, { text = it.take(16_384) }, label = { Text(str(R.string.message_paste)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = { vm.parse(text); text = "" }, enabled = text.isNotBlank() && !vm.busy) {
                    Text(str(R.string.message_parse))
                }
                vm.feedback?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            item { OptionalModelCard() }
            item { Text(str(R.string.message_review_count, pending.size), style = MaterialTheme.typography.titleLarge) }
            items(pending, key = { it.fingerprint }) { row ->
                MessageReview(row, data.accounts.map { it.account }, data.categories, vm)
            }
        }
    }
}

@Composable
private fun MessageReview(row: MessageInboxEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>, vm: MessageInboxViewModel) {
    // Suggest only an unambiguous suffix match; users always choose/confirm the target account.
    val suggested = accounts.filter { row.accountSuffix != null && it.number.filter(Char::isDigit).endsWith(row.accountSuffix) }.singleOrNull()?.id ?: 0L
    var accountId by rememberSaveable(row.fingerprint, suggested) { mutableStateOf(suggested) }
    var categoryId by rememberSaveable(row.fingerprint) { mutableStateOf(0L) }
    var name by rememberSaveable(row.fingerprint) { mutableStateOf(row.merchant.orEmpty()) }
    TintCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("INR ${java.math.BigDecimal.valueOf(row.amountPaise, 2).toPlainString()} · ${row.type.name}", style = MaterialTheme.typography.titleMedium)
            Text("${row.source} · ${row.accountSuffix ?: str(R.string.message_unknown_account)}")
            Text(str(R.string.message_received_at, DateFormat.getDateTimeInstance().format(Date(row.receivedAt))), style = MaterialTheme.typography.bodySmall)
            Text(row.body, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text(str(R.string.message_payee)) }, modifier = Modifier.fillMaxWidth())
            Text(str(R.string.message_choose_account))
            ChoiceChips<AccountEntity?>(accounts, accounts.firstOrNull { it.id == accountId }, { it?.name.orEmpty() }, { accountId = it?.id ?: 0L })
            Text(str(R.string.message_choose_category))
            ChoiceChips<CategoryEntity?>(categories, categories.firstOrNull { it.id == categoryId }, { it?.name.orEmpty() }, { categoryId = it?.id ?: 0L })
            Text(str(R.string.message_review_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ vm.dismiss(row.fingerprint) }, enabled = !vm.busy) { Text(str(R.string.message_dismiss)) }
                Button({ vm.confirm(row.fingerprint, accountId, categoryId, name) }, enabled = !vm.busy && name.isNotBlank() && accounts.any { it.id == accountId } && categories.any { it.id == categoryId }) {
                    Text(str(R.string.message_save))
                }
            }
        }
    }
}
