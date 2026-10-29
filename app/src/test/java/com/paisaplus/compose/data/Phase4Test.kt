package com.paisaplus.compose.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.paisaplus.compose.Tab as T
import com.paisaplus.compose.TabConfig as C

class NativeBackupTest {
    private val full = Snapshot(
        expenses = listOf(
            ExpenseEntity(7, "Chai \"special\" ☕", 12.5, 1_700_000_000_000, TransactionType.EXPENSE, 2, 3),
            ExpenseEntity(9, "", 1e9, 0, TransactionType.INCOME, -1, -1),
        ),
        accounts = listOf(
            AccountEntity(2, "HDFC", 0xf04f9, "HDFC Bank", "1234", CardType.CREDIT, -4500.75, 100000.0, "hdfc"),
            AccountEntity(5, "Cash", 1, "", "", CardType.CASH, 0.0, null, null),
        ),
        categories = listOf(CategoryEntity(3, "Food", null, 9, 5000.0, true, 0xFF4CAF50.toInt(), true)),
        debts = listOf(DebtEntity(4, "Ravi", "lent for trip", 3000.0, 1_600_000_000_000, 1_800_000_000_000, DebtType.CREDIT)),
        debtTransactions = listOf(DebtTransactionEntity(11, 4, 500.0, 1_650_000_000_000)),
        loans = listOf(LoanEntity(6, "Phone", LoanKind.EMI, "HDFC", 2000.0, 12, 3, 24000.0, 1_750_000_000_000, false, 31)),
        rules = listOf(RecurringRuleEntity(8, "Rent", 20000.0, TransactionType.EXPENSE, "Rent", "HDFC", Frequency.MONTHLY, 1_760_000_000_000, true, 1)),
        deleted = listOf(DeletedExpenseEntity(1, 99, "Oops", 10.0, 5, TransactionType.EXPENSE, 2, 3, 6)),
        settings = JsonObject(mapOf("key_hide_balance" to JsonObject(mapOf("type" to JsonPrimitive("bool"), "value" to JsonPrimitive(true))))),
    )

    @Test fun roundTripIsIdentical() {
        val text = NativeBackup.encode(full, "0.4.0", 123)
        assertTrue(NativeBackup.isNative(text))
        assertEquals(full, NativeBackup.decode(text))
    }

    @Test fun emptyRoundTrip() = assertEquals(Snapshot(), NativeBackup.decode(NativeBackup.encode(Snapshot(), "x", 0)))

    @Test fun flutterBackupIsNotNative() = assertFalse(NativeBackup.isNative("""{"expenses":[],"accounts":[],"categories":[]}"""))

    @Test fun newerFormatIsRejected() {
        val text = NativeBackup.encode(full, "9", 0).replace("\"formatVersion\":1", "\"formatVersion\":2")
        assertThrows(IllegalArgumentException::class.java) { NativeBackup.decode(text) }
    }

    @Test fun headerFields() {
        val text = NativeBackup.encode(Snapshot(), "0.4.0", 42)
        assertTrue(text.contains("\"formatVersion\":1") && text.contains("\"appVersion\":\"0.4.0\"") && text.contains("\"exportedAt\":42"))
    }
}

class MoneyFormatTest {
    @Test fun defaultsToRupeesIndia() = assertEquals(MoneyFormat("INR", "en-IN"), MoneyFormat())

    @Test fun rupeeSymbolAndPaise() {
        val s = Money.formatter(MoneyFormat()).format(1234567.5)
        assertTrue(s, s.startsWith("₹") && s.endsWith("67.50"))
    }

    @Test fun usDollars() = assertEquals("$1,234,567.50", Money.formatter(MoneyFormat("USD", "en-US")).format(1234567.5))

    @Test fun zeroDecimalCurrency() = assertEquals("¥1,235", Money.formatter(MoneyFormat("JPY", "ja-JP")).format(1234.6).replace('￥', '¥'))

    @Test fun negativeKeepsSymbol() = assertTrue(Money.formatter(MoneyFormat()).format(-5.0).contains("₹"))

    @Test fun badCodeFallsBackToLocaleCurrency() {
        val s = Money.formatter(MoneyFormat("XXX?", "en-IN")).format(1.0)
        assertTrue(s, s.contains("₹"))
    }

    @Test fun useSwitchesGlobalFormatter() {
        Money.use(MoneyFormat("EUR", "de-DE"))
        assertTrue(Money.format(1.5).contains("€"))
        Money.use(MoneyFormat())
    }
}

class TabConfigTest {
    @Test fun defaultWhenUnset() = assertEquals(C.DEFAULT, C.parse(null))
    @Test fun homeAlwaysFirst() = assertEquals(listOf(T.HOME, T.REPORTS, T.ACCOUNTS), C.parse("REPORTS,HOME,ACCOUNTS"))
    @Test fun capsAtFive() = assertEquals(5, C.parse(T.entries.joinToString(",") { it.name }).size)
    @Test fun dropsUnknownAndDuplicates() = assertEquals(listOf(T.HOME, T.SEARCH), C.parse("SEARCH,NOPE,SEARCH"))
    @Test fun roundTrip() {
        val tabs = listOf(T.HOME, T.TRANSACTIONS, T.MORE, T.SEARCH)
        assertEquals(tabs, C.parse(C.format(tabs)))
    }
}

/** Port of paisa test/statement_csv_test.dart plus dedupe/installment cases from csv_importer.dart. */
class StatementCsvTest {
    private val zone = java.time.ZoneId.of("Asia/Kolkata")
    private val header = "date,name,amount,type,category,account,bank,account_type,credit_limit,limit_group,loan,loan_type,installment"

    @Test fun parsesCardTypeCombinedLimitAndEmiColumns() {
        val p = parseStatementCsv(
            """
            $header
            2026-09-01,Swiggy,450,expense,Food,HDFC Regalia 1234,HDFC,credit,300000,HDFC combined,,,
            2026-09-05,iPhone EMI,6500,expense,EMI,HDFC Millennia 5678,HDFC,credit,300000,HDFC combined,iPhone EMI,emi,3/12
            2026-09-10,Home loan,25000,expense,Loan,SBI Savings,SBI,bank,,,Home loan SBI,loan,
            """.trimIndent(),
            zone,
        )
        assertTrue(p.errors.isEmpty())
        assertEquals(CardType.CREDIT, p.rows[0].accountType)
        assertEquals(300000.0, p.rows[0].creditLimit)
        assertEquals("HDFC combined", p.rows[0].limitGroup)
        assertTrue(p.rows[0].loan.isEmpty())
        assertEquals(LoanKind.EMI, p.rows[1].loanKind)
        assertEquals(3, p.rows[1].installment)
        assertEquals(12, p.rows[1].tenure)
        assertEquals(LoanKind.LOAN, p.rows[2].loanKind)
        assertEquals(null, p.rows[2].installment)
    }

    @Test fun quotedCommasAliasesAndDefaults() {
        val p = parseStatementCsv("Date,Description,Amount,Account Name\r\n2026-01-02,\"Amazon, Inc \"\"Prime\"\"\",\"1,299.50\",ICICI\r\n", zone)
        assertTrue(p.errors.isEmpty())
        val r = p.rows.single()
        assertEquals("Amazon, Inc \"Prime\"", r.name)
        assertEquals(1299.5, r.amount, 0.0)
        assertEquals("Other", r.category)
        assertEquals("ICICI", r.bank)
        assertEquals(TransactionType.EXPENSE, r.type)
    }

    @Test fun lineErrors() {
        val p = parseStatementCsv("date,name,amount,account\nnope,a,1,x\n2026-01-01,a,-5,x\n2026-01-01,,5,x\n,,,\n2026-01-01,ok,5,x", zone)
        assertEquals(listOf("Line 2: bad date \"nope\"", "Line 3: bad amount \"-5\"", "Line 4: name/account required"), p.errors)
        assertEquals(1, p.rows.size)
    }

    @Test fun missingColumnsAndEmpty() {
        assertEquals(listOf("Missing columns: amount, account"), parseStatementCsv("date,name\n2026-01-01,x", zone).errors)
        assertEquals(listOf("File is empty"), parseStatementCsv("  \n", zone).errors)
    }

    private val hdfc = AccountEntity(id = 1, name = "HDFC", icon = 0, bankName = "HDFC", cardType = CardType.BANK)
    private fun existing(name: String, amount: Double) = ExpenseEntity(
        name = name, amount = amount, time = java.time.LocalDate.of(2026, 9, 1).atTime(15, 0).atZone(zone).toInstant().toEpochMilli(),
        type = TransactionType.EXPENSE, accountId = 1, categoryId = 1,
    )

    @Test fun duplicateOnlyWhileUnmatchedExistingCopyRemains() {
        val csv = "date,name,amount,account\n2026-09-01,UPI Chai,20,hdfc\n2026-09-01,UPI Chai,20,HDFC\n2026-09-01,UPI Chai,20,HDFC"
        val p = previewImport(parseStatementCsv(csv, zone), listOf(existing("upi chai", 20.0)), listOf(hdfc), emptyList(), emptyList(), zone)
        assertEquals(1, p.duplicates) // one existing copy absorbs one row
        assertEquals(2, p.toAdd.size) // identical rows inside one file are real
        assertEquals(setOf("Other"), p.newCategories)
        assertTrue(p.newAccounts.isEmpty())
    }

    @Test fun differentDayAmountOrTypeIsNotDuplicate() {
        val csv = "date,name,amount,type,account\n2026-09-02,UPI Chai,20,expense,HDFC\n2026-09-01,UPI Chai,21,expense,HDFC\n2026-09-01,UPI Chai,20,income,HDFC"
        val p = previewImport(parseStatementCsv(csv, zone), listOf(existing("UPI Chai", 20.0)), listOf(hdfc), emptyList(), emptyList(), zone)
        assertEquals(0, p.duplicates)
        assertEquals(3, p.toAdd.size)
    }

    @Test fun newAccountsCategoriesLoansListed() {
        val csv = "$header\n2026-09-05,iPhone EMI,6500,expense,EMI,Millennia 5678,HDFC,credit,,,iPhone EMI,emi,3/12"
        val p = previewImport(parseStatementCsv(csv, zone), emptyList(), listOf(hdfc), emptyList(), emptyList(), zone)
        assertEquals(setOf("Millennia 5678"), p.newAccounts)
        assertEquals(setOf("EMI"), p.newCategories)
        assertEquals(setOf("iPhone EMI"), p.newLoans)
    }

    @Test fun installmentsCreateThenAdvanceLoanWithAutoPostOff() {
        val rows = parseStatementCsv(
            "$header\n2026-01-31,iPhone EMI,6500,expense,EMI,Card,HDFC,credit,,,iPhone EMI,emi,3/12\n2026-02-28,iPhone EMI,6500,expense,EMI,Card,HDFC,credit,,,iPhone EMI,emi,4/12",
            zone,
        ).rows
        val loans = mutableListOf<LoanEntity>()
        trackInstallment(loans, rows[0], zone)
        val first = loans.single()
        assertEquals(false, first.autoPost)
        assertEquals(3, first.paid)
        assertEquals(12, first.tenure)
        assertEquals(java.time.LocalDate.of(2026, 2, 28), java.time.Instant.ofEpochMilli(first.nextDue).atZone(zone).toLocalDate()) // clamped, not Mar 3
        trackInstallment(loans, rows[1], zone)
        assertEquals(4, loans.single().paid)
        assertEquals(java.time.LocalDate.of(2026, 3, 28), java.time.Instant.ofEpochMilli(loans.single().nextDue).atZone(zone).toLocalDate())
    }

    @Test fun promptHeaderMatchesParser() = assertTrue(STATEMENT_PROMPT.contains(header) && STATEMENT_PROMPT.startsWith("Convert") && STATEMENT_PROMPT.endsWith("oldest first.\n"))
}

class CsvSampleTest {
    @Test fun paymentMethodAndUpiAppColumns() {
        val p = parseStatementCsv(
            "date,name,amount,account,payment_method,upi_app\n" +
                "2026-10-01,Zepto,100,ICICI,UPI,Google Pay\n" +
                "2026-10-02,Fuel,200,ICICI,contactless,\n" +
                "2026-10-03,Shop,300,ICICI,,phonepe\n" +   // app alone implies UPI
                "2026-10-04,Misc,400,ICICI,cheque,\n",     // unknown method ignored
            java.time.ZoneOffset.UTC,
        )
        assertTrue(p.errors.isEmpty())
        assertEquals(listOf(PaymentMethod.UPI, PaymentMethod.TAP, PaymentMethod.UPI, null), p.rows.map { it.method })
        assertEquals(listOf(UpiApp.GPAY, null, UpiApp.PHONEPE, null), p.rows.map { it.upiApp })
        assertEquals(null, parseStatementCsv("date,name,amount,account\n2026-10-01,X,1,A", java.time.ZoneOffset.UTC).rows.single().method)
    }

    @Test fun sampleParsesCleanly() {
        val p = parseStatementCsv(CSV_SAMPLE, java.time.ZoneOffset.UTC)
        assertTrue(p.errors.toString(), p.errors.isEmpty())
        assertEquals(9, p.rows.size)
        assertEquals(LoanKind.EMI, p.rows.single { it.loan.isNotEmpty() }.loanKind)
    }
}
