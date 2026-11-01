package com.paisaplus.compose.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementPaymentTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 1)
    private fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun card(id: Long, group: String? = "G") = AccountEntity(id, "C$id", 0, "B", cardType = CardType.CREDIT, limitGroup = group, statementDay = 20, dueDay = 7)
    private fun stmt(acc: Long, total: Double, min: Double) =
        StatementEntity(accountId = acc, statementDate = at(LocalDate.of(2026, 10, 20)), dueDate = at(LocalDate.of(2026, 11, 7)), total = total, minDue = min, source = "MANUAL")
    private fun pay(acc: Long, amt: Double, d: LocalDate) =
        ExpenseEntity(name = "p", amount = amt, time = at(d), type = TransactionType.INCOME, accountId = acc, categoryId = 1)

    @Test fun partPaymentLowersBillAndMinimum() {
        val a = card(1, null)
        val b = nextBill(a, listOf(pay(1, 770.0, LocalDate.of(2026, 10, 30))), emptyList(), today, zone, listOf(stmt(1, 15_393.60, 770.0)), listOf(a))!!
        assertTrue(b.actual); assertEquals(14_623.60, b.total, 0.001); assertEquals(0.0, b.minDue, 0.0)
    }

    @Test fun groupPaymentOnEitherCardCountsOnce() {
        val a = card(1); val c = card(2)
        val st = listOf(stmt(1, 15_393.60, 770.0), stmt(2, 0.0, 0.0))
        val full = listOf(pay(2, 15_393.60, LocalDate.of(2026, 11, 1))) // paid on the other card
        val billA = nextBill(a, full, emptyList(), today, zone, st, listOf(a, c))!!
        assertTrue(!billA.actual) // settled: back to the next cycle's estimate
        val half = listOf(pay(2, 5_000.0, LocalDate.of(2026, 11, 1)))
        assertEquals(10_393.60, nextBill(a, half, emptyList(), today, zone, st, listOf(a, c))!!.total, 0.001)
    }
}
