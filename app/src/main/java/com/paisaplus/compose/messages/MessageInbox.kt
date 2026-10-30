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
    /** Puts an alert wrongly judged a duplicate by older rules back in line, with its text, to be judged again. */
    @Query("UPDATE message_inbox SET status = 'PENDING', body = :body WHERE fingerprint = :id AND status = 'DUPLICATE'")
    suspend fun reopen(id: String, body: String): Int
    /** UPI-app notices old enough that their bank alert should have come ([from]..[to] by posted time). */
    @Query("SELECT * FROM message_inbox WHERE status = 'HINT' AND receivedAt BETWEEN :from AND :to")
    suspend fun hintsBetween(from: Long, to: Long): List<MessageInboxEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM message_inbox WHERE fingerprint NOT LIKE 'hint:%' AND amountPaise = :amountPaise AND type = :type AND receivedAt BETWEEN :from AND :to)")
    suspend fun bankAlertNear(amountPaise: Long, type: TransactionType, from: Long, to: Long): Boolean
    @Query("UPDATE message_inbox SET status = 'PENDING' WHERE fingerprint = :id AND status = 'HINT'")
    suspend fun promote(id: String): Int
    /** The bank alert arrived after all: its own entry replaces the Review item made from the app's notice. */
    @Query("UPDATE message_inbox SET status = 'DISMISSED', body = '' WHERE status = 'PENDING' AND fingerprint LIKE 'hint:%' AND amountPaise = :amountPaise AND type = :type AND abs(receivedAt - :at) <= 1800000")
    suspend fun dismissPromotedHints(amountPaise: Long, type: TransactionType, at: Long): Int
    @Query("DELETE FROM message_inbox") suspend fun clear()
    @Query("UPDATE message_inbox SET status = 'DISMISSED', body = '' WHERE status = 'PENDING'") suspend fun dismissAllPending(): Int
    // Discard oldest pending text but retain its fingerprint to avoid notification replay.
    @Query("UPDATE message_inbox SET status = 'DISMISSED', body = '' WHERE status = 'PENDING' AND fingerprint NOT IN (SELECT fingerprint FROM message_inbox WHERE status = 'PENDING' ORDER BY receivedAt DESC LIMIT 200)")
    suspend fun trim()
}

/** A name an alert uses for someone ("RAHUL C R") and the name you keep them under. [key]: the alert name, letters only, lowercase. */
@Entity(tableName = "payee_alias")
data class PayeeAliasEntity(@PrimaryKey val key: String, val alias: String, val name: String)

@Dao
interface PayeeAliasDao {
    @Query("SELECT * FROM payee_alias ORDER BY alias COLLATE NOCASE") fun all(): Flow<List<PayeeAliasEntity>>
    @Query("SELECT * FROM payee_alias") suspend fun list(): List<PayeeAliasEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(a: PayeeAliasEntity)
    @Query("DELETE FROM payee_alias") suspend fun clear()
    @Query("DELETE FROM payee_alias WHERE `key` = :key") suspend fun delete(key: String)
    /** The person was renamed or merged: their aliases follow. */
    @Query("UPDATE payee_alias SET name = :to WHERE lower(name) = lower(:from)") suspend fun repoint(from: String, to: String)
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
    private val guard: com.paisaplus.compose.data.BalanceGuard,
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
            if (id != -1L && !scan) db.messageInbox().dismissPromotedHints(p.amountPaise, p.type, receivedAt)
            id != -1L
        }
        if (!inserted) {
            // Seen before (an earlier scan or alert): it can still fill in its statement row, which older versions didn't do.
            val seen = db.messageInbox().get(fingerprint)
            if (scan && seen != null && seen.status in setOf("DUPLICATE", "COVERED", "DISMISSED") && merge(seen.copy(body = body))) return MERGED
            // A scan re-judges earlier "duplicates": the rules were looser (any same-amount entry that day), and it costs one lookup.
            if (scan && seen?.status == "DUPLICATE" && db.messageInbox().reopen(fingerprint, body) > 0) { ScanLog.note("re-checked an earlier duplicate"); return settle(fingerprint, scan, notify = false) }
            if (scan && seen?.status == "PENDING") return settle(fingerprint, scan, notify = false)
            // Already saved earlier: a scan still records the name this alert used for that entry's payee.
            if (scan && seen != null && seen.status in setOf("MERGED", "IMPORTED", "DUPLICATE")) learnFromSaved(seen, body)
            return "Message already detected"
        }
        return settle(fingerprint, scan, notify = !scan)
    }

    /** Re-checks everything waiting (Review opens with this): recorded payments leave, recognisable ones are added. */
    suspend fun sweep(): Int {
        promoteHints()
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

    /** Matches an older alert to its statement row and fills that row in; false if there's none to fill. */
    private suspend fun merge(row: MessageInboxEntity): Boolean {
        if (System.currentTimeMillis() - row.receivedAt < FRESH_MS) return false
        val accounts = db.accounts().list()
        val account = AutoAdd.account(accounts, row.accountSuffix, row.body) ?: return false
        val e = statementRow(row, account, accounts) ?: return false
        val hint = hintFor(row)
        val parsed = (BankMessageParser.parse(row.body) as? MessageParseResult.Candidate)?.transaction
        val name = AutoAdd.complete(AutoAdd.payee(hint?.merchant, "") ?: AutoAdd.payee(parsed?.merchant ?: row.merchant, row.body), db.expenses().payeeNames())
        enrich(e, row, name, hint)
        db.messageInbox().finish(row.fingerprint, "MERGED")
        return true
    }

    private suspend fun settle(fingerprint: String, scan: Boolean, notify: Boolean): String {
        val row = db.messageInbox().get(fingerprint)?.takeIf { it.status == "PENDING" } ?: return DUPLICATE
        val accounts = db.accounts().list()
        val account = AutoAdd.account(accounts, row.accountSuffix, row.body)
        val hint = hintFor(row)
        val parsed = (BankMessageParser.parse(row.body) as? MessageParseResult.Candidate)?.transaction
        val known = db.expenses().payeeNames()
        // Own-account transfer (the debit names another of your accounts) or a card bill: a Transfer, never a UPI-app payment.
        val own = parsed?.counterSuffix?.let { c -> accounts.firstOrNull { it.id != account?.id && it.number.filter(Char::isDigit).endsWith(c) } }
        val transfer = own != null || AutoAdd.cardBill(parsed?.merchant ?: row.merchant, row.body)
        val name = if (own != null) "Transfer to ${own.name}"
            else AutoAdd.aliasFor(db.payeeAlias().list(), parsed?.merchant ?: row.merchant)
            ?: AutoAdd.complete(AutoAdd.payee(hint?.merchant.takeUnless { transfer }, "") ?: AutoAdd.payee(parsed?.merchant ?: row.merchant, row.body), known)
        val old =System.currentTimeMillis() - row.receivedAt >= FRESH_MS
        // Account unknown (no digits, or an account not in the app): older than all your records means nothing to add.
        ScanLog.note("account: ${account?.name ?: "unknown"}${name?.let { " · payee: $it" }.orEmpty()}${if (transfer) " · transfer" else ""}")
        if (old && account == null && db.expenses().firstOwnRecordAnyAccount()?.let { row.receivedAt < it } == true) {
            ScanLog.note("older than all your records")
            db.messageInbox().finish(fingerprint, "COVERED")
            return COVERED
        }
        if (old && account != null) {
            // Before the account's first statement or entry: already counted in its opening balance.
            val start = db.expenses().firstOwnRecord(account.id)
            if (start != null && row.receivedAt < start) {
                ScanLog.note("before ${account.name}'s first record")
                db.messageInbox().finish(fingerprint, "COVERED")
                return COVERED
            }
            // The statement has it: give that row what statements lack (time, readable payee, method, app); balance unchanged.
            statementRow(row, account, accounts)?.let { e ->
                ScanLog.note("filled statement row #${e.id} \"${e.name}\"")
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
        if (old) { ScanLog.note("older than 2 days and not in your statements"); return ADDED_TO_REVIEW }
        if (!transfer && nearMatch(row)) { ScanLog.note("same amount saved within 3 days (maybe the same payment)"); return ADDED_TO_REVIEW }
        if (account == null) { ScanLog.note("account not recognised"); return ADDED_TO_REVIEW }
        if (parsed == null) { ScanLog.note("not a bank alert"); return ADDED_TO_REVIEW }
        if (parsed.hasLink) { ScanLog.note("message has a link"); return ADDED_TO_REVIEW }
        if (!settings.messages.first().autoAdd || settings.money.first().currency != "INR") { ScanLog.note("adding automatically is off"); return ADDED_TO_REVIEW }
        if (name == null) { ScanLog.note("no payee name"); return ADDED_TO_REVIEW }
        val category = AutoAdd.category(name, row.body, db.categories().list(), db.expenses().list(), transfer) ?: run { ScanLog.note("no category fits"); return ADDED_TO_REVIEW }
        val added = confirm(fingerprint, account.id, category.id, name, auto = true, app = hint?.takeUnless { transfer }?.let { appOf(it.source) })
        if (added == 0L) return DUPLICATE
        if (notify) AutoAddNotice.show(ctx, added, name, row.amountPaise / 100.0, row.type, account.name)
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

    /** The ledger entry stamped with this alert's time is the same payment: its payee gets the alert's (and any app's) name as an alias. */
    private suspend fun learnFromSaved(row: MessageInboxEntity, body: String) {
        if (!AutoAdd.hasName(row.merchant)) return
        val account = AutoAdd.account(db.accounts().list(), row.accountSuffix, body) ?: return
        val e = db.expenses().atTime(account.id, row.type, row.amountPaise / 100.0, row.receivedAt) ?: return
        // Transfers carry generated names; cryptic statement names say nothing.
        if (AutoAdd.crypticName(e.name) || e.name.startsWith("Transfer ") || e.name == "Credit card payment") return
        learn(e.name, row.merchant, hintFor(row)?.merchant)
    }

    /** Each name in [seen] (alert or payment-app spelling) that differs from [name] becomes an alias of it. */
    private suspend fun learn(name: String, vararg seen: String?, chosen: Boolean = false) {
        val own = AutoAdd.aliasKey(name)
        // [chosen]: you picked this name in Review, so any spelling counts; otherwise only names that look alike
        // (an amount match can pair an alert with someone else's statement row).
        seen.filter { AutoAdd.hasName(it) && (chosen || AutoAdd.plausibleAlias(it, name)) }.forEach { s ->
            val key = AutoAdd.aliasKey(s)
            if (key.length >= 4 && key != own) {
                db.payeeAlias().put(PayeeAliasEntity(key, s!!.trim(), name.trim()))
                ScanLog.note("name saved: ${s.trim()} → ${name.trim()}")
            }
        }
    }


    /** Fills in a statement row from its alert. Amount and account are the same, so the balance doesn't move. */
    private suspend fun enrich(e: ExpenseEntity, row: MessageInboxEntity, name: String?, hint: MessageInboxEntity?) {
        val method = e.method ?: AutoAdd.method(row.body, row.isUpi)
        val app = e.upiApp ?: hint?.let { appOf(it.source) } ?: if (method == "UPI") AutoAdd.upiApp(row.body)?.name else null
        // Statements say "UPI q6922…" or cut names at 10 characters ("Cochin Caf"): the alert's fuller name wins.
        val finalName = if (name != null && (AutoAdd.crypticName(e.name) || (name.length > e.name.length && name.startsWith(e.name, ignoreCase = true)))) name else e.name
        if (!AutoAdd.crypticName(finalName)) learn(finalName, row.merchant, hint?.merchant)
        expenses.update(e.copy(
            time = row.receivedAt,
            name = finalName,
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
    /** A shopping app's "payment successful" notice (Amazon): kept as a hint like a UPI app's; names the payee and amount for the card or Pay Later alert that follows. */
    suspend fun receiveStoreNotice(text: String, source: String, postedAt: Long) {
        val n = AutoAdd.storeNotice(text) ?: return
        val fp = "hint:" + messageFingerprint(text, ParsedBankMessage(n.amountPaise, n.type, null, n.payee, null, false, 1.0), postedAt)
        db.messageInbox().insert(MessageInboxEntity(fp, source, text.take(1000), postedAt, n.amountPaise, n.type, null, n.payee, null, false, 1.0, status = "HINT"))
    }

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
            expenses.update(e.copy(method = "UPI", upiApp = app.name, name = if (better) full else e.name))
            learn(if (better) full else e.name, h.payee, e.name)
        }
    }

    /**
     * A UPI app's notice whose bank alert never came within [HINT_MS] (and isn't in the ledger either) goes to Review as a
     * payment to confirm. The notice names no account, so it is never added unattended. Returns how many were promoted.
     */
    suspend fun promoteHints(now: Long = System.currentTimeMillis()): Int {
        var n = 0
        for (h in db.messageInbox().hintsBetween(now - FRESH_MS, now - HINT_MS)) {
            val from = h.receivedAt - HINT_MS
            val to = h.receivedAt + HINT_MS
            val saved = db.expenses().hasAmountType(h.type, h.amountPaise / 100.0, from, to)
            if (!saved && !db.messageInbox().bankAlertNear(h.amountPaise, h.type, from, to) && db.messageInbox().promote(h.fingerprint) > 0) n++
        }
        return n
    }

    /**
     * Already in the ledger: its bank reference is on a saved transaction, or the same account, direction and amount
     * is there nearby (live: ±30 min, as SMS and Gmail copies or a hand entry land close; history: same day, as CSV and
     * hand entries may carry only the date).
     */
    private suspend fun recorded(row: MessageInboxEntity, accountId: Long?, scan: Boolean): Boolean {
        if (row.reference != null && db.expenses().hasNote("Ref ${row.reference}")) { ScanLog.note("bank reference already saved"); return true }
        accountId ?: return false
        // A date-only entry (statement import, hand entry) a day either side with the same account, direction and amount,
        // and the same payee name (or an alert with no usable name): already saved.
        val zoneId = java.time.ZoneId.systemDefault()
        val day = java.time.Instant.ofEpochMilli(row.receivedAt).atZone(zoneId).toLocalDate()
        val dayMs = 86_400_000L
        val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val kept = AutoAdd.aliasFor(db.payeeAlias().list(), row.merchant)
        if (db.expenses().ownNear(listOf(accountId), row.type, row.amountPaise / 100.0, dayStart - dayMs, dayStart + 2 * dayMs - 1)
            .any { e -> java.time.Instant.ofEpochMilli(e.time).atZone(zoneId).toLocalTime() == java.time.LocalTime.MIDNIGHT &&
                (!AutoAdd.hasName(row.merchant) || AutoAdd.sameName(row.merchant, e.name) || kept.equals(e.name, ignoreCase = true)) }) {
            ScanLog.note("a dated entry with the same amount and payee is already saved")
            return true
        }
        // Same-day matching is for history, where statements carry only dates; a fresh alert has a time, so ±30 min.
        val (from, to) = if (scan && System.currentTimeMillis() - row.receivedAt >= FRESH_MS) {
            val zone = java.time.ZoneId.systemDefault()
            val day = java.time.Instant.ofEpochMilli(row.receivedAt).atZone(zone).toLocalDate()
            day.atStartOfDay(zone).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        } else row.receivedAt - 30 * 60_000L to row.receivedAt + 30 * 60_000L
        return db.expenses().hasSimilar(accountId, row.type, row.amountPaise / 100.0, from, to).also { if (it) ScanLog.note("same account, direction and amount saved nearby in time") }
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
    /** Adds the alert as a transaction; returns its id, or 0 if it was already handled or is a duplicate. */
    suspend fun confirm(id: String, accountId: Long, categoryId: Long, name: String, auto: Boolean = false, app: String? = null): Long = db.withTransaction {
        val row = db.messageInbox().get(id) ?: return@withTransaction 0L
        if (row.status != "PENDING") return@withTransaction 0L
        require(settings.money.first().currency == "INR") { "INR alerts require INR as the app currency. Change it in Settings before importing." }
        val account = db.accounts().get(accountId) ?: throw IllegalArgumentException("Choose an existing account")
        require(db.categories().list().any { it.id == categoryId }) { "Choose an existing category" }
        require(name.isNotBlank()) { "Enter a payee name" }
        val amount = row.amountPaise / 100.0
        if (db.expenses().hasSimilar(accountId, row.type, amount, row.receivedAt - 300_000L, row.receivedAt + 300_000L)) {
            db.messageInbox().finish(id, "DUPLICATE")
            return@withTransaction 0L
        }
        val method = AutoAdd.method(row.body, row.isUpi)
        val added = expenses.add(ExpenseEntity(
            name = name.trim(), amount = amount, time = row.receivedAt, type = row.type,
            accountId = accountId, categoryId = categoryId,
            note = (if (auto) "Auto-added from ${row.source}" else "Reviewed ${row.source} alert") + (row.reference?.let { "; Ref $it" } ?: ""),
            method = method,
            upiApp = if (method == "UPI") app ?: hintFor(row)?.let { appOf(it.source) } ?: AutoAdd.upiApp(row.body)?.name else null,
        ))
        // Named differently in the bank alert or the payment app: remember both, so the next one lands on this name.
        learn(name, row.merchant, hintFor(row)?.merchant, chosen = !auto)
        if (!auto && row.accountSuffix != null && account.number.none(Char::isDigit)) db.accounts().setNumber(account.id, "XX" + row.accountSuffix)
        db.messageInbox().finish(id, "IMPORTED")
        added
    }.also { guard.check() } // after the transaction commits: puts back any balance this save lost

    /** Balance check for the scan, which adds and merges many entries. */
    suspend fun checkBalances() = guard.check()
}
