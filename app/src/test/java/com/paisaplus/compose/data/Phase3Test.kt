package com.paisaplus.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)
private fun LocalDateTime.ms() = atZone(zone).toInstant().toEpochMilli()
private fun Long.ldt() = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

class LoanApplyDueTest {
    private val now = at(2026, 10, 28, 10)
    private fun loan(id: Long = 1, tenure: Int = 12, paid: Int = 0, next: LocalDateTime = at(2026, 8, 5), autoPost: Boolean = true, kind: LoanKind = LoanKind.EMI) =
        LoanEntity(id = id, name = "Phone", kind = kind, account = "HDFC Card", emi = 1000.0, tenure = tenure, paid = paid, nextDue = next.ms(), autoPost = autoPost)

    @Test fun postsEveryDueInstallmentAndAdvances() {
        val (rows, loans) = dueLoans(listOf(loan(paid = 2)), now, zone)
        assertEquals(listOf("Phone EMI 3/12", "Phone EMI 4/12", "Phone EMI 5/12"), rows.map { it.name }) // Aug 5, Sep 5, Oct 5
        assertEquals(listOf(at(2026, 8, 5), at(2026, 9, 5), at(2026, 10, 5)), rows.map { it.time.ldt() })
        assertTrue(rows.all { it.category == "EMI" && it.account == "HDFC Card" && it.bank == "HDFC Card" && it.type == TransactionType.EXPENSE && it.amount == 1000.0 })
        assertEquals(5, loans.single().paid)
        assertEquals(at(2026, 11, 5), loans.single().nextDue.ldt())
    }

    @Test fun dueAtEndOfTodayCounts() {
        assertEquals(1, dueLoans(listOf(loan(next = at(2026, 10, 28, 23, 59))), now, zone).first.size)
        assertEquals(0, dueLoans(listOf(loan(next = at(2026, 10, 29))), now, zone).first.size)
    }

    @Test fun skipsAutoPostOffAndClosed() {
        val (rows, loans) = dueLoans(listOf(loan(autoPost = false), loan(id = 2, tenure = 3, paid = 3)), now, zone)
        assertEquals(0, rows.size)
        assertEquals(0, loans.size)
    }

    @Test fun stopsWhenLoanCloses() {
        val (rows, loans) = dueLoans(listOf(loan(tenure = 4, paid = 3, next = at(2026, 1, 1), kind = LoanKind.LOAN)), now, zone)
        assertEquals(listOf("Phone Loan 4/4"), rows.map { it.name })
        assertEquals("Loan", rows.single().category)
        assertTrue(loans.single().closed)
    }

    @Test fun tenureZeroOmitsTotalAndHitsGuard() {
        val (rows, loans) = dueLoans(listOf(loan(tenure = 0, paid = 0, next = at(2010, 1, 1))), now, zone)
        assertEquals(60, rows.size)
        assertEquals("Phone EMI 1", rows.first().name)
        assertEquals("Phone EMI 60", rows.last().name)
        assertEquals(60, loans.single().paid)
    }

    @Test fun threeOverdueFromJan31ClampToMonthEnds() {
        val l = loan(tenure = 12, paid = 0, next = at(2027, 1, 31, 10)).copy(dueDay = 31)
        val (rows, loans) = dueLoans(listOf(l), at(2027, 3, 31, 12), zone)
        assertEquals(listOf(at(2027, 1, 31, 10), at(2027, 2, 28, 10), at(2027, 3, 31, 10)), rows.map { it.time.ldt() })
        assertEquals(listOf("Phone EMI 1/12", "Phone EMI 2/12", "Phone EMI 3/12"), rows.map { it.name })
        assertEquals(at(2027, 4, 30, 10), loans.single().nextDue.ldt())
        assertEquals(31, loans.single().dueDay)
    }

    @Test fun secondRunIsIdempotent() {
        val (_, advanced) = dueLoans(listOf(loan()), now, zone)
        assertEquals(0, dueLoans(advanced, now, zone).first.size)
    }
}

class RecurringAdvanceTest {
    private fun rule(f: Frequency, next: LocalDateTime, dueDay: Int = 0) = RecurringRuleEntity(
        id = 1, name = "Rent", amount = 100.0, type = TransactionType.EXPENSE, category = "Rent", account = "ICICI", frequency = f, next = next.ms(), dueDay = dueDay,
    )

    @Test fun nextMonthlyClampsAndKeepsIntendedDay() {
        val d31 = generateSequence(LocalDate.of(2027, 1, 31)) { nextMonthly(it, 31) }.take(4).toList()
        assertEquals(listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30)), d31)
        assertEquals(LocalDate.of(2028, 2, 29), nextMonthly(LocalDate.of(2028, 1, 31), 31)) // leap year
        val d30 = generateSequence(LocalDate.of(2027, 1, 30)) { nextMonthly(it, 30) }.take(3).toList()
        assertEquals(listOf(LocalDate.of(2027, 1, 30), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 30)), d30)
        assertEquals(LocalDate.of(2028, 1, 31), nextMonthly(LocalDate.of(2027, 12, 31), 31)) // year rollover
    }

    @Test fun advanceKeepsTimeOfDay() {
        assertEquals(at(2026, 1, 2, 18, 30), rule(Frequency.DAILY, at(2026, 1, 1, 18, 30)).advance(at(2026, 1, 1, 18, 30)))
        assertEquals(at(2026, 1, 8, 9), rule(Frequency.WEEKLY, at(2026, 1, 1, 9)).advance(at(2026, 1, 1, 9)))
        assertEquals(at(2027, 2, 28, 9), rule(Frequency.MONTHLY, at(2027, 1, 31, 9), dueDay = 31).advance(at(2027, 1, 31, 9)))
    }

    @Test fun postsEachOccurrenceThenAdvances() {
        val (rows, rules) = dueRecurring(listOf(rule(Frequency.MONTHLY, at(2027, 1, 31, 9), dueDay = 31)), at(2027, 3, 31, 8), zone)
        assertEquals(listOf(at(2027, 1, 31, 9), at(2027, 2, 28, 9), at(2027, 3, 31, 9)), rows.map { it.time.ldt() })
        assertEquals(at(2027, 4, 30, 9), rules.single().next.ldt())
        assertEquals(0, dueRecurring(rules, at(2027, 3, 31, 8), zone).first.size)
    }

    @Test fun guardCapsAt60() {
        assertEquals(60, dueRecurring(listOf(rule(Frequency.DAILY, at(2020, 1, 1))), at(2026, 1, 1), zone).first.size)
    }
}

class BudgetTest {
    private fun e(id: Long, amount: Double, cat: Long, time: LocalDateTime, type: TransactionType = TransactionType.EXPENSE) =
        ExpenseEntity(id = id, name = "", amount = amount, time = time.ms(), type = type, accountId = 1, categoryId = cat)

    @Test fun monthToDateSpendPerBudgetCategory() {
        val cats = listOf(
            CategoryEntity(id = 1, name = "Food", icon = 0, budget = 100.0, isBudget = true),
            CategoryEntity(id = 2, name = "Fun", icon = 0, budget = 50.0, isBudget = true),
            CategoryEntity(id = 3, name = "Misc", icon = 0, budget = 999.0, isBudget = false),
        )
        val ex = listOf(
            e(1, 60.0, 1, at(2026, 10, 1)), e(2, 70.0, 1, at(2026, 10, 31, 23)),
            e(3, 500.0, 1, at(2026, 9, 30, 23)), // previous month
            e(4, 20.0, 2, at(2026, 10, 5)), e(5, 40.0, 2, at(2026, 10, 6), TransactionType.INCOME), // income ignored
            e(6, 10.0, 3, at(2026, 10, 6)),
        )
        val s = budgets(cats, ex, YearMonth.of(2026, 10), zone)
        assertEquals(listOf(1L, 2L), s.rows.map { it.category.id })
        assertEquals(130.0, s.rows[0].spent, 0.0)
        assertTrue(s.rows[0].over)
        assertEquals(30.0, s.rows[1].left, 0.0)
        assertEquals(150.0, s.budget, 0.0)
        assertEquals(150.0, s.spent, 0.0)
    }
}

class NameResolutionTest {
    private fun row(account: String, category: String) = StatementRow("x", 1.0, 0, TransactionType.EXPENSE, category, account, account)

    @Test fun matchesCaseInsensitiveAndCreatesMissingOnce() {
        val accounts = listOf(AccountEntity(id = 1, name = "HDFC Card", icon = 0, bankName = "HDFC", cardType = CardType.CREDIT))
        val categories = listOf(CategoryEntity(id = 1, name = "EMI", icon = 0))
        val (newAcc, newCat) = missingByName(
            listOf(row("hdfc card", "emi"), row("Axis 1234", "Loan"), row("AXIS 1234", "loan"), row("Cash", "Swiggy Food")),
            accounts, categories,
        )
        assertEquals(listOf("Axis 1234", "Cash"), newAcc.map { it.name })
        val axis = newAcc[0]
        assertEquals("1234", axis.number)
        assertEquals("Axis 1234", axis.bankName)
        assertEquals(CardType.BANK, axis.cardType)
        assertEquals(0xf0070, axis.icon)
        assertEquals(0.0, axis.amount, 0.0)
        assertEquals("", newAcc[1].number)
        assertEquals(listOf("Loan", "Swiggy Food"), newCat.map { it.name })
        assertEquals(0xf04f9, newCat[0].icon) // no keyword -> tag
        assertEquals(0xf0a70, newCat[1].icon) // "food" -> silverwareForkKnife
        assertEquals(0xFFFFC107.toInt(), newCat[0].color)
    }
}

class BackupLoansTest {
    @Test fun parsesLoansAndRules() {
        val json = """
        {"expenses":[],"accounts":[],"categories":[],
         "loans":[{"name":"Car","kind":0,"account":"ICICI","emi":5000,"tenure":0,"paid":3,"principal":0,"next":"2026-11-05T00:00:00.000","autoPost":false},
                  {"name":"Phone","kind":1,"account":"HDFC","emi":1000.5,"tenure":12,"paid":1,"next":"2026-11-10T00:00:00.000"}],
         "recurring_rules":[{"name":"Rent","amount":20000,"income":false,"category":"Rent","account":"ICICI","frequency":2,"next":"2026-11-01T00:00:00.000","remind":false}]}
        """
        val d = FlutterBackup.parse(json, zone)
        assertEquals(listOf(LoanKind.LOAN, LoanKind.EMI), d.loans.map { it.kind })
        assertEquals(listOf(false, true), d.loans.map { it.autoPost })
        assertEquals(at(2026, 11, 5), d.loans[0].nextDue.ldt())
        assertEquals(listOf(5, 10), d.loans.map { it.dueDay })
        assertEquals(1, d.rules.single().dueDay)
        val r = d.rules.single()
        assertEquals(Frequency.MONTHLY, r.frequency)
        assertEquals(TransactionType.EXPENSE, r.type)
        assertEquals(false, r.remind)
    }
}

class MigrationTest {
    @Test fun v1ToV2AddsDueDayBackfilledInZoneAndDeletedTable() {
        val conn = androidx.sqlite.driver.bundled.BundledSQLiteDriver().open(":memory:")
        fun exec(sql: String) = conn.prepare(sql).use { it.step() }
        // v1 shapes (app/schemas/.../1.json).
        exec("CREATE TABLE `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `account` TEXT NOT NULL, `emi` REAL NOT NULL, `tenure` INTEGER NOT NULL, `paid` INTEGER NOT NULL, `principal` REAL NOT NULL, `nextDue` INTEGER NOT NULL, `autoPost` INTEGER NOT NULL)")
        exec("CREATE TABLE `recurring_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `account` TEXT NOT NULL, `frequency` TEXT NOT NULL, `next` INTEGER NOT NULL, `remind` INTEGER NOT NULL)")
        // Jan 31 00:30 IST is still Jan 30 in UTC: backfill must use the device zone.
        exec("INSERT INTO loans VALUES (1, 'Car', 'LOAN', 'ICICI', 5000, 12, 1, 0, ${at(2027, 1, 31, 0, 30).ms()}, 1)")
        exec("INSERT INTO recurring_rules VALUES (7, 'Rent', 100, 'EXPENSE', 'Rent', 'ICICI', 'MONTHLY', ${at(2027, 2, 15, 9).ms()}, 1)")

        Migration1To2(zone).migrate(conn)

        fun int(sql: String) = conn.prepare(sql).use { it.step(); it.getLong(0) }
        assertEquals(31L, int("SELECT dueDay FROM loans WHERE id = 1"))
        assertEquals(15L, int("SELECT dueDay FROM recurring_rules WHERE id = 7"))
        exec("INSERT INTO deleted_expenses (originalId, name, amount, time, type, accountId, categoryId, deletedAt) VALUES (1, 'x', 1, 0, 'EXPENSE', 1, 1, 0)")
        assertEquals(1L, int("SELECT COUNT(*) FROM deleted_expenses"))
        conn.close()
    }

    @Test fun migrationSqlMatchesExportedV2Schema() {
        val v2 = java.io.File("schemas/com.paisaplus.compose.data.PaisaDatabase/2.json").readText()
        assertTrue(v2.contains("\"version\": 2"))
        assertTrue(v2.contains("CREATE TABLE IF NOT EXISTS `\${TABLE_NAME}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalId` INTEGER NOT NULL, `name` TEXT NOT NULL, `amount` REAL NOT NULL, `time` INTEGER NOT NULL, `type` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `deletedAt` INTEGER NOT NULL)"))
        assertEquals(2, Regex("`dueDay` INTEGER NOT NULL DEFAULT 0").findAll(v2).count())
    }
}
