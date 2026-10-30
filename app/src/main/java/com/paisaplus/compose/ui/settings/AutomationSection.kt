package com.paisaplus.compose.ui.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.Routes
import com.paisaplus.compose.messages.BankNotificationListener
import com.paisaplus.compose.messages.MessageInboxViewModel
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.LinkRow
import com.paisaplus.compose.ui.components.ListGroup
import com.paisaplus.compose.ui.components.ListHeader
import com.paisaplus.compose.ui.components.PrefBlock
import com.paisaplus.compose.ui.components.PrefRow
import com.paisaplus.compose.ui.components.SwitchRow
import com.paisaplus.compose.ui.components.TintCard

/** Scan ranges in days; 0 = every message on the phone. */
private val scanDays = listOf(7, 30, 90, 365, 0)

/** Settings › SMS & AI: notification access, sources, past-SMS scan, the optional on-device model, and the review inbox. */
@Composable
fun AutomationSection(app: AppViewModel, open: (String) -> Unit, inbox: MessageInboxViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val prefs by app.messagePrefs.collectAsStateWithLifecycle()
    val pending by inbox.pending.collectAsStateWithLifecycle()
    fun accessGranted() = ctx.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(ctx, BankNotificationListener::class.java))
    fun smsGranted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    var access by remember { mutableStateOf(accessGranted()) }
    var sms by remember { mutableStateOf(smsGranted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = accessGranted(); sms = smsGranted() }
    var days by remember { mutableIntStateOf(90) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> sms = ok; if (ok) inbox.scanSms(days) }

    Column {
        ListGroup {
            item {
                PrefRow(
                    Icons.Rounded.NotificationsActive, 0, str(R.string.notification_access),
                    str(if (access) R.string.notification_access_on else R.string.notification_access_off),
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                ) { androidx.compose.material3.Switch(access, null) }
            }
            item { LinkRow(Icons.AutoMirrored.Rounded.Message, 2, str(R.string.review_inbox), str(R.string.x_waiting, pending.size)) { open(Routes.IMPORT_MESSAGES) } }
        }
        // Android 13+ greys out notification access for sideloaded apps until "Allow restricted settings" is on.
        if (!access && Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(8.dp))
            TintCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str(R.string.restricted_settings_title), style = MaterialTheme.typography.titleMedium)
                    Text(str(R.string.restricted_settings_steps), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) {
                        Text(str(R.string.open_app_info))
                    }
                }
            }
        }

        ListHeader(str(R.string.when_a_payment_is_found))
        ListGroup {
            item { SwitchRow(Icons.Rounded.AutoMode, 4, str(R.string.add_automatically), str(R.string.add_automatically_summary), prefs.autoAdd) { app.setMessagePrefs(prefs.copy(autoAdd = it)) } }
        }

        ListHeader(str(R.string.sources))
        ListGroup {
            item { SwitchRow(Icons.Rounded.Sms, 1, "SMS", str(R.string.source_sms_summary), prefs.sms) { app.setMessagePrefs(prefs.copy(sms = it)) } }
            item { SwitchRow(Icons.Rounded.Mail, 3, "Gmail", str(R.string.source_gmail_summary), prefs.gmail) { app.setMessagePrefs(prefs.copy(gmail = it)) } }
            item { BankingAppsRow(prefs) { app.setMessagePrefs(it) } }
        }

        ListHeader(str(R.string.past_messages))
        ListGroup {
            item {
                PrefBlock(
                    Icons.Rounded.History, 5, str(R.string.scan_past_sms),
                    if (inbox.busy) str(R.string.scanning) else inbox.feedback ?: str(R.string.scan_past_sms_summary),
                ) {
                    com.paisaplus.compose.ui.components.SegmentedTabs(scanDays, days, { when (it) { 0 -> str(R.string.all); 365 -> str(R.string.one_year); else -> str(R.string.x_days_short, it) } }, { days = it })
                    Spacer(Modifier.height(12.dp))
                    Button(
                        { if (sms) inbox.scanSms(days) else askSms.launch(Manifest.permission.READ_SMS) },
                        Modifier.fillMaxWidth(), enabled = !inbox.busy,
                    ) { Text(if (days == 0) str(R.string.scan_all_messages) else str(R.string.scan_last_x_days, days)) }
                    LiveScanLog(inbox.busy)
                    OutlinedButton({ open(Routes.SCAN_LOG) }, Modifier.fillMaxWidth()) { Text(str(R.string.scan_log)) }
                }
            }
        }

        ListHeader(str(R.string.on_device_ai))
        ListGroup {
            item { SwitchRow(Icons.Rounded.Wifi, 5, str(R.string.wifi_only_download), str(R.string.wifi_only_download_summary), prefs.wifiOnly) { app.setMessagePrefs(prefs.copy(wifiOnly = it)) } }
        }
        Spacer(Modifier.height(8.dp))
        OptionalModelCard()
    }
}

/** The app's notification channels, created up front so each can be opened in system settings. */
private fun ensureChannels(ctx: android.content.Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannels(listOf(
        NotificationChannel("recurring", str(R.string.recurring_transactions), NotificationManager.IMPORTANCE_HIGH),
        NotificationChannel("updates", str(R.string.app_updates), NotificationManager.IMPORTANCE_DEFAULT),
        NotificationChannel("downloads", str(R.string.downloads), NotificationManager.IMPORTANCE_LOW),
        NotificationChannel(com.paisaplus.compose.messages.AutoAddNotice.CHANNEL, str(R.string.auto_added_transactions), NotificationManager.IMPORTANCE_DEFAULT),
    ))
}

/** Settings › Notifications: permission state and one row per channel (each opens Android's own channel settings). */
@Composable
fun NotificationsSection() {
    val ctx = LocalContext.current
    fun allowed() = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    var on by remember { mutableStateOf(allowed()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { on = allowed() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { on = allowed() }
    fun appSettings() = ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
    fun channel(id: String) {
        ensureChannels(ctx)
        ctx.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, id))
    }
    Column {
        ListGroup {
            item {
                PrefRow(
                    Icons.Rounded.Notifications, 0, str(R.string.allow_notifications), str(if (on) R.string.on else R.string.off),
                    onClick = { if (!on && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else appSettings() },
                ) { androidx.compose.material3.Switch(on, null) }
            }
        }
        ListHeader(str(R.string.notification_types))
        ListGroup {
            item { LinkRow(Icons.Rounded.AutoMode, 4, str(R.string.auto_added_transactions), str(R.string.auto_added_channel_summary)) { channel(com.paisaplus.compose.messages.AutoAddNotice.CHANNEL) } }
            item { LinkRow(Icons.Rounded.EventRepeat, 1, str(R.string.recurring_transactions), str(R.string.recurring_reminders_summary)) { channel("recurring") } }
            item { LinkRow(Icons.Rounded.NewReleases, 5, str(R.string.app_updates), str(R.string.app_updates_channel_summary)) { channel("updates") } }
            item { LinkRow(Icons.Rounded.Download, 3, str(R.string.downloads), str(R.string.downloads_channel_summary)) { channel("downloads") } }
        }
    }
}
