package com.paisaplus.compose.messages

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Opt-in notification access. No inbox history, Gmail OAuth, network or SMS permission is used. */
@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {
    @Inject lateinit var inbox: MessageInboxRepository
    @Inject lateinit var settings: com.paisaplus.compose.data.SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Settings › Message import › Sources; read on every post, so a switch applies at once. */
    @Volatile private var prefs = com.paisaplus.compose.data.MessagePrefs()

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch { settings.messages.collect { prefs = it } }
        // Notices already in the shade when access (re)connects: after an update, a reboot or switching access on. Replays dedupe.
        runCatching { activeNotifications?.forEach(::onNotificationPosted) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val smsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        // UPI apps' own "Paid ₹X to Y" notices: kept as hints that tag the matching bank alert with the app and full name.
        AutoAdd.appPackages[sbn.packageName]?.let { app ->
            val ex = sbn.notification.extras
            val text = listOfNotNull(ex.getCharSequence(Notification.EXTRA_TITLE), ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT)).joinToString(" ")
            if (text.isNotBlank() && text.length < 2000) scope.launch {
                runCatching { inbox.receiveHint(text, app, sbn.postTime) }
                // No bank alert by then: the notice goes to Review as a payment to confirm (also re-checked whenever Review opens).
                kotlinx.coroutines.delay(11 * 60_000L)
                runCatching { inbox.promoteHints() }
            }
            return
        }
        if (sbn.packageName in AutoAdd.storePackages) {
            val ex = sbn.notification.extras
            val text = listOfNotNull(ex.getCharSequence(Notification.EXTRA_TITLE), ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT)).joinToString(" ")
            if (text.isNotBlank() && text.length < 2000) scope.launch {
                runCatching { inbox.receiveStoreNotice(text, "Amazon", sbn.postTime) }
                kotlinx.coroutines.delay(11 * 60_000L)
                runCatching { inbox.promoteHints() }
            }
            return
        }
        val source = when (sbn.packageName) {
            "com.google.android.gm" -> if (prefs.gmail) "Gmail" else return
            smsPackage -> if (prefs.sms) "SMS" else return
            // Banking apps picked in Settings: same alerts as their SMS (card bills and own-account transfers show up here too); dedup by reference.
            in prefs.apps -> "Bank app"
            else -> return
        }
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        // A messaging thread bundles unread texts into one notification (big text = all of them); take them one by one.
        // Replays are harmless: receive() dedups by reference/fingerprint.
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { m ->
            (m as? android.os.Bundle)?.getCharSequence("text")?.toString()?.takeIf { it.isNotBlank() }?.let { it to ((m.getLong("time")).takeIf { t -> t > 0 } ?: sbn.postTime) }
        }.orEmpty().ifEmpty {
            // Prefer expanded text. Do not concatenate inbox/group lines: they may be separate payments.
            val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
            listOf(body to sbn.postTime)
        }
        scope.launch {
            // Notification callbacks must not crash the service on malformed input or storage errors.
            messages.forEach { (body, at) -> if (body.length <= 16_384) runCatching { inbox.receive(body, source, at) } }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
