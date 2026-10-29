package com.paisaplus.compose.messages

import android.content.Context
import android.provider.Telephony

/**
 * Reads the last [days] of the SMS inbox (READ_SMS) and sends every bank message through the same parser and
 * review inbox as live alerts. Already-seen messages are skipped by their fingerprint.
 * Returns counts: added automatically, sent to review, already in transactions, seen before, statement rows filled in.
 */
suspend fun MessageInboxRepository.scanSmsHistory(ctx: Context, days: Int): IntArray {
    val since = if (days <= 0) 0L else System.currentTimeMillis() - days * 86_400_000L
    var added = 0; var review = 0; var dupes = 0; var seen = 0; var merged = 0
    ctx.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
        "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
        "${Telephony.Sms.DATE} ASC",
    )?.use { c ->
        val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        while (c.moveToNext()) {
            val text = c.getString(body) ?: continue
            if (text.length > 16_384) continue
            when (runCatching { receive(text, "SMS", c.getLong(date), scan = true) }.getOrNull()) {
                AUTO_ADDED -> added++
                ADDED_TO_REVIEW -> review++
                MERGED -> merged++
                DUPLICATE, COVERED -> dupes++
                "Message already detected" -> seen++
            }
        }
    }
    return intArrayOf(added, review, dupes, seen, merged)
}
