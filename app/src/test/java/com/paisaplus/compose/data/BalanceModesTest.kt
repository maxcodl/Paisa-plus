package com.paisaplus.compose.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BalanceModesTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 28)

    @Test fun summarySplitsBanksCardsAndLoans() {
        val accounts = listOf(
            AccountEntity(1, "Savings", 0, "HDFC", cardType = CardType.BANK, amount = 10_000.0),
            AccountEntity(2, "Wallet", 0, "", cardType = CardType.WALLET, amount = 500.0),
            // Two cards sharing one 50k limit; one has no group and its own 20k limit.
            AccountEntity(3, "Card A", 0, "X", cardType = CardType.CREDIT, amount = -3_000.0, creditLimit = 50_000.0, limitGroup = "x", statementDay = 5),
            AccountEntity(4, "Card B", 0, "X", cardType = CardType.CREDIT, amount = -2_000.0, creditLimit = 50_000.0, limitGroup = " X "),
            AccountEntity(5, "Card C", 0, "Y", cardType = CardType.CREDIT, amount = -1_000.0, creditLimit = 20_000.0),
        )
        val loans = listOf(
            LoanEntity(1, "Phone", LoanKind.EMI, "Card A", emi = 1_000.0, tenure = 6, paid = 2, nextDue = Long.MAX_VALUE, autoPost = false),
            LoanEntity(2, "Car", LoanKind.LOAN, "Savings", emi = 5_000.0, tenure = 10, paid = 4, nextDue = Long.MAX_VALUE),
        )
        val s = balanceSummary(accounts, emptyList(), loans, today, zone)

        assertEquals(4_500.0, s.total, 0.0)
        assertEquals(10_500.0, s.bank, 0.0)
        assertEquals(6_000.0 + 4_000.0, s.cardOwed, 0.0) // charges + remaining EMI on a card
        assertEquals(70_000.0, s.cardLimit, 0.0) // shared group counted once
        assertEquals((50_000.0 - 5_000.0 - 4_000.0) + (20_000.0 - 1_000.0), s.cardAvailable, 0.0)
        assertEquals(LocalDate.of(2026, 11, 5), s.dueDate)
        assertEquals(30_000.0, s.loans, 0.0)
        assertEquals(10_500.0 - 40_000.0, s.netWorth, 0.0)
    }

    @Test fun earlySalaryCountsTowardNextMonth() {
        fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
        val salary = 7L
        val list = listOf(
            ExpenseEntity(name = "Pay", amount = 40_000.0, time = at(2026, 9, 30), type = TransactionType.INCOME, categoryId = salary, accountId = 1),
            ExpenseEntity(name = "Pay early", amount = 1.0, time = at(2026, 9, 27), type = TransactionType.INCOME, categoryId = salary, accountId = 1), // outside last 3 days
            ExpenseEntity(name = "Refund", amount = 500.0, time = at(2026, 9, 30), type = TransactionType.INCOME, categoryId = 8, accountId = 1), // not salary
            ExpenseEntity(name = "Rent", amount = 900.0, time = at(2026, 9, 30), type = TransactionType.EXPENSE, categoryId = salary, accountId = 1), // expenses never move
        )
        val oct = monthTotals(list, java.time.YearMonth.of(2026, 10), zone, salary = setOf(salary))
        val sep = monthTotals(list, java.time.YearMonth.of(2026, 9), zone, salary = setOf(salary))
        assertEquals(40_000.0, oct.income, 0.0)
        assertEquals(501.0, sep.income, 0.0)
        assertEquals(900.0, sep.expense, 0.0)
    }

    @Test fun monthStartPeriods() {
        val oct = java.time.YearMonth.of(2026, 10)
        // Start 30: named after the month it ends in.
        assertEquals(oct, periodOf(LocalDate.of(2026, 9, 30), 30))
        assertEquals(oct, periodOf(LocalDate.of(2026, 10, 29), 30))
        assertEquals(oct.plusMonths(1), periodOf(LocalDate.of(2026, 10, 30), 30))
        assertEquals(LocalDate.of(2026, 9, 30) to LocalDate.of(2026, 10, 29), periodRange(oct, 30))
        // Start 31 clamps in short months: Feb's period starts on the 28th.
        assertEquals(java.time.YearMonth.of(2026, 3), periodOf(LocalDate.of(2026, 2, 28), 31))
        // Start 5: named after the month it begins in.
        assertEquals(oct, periodOf(LocalDate.of(2026, 11, 4), 5))
        assertEquals(LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 11, 4), periodRange(oct, 5))
        // Start 1 = calendar month.
        assertEquals(oct, periodOf(LocalDate.of(2026, 10, 31), 1))
        // With a custom start the salary rule is off: income follows its period.
        fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val pay = ExpenseEntity(name = "Pay", amount = 1.0, time = at(LocalDate.of(2026, 10, 29)), type = TransactionType.INCOME, accountId = 1, categoryId = 7)
        assertEquals(oct, pay.countedMonth(zone, setOf(7L), start = 30))
        assertEquals(oct.plusMonths(1), pay.countedMonth(zone, setOf(7L), start = 1))
    }

    @Test fun modesParseKeepsOrderAndFallsBack() {
        assertEquals(listOf(BalanceMode.BANK, BalanceMode.CREDIT), BalanceMode.parse("BANK, CREDIT,BOGUS,BANK"))
        assertEquals(BalanceMode.DEFAULT, BalanceMode.parse(""))
        assertEquals(BalanceMode.DEFAULT, BalanceMode.parse(null))
    }
}
