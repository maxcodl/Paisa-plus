package com.paisaplus.compose.messages

import android.content.Context
import android.provider.Telephony
import com.paisaplus.compose.data.TransactionType

private val hasMoney = Regex("""(?:₹|rs\.?|inr)\s*[0-9]""", RegexOption.IGNORE_CASE)
/** Skipped messages worth showing in the log: they talk about a payment but aren't ads, loans or offers. */
private val looksLikePayment = Regex("""\b(debited|credited|deposit(?:ed)?|withdr[ae]wn|paid|payment|received|spent|refund(?:ed)?)\b""", RegexOption.IGNORE_CASE)
private val looksLikeAd = Regex("""https?://|\b(loan|offer|cashback|apply|eligible|pre-?approved|bonus|reward|win|free|discount|coupon|upgrade|recharge now)\b""", RegexOption.IGNORE_CASE)

/**
 * Reads the last [days] of the SMS inbox (READ_SMS) and sends every bank message through the same parser and
 * review inbox as live alerts. Already-seen messages are skipped by their fingerprint.
 * Returns counts: added automatically, sent to review, already in transactions, seen before, statement rows filled in.
 * Every decision is written to [ScanLog] (Settings › SMS & AI › Scan log).
 */
suspend fun MessageInboxRepository.scanSmsHistory(ctx: Context, days: Int): IntArray {
    val since = if (days <= 0) 0L else System.currentTimeMillis() - days * 86_400_000L
    var added = 0; var review = 0; var dupes = 0; var seen = 0; var merged = 0; var ignored = 0; var total = 0
    ScanLog.start()
    try {
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val count = c.count
            while (c.moveToNext()) {
                val text = c.getString(body) ?: continue
                if (text.length > 16_384) continue
                total++
                if (total % 20 == 0 || total == count) ScanLog.progress(total, count)
                val at = c.getLong(date)
                val result = runCatching { receive(text, "SMS", at, scan = true) }
                val why = ScanLog.takeNotes()
                val parsed = BankMessageParser.parse(text)
                val what = when (parsed) {
                    is MessageParseResult.Candidate -> parsed.transaction.let { p ->
                        "₹%.2f %s · %s%s".format(p.amountPaise / 100.0, if (p.type == TransactionType.INCOME) "in" else "out", p.merchant ?: "no payee", p.accountSuffix?.let { " · XX$it" }.orEmpty())
                    }
                    is MessageParseResult.Ignored -> text.replace(Regex("\\s+"), " ").take(90)
                }
                when (val r = result.getOrNull()) {
                    AUTO_ADDED -> { added++; ScanLog.add("ADDED", at, what, why) }
                    ADDED_TO_REVIEW -> { review++; ScanLog.add("REVIEW", at, what, why) }
                    MERGED -> { merged++; ScanLog.add("MERGED", at, what, why) }
                    DUPLICATE -> { dupes++; ScanLog.add("DUPLICATE", at, what, why) }
                    COVERED -> { dupes++; ScanLog.add("COVERED", at, what, why) }
                    "Message already detected" -> { seen++; if (why.isNotEmpty()) ScanLog.add("SEEN", at, what, why) }
                    null -> ScanLog.add("ERROR", at, what, result.exceptionOrNull()?.message.orEmpty())
                    else -> { ignored++; if (hasMoney.containsMatchIn(text) && looksLikePayment.containsMatchIn(text) && !looksLikeAd.containsMatchIn(text)) ScanLog.add("IGNORED", at, what, r) }
                }
            }
        }
    } finally {
        runCatching { checkBalances() }
        ScanLog.finish(ctx, "$total messages · $added added · $review to review · $merged merged · $dupes duplicate/covered · $seen seen before · $ignored not payments")
    }
    return intArrayOf(added, review, dupes, seen, merged)
}
