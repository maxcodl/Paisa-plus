@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.paisaplus.compose.ui.more

import com.paisaplus.compose.R
import com.paisaplus.compose.plural
import com.paisaplus.compose.str

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import com.paisaplus.compose.ui.components.rememberSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.paisaplus.compose.data.DebtEntity
import com.paisaplus.compose.data.DebtType
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.LoanEntity
import com.paisaplus.compose.data.LoanKind
import com.paisaplus.compose.data.dayOfMonth
import com.paisaplus.compose.data.likelyPurchase
import com.paisaplus.compose.ui.AmountField
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.DateField
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.ExpressiveIcon
import com.paisaplus.compose.ui.components.WrapText
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TonalCard
import com.paisaplus.compose.ui.components.TransactionRow
import com.paisaplus.compose.ui.components.WavyProgress
import com.paisaplus.compose.ui.components.segmented
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.plainOrEmpty
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import com.paisaplus.compose.ui.theme.rememberTonal
import com.paisaplus.compose.ui.toAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private enum class LoanTab { BORROWING, LENDING, COMPLETED }

private val LoanTab.label get() = when (this) {
    LoanTab.LENDING -> str(R.string.lending)
    LoanTab.BORROWING -> str(R.string.borrowing)
    LoanTab.COMPLETED -> str(R.string.completed)
}

private val LoanEntity.title get() = label.ifBlank { name }
private val LoanEntity.kindLabel get() = if (kind == LoanKind.EMI) str(R.string.card_emi) else str(R.string.loan)
private val LoanEntity.icon get() = if (kind == LoanKind.EMI) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalance
private val LoanEntity.progress get() = if (tenure > 0) (paid.toFloat() / tenure).coerceIn(0f, 1f) else 0f
private val LoanEntity.total get() = if (tenure > 0) tenure * emi else principal
/** Days until the last installment (next due + the remaining months); unknown tenure → next due. */
private val LoanEntity.daysLeft get() = ZoneId.systemDefault().let { z ->
    val next = Instant.ofEpochMilli(nextDue).atZone(z).toLocalDate()
    ChronoUnit.DAYS.between(LocalDate.now(z), if (tenure > 0) next.plusMonths((tenure - paid - 1).coerceAtLeast(0).toLong()) else next)
}

/** Installments posted for [l]: auto-posted ones are named "<name> <Loan|EMI> n/N"; imports may carry the bare name. */
private fun installmentsOf(l: LoanEntity, expenses: List<ExpenseEntity>) = expenses.filter {
    it.name.equals(l.name, true) || it.name.startsWith("${l.name} ${l.category} ", true)
}

/**
 * Loans: money lent (Lending), money borrowed plus bank loans and card EMIs (Borrowing), and whatever's paid off (Completed).
 * Debt cards open [DebtDetailScreen], loan cards [LoanDetailScreen].
 */
@Composable
fun LoansScreen(vm: AppViewModel, onBack: () -> Unit, onOpenLoan: (Long) -> Unit, onOpenDebt: (Long) -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LoanTab.BORROWING) }
    var editing by remember { mutableStateOf<LoanEntity?>(null) }
    var editingDebt by remember { mutableStateOf<DebtEntity?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    val active = remember(d.loans) { d.loans.filter { !it.closed } }
    val (lentRows, owedRows) = debts.partition { it.debt.debtType == DebtType.CREDIT }
    val shownDebts = when (tab) {
        LoanTab.LENDING -> lentRows.filter { !it.settled }
        LoanTab.BORROWING -> owedRows.filter { !it.settled }
        LoanTab.COMPLETED -> debts.filter { it.settled }
    }
    val shownLoans = when (tab) {
        LoanTab.LENDING -> emptyList()
        LoanTab.BORROWING -> active
        LoanTab.COMPLETED -> d.loans.filter { it.closed }
    }
    val newLoan = { editing = LoanEntity(name = "", kind = LoanKind.LOAN, account = d.accounts.firstOrNull()?.account?.name.orEmpty(), emi = 0.0, tenure = 0, nextDue = System.currentTimeMillis()) }
    Scaffold(
        topBar = {
            BackTopBar(str(R.string.loans_tab), onBack) {
                IconButton(onClick = { vm.postDueNow() }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, str(R.string.post_due_now)) }
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { if (tab == LoanTab.LENDING) editingDebt = newDebt(DebtType.CREDIT) else addMenu = true }) {
                    Icon(Icons.Rounded.Add, str(R.string.add))
                }
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem(text = { Text(str(R.string.add_money_lent)) }, onClick = { addMenu = false; editingDebt = newDebt(DebtType.CREDIT) })
                    DropdownMenuItem(text = { Text(str(R.string.add_money_borrowed)) }, onClick = { addMenu = false; editingDebt = newDebt(DebtType.DEBT) })
                    DropdownMenuItem(text = { Text(str(R.string.add_loan)) }, onClick = { addMenu = false; newLoan() })
                }
            }
        },
        bottomBar = {
            Surface(
                Modifier.navigationBarsPadding().padding(16.dp, 0.dp, 16.dp, 12.dp).fillMaxWidth(),
                shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) { SegmentedTabs(LoanTab.entries, tab, { it.label }, { tab = it }, Modifier.padding(6.dp)) }
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LoansHero(lentRows.sumOf { it.remaining }, owedRows.sumOf { it.remaining } + active.sumOf { it.remaining }, lentRows.count { !it.settled }, active) }
            if (shownDebts.isEmpty() && shownLoans.isEmpty()) item {
                EmptyState(
                    Icons.Rounded.RequestQuote,
                    when (tab) { LoanTab.LENDING -> str(R.string.nothing_lent); LoanTab.BORROWING -> str(R.string.nothing_borrowed); LoanTab.COMPLETED -> str(R.string.nothing_completed) },
                )
            }
            if (shownDebts.isNotEmpty()) item { SectionTitle(if (tab == LoanTab.COMPLETED) str(R.string.completed) else str(R.string.active), Modifier.padding(top = 0.dp)) }
            items(shownDebts, key = { "d" + it.debt.id }) { DebtCard(it) { onOpenDebt(it.debt.id) } }
            if (shownLoans.isNotEmpty()) item { Text(str(R.string.loans_emis), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            items(shownLoans, key = { "l" + it.id }) { LoanCard(it) { onOpenLoan(it.id) } }
        }
    }
    editingDebt?.let { DebtSheet(it, vm) { editingDebt = null } }
    editing?.let { l ->
        LoanSheet(
            l, d.accounts.map { it.account.name },
            onDismiss = { editing = null },
            onSave = { vm.saveLoan(it); editing = null },
            onDelete = { vm.deleteLoan(l); editing = null },
            names = vm.names.collectAsStateWithLifecycle().value,
        )
    }
}

/** Equal-size stat tile in the Home overview style: expressive icon, label, amount, one-line note. */
@Composable
private fun HeroTile(icon: androidx.compose.ui.graphics.vector.ImageVector, index: Int, label: String, amount: Double, note: String, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.background(c.surfaceContainerHigh, MaterialTheme.shapes.large).padding(12.dp)) {
        ExpressiveIcon(icon, index, 40.dp)
        Spacer(Modifier.height(8.dp))
        WrapText(label, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
        AmountText(amount, style = MaterialTheme.typography.titleLargeEmphasized, color = c.primary)
        WrapText(note, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

/** Net balance, lent vs monthly EMI due, and overall repayment progress across active loans/EMIs. */
@Composable
private fun LoansHero(lent: Double, owed: Double, lentOpen: Int, loans: List<LoanEntity>) {
    val p = LocalPaisaColors.current
    val c = MaterialTheme.colorScheme
    val total = loans.sumOf { it.total }
    val paid = loans.sumOf { it.paid * it.emi }
    val progress = if (total > 0) (paid / total).toFloat().coerceIn(0f, 1f) else 0f
    TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Text(str(R.string.net_balance), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(if (lent >= owed) str(R.string.owed_more_than_you_owe) else str(R.string.owe_more_than_owed), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            AmountText(lent - owed, Modifier.padding(top = 4.dp), color = if (lent >= owed) p.income else p.expense, style = MaterialTheme.typography.headlineLargeEmphasized)
            Row(Modifier.padding(top = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroTile(Icons.AutoMirrored.Rounded.TrendingUp, 0, str(R.string.lending), lent, str(R.string.x_open, lentOpen), Modifier.weight(1f).fillMaxHeight())
                HeroTile(
                    Icons.Rounded.Payments, 1, str(R.string.monthly_payments), loans.sumOf { it.emi },
                    if (loans.isEmpty()) "" else str(R.string.next_x, dateText(loans.minOf { it.nextDue })), Modifier.weight(1f).fillMaxHeight(),
                )
            }
            if (total > 0) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str(R.string.x_repaid, money(paid)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(str(R.string.x_completed, (progress * 100).roundToInt()), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                WavyProgress(progress, colors = rememberTonal(c.primary))
            }
        }
    }
}

@Composable
private fun LoanCard(l: LoanEntity, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    TonalCard(if (l.kind == LoanKind.EMI) c.tertiary else c.primary, Modifier.fillMaxWidth(), onClick) { t ->
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(t.content, RoundedCornerShape(14.dp)), Alignment.Center) {
                    Icon(l.icon, null, tint = t.container)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (l.label.isNotBlank()) Text(l.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!l.closed) DaysPill(l.daysLeft, t, left = false)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.height(IntrinsicSize.Min)) {
                Stat(str(R.string.monthly_installment), l.emi, t.content, Modifier.weight(1f), suffix = str(R.string.per_month))
                VerticalDivider(color = t.track)
                Stat(str(R.string.remaining), l.remaining, t.content, Modifier.weight(1f).padding(start = 16.dp))
            }
            if (l.tenure > 0) {
                Spacer(Modifier.height(12.dp))
                WavyProgress(l.progress, colors = t)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (l.closed) str(R.string.closed) else str(R.string.next_x, dateText(l.nextDue)),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                Text(if (l.tenure > 0) str(R.string.x_x_paid, l.paid, l.tenure) + " · ${(l.progress * 100).roundToInt()}%" else str(R.string.x_paid, l.paid), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One loan/EMI: progress, details, Mark as done (closes it), posted installments; Edit opens [LoanSheet]. */
@Composable
fun LoanDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onOpenTx: (Long) -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val l = d.loans.firstOrNull { it.id == id } ?: return
    val c = MaterialTheme.colorScheme
    val seed = if (l.kind == LoanKind.EMI) c.tertiary else c.primary
    val t = rememberTonal(seed)
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val history = remember(d.expenses, l) { installmentsOf(l, d.expenses) }

    Scaffold(
        topBar = {
            BackTopBar("", onBack) {
                IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, str(R.string.delete)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = true }, icon = { Icon(Icons.Rounded.Edit, null) }, text = { Text(str(R.string.edit)) })
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(l.title, style = MaterialTheme.typography.displaySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(l.icon, l.kindLabel, t.container, t.content)
                        if (l.closed) Pill(Icons.Rounded.Check, str(R.string.closed), c.secondaryContainer, c.onSecondaryContainer)
                        else DaysPill(l.daysLeft, t, left = true)
                    }
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(str(R.string.monthly_installment), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                        AmountText(l.emi, style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
            item {
                TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp)) {
                        Text(str(R.string.repayment_progress), style = MaterialTheme.typography.titleMedium)
                        if (l.tenure > 0) Text(str(R.string.x_of_x_installments, l.paid, l.tenure), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        WavyProgress(l.progress, colors = t)
                        Spacer(Modifier.height(16.dp))
                        Row {
                            Stat(str(R.string.paid), l.paid * l.emi, null, Modifier.weight(1f))
                            Stat(str(R.string.remaining), l.remaining, seed, Modifier.weight(1f), Alignment.CenterHorizontally)
                            Stat(str(R.string.total), l.total, null, Modifier.weight(1f), Alignment.End)
                        }
                    }
                }
            }
            val purchase = d.accounts.firstOrNull { it.account.name.equals(l.account, true) }?.let { likelyPurchase(l, it.account.id, d.expenses) }
            if (purchase != null) item {
                TintCard(Modifier.fillMaxWidth(), color = c.errorContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(str(R.string.purchase_counted_twice, purchase.name, money(purchase.amount)), style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ vm.saveLoan(l.copy(charged = true)) }) { Text(str(R.string.mark_charged)) }
                            TextButton({ onOpenTx(purchase.id) }) { Text(str(R.string.view)) }
                        }
                    }
                }
            }
            item { SectionTitle(str(R.string.details)) }
            item {
                TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column {
                        if (l.label.isNotBlank()) {
                            DetailRow(Icons.Rounded.Edit, str(R.string.bank_name), l.name)
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        }
                        DetailRow(l.icon, if (l.kind == LoanKind.EMI) str(R.string.card) else str(R.string.paid_from), l.account)
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        DetailRow(Icons.Rounded.EventAvailable, str(R.string.next_due), if (l.closed) str(R.string.closed) else dateText(l.nextDue))
                        if (l.tenure > 0) {
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                            DetailRow(Icons.Rounded.Schedule, str(R.string.tenure_months), str(R.string.x_months, l.tenure))
                        }
                        if (l.principal > 0) {
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                            DetailRow(Icons.Rounded.Payments, str(R.string.principal), money(l.principal))
                        }
                    }
                }
            }
            if (!l.closed && l.tenure > 0) item {
                FilledTonalButton(
                    { vm.saveLoan(l.copy(paid = l.tenure)) },
                    Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 56.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.mark_as_done), style = MaterialTheme.typography.titleMedium)
                }
            }
            item { SectionTitle(str(R.string.payment_history)) }
            if (history.isEmpty()) item { Text(str(R.string.no_payments_yet), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant) }
            itemsIndexed(history, key = { _, e -> e.id }) { i, e ->
                Box(Modifier.segmented(i, history.size).clickable { onOpenTx(e.id) }) {
                    TransactionRow(e, d.categoryById[e.categoryId], d.accountById[e.accountId]?.name)
                }
            }
        }
    }
    if (editing) LoanSheet(
        l, d.accounts.map { it.account.name },
        onDismiss = { editing = false },
        onSave = { vm.saveLoan(it); editing = false },
        onDelete = { editing = false; vm.deleteLoan(l); onBack() },
        names = vm.names.collectAsStateWithLifecycle().value,
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(str(R.string.delete_x, l.name)) },
        text = { Text(str(R.string.posted_installments_stay)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteLoan(l); onBack() }) { Text(str(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LoanSheet(l: LoanEntity, accounts: List<String>, onDismiss: () -> Unit, onSave: (LoanEntity) -> Unit, onDelete: () -> Unit, names: List<String> = emptyList()) {
    val isNew = l.id == 0L
    var kind by rememberSaveable { mutableStateOf(l.kind) }
    var name by rememberSaveable { mutableStateOf(l.name) }
    var label by rememberSaveable { mutableStateOf(l.label) }
    var account by rememberSaveable { mutableStateOf(l.account) }
    var emi by rememberSaveable { mutableStateOf(l.emi.plainOrEmpty()) }
    var tenure by rememberSaveable { mutableStateOf(if (l.tenure == 0) "" else l.tenure.toString()) }
    var paid by rememberSaveable { mutableStateOf(l.paid.toString()) }
    var principal by rememberSaveable { mutableStateOf(l.principal.plainOrEmpty()) }
    var next by rememberSaveable { mutableLongStateOf(l.nextDue) }
    var autoPost by rememberSaveable { mutableStateOf(l.autoPost) }
    var charged by rememberSaveable { mutableStateOf(l.charged) }
    var confirmDelete by remember { mutableStateOf(false) }
    val emiV = emi.toAmount()
    val tenureV = if (tenure.isBlank()) 0 else tenure.toIntOrNull()
    val paidV = paid.toIntOrNull()
    val ok = name.isNotBlank() && account.isNotBlank() && emiV != null && emiV > 0 && tenureV != null && paidV != null && paidV >= 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isNew) str(R.string.new_loan_emi) else str(R.string.edit_loan), style = MaterialTheme.typography.titleLarge)
            SegmentedTabs(LoanKind.entries, kind, { if (it == LoanKind.EMI) str(R.string.card_emi) else str(R.string.loan) }, { kind = it })
            OutlinedTextField(label, { label = it }, label = { Text(str(R.string.loan_name)) }, placeholder = { Text(str(R.string.loan_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            com.paisaplus.compose.ui.components.NameField(name, { name = it }, str(R.string.bank_name), names)
            Text(if (kind == LoanKind.EMI) str(R.string.card) else str(R.string.paid_from), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (account.isBlank() || accounts.any { it.equals(account, true) }) accounts else accounts + account).forEach { a ->
                    FilterChip(selected = a.equals(account, true), onClick = { account = a }, label = { Text(a) })
                }
            }
            AmountField(emi, { emi = it }, str(R.string.monthly_installment))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tenure, { tenure = it.filter(Char::isDigit) }, label = { Text(str(R.string.tenure_months)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(paid, { paid = it.filter(Char::isDigit) }, label = { Text(str(R.string.paid)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            AmountField(principal, { principal = it }, str(R.string.principal_optional))
            DateField(str(R.string.next_due), next, { next = it }, Modifier.fillMaxWidth())
            if (kind == LoanKind.EMI) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.purchase_on_card), style = MaterialTheme.typography.titleSmall)
                    Text(str(R.string.purchase_on_card_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(charged, { charged = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str(R.string.auto_add_installments), style = MaterialTheme.typography.titleSmall)
                    Text(str(R.string.auto_post_on_due_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(autoPost, { autoPost = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) OutlinedButton(onClick = { confirmDelete = true }) { Text(str(R.string.delete)) }
                Button(
                    onClick = {
                        onSave(
                            l.copy(
                                name = name.trim(), label = label.trim(), kind = kind, account = account, emi = emiV!!, tenure = tenureV!!, paid = paidV!!,
                                principal = principal.toAmount() ?: 0.0, nextDue = next, autoPost = autoPost, charged = charged && kind == LoanKind.EMI,
                                dueDay = if (isNew || next != l.nextDue) dayOfMonth(next) else l.dueDay,
                            ),
                        )
                    },
                    enabled = ok,
                    modifier = Modifier.weight(1f),
                ) { Text(str(R.string.save)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.delete_x, l.name)) },
            text = { Text(str(R.string.posted_installments_stay)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(str(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
        )
    }
}
