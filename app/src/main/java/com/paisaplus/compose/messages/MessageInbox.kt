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
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: MessageInboxEntity): Long
    @Query("UPDATE message_inbox SET status = :status, body = '' WHERE fingerprint = :id")
    suspend fun finish(id: String, status: String)
    @Query("DELETE FROM message_inbox") suspend fun clear()
    // Discard oldest pending text but retain its fingerprint to avoid notification replay.
    @Query("UPDATE message_inbox SET status = 'DISMISSED', body = '' WHERE status = 'PENDING' AND fingerprint NOT IN (SELECT fingerprint FROM message_inbox WHERE status = 'PENDING' ORDER BY receivedAt DESC LIMIT 200)")
    suspend fun trim()
}

@Singleton
/** [MessageInboxRepository.receive] results. */
const val ADDED_TO_REVIEW = "Transaction added to review inbox"
const val AUTO_ADDED = "Transaction added automatically"
const val DUPLICATE = "Already in your transactions"

class MessageInboxRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: android.content.Context,
    private val db: PaisaDatabase, private val expenses: ExpenseRepository, private val settings: SettingsStore,
) {
    fun pending() = db.messageInbox().pending()

    /**
     * Parses one alert. Every candidate is recorded once (by fingerprint); with Settings › SMS & AI › Add automatically
     * on, a recognised account posts it straight away, a matching existing transaction marks it a duplicate, and
     * anything uncertain stays in Review. [scan]: from the SMS history scan, where duplicates are judged per day.
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
        if (!settings.messages.first().autoAdd || p.hasLink || settings.money.first().currency != "INR") return ADDED_TO_REVIEW
        val account = AutoAdd.account(db.accounts().list(), p.accountSuffix, body) ?: return ADDED_TO_REVIEW
        val name = AutoAdd.payee(p.merchant, body) ?: return ADDED_TO_REVIEW
        val category = AutoAdd.category(name, body, db.categories().list(), db.expenses().list()) ?: return ADDED_TO_REVIEW
        // Live: the same payment from SMS and Gmail, or typed in by hand, lands within minutes. History: entries
        // from CSV or by hand may carry only the date, so any same-day match counts.
        val (from, to) = if (scan) {
            val day = java.time.Instant.ofEpochMilli(receivedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        } else receivedAt - 30 * 60_000L to receivedAt + 30 * 60_000L
        if (db.expenses().hasSimilar(account.id, p.type, p.amountPaise / 100.0, from, to)) {
            db.messageInbox().finish(fingerprint, "DUPLICATE")
            return DUPLICATE
        }
        confirm(fingerprint, account.id, category.id, name, auto = true)
        if (!scan) AutoAddNotice.show(ctx, name, p.amountPaise / 100.0, p.type, account.name)
        return AUTO_ADDED
    }

    suspend fun dismiss(id: String) = db.messageInbox().finish(id, "DISMISSED")

    /** Status, expense and account balance are committed atomically; repeated taps cannot double-post. */
    suspend fun confirm(id: String, accountId: Long, categoryId: Long, name: String, auto: Boolean = false) = db.withTransaction {
        val row = db.messageInbox().get(id) ?: return@withTransaction
        if (row.status != "PENDING") return@withTransaction
        require(settings.money.first().currency == "INR") { "INR alerts require INR as the app currency. Change it in Settings before importing." }
        require(db.accounts().get(accountId) != null) { "Choose an existing account" }
        require(db.categories().list().any { it.id == categoryId }) { "Choose an existing category" }
        require(name.isNotBlank()) { "Enter a payee name" }
        val amount = row.amountPaise / 100.0
        require(!db.expenses().hasSimilar(accountId, row.type, amount, row.receivedAt - 300_000L, row.receivedAt + 300_000L)) { "A similar transaction already exists within five minutes. Review it in Transactions." }
        expenses.add(ExpenseEntity(
            name = name.trim(), amount = amount, time = row.receivedAt, type = row.type,
            accountId = accountId, categoryId = categoryId,
            note = (if (auto) "Auto-added from ${row.source}" else "Reviewed ${row.source} alert") + (row.reference?.let { "; Ref $it" } ?: ""),
            method = AutoAdd.method(row.body, row.isUpi),
            upiApp = if (AutoAdd.method(row.body, row.isUpi) == "UPI") AutoAdd.upiApp(row.body)?.name else null,
        ))
        db.messageInbox().finish(id, "IMPORTED")
    }
}
