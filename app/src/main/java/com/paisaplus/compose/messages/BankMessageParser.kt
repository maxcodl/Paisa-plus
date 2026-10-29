package com.paisaplus.compose.messages

import com.paisaplus.compose.data.TransactionType
import java.math.BigDecimal
import java.util.Locale

/** Amounts remain exact paise until the existing expense repository boundary. */
data class ParsedBankMessage(
    val amountPaise: Long,
    val type: TransactionType,
    val accountSuffix: String?,
    val merchant: String?,
    val reference: String?,
    val isUpi: Boolean,
    val score: Double,
    /** The alert carries a link: kept for Review, never added automatically (phishing texts imitate bank alerts). */
    val hasLink: Boolean = false,
)

sealed interface MessageParseResult {
    data class Candidate(val transaction: ParsedBankMessage) : MessageParseResult
    data class Ignored(val reason: String) : MessageParseResult
}

/** Conservative INR-only parser shared by SMS, email notification and pasted text entry points. */
object BankMessageParser {
    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)
    private val exclusions = rx("""\b(otp|one[ -]time|verification code|password|pin|cvv|failed|failure|declined|unsuccessful|pending|will be|would be|to be debited|scheduled|upcoming|autopay request|payment request|collect request|due|statement|eligible|apply now|claim|verify kyc|update kyc|click here|not debited|not credited)\b""")
    private val foreignCurrency = rx("""\b(USD|EUR|GBP|AED|LKR|CAD|AUD|SGD|JPY)\b|[$€£]""")
    private val links = rx("""https?://|www\.|\b[a-z0-9-]+\.(com|in|net|org|xyz|click|link)\b""")
    private val action = rx("""(?<![a-z])(?:debited|credited|spent|withdrawn|received|refunded|deposited|charged|paid|sent|payment of|credit of|debit of|debit transaction of|credit transaction of)\b|\b(?:dr|cr)\.?(?=\s*(?:INR|Rs|₹))|\b(?:dr|cr)\.(?=\s+from\b)""")
    private val amount = rx("""(?:INR\b|Rs\.?|₹)\s*:?\s*([0-9][0-9,]*(?:\.[0-9]+)?)(?![0-9.,])""")
    private val validMoney = Regex("""(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3})(?:\.[0-9]{1,2})?""")
    private val account = rx("""\b(?:a/c|acct|account|acc|card|ac)\s*(?:(?:no\.?|number|ending(?:\s+with)?|ends?\s+with|xx)\s*[:.-]?\s*)*(?:[0-9]*[xX*•]+)*(\d{3,18})\b""")
    private val reference = rx("""\b(?:upi\s+ref(?:erence)?|rrn|ref(?:erence)?|utr|txn\s*(?:id|no)|transaction\s*(?:id|no))\s*(?:no\.?)?\s*[:#.-]?\s*([a-z0-9]{6,40})\b|\bUPI\s*[:#-]?\s*(\d{8,20})\b""")
    private val payee = rx("""(?:\bInfo\s*:\s*|\bat\s+|\bto\s+(?!your\b|(?:a/c|account|acct|card|ac)(?=\b|[xX*•0-9]))|\bfrom\s+(?!your\b|(?:a/c|account|acct|card|ac)(?=\b|[xX*•0-9])))(.+?)(?=\s+(?:on|using|via|from|to|at|ref|upi|avl|available|balance)\b|[.;\n]|$)""")
    private val beneficiaryCredit = rx(""";\s*([a-z][a-z0-9 @&'_/-]{1,90}?)\s+credited(?=[.;])|\band\s+credited\s+to\s+(.+?)(?=\s+(?:via|on|ref)\b|[.;]|$)""")
    private val ownedAccountWords = rx("""\b(account|a/c|acct|card|bank|your|my|own|another)\b""")
    private val reversal = rx("""\brevers(?:al|ed)\b""")
    private val mandate = rx("""\bmandate\b""")
    private val creditedWord = rx("""(?<![a-z])credited\b""")
    private val debitedWord = rx("""(?<![a-z])debited\b""")
    /** A credit card bill payment reaching the card: "Payment of INR 34,000 has been received on your Credit Card". */
    private val cardPayment = rx("""\bpayment\b.*\bcredit card\b.*\b(?:received|credited|processed)\b|\b(?:received|credited)\b.*\bpayment\b.*\bcredit card\b|\breceived payment\b.*\bcredit card\b|\bpayment\b.*\b(?:received|credited)\b.*\bcredit card\b""")
    /** Second leg of a transfer: "Acct XXX002 debited ... Acct XXX626 credited." The alert is the debit. */
    private val secondLeg = rx("""[.;]\s*(?:a/c|acct|account|acc)\s*[xX*•]*\d{3,}\s+credited""")
    private val balance = rx("""\b(?:avl|avail(?:able)?|bal(?:ance)?|limit|outstanding|reward|cashback)\b""")

    fun parse(body: String): MessageParseResult {
        fun ignored(reason: String) = MessageParseResult.Ignored(reason)
        if (body.length !in 10..16_384) return ignored("Message is empty, too short or too long")
        val text = body.replace('\u00a0', ' ').replace(Regex("[\\t\\r ]+"), " ")
        if (exclusions.containsMatchIn(text)) return ignored("OTP, failed, future, reversal or informational message; review manually")
        // Reversals count only as money credited back; mandates only once actually debited (not "created" or "will be").
        if (reversal.containsMatchIn(text) && !creditedWord.containsMatchIn(text)) return ignored("Reversal without a credit; review manually")
        if (mandate.containsMatchIn(text) && !debitedWord.containsMatchIn(text)) return ignored("Mandate set-up, not a payment")
        // Many real alerts add a dispute or offer link; links can't authenticate a bank, so such alerts only go to Review.
        val hasLink = links.containsMatchIn(text)
        if (foreignCurrency.containsMatchIn(text)) return ignored("Only INR transaction alerts are supported")
        val beneficiary = beneficiaryCredit.findAll(text).firstOrNull { match ->
            val name = match.groupValues.drop(1).first { it.isNotBlank() }
            !ownedAccountWords.containsMatchIn(name) && !amount.containsMatchIn(name)
        }
        val allActions = action.findAll(text).toList()
        // Some alerts say "your account debited; MERCHANT credited". Only this explicit
        // beneficiary clause may be ignored; a second account/amount remains ambiguous.
        val leg = secondLeg.find(text)
        var actions = allActions.filterNot { a ->
            (a.value.equals("credited", true) && beneficiary != null && a.range.first in beneficiary.range &&
                allActions.any { it.range.first < a.range.first && it.value.lowercase(Locale.ROOT) in setOf("debited", "sent", "dr.") }) ||
                (leg != null && a.range.first in leg.range && allActions.any { it.range.first < leg.range.first && it.value.equals("debited", true) })
        }
        if (actions.isEmpty()) return ignored("No completed transaction action")
        // Mixed debit/credit statements often describe a transfer's two legs; never guess ownership.
        fun isCredit(a: String) = a.lowercase(Locale.ROOT) in setOf("credited", "received", "refunded", "deposited", "cr.", "cr", "credit of", "credit transaction of")
        // A card bill payment is a credit to the card even though it says "payment of".
        if (cardPayment.containsMatchIn(text)) actions = actions.filter { isCredit(it.value) }.ifEmpty { actions.map { it } }
        val cardPaid = cardPayment.containsMatchIn(text)
        if (actions.map { isCredit(it.value) }.distinct().size > 1) return ignored("Ambiguous transaction direction")
        val firstAction = actions.first()
        val amounts = amount.findAll(text).filter { m ->
            // Exclude amounts directly introduced by balance/limit/reward labels.
            val prefix = text.substring(0, m.range.first).takeLast(32)
            val lastClause = prefix.split(Regex("[.;\\n]")).last()
            !balance.containsMatchIn(lastClause)
        }.toList()
        if (amounts.size != 1) return ignored("Missing or ambiguous transaction amount")
        if (!validMoney.matches(amounts.single().groupValues[1])) return ignored("Invalid amount formatting")
        val money = runCatching { BigDecimal(amounts.single().groupValues[1].replace(",", "")).movePointRight(2).longValueExact() }.getOrNull()
            ?: return ignored("Invalid amount")
        if (money <= 0) return ignored("Amount must be positive")
        val suffix = account.find(text)?.groupValues?.get(1)?.takeLast(4)
        val ref = reference.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.uppercase(Locale.ROOT)
        if (suffix == null && ref == null) return ignored("No account or transaction reference")
        val score = MessageModel.score(text)
        if (score < MessageModel.THRESHOLD) return ignored("Local model is uncertain; enter manually")
        val beneficiaryName = beneficiary?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim()
        val merchant = beneficiaryName ?: payee.findAll(text).map { it.groupValues[1].trim().take(100) }
            .firstOrNull { it.isNotBlank() && !it.startsWith("INR", true) && !it.startsWith("Rs", true) && !it.first().isDigit() && it.first() != '+' && !ownedAccountWords.containsMatchIn(it) }
        return MessageParseResult.Candidate(ParsedBankMessage(
            money, if (cardPaid || isCredit(firstAction.value)) TransactionType.INCOME else TransactionType.EXPENSE,
            suffix, merchant, ref, rx("\\bUPI\\b").containsMatchIn(text), score, hasLink,
        ))
    }
}
