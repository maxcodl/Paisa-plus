package com.paisaplus.compose.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val CSV_HEADER = "date,name,amount,type,category,account,bank,account_type,credit_limit,limit_group,loan,loan_type,installment,payment_method,upi_app"

/** Fake rows showing every column: a bank account, a credit card with an EMI, and a card bill payment. */
const val CSV_SAMPLE = """$CSV_HEADER
2026-10-01,Salary - Acme Corp,85000,income,Salary,HDFC Savings,HDFC Bank,bank,,,,,,,
2026-10-02,Swiggy,420,expense,Food,HDFC Savings,HDFC Bank,bank,,,,,,upi,gpay
2026-10-03,DMart,2350.50,expense,Groceries,HDFC Savings,HDFC Bank,bank,,,,,,tap,
2026-10-05,Uber,286,expense,Transport,ICICI Amazon Pay 4321,ICICI Bank,credit,150000,,,,,upi,supermoney
2026-10-07,Netflix,649,expense,Entertainment,ICICI Amazon Pay 4321,ICICI Bank,credit,150000,,,,,,
2026-10-10,iPhone EMI,5825,expense,EMI,ICICI Amazon Pay 4321,ICICI Bank,credit,150000,,iPhone EMI,emi,3/12,,
2026-10-15,Electricity bill,1840,expense,Bills,HDFC Savings,HDFC Bank,bank,,,,,,upi,phonepe
2026-10-20,Card bill payment,7200,expense,Transfer,HDFC Savings,HDFC Bank,bank,,,,,,,
2026-10-20,Card bill payment,7200,income,Transfer,ICICI Amazon Pay 4321,ICICI Bank,credit,150000,,,,,,
"""

/** csv_importer.dart statementPrompt, verbatim: pasted into an AI assistant together with a bank statement. */
const val STATEMENT_PROMPT = """Convert the attached bank / credit card / loan statement(s) into CSV for the
Paisa expense app. Output ONLY the CSV, no commentary, with this exact header:
$CSV_HEADER

Rules:
- date: yyyy-MM-dd
- name: short merchant/description
- amount: positive number, no currency symbol, no thousands separators
- type: income (credit / money in / card payment received) or expense (debit / money out / card spend)
- category: one of Food, Groceries, Transport, Shopping, Bills, Rent, Health, Entertainment, Travel, Salary, Transfer, EMI, Loan, Other
- account: account or card name; one name per account/card, same on every row of it.
  For cards include the last 4 digits, e.g. "HDFC Millennia 1234".
- bank: bank name
- account_type: bank, credit (credit card), cash or wallet
- credit_limit: credit cards only, the card's total credit limit as a number; else empty
- limit_group: credit cards only. When the bank issues several cards on one
  COMBINED / shared credit limit (statement shows one total limit for multiple
  cards, add-on cards, or "combined limit"), give every such card the same
  short group name, e.g. "HDFC combined", and put the combined total in
  credit_limit on each. Cards with their own separate limits: leave empty.
- loan: for an EMI or loan installment row, the loan's name (same on every
  installment of that loan, e.g. "Home loan SBI", "iPhone EMI"); else empty.
  Use category EMI for card EMIs, Loan for other loans.
- loan_type: emi (purchase converted to EMI on a credit card) or loan (home,
  car, personal, education...); empty if not a loan row
- installment: "n/N" when the statement shows it (e.g. "3/12"); else empty
- payment_method: upi, tap (contactless / tap to pay), swipe or insert (chip)
  when the statement shows how a card or account payment was made; else empty
- upi_app: for upi rows, the app if the statement names it: gpay, supermoney,
  phonepe, paytm, bhim, cred or other; else empty
- A purchase converted to EMI: skip the original purchase and its reversal
  ("EMI conversion" credit); output only the monthly installment rows.
- Credit card bill payments: on the card, type income, category Transfer;
  on the paying bank account, type expense, category Transfer.
- Quote fields containing commas. One row per transaction, oldest first.
"""

/** One parsed statement line. Optional columns null/empty = not given (keep what the account has). */
data class CsvRow(
    val name: String,
    val amount: Double,
    val date: LocalDateTime,
    val type: TransactionType,
    val category: String,
    val account: String,
    val bank: String,
    val accountType: CardType? = null,
    val creditLimit: Double? = null,
    val limitGroup: String = "",
    val loan: String = "",
    val loanKind: LoanKind? = null,
    val installment: Int? = null,
    val tenure: Int? = null,
    val method: PaymentMethod? = null,
    val upiApp: UpiApp? = null,
) {
    /** Duplicate key: calendar day, amount, name, type, account (case-insensitive), as in Flutter. */
    val key get() = dedupeKey(date.toLocalDate(), amount, name, type, account)
}

fun dedupeKey(day: LocalDate, amount: Double, name: String, type: TransactionType, account: String) =
    "${day.year}-${day.monthValue}-${day.dayOfMonth}|$amount|${name.lowercase()}|${type.ordinal}|${account.lowercase()}"

data class ParsedStatement(val rows: List<CsvRow>, val errors: List<String>)

private val aliases = mapOf(
    "date" to "date", "name" to "name", "expense name" to "name", "description" to "name",
    "amount" to "amount", "type" to "type", "category" to "category", "category name" to "category",
    "account" to "account", "account name" to "account", "bank" to "bank", "bank name" to "bank",
    "account_type" to "account_type", "account type" to "account_type", "card_type" to "account_type",
    "credit_limit" to "credit_limit", "credit limit" to "credit_limit",
    "limit_group" to "limit_group", "limit group" to "limit_group",
    "loan" to "loan", "emi" to "loan", "loan_type" to "loan_type", "loan type" to "loan_type",
    "installment" to "installment",
    "payment_method" to "payment_method", "payment method" to "payment_method", "method" to "payment_method", "mode" to "payment_method",
    "upi_app" to "upi_app", "upi app" to "upi_app",
)

private val methods = mapOf(
    "upi" to PaymentMethod.UPI,
    "tap" to PaymentMethod.TAP, "tap to pay" to PaymentMethod.TAP, "contactless" to PaymentMethod.TAP, "nfc" to PaymentMethod.TAP,
    "swipe" to PaymentMethod.SWIPE, "swiped" to PaymentMethod.SWIPE, "magstripe" to PaymentMethod.SWIPE,
    "insert" to PaymentMethod.INSERT, "chip" to PaymentMethod.INSERT, "insert (chip)" to PaymentMethod.INSERT, "dip" to PaymentMethod.INSERT,
)

private val upiApps = mapOf(
    "gpay" to UpiApp.GPAY, "google pay" to UpiApp.GPAY, "googlepay" to UpiApp.GPAY, "tez" to UpiApp.GPAY,
    "supermoney" to UpiApp.SUPERMONEY, "super.money" to UpiApp.SUPERMONEY, "super money" to UpiApp.SUPERMONEY,
    "phonepe" to UpiApp.PHONEPE, "phone pe" to UpiApp.PHONEPE, "paytm" to UpiApp.PAYTM, "bhim" to UpiApp.BHIM,
    "cred" to UpiApp.CRED, "other" to UpiApp.OTHER,
)

private val accountTypes = mapOf(
    "bank" to CardType.BANK, "savings" to CardType.BANK, "current" to CardType.BANK,
    "credit" to CardType.CREDIT, "credit card" to CardType.CREDIT, "card" to CardType.CREDIT,
    "cash" to CardType.CASH, "wallet" to CardType.WALLET,
)

/** RFC 4180-style split (quoted fields, "" escapes, commas/newlines inside quotes), like Dart's CsvToListConverter. */
fun csvTable(text: String): List<List<String>> {
    val s = text.replace("\r\n", "\n").trim()
    if (s.isEmpty()) return emptyList()
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            quoted && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == ',' -> { row += cell.toString(); cell.clear() }
            !quoted && c == '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
            else -> cell.append(c)
        }
        i++
    }
    row += cell.toString()
    rows += row
    return rows
}

/** Dart DateTime.tryParse for the shapes statements use: date, date time ('T' or space), with offset/Z. */
fun parseCsvDate(s: String, zone: ZoneId): LocalDateTime? {
    val t = s.trim()
    if (t.isEmpty()) return null
    runCatching { return LocalDate.parse(t).atStartOfDay() }
    val iso = t.replace(' ', 'T')
    runCatching { return LocalDateTime.parse(iso) }
    runCatching { return OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDateTime() }
    runCatching { return Instant.parse(iso).atZone(zone).toLocalDateTime() }
    runCatching { if (t.length == 8 && t.all(Char::isDigit)) return LocalDate.parse("${t.take(4)}-${t.substring(4, 6)}-${t.takeLast(2)}").atStartOfDay() }
    return null
}

/** csv_importer.dart parseStatementCsv: CSV text -> rows + per-line errors. */
fun parseStatementCsv(text: String, zone: ZoneId = ZoneId.systemDefault()): ParsedStatement {
    val table = csvTable(text)
    if (table.isEmpty()) return ParsedStatement(emptyList(), listOf("File is empty"))
    val index = HashMap<String, Int>()
    table.first().forEachIndexed { i, h -> aliases[h.trim().lowercase()]?.let { index.putIfAbsent(it, i) } }
    val missing = listOf("date", "name", "amount", "account").filter { it !in index }
    if (missing.isNotEmpty()) return ParsedStatement(emptyList(), listOf("Missing columns: ${missing.joinToString(", ")}"))
    fun cell(row: List<String>, col: String) = index[col]?.let { row.getOrNull(it) }?.trim().orEmpty()

    val rows = mutableListOf<CsvRow>()
    val errors = mutableListOf<String>()
    for (r in 1 until table.size) {
        val row = table[r]
        if (row.all { it.isBlank() }) continue
        val line = r + 1
        val date = parseCsvDate(cell(row, "date"), zone)
        val amount = cell(row, "amount").replace(",", "").toDoubleOrNull()
        val name = cell(row, "name")
        val account = cell(row, "account")
        when {
            date == null -> errors += "Line $line: bad date \"${cell(row, "date")}\""
            amount == null || amount <= 0 -> errors += "Line $line: bad amount \"${cell(row, "amount")}\""
            name.isEmpty() || account.isEmpty() -> errors += "Line $line: name/account required"
            else -> {
                val category = cell(row, "category")
                val bank = cell(row, "bank")
                val loan = cell(row, "loan")
                val loanType = cell(row, "loan_type").lowercase()
                // "3/12" -> installment 3 of 12.
                val inst = Regex("""(\d+)\s*/\s*(\d+)""").find(cell(row, "installment"))
                rows += CsvRow(
                    name = name,
                    amount = amount,
                    date = date,
                    type = if (cell(row, "type").lowercase() == "income") TransactionType.INCOME else TransactionType.EXPENSE,
                    category = category.ifEmpty { "Other" },
                    account = account,
                    bank = bank.ifEmpty { account },
                    accountType = accountTypes[cell(row, "account_type").lowercase()],
                    creditLimit = cell(row, "credit_limit").replace(",", "").toDoubleOrNull(),
                    limitGroup = cell(row, "limit_group"),
                    loan = loan,
                    loanKind = when {
                        loan.isEmpty() -> null
                        loanType == "loan" || (loanType.isEmpty() && category.lowercase() == "loan") -> LoanKind.LOAN
                        else -> LoanKind.EMI
                    },
                    installment = inst?.groupValues?.get(1)?.toInt(),
                    tenure = inst?.groupValues?.get(2)?.toInt(),
                    // Optional; unknown words are ignored. An app alone implies UPI.
                    method = methods[cell(row, "payment_method").lowercase()] ?: upiApps[cell(row, "upi_app").lowercase()]?.let { PaymentMethod.UPI },
                    upiApp = upiApps[cell(row, "upi_app").lowercase()],
                )
            }
        }
    }
    return ParsedStatement(rows, errors)
}

data class ImportPreview(
    val toAdd: List<CsvRow>,
    val duplicates: Int,
    val errors: List<String>,
    val newAccounts: Set<String>,
    val newCategories: Set<String>,
    val newLoans: Set<String> = emptySet(),
)

/**
 * csv_importer.dart preview. Identical rows within one statement are real (repeat UPI payments, test
 * charges), so a row is a duplicate only while an unmatched existing identical copy remains.
 */
fun previewImport(
    parsed: ParsedStatement,
    expenses: List<ExpenseEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    loans: List<LoanEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
): ImportPreview {
    val accountById = accounts.associateBy { it.id }
    val accountNames = accounts.map { it.name.lowercase() }.toSet()
    val categoryNames = categories.map { it.name.lowercase() }.toSet()
    val loanNames = loans.map { it.name.lowercase() }.toSet()
    val left = HashMap<String, Int>()
    for (e in expenses) {
        val account = accountById[e.accountId] ?: continue
        val key = dedupeKey(Instant.ofEpochMilli(e.time).atZone(zone).toLocalDate(), e.amount, e.name, e.type, account.name)
        left.merge(key, 1, Int::plus)
    }
    val toAdd = mutableListOf<CsvRow>()
    var duplicates = 0
    for (row in parsed.rows) {
        val n = left[row.key] ?: 0
        if (n > 0) { left[row.key] = n - 1; duplicates++ } else toAdd += row
    }
    return ImportPreview(
        toAdd, duplicates, parsed.errors,
        toAdd.filter { it.account.lowercase() !in accountNames }.map { it.account }.toCollection(LinkedHashSet()),
        toAdd.filter { it.category.lowercase() !in categoryNames }.map { it.category }.toCollection(LinkedHashSet()),
        toAdd.filter { it.loan.isNotEmpty() && it.loan.lowercase() !in loanNames }.map { it.loan }.toCollection(LinkedHashSet()),
    )
}

/**
 * csv_importer.dart _trackInstallment: creates the loan on its first imported installment (autoPost off),
 * then advances paid and the next due date so auto-post never re-posts that month.
 * Next month is month-end clamped ([nextMonthly]), not Dart's rollover.
 */
fun trackInstallment(loans: MutableList<LoanEntity>, row: CsvRow, zone: ZoneId) {
    val day = row.date.dayOfMonth
    val next = nextMonthly(row.date.toLocalDate(), day).atTime(row.date.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
    val i = loans.indexOfFirst { it.name.equals(row.loan, ignoreCase = true) }
    if (i < 0) {
        loans += LoanEntity(
            name = row.loan, kind = row.loanKind ?: LoanKind.EMI, account = row.account, emi = row.amount,
            tenure = row.tenure ?: 0, paid = row.installment ?: 1, nextDue = next, autoPost = false, dueDay = day,
        )
        return
    }
    var l = loans[i]
    l = when {
        row.installment == null -> l.copy(paid = l.paid + 1)
        row.installment > l.paid -> l.copy(paid = row.installment)
        else -> l
    }
    if (row.tenure != null) l = l.copy(tenure = row.tenure)
    if (next >= l.nextDue) l = l.copy(nextDue = next, dueDay = day)
    loans[i] = l
}

private fun CardType.icon() = when (this) {
    CardType.CASH -> 0xf0116
    CardType.BANK -> 0xf0070 // MdiIcons.bank
    CardType.WALLET -> 0xf0584
    CardType.CREDIT -> 0xf190f
}

/** Writes an import through [ExpenseRepository] so account balances move exactly as for a manual add. */
@Singleton
class CsvImporter @Inject constructor(private val db: PaisaDatabase, private val expenses: ExpenseRepository) {
    suspend fun preview(text: String, zone: ZoneId = ZoneId.systemDefault()) =
        previewImport(parseStatementCsv(text, zone), db.expenses().list(), db.accounts().list(), db.categories().list(), db.loans().list(), zone)

    /** Commits in chunks of [CHUNK] (one transaction each); [onProgress] gets 0..1. Returns rows added. */
    suspend fun commit(p: ImportPreview, zone: ZoneId = ZoneId.systemDefault(), onProgress: (Float) -> Unit = {}): Int {
        val total = p.toAdd.size
        val loans = db.loans().list().toMutableList()
        p.toAdd.chunked(CHUNK).forEachIndexed { c, chunk ->
            db.withTransaction {
                val accounts = db.accounts().list().associateBy { it.name.lowercase() }.toMutableMap()
                val categories = db.categories().list().associateBy { it.name.lowercase() }.toMutableMap()
                for (row in chunk) {
                    var account = accounts[row.account.lowercase()] ?: run {
                        val type = row.accountType ?: CardType.BANK
                        val a = AccountEntity(
                            name = row.account, icon = type.icon(), bankName = row.bank,
                            number = Regex("""\d{4}$""").find(row.account)?.value.orEmpty(), cardType = type,
                        )
                        a.copy(id = db.accounts().insert(a))
                    }
                    // The statement is authoritative for card type / limit / combined group.
                    val updated = account.copy(
                        cardType = row.accountType ?: account.cardType,
                        creditLimit = row.creditLimit?.takeIf { it > 0 } ?: account.creditLimit,
                        limitGroup = row.limitGroup.ifEmpty { null } ?: account.limitGroup,
                    )
                    if (updated != account) { db.accounts().upsert(updated); account = updated }
                    accounts[row.account.lowercase()] = account
                    if (row.loan.isNotEmpty()) trackInstallment(loans, row, zone)
                    val category = categories[row.category.lowercase()] ?: run {
                        val cat = CategoryEntity(name = row.category, icon = categoryIconFor(row.category), color = 0xFFFFC107.toInt())
                        cat.copy(id = db.categories().insert(cat)).also { categories[row.category.lowercase()] = it }
                    }
                    // Re-read balance-sensitive state: add() adjusts the account amount in the DB.
                    expenses.add(
                        ExpenseEntity(
                            name = row.name, amount = row.amount, time = row.date.atZone(zone).toInstant().toEpochMilli(),
                            type = row.type, accountId = account.id, categoryId = category.id,
                            method = row.method?.name, upiApp = row.upiApp?.takeIf { row.method == PaymentMethod.UPI }?.name,
                        ),
                    )
                    account = db.accounts().get(account.id)!!
                    accounts[row.account.lowercase()] = account
                }
            }
            onProgress(((c + 1) * CHUNK).coerceAtMost(total).toFloat() / total)
        }
        if (p.toAdd.any { it.loan.isNotEmpty() }) db.withTransaction { loans.forEach { db.loans().upsert(it) } }
        onProgress(1f)
        return total
    }

    companion object { const val CHUNK = 200 }
}
