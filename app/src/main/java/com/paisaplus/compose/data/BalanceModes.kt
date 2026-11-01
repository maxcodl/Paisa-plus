package com.paisaplus.compose.data

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import java.time.LocalDate
import java.time.ZoneId

/** Pages of the Home balance card and the widget; which ones show (and their order) is a setting. */
enum class BalanceMode(val label: String) {
    BANK(str(R.string.bank_balance)),
    TOTAL(str(R.string.total_balance)),
    CREDIT(str(R.string.credit_cards)),
    NET_WORTH(str(R.string.net_worth));

    companion object {
        val DEFAULT = entries.toList()

        fun parse(s: String?): List<BalanceMode> =
            s?.split(',')?.mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }?.distinct()?.ifEmpty { null } ?: DEFAULT

        fun format(modes: List<BalanceMode>) = modes.joinToString(",") { it.name }
    }
}

data class CardBill(val card: String, val date: LocalDate, val amount: Double)

data class BalanceSummary(
    val total: Double,
    /** Every non-credit account: savings/bank, cash, wallets. */
    val bank: Double,
    /** Credit card outstanding, including remaining installments of EMIs booked on cards. */
    val cardOwed: Double,
    val cardLimit: Double,
    val cardAvailable: Double,
    /** Each card's (or shared-limit group's) next bill by payment due date, soonest first. */
    val bills: List<CardBill>,
    /** Remaining on non-card loans. */
    val loans: Double,
) {
    val due get() = bills.sumOf { it.amount }
    val dueDate get() = bills.firstOrNull()?.date
    val liabilities get() = cardOwed + loans
    val netWorth get() = bank - liabilities
}

fun balanceSummary(accounts: List<AccountEntity>, expenses: List<ExpenseEntity>, loans: List<LoanEntity>, today: LocalDate, zone: ZoneId, statements: List<StatementEntity> = emptyList()): BalanceSummary {
    val cards = accounts.filter { it.cardType == CardType.CREDIT }
    val emiOnCards = loans.filter { l -> l.kind == LoanKind.EMI && !l.charged && cards.any { it.name.equals(l.account, ignoreCase = true) } }.sumOf { it.remaining }
    // Cards in one limitGroup share a limit: count each group once.
    val groups = cards.groupBy { it.limitGroup?.trim()?.lowercase()?.ifEmpty { null } ?: "#${it.id}" }.values
        .mapNotNull { creditUsage(it.first(), accounts, loans) }
    // Cards sharing a limit are billed together by the bank: one row per group, due on its earliest due date.
    val bills = cards.groupBy { it.limitGroup?.trim()?.lowercase()?.ifEmpty { null } ?: "#${it.id}" }.values.mapNotNull { members ->
        val b = combineBills(members.mapNotNull { nextBill(it, expenses, loans, today, zone, statements, accounts) }) ?: return@mapNotNull null
        CardBill(if (members.size > 1) members.first().limitGroup!!.trim() else members.first().name, b.due ?: b.date, b.total.coerceAtLeast(0.0))
    }.sortedBy { it.date }
    return BalanceSummary(
        total = accounts.sumOf { it.amount },
        bank = accounts.filter { it.cardType != CardType.CREDIT }.sumOf { it.amount },
        cardOwed = cards.sumOf { -it.amount } + emiOnCards,
        cardLimit = groups.sumOf { it.limit },
        cardAvailable = groups.sumOf { it.available },
        bills = bills,
        loans = loans.filter { it.kind == LoanKind.LOAN }.sumOf { it.remaining },
    )
}
