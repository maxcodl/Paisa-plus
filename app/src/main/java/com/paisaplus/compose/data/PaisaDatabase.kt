package com.paisaplus.compose.data

import com.paisaplus.compose.messages.MessageInboxEntity
import com.paisaplus.compose.messages.MessageInboxDao
import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import java.time.Instant
import java.time.ZoneId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY time DESC")
    fun all(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses ORDER BY id") suspend fun list(): List<ExpenseEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE accountId = :accountId AND type = :type AND amount = :amount AND time BETWEEN :from AND :to)")
    suspend fun hasSimilar(accountId: Long, type: TransactionType, amount: Double, from: Long, to: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE note LIKE '%' || :text || '%')")
    suspend fun hasNote(text: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE abs(amount - :amount) < 0.005 AND time BETWEEN :from AND :to)")
    suspend fun hasAmountNear(amount: Double, from: Long, to: Long): Boolean

    @Insert suspend fun insert(e: ExpenseEntity): Long
    @Insert suspend fun insertAll(e: List<ExpenseEntity>)
    @Update suspend fun update(e: ExpenseEntity)
    @Delete suspend fun delete(e: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE categoryId = :id")
    suspend fun byCategory(id: Long): List<ExpenseEntity>

    @Query("UPDATE expenses SET name = :name WHERE id IN (:ids)") suspend fun rename(ids: List<Long>, name: String)

    @Query("DELETE FROM expenses WHERE accountId = :id") suspend fun deleteByAccount(id: Long)
    @Query("DELETE FROM expenses WHERE categoryId = :id") suspend fun deleteByCategory(id: Long)
    @Query("DELETE FROM expenses") suspend fun clear()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun all(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY id") suspend fun list(): List<AccountEntity>

    @Upsert suspend fun upsert(a: AccountEntity): Long
    @Insert suspend fun insert(a: AccountEntity): Long
    @Delete suspend fun delete(a: AccountEntity)

    @Query("UPDATE accounts SET amount = amount + :delta WHERE id = :id")
    suspend fun adjust(id: Long, delta: Double)

    @Query("DELETE FROM accounts") suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun all(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id") suspend fun list(): List<CategoryEntity>

    @Upsert suspend fun upsert(c: CategoryEntity): Long
    @Insert suspend fun insert(c: CategoryEntity): Long
    @Delete suspend fun delete(c: CategoryEntity)

    @Query("DELETE FROM categories") suspend fun clear()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY dateTime DESC")
    fun all(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debt_transactions WHERE debtId = :debtId ORDER BY time DESC")
    fun transactions(debtId: Long): Flow<List<DebtTransactionEntity>>

    @Query("SELECT * FROM debts ORDER BY id") suspend fun list(): List<DebtEntity>
    @Query("SELECT * FROM debt_transactions ORDER BY id") suspend fun allTransactions(): List<DebtTransactionEntity>
    @Query("SELECT * FROM debt_transactions ORDER BY time DESC") fun transactionsFlow(): Flow<List<DebtTransactionEntity>>
    @Delete suspend fun deleteTransaction(t: DebtTransactionEntity)

    @Upsert suspend fun upsert(d: DebtEntity): Long
    @Insert suspend fun insert(d: DebtEntity): Long
    @Delete suspend fun delete(d: DebtEntity)
    @Insert suspend fun insertTransaction(t: DebtTransactionEntity): Long
    @Insert suspend fun insertTransactions(t: List<DebtTransactionEntity>)

    @Query("DELETE FROM debts") suspend fun clear()
    @Query("DELETE FROM debt_transactions") suspend fun clearTransactions()

    @Query("DELETE FROM debt_transactions WHERE debtId = :debtId")
    suspend fun deleteTransactions(debtId: Long)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY id")
    fun all(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans ORDER BY id") suspend fun list(): List<LoanEntity>

    @Upsert suspend fun upsert(l: LoanEntity): Long
    @Delete suspend fun delete(l: LoanEntity)
    @Query("DELETE FROM loans") suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY next")
    fun all(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY next") suspend fun list(): List<RecurringRuleEntity>

    @Upsert suspend fun upsert(r: RecurringRuleEntity): Long
    @Delete suspend fun delete(r: RecurringRuleEntity)
    @Query("DELETE FROM recurring_rules") suspend fun clear()
}

@Dao
interface DeletedExpenseDao {
    @Query("SELECT * FROM deleted_expenses ORDER BY deletedAt DESC, id DESC")
    fun all(): Flow<List<DeletedExpenseEntity>>

    @Query("SELECT * FROM deleted_expenses WHERE id = :id") suspend fun get(id: Long): DeletedExpenseEntity?
    @Query("SELECT * FROM deleted_expenses ORDER BY id") suspend fun list(): List<DeletedExpenseEntity>
    @Insert suspend fun insert(d: DeletedExpenseEntity): Long
    @Insert suspend fun insertAll(d: List<DeletedExpenseEntity>)
    @Query("DELETE FROM deleted_expenses WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM deleted_expenses") suspend fun clear()

    @Query("DELETE FROM deleted_expenses WHERE id NOT IN (SELECT id FROM deleted_expenses ORDER BY deletedAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

/**
 * v2: loans/recurring_rules get dueDay (backfilled from the next date's day-of-month in [zone]);
 * deleted_expenses table for "Recently deleted".
 */
class Migration1To2(private val zone: ZoneId = ZoneId.systemDefault()) : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) = migrate({ db.execSQL(it) }) { sql ->
        db.query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getLong(1)) } }
    }

    override fun migrate(connection: SQLiteConnection) = migrate({ connection.execSQL(it) }) { sql ->
        connection.prepare(sql).use { st -> buildList { while (st.step()) add(st.getLong(0) to st.getLong(1)) } }
    }

    private fun migrate(exec: (String) -> Unit, rows: (String) -> List<Pair<Long, Long>>) {
        for ((table, col) in listOf("loans" to "nextDue", "recurring_rules" to "next")) {
            exec("ALTER TABLE `$table` ADD COLUMN `dueDay` INTEGER NOT NULL DEFAULT 0")
            rows("SELECT id, `$col` FROM `$table`").forEach { (id, millis) ->
                exec("UPDATE `$table` SET dueDay = ${Instant.ofEpochMilli(millis).atZone(zone).dayOfMonth} WHERE id = $id")
            }
        }
        exec(
            "CREATE TABLE IF NOT EXISTS `deleted_expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`originalId` INTEGER NOT NULL, `name` TEXT NOT NULL, `amount` REAL NOT NULL, `time` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `deletedAt` INTEGER NOT NULL)",
        )
    }
}

@Database(
    entities = [
        ExpenseEntity::class, AccountEntity::class, CategoryEntity::class,
        DebtEntity::class, DebtTransactionEntity::class, LoanEntity::class,
        RecurringRuleEntity::class, DeletedExpenseEntity::class, MessageInboxEntity::class,
    ],
    version = 7,
    // accounts.statementDay, accounts.network, expenses/deleted_expenses .note, then .method + .upiApp
    autoMigrations = [AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7)],
)
abstract class PaisaDatabase : RoomDatabase() {
    abstract fun messageInbox(): MessageInboxDao
    abstract fun expenses(): ExpenseDao
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun debts(): DebtDao
    abstract fun loans(): LoanDao
    abstract fun recurring(): RecurringDao
    abstract fun deleted(): DeletedExpenseDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): PaisaDatabase =
        Room.databaseBuilder(ctx, PaisaDatabase::class.java, "paisa.db")
            .addMigrations(Migration1To2())
            .build()

    @Provides fun expenses(db: PaisaDatabase) = db.expenses()
    @Provides fun accounts(db: PaisaDatabase) = db.accounts()
    @Provides fun categories(db: PaisaDatabase) = db.categories()
    @Provides fun debts(db: PaisaDatabase) = db.debts()
    @Provides fun loans(db: PaisaDatabase) = db.loans()
    @Provides fun recurring(db: PaisaDatabase) = db.recurring()
}
