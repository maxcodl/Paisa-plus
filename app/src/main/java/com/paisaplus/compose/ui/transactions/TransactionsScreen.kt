package com.paisaplus.compose.ui.transactions

import com.paisaplus.compose.openPayee
import com.paisaplus.compose.ui.components.segmented
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.ui.components.EmptyState
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ModalBottomSheet
import com.paisaplus.compose.data.DeletedExpenseEntity
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.RoundedCornerShape
import com.paisaplus.compose.ui.theme.LocalGlass
import com.paisaplus.compose.ui.theme.glass
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.data.date
import com.paisaplus.compose.data.groupByDay
import com.paisaplus.compose.data.signed
import com.paisaplus.compose.editTransaction
import com.paisaplus.compose.ui.AppData
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.TransactionRow
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.money
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class Filter(val label: String) { ALL(str(R.string.all)), EXPENSE(str(R.string.expenses)), INCOME(str(R.string.income)) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(vm: AppViewModel, nav: NavHostController, outer: PaddingValues, onBack: (() -> Unit)? = null) {
    val d by vm.data.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var showDeleted by rememberSaveable { mutableStateOf(false) }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) } // "" = all time
    val month = monthKey.takeIf { it.isNotEmpty() }?.let(YearMonth::parse)
    val zone = remember { ZoneId.systemDefault() }
    val months = remember(d.expenses) { d.expenses.map { YearMonth.from(it.date(zone)) }.distinct().sortedDescending() }
    val groups = remember(d.expenses, filter, month) {
        groupByDay(
            d.expenses.filter { e ->
                (month == null || YearMonth.from(e.date(zone)) == month) && when (filter) {
                    Filter.ALL -> true
                    Filter.EXPENSE -> e.type == TransactionType.EXPENSE
                    Filter.INCOME -> e.type == TransactionType.INCOME
                }
            },
        )
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            com.paisaplus.compose.ui.components.BackTopBar(str(R.string.transactions), onBack) {
                IconButton(onClick = { showDeleted = true }) { Icon(Icons.Rounded.History, str(R.string.recently_deleted)) }
            }
        },
        floatingActionButton = { if (d.accounts.isNotEmpty()) Box(Modifier.padding(bottom = outer.calculateBottomPadding())) { AddFab { nav.editTransaction(type = it) } } },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = outer.calculateBottomPadding() + 88.dp)) {
            item { MonthPicker(month, months) { monthKey = it?.toString().orEmpty() } }
            item {
                SegmentedTabs(Filter.entries, filter, { it.label }, { filter = it }, Modifier.padding(16.dp, 4.dp, 16.dp, 8.dp))
            }
            if (groups.isEmpty()) item {
                EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, str(R.string.no_transactions_yet))
            }
            groups.forEach { (day, list) ->
                stickyHeader(key = "h$day") { DayHeader(day, list) }
                // Expressive segmented list: one rounded group per day, 2dp seams between rows.
                itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                    TxRow(e, d, vm, onOpen = { nav.openPayee(e) }, Modifier.animateItem().padding(horizontal = 16.dp).segmented(i, list.size))
                }
            }
        }
    }
    if (showDeleted) RecentlyDeletedSheet(vm, d) { showDeleted = false }
}

/** Last deleted transactions with Restore (balance re-applied). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentlyDeletedSheet(vm: AppViewModel, d: AppData, onDismiss: () -> Unit) {
    val items by vm.recentlyDeleted.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(str(R.string.recently_deleted), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            str(R.string.last_x_deleted, DeletedExpenseEntity.KEEP),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (items.isEmpty()) EmptyState(Icons.Rounded.Delete, str(R.string.nothing_recently_deleted))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items, key = { it.id }) { del ->
                val e = del.toExpense()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TransactionRow(e, d.categoryById[e.categoryId], d.accountById[e.accountId]?.name) }
                    TextButton(onClick = { vm.restore(del.id) }, Modifier.padding(end = 8.dp)) { Text(str(R.string.restore)) }
                }
            }
        }
    }
}

@Composable
private fun MonthPicker(month: YearMonth?, months: List<YearMonth>, onChange: (YearMonth?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((month ?: YearMonth.now()).minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, str(R.string.previous_month))
        }
        Box(Modifier.weight(1f), Alignment.Center) {
            TextButton(onClick = { menu = true }) {
                Text(month?.format(monthFmt) ?: str(R.string.all_time), style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp)) {
                DropdownMenuItem(text = { Text(str(R.string.all_time)) }, onClick = { menu = false; onChange(null) })
                months.forEach { m ->
                    DropdownMenuItem(text = { Text(m.format(monthFmt)) }, onClick = { menu = false; onChange(m) })
                }
            }
        }
        IconButton(onClick = { onChange((month ?: YearMonth.now()).plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, str(R.string.next_month))
        }
    }
}

private val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")

/** [inset]: start/end padding; lists that already pad their content pass less. */
@Composable
fun DayHeader(day: LocalDate, list: List<ExpenseEntity>, inset: androidx.compose.ui.unit.Dp = 20.dp) {
    val c = MaterialTheme.colorScheme
    // Sticky: rows scroll under it, so it needs a real fill (glass when on; surface is transparent then).
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth()
            .then(if (glass != null) Modifier.glass(glass, RoundedCornerShape(0.dp), c.surfaceContainer, alpha = 0.8f, lens = 0.dp) else Modifier.background(c.surface))
            .padding(horizontal = inset, vertical = 8.dp),
    ) {
        val today = LocalDate.now()
        Text(
            when (day) { today -> str(R.string.today); today.minusDays(1) -> str(R.string.yesterday); else -> day.format(dayFmt) },
            style = MaterialTheme.typography.labelLarge,
            color = c.primary,
            modifier = Modifier.weight(1f),
        )
        Text(money(list.sumOf { it.signed }), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TxRow(e: ExpenseEntity, d: AppData, vm: AppViewModel, onOpen: () -> Unit, modifier: Modifier) {
    // No swipe-to-delete (too easy to trigger by accident); delete via long-press menu, with Undo.
    var menu by remember { mutableStateOf(false) }
    Box(modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
        TransactionRow(e, d.categoryById[e.categoryId], d.accountById[e.accountId]?.name)
        DropdownMenu(menu, { menu = false }, Modifier.align(Alignment.CenterEnd)) {
            DropdownMenuItem(
                text = { Text(str(R.string.delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; vm.deleteExpense(e) },
            )
        }
    }
}

/** Expressive FAB menu: add expense / add income. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddFab(onAdd: (TransactionType) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    FloatingActionButtonMenu(
        expanded = open,
        button = {
            ToggleFloatingActionButton(checked = open, onCheckedChange = { open = it }) {
                Icon(if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add, str(R.string.add_transaction))
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { open = false; onAdd(TransactionType.INCOME) },
            text = { Text(str(R.string.income)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.TrendingUp, null) },
        )
        FloatingActionButtonMenuItem(
            onClick = { open = false; onAdd(TransactionType.EXPENSE) },
            text = { Text(str(R.string.expense)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.TrendingDown, null) },
        )
    }
}
