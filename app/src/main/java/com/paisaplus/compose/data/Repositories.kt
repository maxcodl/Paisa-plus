package com.paisaplus.compose.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

val ExpenseEntity.signed get() = if (type == TransactionType.INCOME) amount else -amount

/** Per-account balance change when a transaction goes from [old] to [new] (null = absent). */
fun balanceDeltas(old: ExpenseEntity?, new: ExpenseEntity?): Map<Long, Double> {
    val m = mutableMapOf<Long, Double>()
    old?.let { m[it.accountId] = (m[it.accountId] ?: 0.0) - it.signed }
    new?.let { m[it.accountId] = (m[it.accountId] ?: 0.0) + it.signed }
    return m.filterValues { it != 0.0 }
}

/** Keeps each account's running balance in sync with its transactions. */
@Singleton
class ExpenseRepository @Inject constructor(private val db: PaisaDatabase) {
    private val dao = db.expenses()
    fun all() = dao.all()
    suspend fun get(id: Long) = dao.get(id)

    private suspend fun apply(old: ExpenseEntity?, new: ExpenseEntity?) =
        balanceDeltas(old, new).forEach { (id, d) -> db.accounts().adjust(id, d) }

    suspend fun add(e: ExpenseEntity) = db.withTransaction {
        apply(null, e)
        dao.insert(e)
    }

    suspend fun update(e: ExpenseEntity) = db.withTransaction {
        apply(dao.get(e.id), e)
        dao.update(e)
    }

    /** Returns the "Recently deleted" row id, for Undo/Restore. */
    suspend fun delete(e: ExpenseEntity, now: Long = System.currentTimeMillis()): Long = db.withTransaction {
        apply(e, null)
        dao.delete(e)
        db.deleted().insert(DeletedExpenseEntity.of(e, now)).also { db.deleted().trim(DeletedExpenseEntity.KEEP) }
    }

    /** Renames every transaction of payee [from] to [to]; onto an existing name this merges the two. */
    suspend fun setCategory(ids: List<Long>, categoryId: Long) = dao.setCategory(ids, categoryId)

    suspend fun renamePayee(from: String, to: String) = db.withTransaction {
        dao.rename(payeeTransactions(dao.list(), from).map { it.id }, to)
        // Alerts that name them the old way keep landing on the new name; earlier aliases follow.
        db.payeeAlias().repoint(from, to)
        val key = com.paisaplus.compose.messages.AutoAdd.aliasKey(from)
        if (key.length >= 4 && key != com.paisaplus.compose.messages.AutoAdd.aliasKey(to)) db.payeeAlias().put(com.paisaplus.compose.messages.PayeeAliasEntity(key, from.trim(), to))
    }

    fun aliases() = db.payeeAlias().all()

    /** A name messages or payment apps use for [name]; later alerts under it land on [name]. */
    suspend fun addAlias(name: String, alias: String) {
        val key = com.paisaplus.compose.messages.AutoAdd.aliasKey(alias)
        if (key.length >= 4 && key != com.paisaplus.compose.messages.AutoAdd.aliasKey(name)) db.payeeAlias().put(com.paisaplus.compose.messages.PayeeAliasEntity(key, alias.trim(), name.trim()))
    }

    suspend fun removeAlias(key: String) = db.payeeAlias().delete(key)

    suspend fun editAlias(oldKey: String, name: String, alias: String) = db.withTransaction { removeAlias(oldKey); addAlias(name, alias) }

    fun recentlyDeleted() = db.deleted().all()

    /** Re-adds a deleted transaction (balance re-applied by [add]); keeps its id unless that's been reused. */
    suspend fun restore(deletedId: Long) = db.withTransaction {
        val d = db.deleted().get(deletedId) ?: return@withTransaction
        add(if (dao.get(d.originalId) == null) d.toExpense() else d.toExpense(id = 0))
        db.deleted().delete(deletedId)
    }
}

/** Deleting an account also deletes its transactions, as in the Flutter app. */
@Singleton
class AccountRepository @Inject constructor(private val db: PaisaDatabase, private val guard: BalanceGuard) {
    private val dao = db.accounts()
    fun all() = dao.all()
    /** You edited this account (its balance included), so its balance check starts over from what you saved. */
    suspend fun save(a: AccountEntity) = dao.upsert(a).also { guard.rebaseline(setOf(if (a.id != 0L) a.id else it)) }
    suspend fun delete(a: AccountEntity) = db.withTransaction {
        db.expenses().deleteByAccount(a.id)
        dao.delete(a)
    }
}

/** Deleting a category also deletes its transactions (Flutter parity), reverting their balance effect. */
@Singleton
class CategoryRepository @Inject constructor(private val db: PaisaDatabase) {
    private val dao = db.categories()
    fun all() = dao.all()
    suspend fun save(c: CategoryEntity) = dao.upsert(c)
    suspend fun delete(c: CategoryEntity) = db.withTransaction {
        db.expenses().byCategory(c.id).forEach { e -> db.accounts().adjust(e.accountId, -e.signed) }
        db.expenses().deleteByCategory(c.id)
        dao.delete(c)
    }
}

@Singleton
class DebtRepository @Inject constructor(private val db: PaisaDatabase) {
    private val dao = db.debts()
    fun all() = dao.all()
    fun transactions(debtId: Long) = dao.transactions(debtId)
    fun allTransactions() = dao.transactionsFlow()
    suspend fun save(d: DebtEntity) = dao.upsert(d)
    suspend fun addTransaction(t: DebtTransactionEntity) = dao.insertTransaction(t)
    suspend fun deleteTransaction(t: DebtTransactionEntity) = dao.deleteTransaction(t)
    suspend fun delete(d: DebtEntity) = db.withTransaction {
        dao.deleteTransactions(d.id)
        dao.delete(d)
    }
}

@Singleton
class LoanRepository @Inject constructor(private val dao: LoanDao) {
    fun all() = dao.all()
    suspend fun save(l: LoanEntity) = dao.upsert(l)
    suspend fun delete(l: LoanEntity) = dao.delete(l)
}

@Singleton
class RecurringRepository @Inject constructor(private val dao: RecurringDao) {
    fun all() = dao.all()
    suspend fun save(r: RecurringRuleEntity) = dao.upsert(r)
    suspend fun delete(r: RecurringRuleEntity) = dao.delete(r)
}
