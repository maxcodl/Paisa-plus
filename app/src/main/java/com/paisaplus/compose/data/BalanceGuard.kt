package com.paisaplus.compose.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Income minus expense of one account's transactions. */
data class AccountSum(val accountId: Long, val total: Double)

/**
 * Fail-safe for the saved account balances. A balance is kept up to date by adding each transaction's amount to it, so a
 * bug anywhere that writes an account from an older copy silently loses an update. What can't change by itself is
 * the gap between an account's saved balance and the sum of its transactions (an opening balance typed in, say):
 * this remembers that gap per account and, when the app finds it changed without you editing the account, puts the
 * balance back and reports it on [fixed].
 */
@Singleton
class BalanceGuard @Inject constructor(private val db: PaisaDatabase, private val settings: SettingsStore) {
    data class Fix(val account: String, val by: Double)

    private val _fixed = MutableSharedFlow<Fix>(extraBufferCapacity = 8)
    val fixed = _fixed.asSharedFlow()
    private val lock = Mutex()

    private suspend fun gaps(): Map<Long, Double> {
        val sums = db.expenses().sums().associate { it.accountId to it.total }
        return db.accounts().list().associate { it.id to it.amount - (sums[it.id] ?: 0.0) }
    }

    /** Accepts the current gap as right: for [ids] (accounts you just edited), or for every account (restore, import). */
    suspend fun rebaseline(ids: Set<Long>? = null) = lock.withLock {
        db.withTransaction {
            val now = gaps()
            val old = settings.balanceGaps.first()
            settings.setBalanceGaps(if (ids == null) now else old.filterKeys { it in now } + now.filterKeys { it in ids })
        }
    }

    /** Repairs any account whose gap moved; accounts seen for the first time just get their gap remembered. */
    suspend fun check(): List<Fix> = lock.withLock {
        val fixes = mutableListOf<Fix>()
        db.withTransaction {
            val base = settings.balanceGaps.first()
            val now = gaps()
            val keep = base.filterKeys { it in now }.toMutableMap()
            val names = db.accounts().list().associate { it.id to it.name }
            for ((id, gap) in now) {
                val was = keep[id]
                if (was == null) { keep[id] = gap; continue }
                val drift = gap - was
                if (abs(drift) > 0.005) {
                    db.accounts().adjust(id, -drift)
                    fixes += Fix(names[id].orEmpty(), -drift)
                }
            }
            settings.setBalanceGaps(keep)
        }
        fixes.forEach { _fixed.tryEmit(it) }
        fixes
    }
}
