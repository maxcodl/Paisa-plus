package com.paisaplus.compose.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MonthTotals(val income: Double, val expense: Double, val count: Int) {
    val net get() = income - expense
}

fun ExpenseEntity.date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

/** Ids of categories named "Transfer" (case-insensitive): moves between own accounts, not income/expense. */
fun transferIds(categories: List<CategoryEntity>): Set<Long> =
    categories.filter { it.name.trim().equals("Transfer", ignoreCase = true) }.map { it.id }.toSet()

/** Salary paid in a month's last this-many days counts toward the next month (paid on the 30th for the next). */
const val EARLY_SALARY_DAYS = 3

/** Ids of categories named "Salary" (case-insensitive). */
fun salaryIds(categories: List<CategoryEntity>): Set<Long> =
    categories.filter { it.name.trim().equals("Salary", ignoreCase = true) }.map { it.id }.toSet()

/**
 * The month [d] belongs to when months start on day [start] (Settings › Month starts on; clamped in short months).
 * Starts after the 16th name the period after the month it ends in (start 30: 30 Sep–29 Oct is October);
 * earlier starts after the month it begins in (start 5: 5 Oct–4 Nov is October).
 */
fun periodOf(d: LocalDate, start: Int): YearMonth {
    val ym = YearMonth.from(d)
    if (start <= 1) return ym
    val began = if (d >= ym.atDay(minOf(start, ym.lengthOfMonth()))) ym else ym.minusMonths(1)
    return if (start > 16) began.plusMonths(1) else began
}

/** First and last day of [month]'s period (inverse of [periodOf]). */
fun periodRange(month: YearMonth, start: Int): Pair<LocalDate, LocalDate> {
    if (start <= 1) return month.atDay(1) to month.atEndOfMonth()
    val began = if (start > 16) month.minusMonths(1) else month
    fun on(ym: YearMonth) = ym.atDay(minOf(start, ym.lengthOfMonth()))
    return on(began) to on(began.plusMonths(1)).minusDays(1)
}

/** The month (period) today is in. */
fun currentPeriod(start: Int, zone: ZoneId = ZoneId.systemDefault()) = periodOf(LocalDate.now(zone), start)

/**
 * Month this entry counts toward in totals: its period ([start]); with calendar months (start 1),
 * early salary ([salary] category ids) still rolls forward to the next month.
 */
fun ExpenseEntity.countedMonth(zone: ZoneId, salary: Set<Long>, start: Int = 1): YearMonth {
    val d = date(zone)
    if (start > 1) return periodOf(d, start)
    val ym = YearMonth.from(d)
    val early = type == TransactionType.INCOME && categoryId in salary && d.dayOfMonth > ym.lengthOfMonth() - EARLY_SALARY_DAYS
    return if (early) ym.plusMonths(1) else ym
}

/** [skip]: category ids left out of income/expense (count still includes them). [salary], [start]: see [countedMonth]. */
fun monthTotals(expenses: List<ExpenseEntity>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault(), skip: Set<Long> = emptySet(), salary: Set<Long> = emptySet(), start: Int = 1): MonthTotals {
    val inMonth = expenses.filter { it.countedMonth(zone, salary, start) == month }
    val counted = inMonth.filter { it.categoryId !in skip }
    return MonthTotals(
        income = counted.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        expense = counted.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
        count = inMonth.size,
    )
}

/** Newest day first; input order kept within a day. */
fun groupByDay(expenses: List<ExpenseEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<ExpenseEntity>>> =
    expenses.groupBy { it.date(zone) }.toList().sortedByDescending { it.first }

/** Month-to-date expense total per categoryId; [start]: see [periodOf]. */
fun spendByCategory(expenses: List<ExpenseEntity>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault(), skip: Set<Long> = emptySet(), start: Int = 1): Map<Long, Double> =
    expenses.filter { it.type == TransactionType.EXPENSE && it.categoryId !in skip && periodOf(it.date(zone), start) == month }
        .groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }

data class BudgetRow(val category: CategoryEntity, val spent: Double) {
    val over get() = spent > category.budget
    val left get() = category.budget - spent
}

data class BudgetSummary(val rows: List<BudgetRow>) {
    val budget get() = rows.sumOf { it.category.budget }
    val spent get() = rows.sumOf { it.spent }
}

fun budgets(categories: List<CategoryEntity>, expenses: List<ExpenseEntity>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault(), start: Int = 1): BudgetSummary {
    val spent = spendByCategory(expenses, month, zone, start = start)
    return BudgetSummary(categories.filter { it.isBudget }.map { BudgetRow(it, spent[it.id] ?: 0.0) })
}

/** Per day of [month]: (expense, income). */
fun dailyTotals(expenses: List<ExpenseEntity>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault(), skip: Set<Long> = emptySet()): List<Pair<Double, Double>> {
    val out = DoubleArray(month.lengthOfMonth())
    val inc = DoubleArray(month.lengthOfMonth())
    expenses.forEach {
        val d = it.date(zone)
        if (YearMonth.from(d) != month || it.categoryId in skip) return@forEach
        if (it.type == TransactionType.INCOME) inc[d.dayOfMonth - 1] += it.amount else out[d.dayOfMonth - 1] += it.amount
    }
    return out.indices.map { out[it] to inc[it] }
}
