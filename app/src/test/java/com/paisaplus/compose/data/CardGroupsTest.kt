package com.paisaplus.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CardGroupsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun card(id: Long, name: String, balance: Double, limit: Double?, group: String?, number: String = "") =
        AccountEntity(id, name, 0, "", number = number, cardType = CardType.CREDIT, amount = balance, creditLimit = limit, limitGroup = group)
    private fun emi(account: String, emi: Double, tenure: Int, paid: Int) =
        LoanEntity(name = "Phone", kind = LoanKind.EMI, account = account, emi = emi, tenure = tenure, paid = paid, nextDue = 0)
    private fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000
    private fun tx(id: Long, acc: Long, amt: Double, d: LocalDate, type: TransactionType = TransactionType.EXPENSE, cat: Long = 1) =
        ExpenseEntity(id = id, name = "x", amount = amt, time = at(d), type = type, accountId = acc, categoryId = cat)

    private val i1007 = card(1, "ICICI MMT 1007", 45224.19, 50000.0, "ICICI combined", "1007")
    private val i4006 = card(2, "ICICI MMT 4006", -69320.79, 50000.0, "ICICI combined", "4006")
    private val f0584 = card(3, "Federal RuPay 0584", -9000.0, 13000.0, "Federal combined", "0584")
    private val f0324 = card(4, "Federal Visa 0324", -3719.59, 13000.0, "Federal combined", "0324")
    private val all = listOf(i1007, i4006, f0584, f0324)

    @Test fun groupUsedAndAvailableWithOppositeSignBalances() {
        val loans = listOf(emi("ICICI MMT 4006", 1000.0, 6, 2)) // 4 left -> 4000 blocked
        val g = cardGroup(i4006, all, loans)!!
        assertEquals("ICICI combined", g.name)
        assertEquals(listOf(i1007, i4006), g.members)
        assertEquals(50000.0, g.usage.limit, 0.0)                      // max, not sum
        assertEquals(24096.60, g.usage.owed, 0.005)
        assertEquals(4000.0, g.usage.emi, 0.0)
        assertEquals(24096.60 + 4000.0, g.usage.used, 0.005)
        assertEquals(25903.40 - 4000.0, g.usage.available, 0.005)
        // Same group whichever member it is computed from.
        assertEquals(g, cardGroup(i1007, all, loans))
    }

    @Test fun allCardsMergedNewestFirstWithGroupPaymentChip() {
        val g = cardGroup(i1007, all, emptyList())!!
        val transfer = setOf(9L)
        val e = listOf(
            tx(10, 1, 500.0, LocalDate.of(2026, 10, 3)),
            tx(11, 2, 700.0, LocalDate.of(2026, 10, 5)),
            tx(12, 3, 900.0, LocalDate.of(2026, 10, 6)),                                 // Federal: not in this group
            tx(13, 1, 20000.0, LocalDate.of(2026, 10, 4), TransactionType.INCOME, cat = 9), // bill payment
        )
        val merged = groupTransactions(e, g)
        assertEquals(listOf(11L, 13L, 10L), merged.map { it.id })
        assertEquals(listOf(1L, 2L, 1L).sorted(), merged.map { it.accountId }.sorted()) // own accountIds kept
        assertTrue(isGroupPayment(merged[1], g, transfer))
        assertFalse(isGroupPayment(merged[0], g, transfer))
        // Federal pays per card: a Transfer income there is that card's payment.
        val fed = cardGroup(f0584, all, emptyList())!!
        assertFalse(isGroupPayment(tx(14, 3, 100.0, LocalDate.of(2026, 10, 4), TransactionType.INCOME, cat = 9), fed, transfer))
    }

    @Test fun onlyTheIciciGroupSharesPayments() {
        assertTrue(cardGroup(i1007, all, emptyList())!!.sharedPayments)
        assertFalse(cardGroup(f0324, all, emptyList())!!.sharedPayments)
    }

    @Test fun networkFromCardName() {
        assertEquals(CardNetwork.RUPAY, cardNetwork(f0584))
        assertEquals(CardNetwork.VISA, cardNetwork(f0324))
        assertEquals(CardNetwork.MASTERCARD, cardNetwork(card(7, "HDFC MasterCard 1111", 0.0, 1.0, null)))
        assertEquals(CardNetwork.MASTERCARD, cardNetwork(card(8, "Axis Master 2222", 0.0, 1.0, null)))
        assertNull(cardNetwork(i1007))                                   // "ICICI MMT 1007": no network in the name
        assertNull(cardNetwork(card(9, "Advisable Card", 0.0, 1.0, null))) // "visa" inside a word is not Visa
        // A network picked in the edit sheet wins over the name; an unknown stored value falls back to the name.
        assertEquals(CardNetwork.MASTERCARD, cardNetwork(i1007.copy(network = "MASTERCARD")))
        assertEquals(CardNetwork.RUPAY, cardNetwork(f0584.copy(network = "bogus")))
    }

    @Test fun noLimitGroupOrSingleMemberIsNotAGroup() {
        val solo = card(5, "HDFC", -9000.0, 10000.0, null)
        assertNull(cardGroup(solo, all + solo, emptyList()))
        assertNull(cardGroup(solo.copy(limitGroup = " "), all + solo, emptyList()))
        val lone = card(6, "SBI", -100.0, 5000.0, "SBI only")
        assertNull(cardGroup(lone, all + lone, emptyList()))
        // Ungrouped usage is unchanged.
        assertEquals(9000.0, creditUsage(solo, all + solo, emptyList())!!.used, 0.0)
    }
}
