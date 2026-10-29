package com.paisaplus.compose.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class BalanceDeltaTest {
    private val base = ExpenseEntity(id = 1, name = "x", amount = 100.0, time = 0, type = TransactionType.EXPENSE, accountId = 1, categoryId = 1)

    @Test fun add() = assertEquals(mapOf(1L to -100.0), balanceDeltas(null, base))
    @Test fun delete() = assertEquals(mapOf(1L to 100.0), balanceDeltas(base, null))
    @Test fun amountChange() = assertEquals(mapOf(1L to -50.0), balanceDeltas(base, base.copy(amount = 150.0)))
    @Test fun unchangedIsEmpty() = assertEquals(emptyMap<Long, Double>(), balanceDeltas(base, base.copy(name = "y")))

    @Test fun accountChangeMovesMoney() =
        assertEquals(mapOf(1L to 100.0, 2L to -100.0), balanceDeltas(base, base.copy(accountId = 2)))

    @Test fun typeChangeFlipsTwice() =
        assertEquals(mapOf(1L to 200.0), balanceDeltas(base, base.copy(type = TransactionType.INCOME)))

    @Test fun accountAndTypeChange() =
        assertEquals(mapOf(1L to 100.0, 2L to 80.0), balanceDeltas(base, base.copy(accountId = 2, type = TransactionType.INCOME, amount = 80.0)))
}

class SummaryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun e(id: Long, amount: Double, time: Long, type: TransactionType = TransactionType.EXPENSE) =
        ExpenseEntity(id = id, name = "", amount = amount, time = time, type = type, accountId = 1, categoryId = 1)

    private val list = listOf(
        e(1, 10.0, at(2026, 10, 31, 23)), // last minute of month in local zone
        e(2, 500.0, at(2026, 10, 1, 0), TransactionType.INCOME),
        e(3, 20.0, at(2026, 10, 1, 9)),
        e(4, 99.0, at(2026, 11, 1, 0)),
        e(5, 7.0, at(2026, 9, 30, 23)),
    )

    @Test fun monthBoundariesUseLocalZone() {
        val t = monthTotals(list, YearMonth.of(2026, 10), zone)
        assertEquals(500.0, t.income, 0.0)
        assertEquals(30.0, t.expense, 0.0)
        assertEquals(3, t.count)
        assertEquals(470.0, t.net, 0.0)
    }

    @Test fun groupsNewestDayFirst() {
        val g = groupByDay(list, zone)
        assertEquals(LocalDate.of(2026, 11, 1), g.first().first)
        assertEquals(listOf(2L, 3L), g.first { it.first == LocalDate.of(2026, 10, 1) }.second.map { it.id })
        assertEquals(LocalDate.of(2026, 9, 30), g.last().first)
    }
}
