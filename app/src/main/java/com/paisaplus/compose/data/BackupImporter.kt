package com.paisaplus.compose.data

import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

data class BackupData(
    val expenses: List<ExpenseEntity>,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    /** Expenses whose account or category wasn't in the backup. */
    val orphans: Int,
    val loans: List<LoanEntity> = emptyList(),
    val rules: List<RecurringRuleEntity> = emptyList(),
)

/**
 * Reads the Flutter app's JSON backup: {"expenses":[...],"accounts":[...],"categories":[...]}.
 * Expenses reference accounts/categories by their Hive `superId` (starts at 0, may repeat after a
 * Flutter restore), so ids are reassigned 1..n in file order and references remapped; first wins.
 */
object FlutterBackup {
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): BackupData {
        val root = Json.parseToJsonElement(text).jsonObject
        fun list(key: String) = (root[key] as? JsonArray).orEmpty().map { it.jsonObject }

        val accountIds = HashMap<Long, Long>()
        val accounts = list("accounts").mapIndexed { i, o ->
            val id = i + 1L
            o.long("superId")?.let { accountIds.putIfAbsent(it, id) }
            AccountEntity(
                id = id,
                name = o.str("name").orEmpty(),
                icon = o.long("icon")?.toInt() ?: 0,
                bankName = o.str("bankName").orEmpty(),
                number = o.str("number").orEmpty(),
                cardType = cardType(o.str("cardType")),
                amount = o.num("amount") ?: 0.0,
                creditLimit = o.num("creditLimit"),
                limitGroup = o.str("limitGroup")?.takeIf { it.isNotBlank() },
            )
        }
        val categoryIds = HashMap<Long, Long>()
        val categories = list("categories").mapIndexed { i, o ->
            val id = i + 1L
            o.long("superId")?.let { categoryIds.putIfAbsent(it, id) }
            val budget = o.num("budget") ?: 0.0
            CategoryEntity(
                id = id,
                name = o.str("name").orEmpty(),
                description = o.str("description"),
                icon = o.long("icon")?.toInt() ?: 0,
                budget = budget,
                isBudget = o.bool("isBudget") ?: (budget > 0),
                color = o.long("color")?.toInt() ?: 0xFFFFC107.toInt(),
                isDefault = o.bool("isDefault") ?: false,
            )
        }
        var orphans = 0
        val expenses = list("expenses").map { o ->
            val acc = o.long("accountId")?.let(accountIds::get)
            val cat = o.long("categoryId")?.let(categoryIds::get)
            if (acc == null || cat == null) orphans++
            ExpenseEntity(
                name = o.str("name").orEmpty(),
                amount = o.num("currency") ?: 0.0,
                time = parseTime(o.str("time")!!, zone),
                type = if (o.str("type") == "income") TransactionType.INCOME else TransactionType.EXPENSE,
                accountId = acc ?: -1,
                categoryId = cat ?: -1,
            )
        }
        // Settings-box lists (LoanService / RecurringService toMap).
        val loans = list("loans").map { o ->
            LoanEntity(
                name = o.str("name").orEmpty(),
                kind = if (o.long("kind") == 1L) LoanKind.EMI else LoanKind.LOAN,
                account = o.str("account").orEmpty(),
                emi = o.num("emi") ?: 0.0,
                tenure = o.long("tenure")?.toInt() ?: 0,
                paid = o.long("paid")?.toInt() ?: 0,
                principal = o.num("principal") ?: 0.0,
                nextDue = parseTime(o.str("next")!!, zone),
                autoPost = o.bool("autoPost") ?: true,
            ).let { it.copy(dueDay = dayOfMonth(it.nextDue, zone)) }
        }
        val rules = list("recurring_rules").map { o ->
            RecurringRuleEntity(
                name = o.str("name").orEmpty(),
                amount = o.num("amount") ?: 0.0,
                type = if (o.bool("income") == true) TransactionType.INCOME else TransactionType.EXPENSE,
                category = o.str("category").orEmpty(),
                account = o.str("account").orEmpty(),
                frequency = Frequency.entries.getOrElse(o.long("frequency")?.toInt() ?: 2) { Frequency.MONTHLY },
                next = parseTime(o.str("next")!!, zone),
                remind = o.bool("remind") ?: true,
            ).let { it.copy(dueDay = dayOfMonth(it.next, zone)) }
        }
        return BackupData(expenses, accounts, categories, orphans, loans, rules)
    }

    /** Dart's toIso8601String: local time has no offset, UTC ends with Z. */
    fun parseTime(s: String, zone: ZoneId): Long = when {
        s.endsWith("Z") -> Instant.parse(s).toEpochMilli()
        s.length > 19 && (s.lastIndexOf('+') > 10 || s.lastIndexOf('-') > 10) ->
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        else -> LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    }

    // Flutter writes the built-in enum name ("credit"); older builds wrote labels ("Credit card").
    private fun cardType(s: String?) = when (s?.trim()?.lowercase()) {
        "cash" -> CardType.CASH
        "wallet" -> CardType.WALLET
        "credit", "credit card" -> CardType.CREDIT
        else -> CardType.BANK
    }

    private fun JsonObject.p(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.str(k: String) = p(k)?.content
    private fun JsonObject.num(k: String) = p(k)?.doubleOrNull
    private fun JsonObject.long(k: String) = p(k)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.bool(k: String) = p(k)?.booleanOrNull
}

class BackupImporter @Inject constructor(private val db: PaisaDatabase) {
    /** Replaces expenses, accounts, categories, loans and recurring rules, like the Flutter restore. */
    suspend fun import(text: String): BackupData {
        val data = FlutterBackup.parse(text)
        db.withTransaction {
            db.messageInbox().clear()
            db.payeeAlias().clear()
            db.expenses().clear()
            db.accounts().clear()
            db.categories().clear()
            db.loans().clear()
            db.recurring().clear()
            db.deleted().clear()
            data.loans.forEach { db.loans().upsert(it) }
            data.rules.forEach { db.recurring().upsert(it) }
            data.accounts.forEach { db.accounts().insert(it) }
            data.categories.forEach { db.categories().insert(it) }
            data.expenses.chunked(500).forEach { db.expenses().insertAll(it) }
        }
        return data
    }
}
