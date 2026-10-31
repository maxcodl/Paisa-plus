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

data class NextBill(val date: LocalDate, val charges: Double, val emi: Double) {
    val total get() = charges + emi
}

/**
 * Next statement on [a]'s statement day (clamped to month length); null when no statement day.
 * charges = all charges (EMI category included) − payments dated after the last statement day.
 * emi = monthly emi of each open auto-post EMI loan on this card whose nextDue is on or before the next
 * statement date. AutoPoster advances nextDue past every installment it posts, so nextDue is always the
 * first unposted one: each installment counts once, as a charge or as upcoming EMI.
 * Imported loans (autoPost = false) are never posted or advanced here; their installments count only as charges.
 */
fun nextBill(a: AccountEntity, expenses: List<ExpenseEntity>, loans: List<LoanEntity>, today: LocalDate, zone: ZoneId): NextBill? {
    val day = a.statementDay ?: return null
    fun on(ym: YearMonth) = ym.atDay(minOf(day, ym.lengthOfMonth()))
    val next = on(YearMonth.from(today)).takeIf { it >= today } ?: on(YearMonth.from(today).plusMonths(1))
    val after = on(YearMonth.from(next).minusMonths(1)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val dueBy = next.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val charges = expenses.filter { it.accountId == a.id && it.time >= after }
        .sumOf { if (it.type == TransactionType.EXPENSE) it.amount else -it.amount }
    val emi = loans.filter { it.kind == LoanKind.EMI && it.autoPost && !it.charged && !it.closed && it.account.equals(a.name, ignoreCase = true) && it.nextDue < dueBy }
        .sumOf { it.emi }
    return NextBill(next, charges, emi)
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
