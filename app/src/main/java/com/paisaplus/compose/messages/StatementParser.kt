package com.paisaplus.compose.messages

import java.time.LocalDate

/** Due-date / statement alerts the transaction parser deliberately ignores. Pure; matching to accounts and loans is in [MessageInboxRepository]. */
sealed interface StatementMessage {
    /** "EMI of INR 4917.00 for Axis Bank Loan A/c XX9692 is due on 05-11-26". */
    data class LoanDue(val amount: Double, val suffix: String, val bank: String, val due: LocalDate) : StatementMessage
    /** "Your Pay Later bill of Rs 16559 will be debited on 5th of this month". */
    data class PayLater(val amount: Double, val day: Int) : StatementMessage
    /** Card statement: total due, minimum due, payment due date, last digits when given. */
    data class CardStatement(val suffix: String?, val total: Double, val minDue: Double, val due: LocalDate) : StatementMessage
}

object StatementParser {
    private val num = """([\d,]+(?:\.\d+)?)"""
    private val loanDue = Regex("""EMI\s+of\s+(?:INR|Rs\.?)\s*$num\s+for\s+(.+?)\s+(?:Loan\s+)?A/c\s+[Xx*]*(\d{3,})\s+is\s+due\s+on\s+(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""", RegexOption.IGNORE_CASE)
    private val payLater = Regex("""Pay\s*Later\s+bill\s+of\s+(?:INR|Rs\.?)\s*$num\s+will\s+be\s+debited\s+on\s+(\d{1,2})(?:st|nd|rd|th)""", RegexOption.IGNORE_CASE)
    private val total = Regex("""(?:total\s+(?:amt|amount)\s+due|total\s+due|statement\s+amount)[^\d]{0,20}(?:INR|Rs\.?)?\s*$num""", RegexOption.IGNORE_CASE)
    private val min = Regex("""(?:min(?:imum)?\.?\s+(?:amt|amount)?\s*due)[^\d]{0,20}(?:INR|Rs\.?)?\s*$num""", RegexOption.IGNORE_CASE)
    private val dueDate = Regex("""due\s+(?:date|by|on)[^\d]{0,10}(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""", RegexOption.IGNORE_CASE)
    private val card = Regex("""card\s+(?:no\.?\s*)?(?:ending\s+)?[Xx*]*(\d{4})""", RegexOption.IGNORE_CASE)

    fun parse(body: String): StatementMessage? {
        loanDue.find(body)?.let { m ->
            val due = date(m.groupValues[4]) ?: return null
            return StatementMessage.LoanDue(amount(m.groupValues[1]) ?: return null, m.groupValues[3].takeLast(4), m.groupValues[2].trim(), due)
        }
        payLater.find(body)?.let { m ->
            return StatementMessage.PayLater(amount(m.groupValues[1]) ?: return null, m.groupValues[2].toInt().takeIf { it in 1..31 } ?: return null)
        }
        if (Regex("""statement|total\s+(amt|amount)\s+due""",RegexOption.IGNORE_CASE).containsMatchIn(body)) {
            val t = total.find(body)?.let { amount(it.groupValues[1]) } ?: return null
            val d = dueDate.find(body)?.let { date(it.groupValues[1]) } ?: return null
            return StatementMessage.CardStatement(card.find(body)?.groupValues?.get(1), t, min.find(body)?.let { amount(it.groupValues[1]) } ?: 0.0, d)
        }
        return null
    }

    private fun amount(s: String) = s.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    internal fun date(s: String): LocalDate? {
        val p = s.split('-', '/').map { it.toIntOrNull() ?: return null }
        if (p.size != 3) return null
        return runCatching { LocalDate.of(if (p[2] < 100) 2000 + p[2] else p[2], p[1], p[0]) }.getOrNull()
    }
}
