package com.paisaplus.compose.messages

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.groupKey
import com.paisaplus.compose.data.ExpenseRepository
import com.paisaplus.compose.data.SettingsStore
import com.paisaplus.compose.data.PaisaDatabase
import com.paisaplus.compose.data.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "message_inbox")
data class MessageInboxEntity(
    @PrimaryKey val fingerprint: String,
    val source: String,
    val body: String,
    val receivedAt: Long,
    val amountPaise: Long,
    val type: TransactionType,
    val accountSuffix: String?,
    val merchant: String?,
    val reference: String?,
    val isUpi: Boolean,
    val score: Double,
    val status: String = "PENDING",
)

@Dao
interface MessageInboxDao {
    @Query("SELECT * FROM message_inbox WHERE status = 'PENDING' ORDER BY receivedAt DESC")
    fun pending(): Flow<List<MessageInboxEntity>>
    @Query("SELECT * FROM message_inbox WHERE fingerprint = :id")
    suspend fun get(id: String): MessageInboxEntity?
    @Query("SELECT * FROM message_inbox WHERE status = 'PENDING' ORDER BY receivedAt DESC")
    suspend fun pendingList(): List<MessageInboxEntity>
    @Query("SELECT * FROM message_inbox WHERE status = 'HINT' AND amountPaise = :amountPaise AND receivedAt BETWEEN :from AND :to ORDER BY abs(receivedAt - :at) LIMIT 1")
    suspend fun hintNear(amountPaise: Long, from: Long, to: Long, at: Long): MessageInboxEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: MessageInboxEntity): Long
    @Query("UPDATE message_inbox SET status = :status, body = '' WHERE fingerprint = :id")
    suspend fun finish(id: String, status: String)
    @Query("DELETE FROM message_inbox") suspend fun clear()
    @Query("UPDATE message_inbox SET status = 'DISMISSED', body = '' WHERE status = 'PENDING'") suspend fun dismissAllPending(): Int
    // Discard oldest pending text but retain its fingerprint to avoid notification replay.
    @Query("UPDATE message_inbox SET status = 'DISMISSED', body = '' WHERE status = 'PENDING' AND fingerprint NOT IN (SELECT fingerprint FROM message_inbox WHERE status = 'PENDING' ORDER BY receivedAt DESC LIMIT 200)")
    suspend fun trim()
}

/** [MessageInboxRepository.receive] results. */
const val ADDED_TO_REVIEW = "Transaction added to review inbox"
const val AUTO_ADDED = "Transaction added automatically"
const val DUPLICATE = "Already in your transactions"
/** Older than your own records for that account (statement import or entries): already reflected in the balance. */
const val COVERED = "Before your records"
/** An older alert matched to its statement row, which got the alert's time, payee and app. */
const val MERGED = "Matched to your statement"
/** How far apart a UPI app's notice and the bank's alert for one payment may be. */
private const val HINT_MS = 10 * 60_000L
/** Alerts younger than this may be added unattended; anything older goes through Review. */
private const val FRESH_MS = 2 * 86_400_000L

@Singleton
class MessageInboxRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: android.content.Context,
    private val db: PaisaDatabase, private val expenses: ExpenseRepository, private val settings: SettingsStore,
) {
    fun pending() = db.messageInbox().pending()

    /**
     * Parses one alert. Every candidate is recorded once (by fingerprint), then [settle]d: already in the ledger
     * leaves Review, a recognised account is added (Settings › SMS & AI › Add automatically), anything else waits.
     * [scan]: from the SMS history scan, where duplicates are judged per day.
     */
    suspend fun receive(body: String, source: String, receivedAt: Long, scan: Boolean = false): String {
        val result = BankMessageParser.parse(body)
        if (result is MessageParseResult.Ignored) return result.reason
        val p = (result as MessageParseResult.Candidate).transaction
        val fingerprint = messageFingerprint(body, p, receivedAt)
        val inserted = db.withTransaction {
            val id = db.messageInbox().insert(MessageInboxEntity(
                fingerprint, source.take(100), body, receivedAt,
                p.amountPaise, p.type, p.accountSuffix, p.merchant, p.reference, p.isUpi, p.score,
            ))
            db.messageInbox().trim()
            id != -1L
        }
        if (!inserted) return "Message already detected"
        return settle(fingerprint, scan, notify = !scan)
    }

    /** Re-checks everything waiting (Review opens with this): recorded payments leave, recognisable ones are added. */
    suspend fun sweep(): Int {
        val repaired = if (settings.autoAddRepaired.first()) 0 else repairAutoDuplicates().also { settings.setAutoAddRepaired() }
        return repaired + db.messageInbox().pendingList().count { settle(it.fingerprint, scan = true, notify = false) != ADDED_TO_REVIEW }
    }

    /**
     * One-time fix for 0.9.1 test builds, which added old alerts unattended: removes auto-added entries that duplicate
     * a transaction you entered or imported (same amount within 3 days, any account), e.g. a statement row with
     * another posting date or card. Deleted through the repository, so balances are reversed and the entries stay
     * restorable in Recently deleted.
     */
    private suspend fun repairAutoDuplicates(): Int {
        val all = db.expenses().list()
        val imported = listOf("Auto-added", "Added from", "Reviewed")
        val yours = all.filter { e -> imported.none { e.note?.startsWith(it) == true } }
        val days3 = 3 * 86_400_000L
        val dupes = all.filter { a ->
            a.note?.startsWith("Auto-added") == true && yours.any { kotlin.math.abs(it.amount - a.amount) < 0.005 && kotlin.math.abs(it.time - a.time) <= days3 }
        }
        dupes.forEach { expenses.delete(it) }
        return dupes.size
    }

    private suspend fun settle(fingerprint: String, scan: Boolean, notify: Boolean): String {
        val row = db.messageInbox().get(fingerprint)?.takeIf { it.status == "PENDING" } ?: return DUPLICATE
        val accounts = db.accounts().list()
        val account = AutoAdd.account(accounts, row.accountSuffix, row.body)
        val hint = hintFor(row)
        val parsed = (BankMessageParser.parse(row.body) as? MessageParseResult.Candidate)?.transaction
        val known = db.expenses().payeeNames()
        val name = AutoAdd.complete(AutoAdd.payee(hint?.merchant, "") ?: AutoAdd.payee(parsed?.merchant ?: row.merchant, row.body), known)
        val old = System.currentTimeMillis() - row.receivedAt >= FRESH_MS
        if (old && account != null) {
            // Before the account's first statement or entry: already counted in its opening balance.
            val start = db.expenses().firstOwnRecord(account.id)
            if (start != null && row.receivedAt < start) {
                db.messageInbox().finish(fingerprint, "COVERED")
                return COVERED
            }
            // The statement has it: give that row what statements lack (time, readable payee, method, app); balance unchanged.
            statementRow(row, account, accounts)?.let { e ->
                enrich(e, row, name, hint)
                db.messageInbox().finish(fingerprint, "MERGED")
                return MERGED
            }
        }
        if (recorded(row, account?.id, scan)) {
            db.messageInbox().finish(fingerprint, "DUPLICATE")
            return DUPLICATE
        }
        // Only fresh alerts are added unattended; older ones not in the statement wait in Review as possibly missed.
        if (old || nearMatch(row)) return ADDED_TO_REVIEW
        if (account == null || parsed == null || parsed.hasLink || !settings.messages.first().autoAdd || settings.money.first().currency != "INR") return ADDED_TO_REVIEW
        name ?: return ADDED_TO_REVIEW
        val category = AutoAdd.category(name, row.body, db.categories().list(), db.expenses().list()) ?: return ADDED_TO_REVIEW
        if (!confirm(fingerprint, account.id, category.id, name, auto = true, app = hint?.let { appOf(it.source) })) return DUPLICATE
        if (notify) AutoAddNotice.show(ctx, name, row.amountPaise / 100.0, row.type, account.name)
        return AUTO_ADDED
    }

    /**
     * The statement row for an older alert: one you imported or entered, same direction and amount, within 3 days
     * (statements use the posting date), on this account or another card of its group, and not matched yet
     * (statement rows carry no time of day; a matched one gets the alert's).
     */
    private suspend fun statementRow(row: MessageInboxEntity, account: AccountEntity, accounts: List<AccountEntity>): ExpenseEntity? {
        val group = groupKey(account)
        val ids = accounts.filter { it.id == account.id || (group != null && groupKey(it) == group) }.map { it.id }
        val days3 = 3 * 86_400_000L
        val zone = java.time.ZoneId.systemDefault()
        return db.expenses().ownNear(ids, row.type, row.amountPaise / 100.0, row.receivedAt - days3, row.receivedAt + days3)
            .filter { java.time.Instant.ofEpochMilli(it.time).atZone(zone).toLocalTime() == java.time.LocalTime.MIDNIGHT }
            .minByOrNull { kotlin.math.abs(it.time - row.receivedAt) }
    }

    /** Fills in a statement row from its alert. Amount and account are the same, so the balance doesn't move. */
    private suspend fun enrich(e: ExpenseEntity, row: MessageInboxEntity, name: String?, hint: MessageInboxEntity?) {
        val method = e.method ?: AutoAdd.method(row.body, row.isUpi)
        val app = e.upiApp ?: hint?.let { appOf(it.source) } ?: if (method == "UPI") AutoAdd.upiApp(row.body)?.name else null
        expenses.update(e.copy(
            time = row.receivedAt,
            name = if (name != null && AutoAdd.crypticName(e.name)) name else e.name,
            method = method,
            upiApp = if (method == "UPI") app else e.upiApp,
        ))
    }

    /** A UPI app's own notice for the same payment (same amount within 10 minutes), if one was seen. */
    private suspend fun hintFor(row: MessageInboxEntity): MessageInboxEntity? =
        db.messageInbox().hintNear(row.amountPaise, row.receivedAt - HINT_MS, row.receivedAt + HINT_MS, row.receivedAt)

    private fun appOf(source: String) = com.paisaplus.compose.data.UpiApp.entries.firstOrNull { it.label == source }?.name

    /**
     * A payment notice from a UPI app (GPay, PhonePe, ...). Never a transaction by itself: it is kept as a hint so the
     * bank alert or statement row for the same payment gets the app and the full payee name. If that entry already
     * exists (the bank alert came first), it is updated now.
     */
    suspend fun receiveHint(text: String, app: com.paisaplus.compose.data.UpiApp, postedAt: Long) {
        val h = AutoAdd.upiNotice(text) ?: return
        val fp = "hint:" + messageFingerprint(text, ParsedBankMessage(h.amountPaise, h.type, null, h.payee, null, true, 1.0), postedAt)
        val row = MessageInboxEntity(fp, app.label, text.take(1000), postedAt, h.amountPaise, h.type, null, h.payee, null, true, 1.0, status = "HINT")
        if (db.messageInbox().insert(row) == -1L) return
        val from = postedAt - HINT_MS
        val to = postedAt + HINT_MS
        val known = db.expenses().payeeNames()
        db.expenses().list().filter { e ->
            e.time in from..to && kotlin.math.abs(e.amount - h.amountPaise / 100.0) < 0.005 && e.type == h.type && e.upiApp == null &&
                listOf("Auto-added", "Reviewed").any { e.note?.startsWith(it) == true }
        }.forEach { e ->
            val full = AutoAdd.complete(AutoAdd.payee(h.payee, ""), known)
            val better = full != null && (AutoAdd.crypticName(e.name) || full.startsWith(e.name, ignoreCase = true))
            expenses.update(e.copy(method = "UPI", upiApp = app.name, name = if (better) full!! else e.name))
        }
    }

    /**
     * Already in the ledger: its bank reference is on a saved transaction, or the same account, direction and amount
     * is there nearby (live: ±30 min, as SMS and Gmail copies or a hand entry land close; history: same day, as CSV and
     * hand entries may carry only the date).
     */
    private suspend fun recorded(row: MessageInboxEntity, accountId: Long?, scan: Boolean): Boolean {
        if (row.reference != null && db.expenses().hasNote("Ref ${row.reference}")) return true
        accountId ?: return false
        val (from, to) = if (scan) {
            val zone = java.time.ZoneId.systemDefault()
            val day = java.time.Instant.ofEpochMilli(row.receivedAt).atZone(zone).toLocalDate()
            day.atStartOfDay(zone).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        } else row.receivedAt - 30 * 60_000L to row.receivedAt + 30 * 60_000L
        return db.expenses().hasSimilar(accountId, row.type, row.amountPaise / 100.0, from, to)
    }

    /**
     * Something that may be this payment is already saved: same amount and direction within 3 days on any account
     * (a statement's posting date, a card of the same group, or the bank side of a card bill payment). Not certain
     * enough to drop the alert, but enough to never add it unattended.
     */
    private suspend fun nearMatch(row: MessageInboxEntity): Boolean {
        val amount = row.amountPaise / 100.0
        val days3 = 3 * 86_400_000L
        return db.expenses().hasAmountNear(amount, row.receivedAt - days3, row.receivedAt + days3)
    }

    suspend fun dismiss(id: String) = db.messageInbox().finish(id, "DISMISSED")

    /** Clears Review in one go; fingerprints stay, so the same alerts aren't brought back by a later scan. */
    suspend fun dismissAll(): Int = db.messageInbox().dismissAllPending()

    /**
     * Saves a waiting alert; status, expense and balance commit together, so repeated taps can't double-post.
     * A payment already in the ledger nearby isn't added again: the alert just leaves Review (returns false).
     * An account picked for alerts ending in digits it doesn't have yet learns them, so the next one is added on its own.
     */
    suspend fun confirm(id: String, accountId: Long, categoryId: Long, name: String, auto: Boolean = false, app: String? = null): Boolean = db.withTransaction {
        val row = db.messageInbox().get(id) ?: return@withTransaction false
        if (row.status != "PENDING") return@withTransaction false
        require(settings.money.first().currency == "INR") { "INR alerts require INR as the app currency. Change it in Settings before importing." }
        val account = db.accounts().get(accountId) ?: throw IllegalArgumentException("Choose an existing account")
        require(db.categories().list().any { it.id == categoryId }) { "Choose an existing category" }
        require(name.isNotBlank()) { "Enter a payee name" }
        val amount = row.amountPaise / 100.0
        if (db.expenses().hasSimilar(accountId, row.type, amount, row.receivedAt - 300_000L, row.receivedAt + 300_000L)) {
            db.messageInbox().finish(id, "DUPLICATE")
            return@withTransaction false
        }
        val method = AutoAdd.method(row.body, row.isUpi)
        expenses.add(ExpenseEntity(
            name = name.trim(), amount = amount, time = row.receivedAt, type = row.type,
            accountId = accountId, categoryId = categoryId,
            note = (if (auto) "Auto-added from ${row.source}" else "Reviewed ${row.source} alert") + (row.reference?.let { "; Ref $it" } ?: ""),
            method = method,
            upiApp = if (method == "UPI") app ?: hintFor(row)?.let { appOf(it.source) } ?: AutoAdd.upiApp(row.body)?.name else null,
        ))
        if (!auto && row.accountSuffix != null && account.number.none(Char::isDigit)) db.accounts().upsert(account.copy(number = "XX" + row.accountSuffix))
        db.messageInbox().finish(id, "IMPORTED")
        true
    }
}
