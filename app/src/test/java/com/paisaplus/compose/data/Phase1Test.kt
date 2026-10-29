package com.paisaplus.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BackupImportTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    // Shape produced by the Flutter FileHandler (Expense/Account/Category.toJson).
    private val json = """
    {"expenses":[
      {"name":"Lunch","currency":250.5,"time":"2024-05-01T13:30:00.000","type":"expense","accountId":0,"categoryId":3,"superId":0},
      {"name":"Salary","currency":50000,"time":"2024-05-01T09:00:00.000Z","type":"income","accountId":7,"categoryId":3,"superId":1},
      {"name":"Ghost","currency":10.0,"time":"2024-05-02T10:00:00.000","type":null,"accountId":99,"categoryId":3,"superId":2}
    ],
    "accounts":[
      {"name":"Cash","bankName":"","icon":61234,"number":"","cardType":"cash","superId":0,"amount":1200.0,"creditLimit":null,"limitGroup":null},
      {"name":"HDFC Card","bankName":"HDFC","icon":61235,"number":"1234","cardType":"credit","superId":7,"amount":-3000,"creditLimit":100000,"limitGroup":"hdfc"},
      {"name":"Old","bankName":"SBI","icon":61236,"number":"","cardType":"Credit card","superId":7,"amount":0.0}
    ],
    "categories":[
      {"name":"Food","description":null,"icon":1,"superId":3,"budget":5000.0,"color":4294951175}
    ]}
    """

    @Test fun parsesFlutterBackup() {
        val d = FlutterBackup.parse(json, zone)
        assertEquals(listOf(1L, 2L, 3L), d.accounts.map { it.id })
        assertEquals(listOf(CardType.CASH, CardType.CREDIT, CardType.CREDIT), d.accounts.map { it.cardType })
        assertEquals(100000.0, d.accounts[1].creditLimit!!, 0.0)
        assertEquals("hdfc", d.accounts[1].limitGroup)
        assertNull(d.accounts[0].limitGroup)

        val food = d.categories.single()
        assertEquals(true, food.isBudget)
        assertEquals(0xFFFFC107.toInt(), food.color) // 4294951175 == 0xFFFFC107

        val (lunch, salary, ghost) = d.expenses
        assertEquals(1L, lunch.accountId)  // superId 0 -> id 1
        assertEquals(2L, salary.accountId) // duplicate superId 7: first account wins
        assertEquals(1L, lunch.categoryId)
        assertEquals(TransactionType.INCOME, salary.type)
        assertEquals(TransactionType.EXPENSE, ghost.type)
        assertEquals(-1L, ghost.accountId)
        assertEquals(1, d.orphans)
        assertEquals(50000.0, salary.amount, 0.0)
        assertEquals(LocalDateTime.of(2024, 5, 1, 13, 30).atZone(zone).toInstant().toEpochMilli(), lunch.time)
        assertEquals(1714554000000L, salary.time) // 09:00Z
    }
}

class CreditUsageTest {
    private fun card(id: Long, name: String, owed: Double, limit: Double?, group: String?) =
        AccountEntity(id, name, 0, "", cardType = CardType.CREDIT, amount = -owed, creditLimit = limit, limitGroup = group)

    private fun emi(account: String, emi: Double, tenure: Int, paid: Int) =
        LoanEntity(name = "Phone", kind = LoanKind.EMI, account = account, emi = emi, tenure = tenure, paid = paid, nextDue = 0)

    @Test fun sharedGroupUsesMaxLimitAndSumsOwedPlusEmis() {
        val a = card(1, "Amazon ICICI", 2000.0, 50000.0, "ICICI ")
        val b = card(2, "Coral ICICI", 3000.0, 80000.0, "icici")
        val other = card(3, "HDFC", 9000.0, 10000.0, null)
        val loans = listOf(
            emi("coral icici", 1000.0, 6, 2), // 4 left -> 4000 blocked
            emi("HDFC", 500.0, 3, 1),         // other card, ignored
            LoanEntity(name = "Car", kind = LoanKind.LOAN, account = "Amazon ICICI", emi = 9e3, tenure = 10, nextDue = 0),
        )
        val u = creditUsage(a, listOf(a, b, other), loans)!!
        assertEquals(80000.0, u.limit, 0.0)
        assertEquals(2000.0 + 3000.0 + 4000.0, u.used, 0.0)
        assertEquals(71000.0, u.available, 0.0)
        assertEquals(listOf(b), u.shared)
    }

    @Test fun ungroupedAndUnknownTenure() {
        val c = card(3, "HDFC", 9000.0, 10000.0, null)
        val u = creditUsage(c, listOf(c), listOf(emi("hdfc", 500.0, 0, 3)))!!
        assertEquals(9000.0, u.used, 0.0)
        assertNull(creditUsage(card(4, "NoLimit", 1.0, null, null), emptyList(), emptyList()))
        assertNull(creditUsage(c.copy(cardType = CardType.BANK), listOf(c), emptyList()))
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 28)
    private fun at(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000
    private fun tx(acc: Long, amt: Double, d: LocalDate, type: TransactionType = TransactionType.EXPENSE) =
        ExpenseEntity(name = "x", amount = amt, time = at(d), type = type, accountId = acc, categoryId = 1)

    @Test fun totalOutstandingAndNextBillWithAndWithoutEmi() {
        val amazon = card(1, "Amazon Pay Later", 15787.0, 60000.0, null).copy(statementDay = 1)
        val federal = card(2, "Federal", 3000.0, 50000.0, null).copy(statementDay = 21) // no EMI
        val loans = listOf(emi("Amazon Pay Later", 2000.0, 12, 3).copy(nextDue = at(LocalDate.of(2026, 10, 30)))) // 9 left
        val all = listOf(amazon, federal)
        assertEquals(15787.0 + 18000.0, creditUsage(amazon, all, loans)!!.used, 0.0)
        assertEquals(3000.0, creditUsage(federal, all, loans)!!.used, 0.0)

        val e = listOf(
            tx(1, 999.0, LocalDate.of(2026, 10, 1)),                          // on last statement day → previous bill
            tx(1, 1200.0, LocalDate.of(2026, 10, 2)),
            tx(1, 300.0, LocalDate.of(2026, 10, 20), TransactionType.INCOME), // payment
            tx(2, 50.0, LocalDate.of(2026, 10, 21)),
            tx(2, 700.0, LocalDate.of(2026, 10, 22)),
        )
        val a = nextBill(amazon, e, loans, today, zone)!!
        assertEquals(LocalDate.of(2026, 11, 1), a.date)
        assertEquals(900.0, a.charges, 0.0); assertEquals(2000.0, a.emi, 0.0); assertEquals(2900.0, a.total, 0.0)
        val f = nextBill(federal, e, loans, today, zone)!!
        assertEquals(LocalDate.of(2026, 11, 21), f.date)
        assertEquals(700.0, f.charges, 0.0); assertEquals(0.0, f.emi, 0.0)
        assertNull(nextBill(federal.copy(statementDay = null), e, loans, today, zone))
    }

    @Test fun installmentPostedAfterStatementCountsOnceAsCharge() {
        val amazon = card(1, "Amazon Pay Later", 0.0, 60000.0, null).copy(statementDay = 1)
        // AutoPoster posted installment 4 on Oct 10 (after the Oct 1 statement) and advanced nextDue to Nov 10.
        val posted = emi("Amazon Pay Later", 2000.0, 12, 4).copy(nextDue = at(LocalDate.of(2026, 11, 10)))
        val upcoming = emi("Amazon Pay Later", 500.0, 4, 0).copy(nextDue = at(LocalDate.of(2026, 10, 30)))
        val imported = emi("Amazon Pay Later", 700.0, 10, 0).copy(nextDue = at(LocalDate.of(2026, 10, 15)), autoPost = false)
        val e = listOf(tx(1, 2000.0, LocalDate.of(2026, 10, 10))) // the posted installment (EMI category is not skipped)
        val b = nextBill(amazon, e, listOf(posted, upcoming, imported), today, zone)!!
        assertEquals(2000.0, b.charges, 0.0) // posted → charge
        assertEquals(500.0, b.emi, 0.0)      // only the unposted auto-post installment; posted and imported not added again
        assertEquals(2500.0, b.total, 0.0)
    }
}
