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
    private val transferWords = rx("""\b(neft|rtgs|imps)\b|\bself\b|\bown account\b""")

    fun upiApp(body: String): UpiApp? =
        appWords.firstOrNull { it.second.containsMatchIn(body) }?.first ?: handles.firstOrNull { it.second.containsMatchIn(body) }?.first

    /** PaymentMethod name for the expense: UPI (with app), tap for contactless card use, swipe for other card spends. */
    fun method(body: String, isUpi: Boolean): String? = when {
        isUpi || upiWords.containsMatchIn(body) -> PaymentMethod.UPI.name
        tap.containsMatchIn(body) -> PaymentMethod.TAP.name
        cardWords.containsMatchIn(body) -> PaymentMethod.SWIPE.name
        else -> null
    }

    /** "swiggy@icici" → "Swiggy"; trims bank boilerplate. */
    fun payee(merchant: String?, body: String): String? {
        val m = merchant?.trim()?.trimEnd('.', ',')?.takeIf { it.length in 2..60 } ?: return null
        val name = if ('@' in m) m.substringBefore('@').replace(Regex("[._-]+"), " ") else m
        return name.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() }) w else w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }.takeIf { it.isNotBlank() }
    }

    /** The one account whose number ends with the alert's digits; credit cards preferred when the alert names a card. */
    fun account(accounts: List<AccountEntity>, suffix: String?, body: String): AccountEntity? {
        val tail = suffix?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length >= 3 } ?: return null
        val matches = accounts.filter { a -> a.number.filter(Char::isDigit).let { it.length >= tail.length && it.endsWith(tail) } }
        if (matches.size <= 1) return matches.singleOrNull()
        val card = cardWords.containsMatchIn(body)
        return matches.filter { (it.cardType == CardType.CREDIT) == card }.singleOrNull()
    }

    /** Same payee's last category; else Transfer for bank transfers; else "Other(s)". Null = can't choose, so review. */
    fun category(name: String?, body: String, categories: List<CategoryEntity>, history: List<ExpenseEntity>): CategoryEntity? {
        val byId = categories.associateBy { it.id }
        name?.let { n -> history.filter { it.name.equals(n, ignoreCase = true) }.maxByOrNull { it.time }?.let { byId[it.categoryId] }?.let { return it } }
        fun named(vararg n: String) = categories.firstOrNull { c -> n.any { c.name.trim().equals(it, ignoreCase = true) } }
        if (transferWords.containsMatchIn(body)) named("Transfer")?.let { return it }
        return named("Other", "Others", "Miscellaneous", "Misc")
    }
}
