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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val smsPackage = Telephony.Sms.getDefaultSmsPackage(this)
        val source = when (sbn.packageName) {
            "com.google.android.gm" -> "Gmail"
            smsPackage -> "SMS"
            else -> return
        }
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        // Prefer expanded text. Do not concatenate inbox/group lines: they may be separate payments.
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        if (body.length > 16_384) return
        scope.launch {
            // Notification callbacks must not crash the service on malformed input or storage errors.
            runCatching { inbox.receive(body, source, sbn.postTime) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
