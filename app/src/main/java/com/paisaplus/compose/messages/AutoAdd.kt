package com.paisaplus.compose.messages

import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.PaymentMethod
import com.paisaplus.compose.data.UpiApp
import java.util.Locale

/**
 * Turns a parsed bank alert into a ledger entry without asking: which of your accounts, the payee,
 * payment method and UPI app, and a category. Any doubt returns null and the message stays in Review.
 * (The optional AI model is not used here: in testing it misread amounts, dates and debit/credit.)
 */
object AutoAdd {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    // App named in the text first, then the payee's UPI handle (VPA suffixes each app issues).
    private val appWords = listOf(
        UpiApp.GPAY to rx("""\b(g ?pay|google ?pay)\b"""),
        UpiApp.PHONEPE to rx("""\bphone ?pe\b"""),
        UpiApp.PAYTM to rx("""\bpaytm\b"""),
        UpiApp.CRED to rx("""\bcred\b"""),
        UpiApp.SUPERMONEY to rx("""\bsuper\.?money\b"""),
        UpiApp.BHIM to rx("""\bbhim\b"""),
    )
    private val handles = listOf(
        UpiApp.GPAY to rx("""@ok(axis|hdfcbank|icici|sbi)\b"""),
        UpiApp.PHONEPE to rx("""@(ybl|ibl|axl)\b"""),
        UpiApp.PAYTM to rx("""@(paytm|ptys|ptaxis|pthdfc|ptyes|ptsbi)\b"""),
        UpiApp.CRED to rx("""@axisb\b"""),
        UpiApp.SUPERMONEY to rx("""@superyes\b"""),
        UpiApp.BHIM to rx("""@upi\b"""),
    )
    private val upiWords = rx("""\b(upi|vpa|imps|bhim)\b|@[a-z]{2,}\b""")
    private val cardWords = rx("""\b(credit|debit)\s+card\b|\bcard\s*(no\.?|ending|xx)""")
    private val tap = rx("""\b(contactless|tap(ped)?\s*(and|&|to)?\s*pay|nfc)\b""")
    private val salary = rx("""\bsalary\b""")
    /** A bill payment reaching a credit card: a transfer between your own accounts, not income. */
    private val cardPayment = rx("""\bpayment\b.*\bcredit card\b.*\b(received|processed|credited)\b|\b(received|credited)\b.*\bpayment\b.*\bcredit card\b|\bpayment\b.*\b(received|credited)\b.*\bcredit card\b""")
    private val transferWords = rx("""\b(neft|rtgs|imps)\b|\bself\b|\bown account\b""")

    /**
     * Bank SMS cut payee names at 15 characters ("KARTHIK JANARDA"): the one saved payee that starts with it
     * (ignoring case) becomes the name; none or several, the alert's own name stays.
     */
    fun complete(name: String?, known: Collection<String>): String? {
        if (name == null || name.length < 5) return name
        return known.filter { it.length > name.length && it.startsWith(name, ignoreCase = true) }.distinctBy { it.lowercase(Locale.ROOT) }.singleOrNull() ?: name
    }

    /** Statement names that say nothing about the payee: "UPI q692232694", "Paytm d19587428510", long id runs. */
    fun crypticName(name: String) = name.isBlank() || Regex("""\d{6,}|^(upi|paytm|imps|neft|rtgs|pos|nach|ach)\b""", RegexOption.IGNORE_CASE).containsMatchIn(name.trim())

    /** UPI apps by package, for their own payment notices. */
    val appPackages = mapOf(
        "com.google.android.apps.nbu.paisa.user" to UpiApp.GPAY,
        "com.phonepe.app" to UpiApp.PHONEPE,
        "net.one97.paytm" to UpiApp.PAYTM,
        "com.dreamplug.androidapp" to UpiApp.CRED,
        "money.super.payments" to UpiApp.SUPERMONEY,
        "in.org.npci.upiapp" to UpiApp.BHIM,
    )

    data class UpiNotice(val amountPaise: Long, val type: com.paisaplus.compose.data.TransactionType, val payee: String)

    private val noticeSkip = rx("""\b(request|requested|collect|failed|pending|declined|reminder|cashback|offer|reward|due|scratch|win|bill)\b""")
    private val money = """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val paidTo = listOf(
        rx("""(?:paid|sent)\s*$money\s*(?:to|for)\s+(.+?)(?=\s+(?:via|using|on|from|successfully|is|was)\b|[.!\n]|$)"""),
        rx("""$money\s*(?:paid|sent)\s+(?:to|for)\s+(.+?)(?=\s+(?:via|using|on|from|successfully|is|was)\b|[.!\n]|$)"""),
        rx("""(?:payment|transfer)\s+of\s*$money\s*(?:to|for)\s+(.+?)(?=\s+(?:via|using|on|from|successful|is|was)\b|[.!\n]|$)"""),
    )
    private val gotFrom = listOf(
        rx("""(?:received|got)\s*$money\s*from\s+(.+?)(?=\s+(?:via|using|on|in|to|is)\b|[.!\n]|$)"""),
        rx("""$money\s*received\s+from\s+(.+?)(?=\s+(?:via|using|on|in|to|is)\b|[.!\n]|$)"""),
    )

    /** "Paid ₹250 to Swiggy" / "₹1,200 received from Rahul": amount, direction and the full payee; null if it isn't one. */
    fun upiNotice(text: String): UpiNotice? {
        val t = text.replace('\u00a0', ' ')
        if (noticeSkip.containsMatchIn(t)) return null
        fun hit(list: List<Regex>, type: com.paisaplus.compose.data.TransactionType) = list.firstNotNullOfOrNull { it.find(t) }?.let { m ->
            val paise = runCatching { java.math.BigDecimal(m.groupValues[1].replace(",", "")).movePointRight(2).longValueExact() }.getOrNull() ?: return@let null
            val who = m.groupValues[2].trim().trimEnd('.', ',', '!').take(60)
            if (paise <= 0 || who.isBlank()) null else UpiNotice(paise, type, who)
        }
        return hit(paidTo, com.paisaplus.compose.data.TransactionType.EXPENSE) ?: hit(gotFrom, com.paisaplus.compose.data.TransactionType.INCOME)
    }

    fun upiApp(body: String): UpiApp? =
        appWords.firstOrNull { it.second.containsMatchIn(body) }?.first ?: handles.firstOrNull { it.second.containsMatchIn(body) }?.first

    /**
     * PaymentMethod name: UPI (with app) or tap for contactless. Card alerts don't say swipe vs online,
     * and a card bill payment isn't a card purchase, so those stay unset rather than guessed.
     */
    fun method(body: String, isUpi: Boolean): String? = when {
        cardPayment.containsMatchIn(body) -> null
        isUpi || upiWords.containsMatchIn(body) -> PaymentMethod.UPI.name
        tap.containsMatchIn(body) -> PaymentMethod.TAP.name
        else -> null
    }

    /** "swiggy@icici" → "Swiggy"; trims bank boilerplate. */
    fun payee(merchant: String?, body: String): String? {
        if (salary.containsMatchIn(body)) return "Salary"
        if (cardPayment.containsMatchIn(body)) return "Credit card payment"
        val raw = merchant?.trim()?.trimEnd('.', ',')?.takeIf { it.length in 2..60 } ?: return null
        // Bank codes: "VPS*Thrikkaka" -> Thrikkaka; "INF*INFT*046078" / "BIL*INFT*FJD4" carry no name -> ask.
        val m = if ('*' in raw) raw.substringAfterLast('*').takeIf { s -> s.count(Char::isLetter) >= 5 } ?: return null else raw
        val name = if ('@' in m) m.substringBefore('@').replace(Regex("[._-]+"), " ") else m
        // Banks shout names ("ANUSURI SRI KOU", "OYOROOMS"): title-case them; keep 1–2 letter initials ("K S").
        return name.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length <= 2 && w.all { it.isUpperCase() }) w else w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }.takeIf { it.isNotBlank() }
    }

    /**
     * The one account the alert is about: by its last digits; failing that, by the bank's name ("ICICI") among accounts
     * with no number saved yet. Bank account vs credit card follows whether the alert mentions a card. Null = ask.
     */
    fun account(accounts: List<AccountEntity>, suffix: String?, body: String): AccountEntity? {
        val card = cardWords.containsMatchIn(body)
        fun pick(list: List<AccountEntity>) = if (list.size <= 1) list.singleOrNull() else list.filter { (it.cardType == CardType.CREDIT) == card }.singleOrNull()
        val tail = suffix?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length >= 3 }
        if (tail != null) {
            val matches = accounts.filter { a -> a.number.filter(Char::isDigit).let { it.length >= tail.length && it.endsWith(tail) } }
            if (matches.isNotEmpty()) return pick(matches)
        }
        val named = accounts.filter { a ->
            (tail == null || a.number.none(Char::isDigit)) && a.cardType != CardType.CASH &&
                (a.bankName.ifBlank { a.name }).split(' ').first().takeIf { it.length >= 3 }?.let { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(body) } == true
        }
        return pick(named)
    }

    /** Same payee's last category; else Transfer for bank transfers; else "Other(s)". Null = can't choose, so review. */
    fun category(name: String?, body: String, categories: List<CategoryEntity>, history: List<ExpenseEntity>): CategoryEntity? {
        val byId = categories.associateBy { it.id }
        name?.let { n -> history.filter { it.name.equals(n, ignoreCase = true) }.maxByOrNull { it.time }?.let { byId[it.categoryId] }?.let { return it } }
        fun named(vararg n: String) = categories.firstOrNull { c -> n.any { c.name.trim().equals(it, ignoreCase = true) } }
        if (salary.containsMatchIn(body)) named("Salary", "Income")?.let { return it }
        if (cardPayment.containsMatchIn(body) || transferWords.containsMatchIn(body)) named("Transfer")?.let { return it }
        return named("Other", "Others", "Miscellaneous", "Misc")
    }
}
