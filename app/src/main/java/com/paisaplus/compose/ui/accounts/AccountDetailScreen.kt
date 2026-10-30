package com.paisaplus.compose.ui.accounts

import androidx.compose.runtime.saveable.rememberSaveable
import com.paisaplus.compose.ui.components.GroupHeroCard
import com.paisaplus.compose.ui.components.NetworkBadge
import com.paisaplus.compose.ui.components.BadgeText
import com.paisaplus.compose.data.cardNetwork
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.paisaplus.compose.ui.components.last4Label
import com.paisaplus.compose.ui.AppData
import com.paisaplus.compose.data.transferIds
import com.paisaplus.compose.data.groupByDay
import com.paisaplus.compose.ui.transactions.DayHeader
import com.paisaplus.compose.data.isGroupPayment
import com.paisaplus.compose.data.groupTransactions
import com.paisaplus.compose.data.cardGroup
import com.paisaplus.compose.data.CardGroup
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.ui.components.segmented
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.ui.theme.LocalMonthStart
import com.paisaplus.compose.data.currentPeriod
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.monthTotals
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.AccountHeroCard
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.PillButton
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TransactionRow
import com.paisaplus.compose.ui.components.WavyProgress
import com.paisaplus.compose.ui.label
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.moneyOrHidden
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import java.time.YearMonth

/** One account or card: the card, actions, details, this month, and its transactions. */
@Composable
fun AccountDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onAdd: (accountId: Long) -> Unit, onOpenTx: (Long) -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val row = d.accounts.firstOrNull { it.account.id == id }
    // Deleted from the edit sheet (or by a restore): leave.
    LaunchedEffect(row == null, d.loading) { if (row == null && !d.loading) onBack() }
    if (row == null) return
    val a = row.account
    val group = remember(d.accounts, d.loans, id) { cardGroup(a, d.accounts.map { it.account }, d.loans) }
    if (group != null) { GroupDetail(vm, d, group, hidden, onBack, onAdd, onOpenTx); return }
    val txs = remember(d.expenses, id) { d.expenses.filter { it.accountId == id } }
    val groups = remember(txs) { groupByDay(txs) }
    val start = LocalMonthStart.current
    val month = remember(txs, start) { monthTotals(txs, currentPeriod(start), start = start) }
    val p = LocalPaisaColors.current
    val c = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            BackTopBar(a.name, onBack) {
                IconButton({ editing = true }) { Icon(Icons.Rounded.Edit, str(R.string.edit_x, a.name)) }
            }
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 24.dp)) {
            item { AccountHeroCard(row, hidden) }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(str(R.string.add_transaction), { onAdd(id) }, Modifier.weight(1f), icon = Icons.Rounded.Add)
                    OutlinedButton({ editing = true }, Modifier.heightIn(min = 56.dp)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.padding(end = 8.dp)); Text(str(R.string.edit))
                    }
                }
            }
            item {
                TintCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (a.cardType == CardType.CREDIT) {
                            Line(str(R.string.balance_owed_charges_minus_payments), moneyOrHidden(row.owed, hidden))
                            Line(str(R.string.emi_remaining_incl_interest), moneyOrHidden(row.emiRemaining, hidden))
                            Line(str(R.string.total_outstanding), moneyOrHidden(row.totalOutstanding, hidden))
                            row.nextBill?.let {
                                Line(str(R.string.next_bill_est_on_x, it.date.format(dayFmt)), moneyOrHidden(it.total, hidden))
                                Line(str(R.string.charges_emi), "${moneyOrHidden(it.charges, hidden)} + ${moneyOrHidden(it.emi, hidden)}")
                            }
                        } else Line(str(R.string.balance), moneyOrHidden(a.amount, hidden))
                        val u = row.credit
                        if (u != null) {
                            Line(str(R.string.credit_limit), money(u.limit))
                            if (u.shared.isNotEmpty()) {
                                Line(str(R.string.shared_with), u.shared.joinToString { it.name })
                                Line(str(R.string.group_total_outstanding), moneyOrHidden(u.used, hidden))
                            }
                            Line(str(R.string.available), moneyOrHidden(u.available, hidden))
                            WavyProgress((u.used / u.limit).toFloat())
                        }
                        Line(str(R.string.type), a.cardType.label)
                        if (a.bankName.isNotBlank()) Line(str(R.string.bank), a.bankName)
                        if (a.number.isNotBlank()) Line(str(R.string.number), "•••• ${a.number.takeLast(4)}")
                        a.limitGroup?.let { Line(str(R.string.limit_group), it) }
                    }
                }
            }
            item { SectionTitle(str(R.string.this_month)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(str(R.string.income), month.income, p.income, hidden, Modifier.weight(1f))
                    Stat(str(R.string.expense), month.expense, p.expense, hidden, Modifier.weight(1f))
                }
            }
            item { SectionTitle(str(R.string.transactions)) }
            if (txs.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, str(R.string.no_transactions_yet)) }
            groups.forEach { (day, list) ->
                stickyHeader(key = "h$day") { DayHeader(day, list, inset = 4.dp) }
                itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                    Box(Modifier.segmented(i, list.size).clickable { onOpenTx(e.id) }) {
                        TransactionRow(e, d.categoryById[e.categoryId], null)
                    }
                }
            }
        }
    }
    if (editing) AccountSheet(
        a,
        txCount = txs.size,
        onDismiss = { editing = false },
        onSave = { vm.saveAccount(it); editing = false },
        onDelete = { vm.deleteAccount(a); editing = false },
    )
}

/**
 * A limitGroup: the combined card, then chips "All cards" | each card filtering one merged list, newest first. Rows keep their own account: open / edit / delete act on it.
 * The list shows every transaction ("Exclude transfers" only affects totals).
 */
@Composable
private fun GroupDetail(vm: AppViewModel, d: AppData, g: CardGroup, hidden: Boolean, onBack: () -> Unit, onAdd: (Long) -> Unit, onOpenTx: (Long) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) } // null = All cards
    val card = g.members.firstOrNull { it.id == selected }
    val transfers = remember(d.categories) { transferIds(d.categories) }
    val txs = remember(d.expenses, g, card) { if (card == null) groupTransactions(d.expenses, g) else d.expenses.filter { it.accountId == card.id } }
    val groups = remember(txs) { groupByDay(txs) }
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    Scaffold(
        topBar = {
            BackTopBar(g.name, onBack) {
                IconButton({ editing = card ?: g.members.first() }) { Icon(Icons.Rounded.Edit, str(R.string.edit_x, (card ?: g.members.first()).name)) }
            }
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 24.dp)) {
            item { GroupHeroCard(g, hidden) }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(card == null, { selected = null }, { Text(str(R.string.all_cards)) })
                    g.members.forEach { m ->
                        FilterChip(card?.id == m.id, { selected = m.id }, { Text(m.last4Label()) })
                    }
                }
            }
            item { PillButton(str(R.string.add_transaction), { onAdd((card ?: g.members.first()).id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) }
            item { SectionTitle(str(R.string.transactions)) }
            if (txs.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, str(R.string.no_transactions_yet)) }
            groups.forEach { (day, list) ->
                stickyHeader(key = "h$day") { DayHeader(day, list, inset = 4.dp) }
                itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                    // "All cards": the card's network logo; last 4 only when its name names no network.
                    val acc = d.accountById[e.accountId]
                    val network = acc?.let(::cardNetwork)
                    val badge: (@Composable () -> Unit)? = when {
                        isGroupPayment(e, g, transfers) -> { { BadgeText(str(R.string.group_payment)) } }
                        card != null || acc == null -> null
                        network != null -> { { NetworkBadge(network) } }
                        else -> { { BadgeText(acc.last4Label()) } }
                    }
                    Box(Modifier.segmented(i, list.size).clickable { onOpenTx(e.id) }) {
                        TransactionRow(e, d.categoryById[e.categoryId], null, badge = badge)
                    }
                }
            }
        }
    }
    editing?.let { acc ->
        AccountSheet(
            acc,
            txCount = d.expenses.count { it.accountId == acc.id },
            onDismiss = { editing = null },
            onSave = { vm.saveAccount(it); editing = null },
            onDelete = { vm.deleteAccount(acc); editing = null },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Stat(label: String, v: Double, color: Color, hidden: Boolean, modifier: Modifier) {
    TintCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            AmountText(v, style = MaterialTheme.typography.titleLarge, color = color, hidden = hidden)
        }
    }
}
