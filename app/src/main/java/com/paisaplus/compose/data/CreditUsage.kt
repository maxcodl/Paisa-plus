package com.paisaplus.compose.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CreditUsage(val limit: Double, val owed: Double, val emi: Double, val shared: List<AccountEntity>) {
    val used get() = owed + emi // = total outstanding of the group
    val available get() = limit - used
}

/**
 * Cards sharing a limitGroup (case/space-insensitive) share one limit = max limit in the group.
 * Used = sum owed across the group + remaining installments of EMIs booked on any group card.
 */
fun creditUsage(a: AccountEntity, all: List<AccountEntity>, loans: List<LoanEntity>): CreditUsage? {
    if (a.cardType != CardType.CREDIT) return null
    val group = a.limitGroup?.trim()?.lowercase().orEmpty()
    val members = if (group.isEmpty()) listOf(a)
    else all.filter { it.cardType == CardType.CREDIT && it.limitGroup?.trim()?.lowercase() == group }
    val limit = members.maxOf { it.creditLimit ?: 0.0 }
    if (limit <= 0) return null
    val names = members.map { it.name.lowercase() }.toSet()
    val emi = loans.filter { it.kind == LoanKind.EMI && !it.charged && it.account.lowercase() in names }.sumOf { it.remaining }
    return CreditUsage(limit, members.sumOf { -it.amount }, emi, members.filter { it.id != a.id })
}

/**
 * [date] = statement date (estimate) or due date (bank statement). [due] = payment due date when known.
 * [actual] = [charges] is the bank's statement total, not an estimate. [estimate] = what the app computed for the same cycle.
 */
data class NextBill(
    val date: LocalDate, val charges: Double, val emi: Double,
    val due: LocalDate? = null, val minDue: Double = 0.0, val actual: Boolean = false, val estimate: Double = charges + emi, val statementDate: LocalDate = date,
) {
    val total get() = charges + emi
}

/** One bill for a group of cards sharing a limit: usage summed, earliest date and due date. [actual] only when every card has a bank statement. */
fun combineBills(bills: List<NextBill>): NextBill? = bills.minByOrNull { it.date }?.let { f ->
    NextBill(f.date, bills.sumOf { it.total }, 0.0, bills.mapNotNull { it.due }.minOrNull(), bills.sumOf { it.minDue }, bills.all { it.actual }, bills.sumOf { it.estimate }, f.statementDate)
}

/** Day [day] of [ym], clamped to the month's length (31st in February = 28th/29th). */
fun clampDay(ym: YearMonth, day: Int): LocalDate = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))

/**
 * Next statement on [a]'s statement day (clamped to month length); null when no statement day and no open bank statement.
 * Statement day defaults to month end. charges = this cycle's full usage: every expense (EMI category included) after the last statement day, payments not netted.
 * emi = monthly emi of each open auto-post EMI loan on this card whose nextDue is on or before the next
 * statement date. AutoPoster advances nextDue past every installment it posts, so nextDue is always the
 * first unposted one: each installment counts once, as a charge or as upcoming EMI.
 * Imported loans (autoPost = false) are never posted or advanced here; their installments count only as charges.
 * An unpaid bank [statements] entry (not yet covered by payments after its date) replaces the estimate with the real amount.
 */
fun nextBill(
    a: AccountEntity, expenses: List<ExpenseEntity>, loans: List<LoanEntity>, today: LocalDate, zone: ZoneId,
    statements: List<StatementEntity> = emptyList(), all: List<AccountEntity> = emptyList(),
): NextBill? {
    fun millis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun date(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    // No statement day set: the cycle is the calendar month (statement on its last day).
    val est = (a.statementDay ?: 31).let { day ->
        fun on(ym: YearMonth) = clampDay(ym, day)
        val next = on(YearMonth.from(today)).takeIf { it >= today } ?: on(YearMonth.from(today).plusMonths(1))
        val after = millis(on(YearMonth.from(next).minusMonths(1)).plusDays(1))
        val dueBy = millis(next.plusDays(1))
        val charges = expenses.filter { it.accountId == a.id && it.time >= after && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val emi = loans.filter { it.kind == LoanKind.EMI && it.autoPost && !it.charged && !it.closed && it.account.equals(a.name, ignoreCase = true) && it.nextDue < dueBy }
            .sumOf { it.emi }
        val due = a.dueDay?.let { d -> clampDay(YearMonth.from(next).let { ym -> if (d > next.dayOfMonth) ym else ym.plusMonths(1) }, d) } ?: next.plusDays(20)
        NextBill(next, charges, emi, due = due)
    }
    fun openOf(c: AccountEntity) = statements.filter { it.accountId == c.id && !it.paid && date(it.dueDate) >= today.minusDays(5) }
        .maxWithOrNull(compareBy<StatementEntity>({ YearMonth.from(date(it.dueDate)) }, { it.source != "AUTO" }))
    val open = openOf(a)
    // Payments into any card of the limit group go toward the group's statements, handed out card by card (by id) so none is counted twice.
    val peers = (groupKey(a)?.let { k -> all.filter { it.cardType == CardType.CREDIT && groupKey(it) == k }.sortedBy { it.id } } ?: emptyList()).ifEmpty { listOf(a) }
    var paidOff = 0.0
    if (open != null) {
        var used = 0.0
        for (p in peers) {
            val sp = openOf(p) ?: continue
            val pool = expenses.filter { e -> e.type == TransactionType.INCOME && e.time >= sp.statementDate && peers.any { it.id == e.accountId } }.sumOf { it.amount }
            val share = minOf(sp.total, maxOf(0.0, pool - used))
            used += share
            if (p.id == a.id) { paidOff = share; break }
        }
    }
    val left = open?.let { it.total - paidOff }
    // Settled (to within 1%): back to the next cycle's estimate. A part payment lowers the bill and its minimum.
    if (open == null || open.total > 0 && left!! <= maxOf(1.0, open.total * 0.01)) return est
    return NextBill(date(open.dueDate), left!!, 0.0, due = date(open.dueDate), minDue = maxOf(0.0, open.minDue - paidOff), actual = true, estimate = est?.total ?: open.total, statementDate = date(open.statementDate))
}

/**
 * An EMI loan's installments are charged to the card month by month, so the purchase itself must not be on the card too.
 * Returns the charge that looks like the full purchase (about the loan's total, not one of its installments), else null.
 */
fun likelyPurchase(l: LoanEntity, accountId: Long, expenses: List<ExpenseEntity>): ExpenseEntity? {
    if (l.kind != LoanKind.EMI || l.charged || l.closed || l.tenure <= 0) return null
    val total = l.emi * l.tenure
    return expenses.filter {
        it.accountId == accountId && it.type == TransactionType.EXPENSE && !it.name.startsWith("${l.name} ", true) && !it.name.equals(l.name, true) &&
            it.amount in total * 0.8..total * 1.05
    }.minByOrNull { kotlin.math.abs(it.amount - total) }
}
