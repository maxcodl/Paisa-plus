package com.paisaplus.compose.ui

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisaplus.compose.data.BalanceMode
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.AccountRepository
import com.paisaplus.compose.Tab
import com.paisaplus.compose.TabConfig
import com.paisaplus.compose.data.BackupManager
import com.paisaplus.compose.data.CsvImporter
import com.paisaplus.compose.data.ImportPreview
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.LockSettings
import com.paisaplus.compose.data.MoneyFormat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import com.paisaplus.compose.data.DebtEntity
import com.paisaplus.compose.data.DebtRepository
import com.paisaplus.compose.data.DebtTransactionEntity
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.data.CategoryRepository
import com.paisaplus.compose.data.CreditUsage
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.ExpenseRepository
import com.paisaplus.compose.data.LoanEntity
import com.paisaplus.compose.data.LoanKind
import com.paisaplus.compose.data.LoanRepository
import com.paisaplus.compose.data.AutoPoster
import com.paisaplus.compose.data.PaisaDatabase
import com.paisaplus.compose.data.RecurringRepository
import com.paisaplus.compose.data.RecurringRuleEntity
import com.paisaplus.compose.work.Reminders
import com.paisaplus.compose.data.SettingsStore
import com.paisaplus.compose.data.ThemeSettings
import com.paisaplus.compose.data.NextBill
import com.paisaplus.compose.data.creditUsage
import com.paisaplus.compose.data.nextBill
import java.time.LocalDate
import java.time.ZoneId
import com.paisaplus.compose.data.transferIds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AccountRow(val account: AccountEntity, val credit: CreditUsage?, val emiRemaining: Double, val nextBill: NextBill? = null) {
    /** Credit only: charges − payments, incl. installments already posted. */
    val owed get() = -account.amount
    val totalOutstanding get() = owed + emiRemaining
}

data class AppData(
    val loading: Boolean = true,
    val accounts: List<AccountRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val rules: List<RecurringRuleEntity> = emptyList(),
) {
    val accountById = accounts.associate { it.account.id to it.account }
    val categoryById = categories.associateBy { it.id }
    val balance get() = accounts.sumOf { it.account.amount }
}

data class DebtRow(val debt: DebtEntity, val payments: List<DebtTransactionEntity>) {
    val paid get() = payments.sumOf { it.amount }
    val remaining get() = (debt.amount - paid).coerceAtLeast(0.0)
    val settled get() = debt.amount > 0 && paid >= debt.amount
}

private val DEFAULT_CATEGORIES = listOf(
    Triple("Food & Dining", 0xf0a70, 0xFFFF9800),
    Triple("Groceries", 0xf0110, 0xFF4CAF50),
    Triple("Transport", 0xf00e7, 0xFF2196F3),
    Triple("Shopping", 0xf049a, 0xFFE91E63),
    Triple("Bills & Utilities", 0xf02dc, 0xFF9C27B0),
    Triple("Health", 0xf06ef, 0xFFF44336),
    Triple("Entertainment", 0xf0381, 0xFF673AB7),
    Triple("Education", 0xf0474, 0xFF3F51B5),
    Triple("Travel", 0xf001d, 0xFF009688),
    Triple("Salary", 0xf0116, 0xFF8BC34A),
    Triple("Other", 0xf04f9, 0xFF795548),
)

/** [sticky]: stays until dismissed or replaced (used for Undo). */
data class UiMessage(val text: String, val action: String? = null, val sticky: Boolean = false, val onAction: () -> Unit = {})

/** Activity-scoped: shared by every tab so data and snackbars survive navigation. */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val accounts: AccountRepository,
    private val expenses: ExpenseRepository,
    private val categories: CategoryRepository,
    private val loans: LoanRepository,
    private val recurring: RecurringRepository,
    private val poster: AutoPoster,
    private val db: PaisaDatabase,
    private val settings: SettingsStore,
    private val backup: BackupManager,
    private val debtRepo: DebtRepository,
    private val csv: CsvImporter,
) : ViewModel() {
    // CSV statement import (ImportCsvScreen); kept here so a commit survives leaving the screen.
    var csvPreview by mutableStateOf<ImportPreview?>(null)
        private set
    var csvProgress by mutableStateOf<Float?>(null)
        private set
    /** Bumped after each successful commit; onboarding watches it (progress can flip too fast to observe). */
    var csvCommits by mutableStateOf(0)
        private set
    var csvBusy by mutableStateOf(false)
        private set

    fun previewCsv(uri: Uri) = viewModelScope.launch {
        csvBusy = true
        runCatching {
            val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
            csvPreview = withContext(Dispatchers.Default) { csv.preview(text) }
        }.onFailure { messages.emit(UiMessage(str(R.string.couldn_t_read_file_x, it.message))) }
        csvBusy = false
    }

    fun commitCsv() {
        val p = csvPreview ?: return
        viewModelScope.launch {
            csvBusy = true; csvProgress = 0f
            val msg = runCatching { str(R.string.imported_x_transactions, csv.commit(p) { csvProgress = it }).also { csvCommits++ } }.getOrElse { str(R.string.import_stopped_x_rows_already, it.message) }
            csvBusy = false; csvProgress = null; csvPreview = null
            messages.emit(UiMessage(msg))
        }
    }

    /** Debts with their payments (debt_transactions), newest payment first. */
    val debts = combine(debtRepo.all(), debtRepo.allTransactions()) { d, t ->
        val byDebt = t.groupBy { it.debtId }
        d.map { DebtRow(it, byDebt[it.id].orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveDebt(d: DebtEntity) = viewModelScope.launch { debtRepo.save(d) }
    fun deleteDebt(d: DebtEntity) = viewModelScope.launch {
        debtRepo.delete(d)
        messages.emit(UiMessage(str(R.string.deleted_x, d.name)))
    }
    fun addPayment(debtId: Long, amount: Double, time: Long) = viewModelScope.launch { debtRepo.addTransaction(DebtTransactionEntity(debtId = debtId, amount = amount, time = time)) }
    fun deletePayment(t: DebtTransactionEntity) = viewModelScope.launch { debtRepo.deleteTransaction(t) }

    val data = combine(accounts.all(), loans.all(), expenses.all(), categories.all(), recurring.all()) { a, l, e, c, r ->
        val today = LocalDate.now()
        AppData(
            loading = false,
            accounts = a.map { acc ->
                val emi = l.filter { it.kind == LoanKind.EMI && it.account.equals(acc.name, ignoreCase = true) }.sumOf { it.remaining }
                AccountRow(acc, creditUsage(acc, a, l), emi, nextBill(acc, e, l, today, ZoneId.systemDefault()))
            },
            categories = c,
            expenses = e,
            loans = l,
            rules = r,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    val theme = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSettings())
    val hideBalance = settings.hideBalance.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val excludeTransfers = settings.excludeTransfers.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setExcludeTransfers(v: Boolean) = viewModelScope.launch { settings.setExcludeTransfers(v) }
    val widgetOpacity = settings.widgetOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 0.85f)
    fun setWidgetOpacity(v: Float) = viewModelScope.launch { settings.setWidgetOpacity(v) }
    val monthStart = settings.monthStart.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    fun setMonthStart(day: Int) = viewModelScope.launch { settings.setMonthStart(day) }
    /** Category ids Home/Reports leave out of income/expense totals. */
    val totalsSkip = combine(data, excludeTransfers) { d, on -> if (on) transferIds(d.categories) else emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val userName = settings.userName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val userImage = settings.userImage.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Copies [uri] into app storage, downscaled to 512px (null = remove the photo). */
    fun setUserImage(uri: Uri?) = viewModelScope.launch {
        val old = userImage.value
        val path = runCatching {
            if (uri == null) "" else withContext(Dispatchers.IO) {
                val r = ctx.contentResolver
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                r.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                o.inSampleSize = generateSequence(1) { it * 2 }.first { maxOf(o.outWidth, o.outHeight) / it <= 1024 || it >= 64 }
                o.inJustDecodeBounds = false
                val src = r.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }!!
                // Camera photos store rotation in EXIF; bake it in.
                val deg = r.openInputStream(uri)!!.use {
                    when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
                val k = (512f / maxOf(src.width, src.height)).coerceAtMost(1f)
                val m = android.graphics.Matrix().apply { postScale(k, k); postRotate(deg) }
                val bmp = android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                // New name each time so the UI reloads it.
                val f = java.io.File(ctx.filesDir, "profile-${System.currentTimeMillis()}.jpg")
                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                f.absolutePath
            }
        }.getOrElse { messages.emit(UiMessage(str(R.string.couldn_t_use_that_photo))); return@launch }
        settings.setUserImage(path)
        if (old.isNotEmpty() && old != path) withContext(Dispatchers.IO) { java.io.File(old).delete() }
    }
    /** null until DataStore has loaded (avoids flashing onboarding for existing users). */
    val onboarded = settings.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, null as Boolean?)
    val tabs = settings.tabs.map(TabConfig::parse).stateIn(viewModelScope, SharingStarted.Eagerly, TabConfig.DEFAULT)
    val money = settings.money.stateIn(viewModelScope, SharingStarted.Eagerly, MoneyFormat())
    val lock = settings.lock.stateIn(viewModelScope, SharingStarted.Eagerly, LockSettings())
    val balanceModes = settings.balanceModes.stateIn(viewModelScope, SharingStarted.Eagerly, BalanceMode.DEFAULT)

    init {
        // Keep the home-screen widgets current: data, masking, currency, balance modes, month start, transfers, theme or opacity changed.
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            combine(data, hideBalance, money, balanceModes, combine(monthStart, excludeTransfers, theme, widgetOpacity) { a, b, c, e -> listOf(a, b, c, e) }) { d, h, m, b, s -> listOf(d.accounts, d.loans, d.expenses.size, h, m, b, s) }
                .drop(1).debounce(800).collect { com.paisaplus.compose.widget.BalanceWidget.refresh(ctx) }
        }
    }

    fun setOnboarded() = viewModelScope.launch { settings.setOnboarded(true) }
    fun setUserName(v: String) = viewModelScope.launch { settings.setUserName(v.trim()) }
    fun setTabs(t: List<Tab>) = viewModelScope.launch { settings.setTabs(TabConfig.format(t)) }
    fun setMoney(m: MoneyFormat) = viewModelScope.launch { settings.setMoney(m) }
    fun setBalanceModes(m: List<BalanceMode>) = viewModelScope.launch { settings.setBalanceModes(m) }
    fun setLock(l: LockSettings) = viewModelScope.launch { settings.setLock(l) }

    /** Onboarding "Start fresh": a Cash account and common categories (skipped if any exist). */
    fun startFresh() { viewModelScope.launch {
        if (data.value.categories.isEmpty()) DEFAULT_CATEGORIES.forEach { (name, icon, color) ->
            categories.save(CategoryEntity(name = name, icon = icon, color = color.toInt(), isDefault = true))
        }
        if (data.value.accounts.isEmpty()) accounts.save(AccountEntity(name = "Cash", icon = 0xf0114, bankName = "", cardType = CardType.CASH))
        settings.setOnboarded(true)
    }
    }
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    var importing by mutableStateOf(false)
        private set

    fun setTheme(t: ThemeSettings) = viewModelScope.launch { settings.setTheme(t) }
    fun toggleHideBalance() = viewModelScope.launch { settings.setHideBalance(!hideBalance.value) }

    fun saveExpense(e: ExpenseEntity) = viewModelScope.launch {
        if (e.id == 0L) expenses.add(e) else expenses.update(e)
    }

    val recentlyDeleted = expenses.recentlyDeleted().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun renamePayee(from: String, to: String, merged: Boolean) = viewModelScope.launch {
        expenses.renamePayee(from, to)
        messages.emit(UiMessage(str(if (merged) R.string.merged_into_x else R.string.renamed_to_x, to)))
    }

    fun deleteExpense(e: ExpenseEntity) = viewModelScope.launch {
        val deletedId = expenses.delete(e)
        messages.emit(UiMessage(if (e.name.isBlank()) str(R.string.deleted_transaction) else str(R.string.deleted_x, e.name), str(R.string.undo), sticky = true) { restore(deletedId) })
    }

    fun restore(deletedId: Long) = viewModelScope.launch { expenses.restore(deletedId) }

    fun saveAccount(a: AccountEntity) = viewModelScope.launch { accounts.save(a) }
    fun deleteAccount(a: AccountEntity) = viewModelScope.launch {
        accounts.delete(a)
        messages.emit(UiMessage(str(R.string.deleted_account_x, a.name)))
    }

    fun saveCategory(c: CategoryEntity) = viewModelScope.launch { categories.save(c) }
    fun deleteCategory(c: CategoryEntity) = viewModelScope.launch {
        categories.delete(c)
        messages.emit(UiMessage(str(R.string.deleted_category_x, c.name)))
    }

    /** Flutter runs applyDue right after a loan/rule is added; same here. */
    fun postDueNow(report: Boolean = true) = viewModelScope.launch {
        val n = poster.run()
        Reminders.reschedule(ctx, db)
        if (report || n > 0) messages.emit(UiMessage(if (n == 0) str(R.string.nothing_due) else if (n == 1) str(R.string.posted_1_transaction) else str(R.string.posted_x_transactions, n)))
    }

    fun saveLoan(l: LoanEntity) = viewModelScope.launch { loans.save(l); postDueNow(report = false) }
    fun deleteLoan(l: LoanEntity) = viewModelScope.launch { loans.delete(l) }
    fun saveRule(r: RecurringRuleEntity) = viewModelScope.launch { recurring.save(r); postDueNow(report = false) }
    fun deleteRule(r: RecurringRuleEntity) = viewModelScope.launch { recurring.delete(r); Reminders.reschedule(ctx, db) }

    /** Restores a paisa+ backup or imports a Flutter Paisa backup (detected from the file). */
    fun import(uri: Uri) = viewModelScope.launch {
        importing = true
        val msg = runCatching { backup.restoreFrom(uri).also { settings.setOnboarded(true) } }.getOrElse { str(R.string.restore_failed_nothing_changed_x, it.message) }
        importing = false
        messages.emit(UiMessage(msg))
    }

    val backupDir = settings.backupDir.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lastBackup = settings.lastBackup.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val msg = runCatching { backup.exportTo(uri); str(R.string.backup_saved) }.getOrElse { str(R.string.backup_failed_x, it.message) }
        messages.emit(UiMessage(msg))
    }

    fun setBackupDir(uri: Uri?) = viewModelScope.launch {
        val r = ctx.contentResolver
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        backupDir.value?.let { old -> runCatching { r.releasePersistableUriPermission(Uri.parse(old), flags) } }
        uri?.let { r.takePersistableUriPermission(it, flags) }
        settings.setBackupDir(uri?.toString())
        if (uri != null) backupNow()
    }

    fun backupNow() = viewModelScope.launch {
        val msg = runCatching { if (backup.autoBackup()) str(R.string.backed_up_to_folder) else str(R.string.choose_a_folder_first) }.getOrElse { str(R.string.backup_failed_x, it.message) }
        messages.emit(UiMessage(msg))
    }
}
