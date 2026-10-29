package com.paisaplus.compose.messages

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import com.paisaplus.compose.data.ExpenseEntity
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
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: MessageInboxEntity): Long
    @Query("UPDATE message_inbox SET status = :status, body = '' WHERE fingerprint = :id")
    suspend fun finish(id: String, status: String)
    @Query("DELETE FROM message_inbox") suspend fun clear()
    // Discard oldest pending text but retain its fingerprint to avoid notification replay.
    @Query("UPDATE message_inbox SET status = 'DISMISSED', body = '' WHERE status = 'PENDING' AND fingerprint NOT IN (SELECT fingerprint FROM message_inbox WHERE status = 'PENDING' ORDER BY receivedAt DESC LIMIT 200)")
    suspend fun trim()
}

/** [MessageInboxRepository.receive] results. */
const val ADDED_TO_REVIEW = "Transaction added to review inbox"
const val AUTO_ADDED = "Transaction added automatically"
const val DUPLICATE = "Already in your transactions"

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
    suspend fun sweep(): Int = db.messageInbox().pendingList().count { settle(it.fingerprint, scan = true, notify = false) != ADDED_TO_REVIEW }

    private suspend fun settle(fingerprint: String, scan: Boolean, notify: Boolean): String {
        val row = db.messageInbox().get(fingerprint)?.takeIf { it.status == "PENDING" } ?: return DUPLICATE
        val account = AutoAdd.account(db.accounts().list(), row.accountSuffix, row.body)
        if (recorded(row, account?.id, scan)) {
            db.messageInbox().finish(fingerprint, "DUPLICATE")
            return DUPLICATE
        }
        val parsed = (BankMessageParser.parse(row.body) as? MessageParseResult.Candidate)?.transaction
        if (account == null || parsed == null || parsed.hasLink || !settings.messages.first().autoAdd || settings.money.first().currency != "INR") return ADDED_TO_REVIEW
        val name = AutoAdd.payee(row.merchant, row.body) ?: return ADDED_TO_REVIEW
        val category = AutoAdd.category(name, row.body, db.categories().list(), db.expenses().list()) ?: return ADDED_TO_REVIEW
        if (!confirm(fingerprint, account.id, category.id, name, auto = true)) return DUPLICATE
        if (notify) AutoAddNotice.show(ctx, name, row.amountPaise / 100.0, row.type, account.name)
        return AUTO_ADDED
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

    suspend fun dismiss(id: String) = db.messageInbox().finish(id, "DISMISSED")

    /**
     * Saves a waiting alert; status, expense and balance commit together, so repeated taps can't double-post.
     * A payment already in the ledger nearby isn't added again: the alert just leaves Review (returns false).
     * An account picked for alerts ending in digits it doesn't have yet learns them, so the next one is added on its own.
     */
    suspend fun confirm(id: String, accountId: Long, categoryId: Long, name: String, auto: Boolean = false): Boolean = db.withTransaction {
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
            upiApp = if (method == "UPI") AutoAdd.upiApp(row.body)?.name else null,
        ))
        if (!auto && row.accountSuffix != null && account.number.none(Char::isDigit)) db.accounts().upsert(account.copy(number = "XX" + row.accountSuffix))
        db.messageInbox().finish(id, "IMPORTED")
        true
    }
}
