package com.paisaplus.compose.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A transaction to post, with account/category given by name (Flutter's StatementRow). */
data class StatementRow(
    val name: String,
    val amount: Double,
    val time: Long,
    val type: TransactionType,
    val category: String,
    val account: String,
    val bank: String,
)

fun dayOfMonth(millis: Long, zone: ZoneId = ZoneId.systemDefault()) = Instant.ofEpochMilli(millis).atZone(zone).dayOfMonth

private fun Long.local(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
private fun LocalDateTime.millis(zone: ZoneId) = atZone(zone).toInstant().toEpochMilli()

/** Next month on [dueDay], clamped to the month's length (Jan 31 → Feb 28/29 → Mar 31). Deliberately not Flutter's Dart rollover. */
fun nextMonthly(from: LocalDate, dueDay: Int): LocalDate {
    val m = from.plusMonths(1)
    return m.withDayOfMonth(minOf(dueDay, m.lengthOfMonth()))
}

/** Monthly step keeping time of day; dueDay 0 (unset) falls back to [t]'s day. */
private fun monthly(t: LocalDateTime, dueDay: Int) =
    nextMonthly(t.toLocalDate(), dueDay.takeIf { it > 0 } ?: t.dayOfMonth).atTime(t.toLocalTime())

fun RecurringRuleEntity.advance(t: LocalDateTime): LocalDateTime = when (frequency) {
    Frequency.DAILY -> t.plusDays(1)
    Frequency.WEEKLY -> t.plusDays(7)
    Frequency.MONTHLY -> monthly(t, dueDay)
}

/** Flutter's applyDue cut-off: today 23:59. */
private fun endOfToday(now: LocalDateTime) = now.toLocalDate().atTime(23, 59)

private const val GUARD = 60

/** loan_service.dart applyDue: every auto-post installment due by end of today; returns rows + advanced loans. */
fun dueLoans(loans: List<LoanEntity>, now: LocalDateTime, zone: ZoneId): Pair<List<StatementRow>, List<LoanEntity>> {
    val end = endOfToday(now)
    val rows = mutableListOf<StatementRow>()
    val changed = mutableListOf<LoanEntity>()
    for (start in loans) {
        if (!start.autoPost) continue
        var loan = start
        var guard = 0
        while (!loan.closed && !loan.nextDue.local(zone).isAfter(end) && guard++ < GUARD) {
            if (!loan.charged) rows += StatementRow(
                name = "${loan.name} ${loan.category} ${loan.paid + 1}" + if (loan.tenure > 0) "/${loan.tenure}" else "",
                amount = loan.emi,
                time = loan.nextDue,
                type = TransactionType.EXPENSE,
                category = loan.category,
                account = loan.account,
                bank = loan.account,
            )
            loan = loan.copy(paid = loan.paid + 1, nextDue = monthly(loan.nextDue.local(zone), loan.dueDay).millis(zone))
        }
        if (loan != start) changed += loan
    }
    return rows to changed
}

/** recurring_service.dart applyDue: every occurrence due by end of today; returns rows + advanced rules. */
fun dueRecurring(rules: List<RecurringRuleEntity>, now: LocalDateTime, zone: ZoneId): Pair<List<StatementRow>, List<RecurringRuleEntity>> {
    val end = endOfToday(now)
    val rows = mutableListOf<StatementRow>()
    val changed = mutableListOf<RecurringRuleEntity>()
    for (start in rules) {
        var rule = start
        var guard = 0
        while (!rule.next.local(zone).isAfter(end) && guard++ < GUARD) {
            rows += StatementRow(rule.name, rule.amount, rule.next, rule.type, rule.category, rule.account, rule.account)
            rule = rule.copy(next = rule.advance(rule.next.local(zone)).millis(zone))
        }
        if (rule != start) changed += rule
    }
    return rows to changed
}

/** csv_importer.dart categoryIconFor: keyword -> MDI codepoint for auto-created categories (first match wins). */
fun categoryIconFor(name: String): Int {
    val n = name.lowercase()
    val table = listOf(
        listOf("food", "dining", "restaurant", "swiggy", "zomato", "eat") to 0xf0a70, // silverwareForkKnife
        listOf("grocer", "supermarket", "vegetable") to 0xf0110, // cart
        listOf("transport", "uber", "ola", "taxi", "metro", "bus", "fuel", "petrol") to 0xf00e7, // bus
        listOf("shop", "amazon", "flipkart", "cloth") to 0xf049a, // shopping
        listOf("bill", "utilit", "electric", "water", "internet", "recharge") to 0xf0241, // flash
        listOf("rent", "home", "house") to 0xf02dc, // home
        listOf("health", "medic", "pharm", "doctor", "hospital") to 0xf06ef, // medicalBag
        listOf("entertain", "movie", "netflix", "game", "subscription") to 0xf0381, // movie
        listOf("travel", "flight", "hotel", "trip") to 0xf001d, // airplane
        listOf("salary", "income", "pay", "bonus", "interest") to 0xf1261, // cashPlus
        listOf("transfer", "upi", "atm", "cash") to 0xf04e1, // swapHorizontal
        listOf("edu", "school", "course", "book") to 0xf0474, // school
    )
    return table.firstOrNull { (keys, _) -> keys.any { it in n } }?.second ?: 0xf04f9 // tag
}

/** Accounts/categories [rows] name that don't exist yet (case-insensitive), as csv_importer.dart commit creates them. */
fun missingByName(rows: List<StatementRow>, accounts: List<AccountEntity>, categories: List<CategoryEntity>): Pair<List<AccountEntity>, List<CategoryEntity>> {
    val accNames = accounts.map { it.name.lowercase() }.toMutableSet()
    val catNames = categories.map { it.name.lowercase() }.toMutableSet()
    val newAccounts = rows.filter { accNames.add(it.account.lowercase()) }.map {
        AccountEntity(
            name = it.account,
            icon = 0xf0070, // MdiIcons.bank
            bankName = it.bank,
            number = Regex("\\d{4}$").find(it.account)?.value.orEmpty(),
            cardType = CardType.BANK,
        )
    }
    val newCategories = rows.filter { catNames.add(it.category.lowercase()) }
        .map { CategoryEntity(name = it.category, icon = categoryIconFor(it.category), color = 0xFFFFC107.toInt()) }
    return newAccounts to newCategories
}

/**
 * Posts due loan installments and recurring occurrences. Each run is one DB transaction that re-reads
 * loans/rules, so concurrent runs serialize and the second finds nothing due (idempotent).
 */
@Singleton
class AutoPoster @Inject constructor(private val db: PaisaDatabase, private val expenses: ExpenseRepository) {
    suspend fun run(now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Int = db.withTransaction {
        val (loanRows, loans) = dueLoans(db.loans().list(), now, zone)
        val (ruleRows, rules) = dueRecurring(db.recurring().list(), now, zone)
        val n = post(loanRows + ruleRows)
        loans.forEach { db.loans().upsert(it) }
        rules.forEach { db.recurring().upsert(it) }
        n
    }

    /** Resolves account/category by name (case-insensitive; last duplicate wins, like a Dart map), creating missing ones. */
    private suspend fun post(rows: List<StatementRow>): Int {
        if (rows.isEmpty()) return 0
        val (newAccounts, newCategories) = missingByName(rows, db.accounts().list(), db.categories().list())
        newAccounts.forEach { db.accounts().insert(it) }
        newCategories.forEach { db.categories().insert(it) }
        val accounts = db.accounts().list().associate { it.name.lowercase() to it.id }
        val categories = db.categories().list().associate { it.name.lowercase() to it.id }
        rows.forEach {
            expenses.add(
                ExpenseEntity(
                    name = it.name, amount = it.amount, time = it.time, type = it.type,
                    accountId = accounts.getValue(it.account.lowercase()),
                    categoryId = categories.getValue(it.category.lowercase()),
                ),
            )
        }
        return rows.size
    }
}
