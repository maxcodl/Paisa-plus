package com.paisaplus.compose.data

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Every table plus portable settings. Ids are kept, so references survive a round trip. */
data class Snapshot(
    val expenses: List<ExpenseEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val debtTransactions: List<DebtTransactionEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val rules: List<RecurringRuleEntity> = emptyList(),
    val deleted: List<DeletedExpenseEntity> = emptyList(),
    val settings: JsonObject = JsonObject(emptyMap()),
    val aliases: List<com.paisaplus.compose.messages.PayeeAliasEntity> = emptyList(),
)

/**
 * Native backup format: {"format":"paisa-compose","formatVersion":1,"appVersion":..,"exportedAt":..,"tables":{..},"settings":{..}}.
 * Hand-written (no serialization plugin); field names equal the entity property names.
 */
object NativeBackup {
    const val FORMAT = "paisa-compose"
    const val VERSION = 1
    private val json = Json { prettyPrint = false }

    fun isNative(text: String): Boolean = runCatching {
        (Json.parseToJsonElement(text).jsonObject["format"] as? JsonPrimitive)?.content == FORMAT
    }.getOrDefault(false)

    fun encode(s: Snapshot, appVersion: String, exportedAt: Long): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT)
            put("formatVersion", VERSION)
            put("appVersion", appVersion)
            put("exportedAt", exportedAt)
            put("tables", buildJsonObject {
                put("expenses", arr(s.expenses) { mapOf("id" to it.id, "name" to it.name, "amount" to it.amount, "time" to it.time, "type" to it.type.name, "accountId" to it.accountId, "categoryId" to it.categoryId, "note" to it.note, "method" to it.method, "upiApp" to it.upiApp) })
                put("accounts", arr(s.accounts) { mapOf("id" to it.id, "name" to it.name, "icon" to it.icon, "bankName" to it.bankName, "number" to it.number, "cardType" to it.cardType.name, "amount" to it.amount, "creditLimit" to it.creditLimit, "limitGroup" to it.limitGroup, "statementDay" to it.statementDay, "network" to it.network) })
                put("categories", arr(s.categories) { mapOf("id" to it.id, "name" to it.name, "description" to it.description, "icon" to it.icon, "budget" to it.budget, "isBudget" to it.isBudget, "color" to it.color, "isDefault" to it.isDefault) })
                put("debts", arr(s.debts) { mapOf("id" to it.id, "name" to it.name, "description" to it.description, "amount" to it.amount, "dateTime" to it.dateTime, "expiryDateTime" to it.expiryDateTime, "debtType" to it.debtType.name) })
                put("debt_transactions", arr(s.debtTransactions) { mapOf("id" to it.id, "debtId" to it.debtId, "amount" to it.amount, "time" to it.time) })
                put("loans", arr(s.loans) { mapOf("id" to it.id, "name" to it.name, "kind" to it.kind.name, "account" to it.account, "emi" to it.emi, "tenure" to it.tenure, "paid" to it.paid, "principal" to it.principal, "nextDue" to it.nextDue, "autoPost" to it.autoPost, "dueDay" to it.dueDay) })
                put("recurring_rules", arr(s.rules) { mapOf("id" to it.id, "name" to it.name, "amount" to it.amount, "type" to it.type.name, "category" to it.category, "account" to it.account, "frequency" to it.frequency.name, "next" to it.next, "remind" to it.remind, "dueDay" to it.dueDay) })
                put("deleted_expenses", arr(s.deleted) { mapOf("id" to it.id, "originalId" to it.originalId, "name" to it.name, "amount" to it.amount, "time" to it.time, "type" to it.type.name, "accountId" to it.accountId, "categoryId" to it.categoryId, "deletedAt" to it.deletedAt, "note" to it.note, "method" to it.method, "upiApp" to it.upiApp) })
                // Names bank alerts use for people; older builds neither write nor read this table.
                put("payee_aliases", arr(s.aliases) { mapOf("key" to it.key, "alias" to it.alias, "name" to it.name) })
            })
            put("settings", s.settings)
        },
    )

    /** Throws on a newer formatVersion or malformed rows: a restore must never half-apply. */
    fun decode(text: String): Snapshot {
        val root = Json.parseToJsonElement(text).jsonObject
        require((root["format"] as? JsonPrimitive)?.content == FORMAT) { str(R.string.not_a_paisa_backup) }
        val v = (root["formatVersion"] as? JsonPrimitive)?.intOrNull ?: 0
        require(v in 1..VERSION) { str(R.string.backup_format_x_is_newer, v, VERSION) }
        val t = root["tables"]!!.jsonObject
        fun rows(k: String) = (t[k] as? JsonArray).orEmpty().map { it.jsonObject }
        return Snapshot(
            expenses = rows("expenses").map { ExpenseEntity(it.l("id"), it.s("name"), it.d("amount"), it.l("time"), TransactionType.valueOf(it.s("type")), it.l("accountId"), it.l("categoryId"), it.sn("note"), it.sn("method"), it.sn("upiApp")) },
            accounts = rows("accounts").map { AccountEntity(it.l("id"), it.s("name"), it.i("icon"), it.s("bankName"), it.s("number"), CardType.valueOf(it.s("cardType")), it.d("amount"), it.dn("creditLimit"), it.sn("limitGroup"), it.p("statementDay")?.intOrNull, it.sn("network")) },
            categories = rows("categories").map { CategoryEntity(it.l("id"), it.s("name"), it.sn("description"), it.i("icon"), it.d("budget"), it.b("isBudget"), it.i("color"), it.b("isDefault")) },
            debts = rows("debts").map { DebtEntity(it.l("id"), it.s("name"), it.s("description"), it.d("amount"), it.l("dateTime"), it.l("expiryDateTime"), DebtType.valueOf(it.s("debtType"))) },
            debtTransactions = rows("debt_transactions").map { DebtTransactionEntity(it.l("id"), it.l("debtId"), it.d("amount"), it.l("time")) },
            loans = rows("loans").map { LoanEntity(it.l("id"), it.s("name"), LoanKind.valueOf(it.s("kind")), it.s("account"), it.d("emi"), it.i("tenure"), it.i("paid"), it.d("principal"), it.l("nextDue"), it.b("autoPost"), it.i("dueDay")) },
            rules = rows("recurring_rules").map { RecurringRuleEntity(it.l("id"), it.s("name"), it.d("amount"), TransactionType.valueOf(it.s("type")), it.s("category"), it.s("account"), Frequency.valueOf(it.s("frequency")), it.l("next"), it.b("remind"), it.i("dueDay")) },
            deleted = rows("deleted_expenses").map { DeletedExpenseEntity(it.l("id"), it.l("originalId"), it.s("name"), it.d("amount"), it.l("time"), TransactionType.valueOf(it.s("type")), it.l("accountId"), it.l("categoryId"), it.l("deletedAt"), it.sn("note"), it.sn("method"), it.sn("upiApp")) },
            settings = root["settings"] as? JsonObject ?: JsonObject(emptyMap()),
            aliases = rows("payee_aliases").map { com.paisaplus.compose.messages.PayeeAliasEntity(it.s("key"), it.s("alias"), it.s("name")) },
        )
    }

    private fun <T> arr(items: List<T>, row: (T) -> Map<String, Any?>) = buildJsonArray {
        items.forEach { add(JsonObject(row(it).mapValues { (_, v) -> prim(v) })) }
    }

    private fun prim(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    private fun JsonObject.p(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.s(k: String) = p(k)!!.content
    private fun JsonObject.sn(k: String) = p(k)?.content
    private fun JsonObject.l(k: String) = p(k)!!.longOrNull!!
    private fun JsonObject.i(k: String) = p(k)!!.intOrNull!!
    private fun JsonObject.d(k: String) = p(k)!!.doubleOrNull!!
    private fun JsonObject.dn(k: String) = p(k)?.doubleOrNull
    private fun JsonObject.b(k: String) = p(k)!!.booleanOrNull!!
}

/** Export, restore (native or Flutter) and the daily folder backup. */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: PaisaDatabase,
    private val settings: SettingsStore,
    private val flutter: BackupImporter,
) {
    val appVersion: String
        get() = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    suspend fun snapshot() = db.withTransaction {
        Snapshot(
            db.expenses().list(), db.accounts().list(), db.categories().list(),
            db.debts().list(), db.debts().allTransactions(), db.loans().list(),
            db.recurring().list(), db.deleted().list(), settings.export(), db.payeeAlias().list(),
        )
    }

    suspend fun exportText(): String = NativeBackup.encode(snapshot(), appVersion, System.currentTimeMillis())

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = exportText()
        ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) }
    }

    /** Atomic replace of every table, then settings. */
    suspend fun restore(s: Snapshot) {
        db.withTransaction {
            db.messageInbox().clear(); db.payeeAlias().clear()
            s.aliases.forEach { db.payeeAlias().put(it) }
            db.expenses().clear(); db.accounts().clear(); db.categories().clear()
            db.debts().clear(); db.debts().clearTransactions()
            db.loans().clear(); db.recurring().clear(); db.deleted().clear()
            s.accounts.forEach { db.accounts().insert(it) }
            s.categories.forEach { db.categories().insert(it) }
            s.expenses.chunked(500).forEach { db.expenses().insertAll(it) }
            s.debts.forEach { db.debts().insert(it) }
            if (s.debtTransactions.isNotEmpty()) db.debts().insertTransactions(s.debtTransactions)
            s.loans.forEach { db.loans().upsert(it) }
            s.rules.forEach { db.recurring().upsert(it) }
            if (s.deleted.isNotEmpty()) db.deleted().insertAll(s.deleted)
        }
        settings.import(s.settings)
    }

    /** Reads either format; returns a one-line summary. */
    suspend fun restoreFrom(uri: Uri): String {
        val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
        return if (NativeBackup.isNative(text)) {
            val s = NativeBackup.decode(text)
            restore(s)
            str(R.string.restored_x_transactions_x_accounts, s.expenses.size, s.accounts.size, s.categories.size, s.debts.size, s.loans.size, s.rules.size)
        } else {
            val d = flutter.import(text)
            str(R.string.imported_paisa_backup_x_transactions, d.expenses.size, d.accounts.size, d.categories.size, d.loans.size, d.rules.size) +
                if (d.orphans > 0) str(R.string.x_unlinked, d.orphans) else ""
        }
    }

    /** Writes paisa-YYYY-MM-DD.json to the chosen folder and keeps the newest [KEEP]. No folder = no-op. */
    suspend fun autoBackup(today: LocalDate = LocalDate.now(ZoneId.systemDefault())): Boolean = withContext(Dispatchers.IO) {
        val tree = settings.backupDir.first()?.let(Uri::parse) ?: return@withContext false
        val r = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = "paisa-$today.json"
        val text = exportText() // build before touching files, so a failure leaves the folder as it was
        val doc = children(tree).firstOrNull { it.second == name }?.first
            ?: DocumentsContract.createDocument(r, parent, "application/json", name) ?: error(str(R.string.couldn_t_create_x, name))
        r.openOutputStream(doc, "wt")!!.bufferedWriter().use { it.write(text) }
        (children(tree).filter { it.second.matches(NAME) }.sortedByDescending { it.second }.drop(KEEP))
            .forEach { DocumentsContract.deleteDocument(r, it.first) }
        settings.setLastBackup(System.currentTimeMillis())
        true
    }

    private fun children(tree: Uri): List<Pair<Uri, String>> {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return ctx.contentResolver.query(kids, cols, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to c.getString(1)) }
        }.orEmpty()
    }

    companion object {
        const val KEEP = 7
        private val NAME = Regex("""paisa-\d{4}-\d{2}-\d{2}\.json""")
    }
}
