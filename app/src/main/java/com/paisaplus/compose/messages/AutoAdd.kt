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
        UpiApp.MOBIKWIK to rx("""\bmobikwik\b"""),
        UpiApp.FREECHARGE to rx("""\bfreecharge\b"""),
        UpiApp.NAVI to rx("""\bnavi\b"""),
        UpiApp.NUBANK to rx("""\bnubank\b"""),
        UpiApp.MERCADOPAGO to rx("""\bmercado ?pago\b"""),
        UpiApp.PICPAY to rx("""\bpic ?pay\b"""),
        UpiApp.PAGBANK to rx("""\bpag ?bank\b"""),
    )
    private val handles = listOf(
        UpiApp.GPAY to rx("""@ok(axis|hdfcbank|icici|sbi)\b"""),
        UpiApp.PHONEPE to rx("""@(ybl|ibl|axl)\b"""),
        UpiApp.PAYTM to rx("""@(paytm|ptys|ptaxis|pthdfc|ptyes|ptsbi)\b"""),
        UpiApp.CRED to rx("""@axisb\b"""),
        UpiApp.SUPERMONEY to rx("""@superyes\b"""),
        UpiApp.BHIM to rx("""@upi\b"""),
        UpiApp.MOBIKWIK to rx("""@ikwik\b"""),
        UpiApp.FREECHARGE to rx("""@freecharge\b"""),
        UpiApp.NAVI to rx("""@naviaxis\b"""),
    )
    private val upiWords = rx("""\b(upi|vpa|imps|bhim)\b|@[a-z]{2,}\b""")
    private val cardWords = rx("""\b(credit|debit)\s+card\b|\bcard\s*(no\.?|ending|xx)""")
    private val tap = rx("""\b(contactless|tap(ped)?\s*(and|&|to)?\s*pay|nfc)\b""")
    private val salary = rx("""\bsalary\b""")
    private val recharge = rx("""\b(recharge|jio|airtel|vodafone|bsnl|vi prepaid)\b""")
    /** Axio's Amazon Pay Later alert: no account digits, reference or shop, only the amount. */
    val payLater = rx("""thank you for availing pay ?later credit of\s*(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    /** A bill payment reaching a credit card: a transfer between your own accounts, not income. */
    private val cardPayment = rx("""\bpayment\b.*\bcredit card\b.*\b(received|processed|credited)\b|\b(received|credited)\b.*\bpayment\b.*\bcredit card\b|\bpayment\b.*\b(received|credited)\b.*\bcredit card\b""")
    private val transferWords = rx("""\b(neft|rtgs|imps)\b|\bself\b|\bown account\b""")
    /** Payee that is a card bill: "SBI CARD", "ICICI Bank Credit Card", "CC payment". */
    private val cardPayee = rx("""\b(sbi ?card|credit ?card|cc ?(?:bill|payment)|(?:hdfc|icici|axis|amex|rbl|idfc|kotak|indusind|yes|federal|au|bob)\w*(?:\s+bank)?\s+(?:credit\s+)?card)\b""")

    /** A credit card bill payment, from either side: the card's "payment received" alert or the bank's debit to a card payee. */
    fun cardBill(merchant: String?, body: String) = cardPayment.containsMatchIn(body) || merchant?.let { cardPayee.containsMatchIn(it) } == true

    /**
     * Bank SMS cut payee names at 15 characters ("KARTHIK JANARDA"): the one saved payee that starts with it
     * (ignoring case) becomes the name; none or several, the alert's own name stays.
     */
    fun complete(name: String?, known: Collection<String>): String? {
        if (name == null || name.length < 5) return name
        return known.filter { it.length > name.length && it.startsWith(name, ignoreCase = true) }.distinctBy { it.lowercase(Locale.ROOT) }.singleOrNull() ?: name
    }

    /** Alert and saved-entry names refer to the same payee: letters only, either one starts the other ("BARKATH RESTAUR" ~ "Barkath Restaurant"). */
    fun sameName(a: String?, b: String?): Boolean {
        fun norm(s: String?) = s.orEmpty().lowercase(Locale.ROOT).filter(Char::isLetter)
        val x = norm(a)
        val y = norm(b)
        return x.length >= 4 && y.length >= 4 && (x.startsWith(y) || y.startsWith(x))
    }

    /**
     * Two names that plausibly belong to one payee: one contains or starts the other (letters only), or they share a word of
     * 4+ letters. Automatic aliases need this, since statement rows are matched by amount and date, not by who was paid.
     */
    fun plausibleAlias(alias: String?, name: String?): Boolean {
        val x = aliasKey(alias)
        val y = aliasKey(name)
        if (x.length >= 4 && y.length >= 4 && (x.startsWith(y) || y.startsWith(x) || x in y || y in x)) return true
        fun words(s: String?) = s.orEmpty().lowercase(Locale.ROOT).split(Regex("[^a-z]+")).filter { it.length >= 4 }.toSet()
        return words(alias).intersect(words(name)).isNotEmpty()
    }

    fun aliasKey(s: String?) = s.orEmpty().lowercase(Locale.ROOT).filter(Char::isLetter)

    /** The name you keep [merchant] under: an alias with the same letters, or one cut at the SMS's 15 characters. */
    fun aliasFor(aliases: List<PayeeAliasEntity>, merchant: String?): String? {
        val k = aliasKey(merchant)
        if (k.length < 4) return null
        return aliases.firstOrNull { a -> a.key == k || (merchant.orEmpty().trim().length >= 15 || a.alias.length >= 15) && (a.key.startsWith(k) || k.startsWith(a.key)) && minOf(a.key.length, k.length) >= 10 }?.name
    }

    /** The alert carries a real payee name, not bank boilerplate ("dispute call 1800…/SMS BLOCK 4006") or nothing. */
    fun hasName(merchant: String?) = merchant != null && merchant.count(Char::isLetter) >= 3 && !Regex("""\d{4,}|\b(dispute|sms|block)\b""", RegexOption.IGNORE_CASE).containsMatchIn(merchant)

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
        "com.mobikwik_new" to UpiApp.MOBIKWIK,
        "com.freecharge.android" to UpiApp.FREECHARGE,
        "com.naviapp" to UpiApp.NAVI,
        // Brazil (Pix)
        "com.nu.production" to UpiApp.NUBANK,
        "com.mercadopago.wallet" to UpiApp.MERCADOPAGO,
        "com.picpay" to UpiApp.PICPAY,
        "br.com.uol.ps.myaccount" to UpiApp.PAGBANK,
        "br.com.intermedium" to UpiApp.INTER,
    )

    /** Banking apps whose notifications are bank alerts: iMobile (ICICI), FedMobile, SBI Card, Axis Mobile. */
    val bankAppPackages = setOf("com.csam.icici.bank.imobile", "com.fedmobile", "com.ge.capital.konysbiapp", "com.axis.mobile")

    /** Shopping apps whose "payment successful" notices are hints like a UPI app's (no account named, so they go to Review). */
    val storePackages = setOf("in.amazon.mShop.android.shopping")
    private val storePaid = rx("""\byour\s+(.+?)\s+payment\s+of\s*(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+is\s+successful""")
    private val rechargeLabel = rx("""\b(prepaid|postpaid|recharge|dth)\b""")

    /** Amazon: "Your Mobile Prepaid payment of Rs 49 is successful." → ₹49 to "Mobile Recharge"; null if it isn't one. */
    fun storeNotice(text: String): UpiNotice? {
        val m = storePaid.find(text.replace(' ', ' ')) ?: return null
        val paise = runCatching { java.math.BigDecimal(m.groupValues[2].replace(",", "")).movePointRight(2).longValueExact() }.getOrNull()?.takeIf { it > 0 } ?: return null
        return UpiNotice(paise, com.paisaplus.compose.data.TransactionType.EXPENSE, if (rechargeLabel.containsMatchIn(m.groupValues[1])) "Mobile Recharge" else "Amazon")
    }

    data class UpiNotice(val amountPaise: Long, val type: com.paisaplus.compose.data.TransactionType, val payee: String)

    private val noticeSkip = rx("""\b(request|requested|collect|failed|pending|declined|reminder|cashback|offer|reward|due|scratch|win|bill|solicita[çc][ãa]o|cobran[çc]a|falhou|recusad[oa]|pendente|oferta|fatura)\b""")
    // ₹ / Rs / INR as 1,234.50; R$ as 1.234,56 (normalised in [paise]).
    private val money = """(?:₹|rs\.?|inr|r\$)\s*([0-9][0-9.,]*)"""
    private val paidTo = listOf(
        rx("""(?:pagou|enviou|transferiu)\s*(?:um\s+pix\s+(?:de\s+)?)?$money\s*(?:para|a)\s+(.+?)(?=\s+(?:via|em|com|pelo|pela|no|na|foi)\b|[.!\n]|$)"""),
        rx("""(?:paid|sent)\s*$money\s*(?:to|for)\s+(.+?)(?=\s+(?:via|using|on|from|successfully|is|was)\b|[.!\n]|$)"""),
        rx("""$money\s*(?:paid|sent)\s+(?:to|for)\s+(.+?)(?=\s+(?:via|using|on|from|successfully|is|was)\b|[.!\n]|$)"""),
        rx("""(?:payment|transfer)\s+of\s*$money\s*(?:to|for)\s+(.+?)(?=\s+(?:via|using|on|from|successful|is|was)\b|[.!\n]|$)"""),
    )
    private val gotFrom = listOf(
        rx("""(?:recebeu|recebido)\s*(?:uma\s+)?(?:transfer[êe]ncia\s+|pix\s+)?(?:de\s+)?$money\s*de\s+(.+?)(?=\s+(?:via|em|com|pelo|pela|no|na|foi)\b|[.!\n]|$)"""),
        rx("""(?:received|got)\s*$money\s*from\s+(.+?)(?=\s+(?:via|using|on|in|to|is)\b|[.!\n]|$)"""),
        rx("""$money\s*received\s+from\s+(.+?)(?=\s+(?:via|using|on|in|to|is)\b|[.!\n]|$)"""),
    )

    /** "Paid ₹250 to Swiggy" / "₹1,200 received from Rahul": amount, direction and the full payee; null if it isn't one. */
    fun upiNotice(text: String): UpiNotice? {
        val t = text.replace('\u00a0', ' ')
        if (noticeSkip.containsMatchIn(t)) return null
        fun hit(list: List<Regex>, type: com.paisaplus.compose.data.TransactionType) = list.firstNotNullOfOrNull { it.find(t) }?.let { m ->
            // "1.234,56" (comma then 1–2 digits at the end) is the Brazilian form; anything else is "1,234.56".
            val num = m.groupValues[1].trimEnd('.', ',').let { if (Regex(""",\d{1,2}$""").containsMatchIn(it)) it.replace(".", "").replace(',', '.') else it.replace(",", "") }
            val paise = runCatching { java.math.BigDecimal(num).movePointRight(2).longValueExact() }.getOrNull() ?: return@let null
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
        if (cardBill(merchant, body)) return "Credit card payment"
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
        if (payLater.containsMatchIn(body)) return accounts.filter { "pay ?later".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(it.name + " " + it.bankName) }.singleOrNull()
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
    fun category(name: String?, body: String, categories: List<CategoryEntity>, history: List<ExpenseEntity>, transfer: Boolean = false): CategoryEntity? {
        val byId = categories.associateBy { it.id }
        name?.let { n -> history.filter { it.name.equals(n, ignoreCase = true) }.maxByOrNull { it.time }?.let { byId[it.categoryId] }?.let { return it } }
        fun named(vararg n: String) = categories.firstOrNull { c -> n.any { c.name.trim().equals(it, ignoreCase = true) } }
        if (salary.containsMatchIn(body)) named("Salary", "Income")?.let { return it }
        if (name != null && recharge.containsMatchIn(name)) named("Mobile Recharge", "Recharge")?.let { return it }
        if (transfer || cardPayment.containsMatchIn(body) || transferWords.containsMatchIn(body)) named("Transfer")?.let { return it }
        return named("Other", "Others", "Miscellaneous", "Misc")
    }
}
