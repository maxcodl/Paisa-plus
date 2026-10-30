package com.paisaplus.compose.ui.more

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.theme.LocalMonthStart
import com.paisaplus.compose.data.currentPeriod
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.paisaplus.compose.ui.components.rememberSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.paisaplus.compose.Routes
import com.paisaplus.compose.go
import com.paisaplus.compose.data.BudgetRow
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.data.budgets
import com.paisaplus.compose.ui.AmountField
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.IconBadge
import com.paisaplus.compose.ui.components.ExpressiveIcon
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.paisaIcon
import com.paisaplus.compose.ui.plainOrEmpty
import com.paisaplus.compose.ui.toAmount

private data class Entry(val title: String, val icon: ImageVector, val route: String)

private val entries = listOf(
    Entry(str(R.string.transactions), Icons.AutoMirrored.Rounded.ReceiptLong, Routes.TRANSACTIONS),
    Entry(str(R.string.categories), Icons.Rounded.Category, Routes.CATEGORIES),
    Entry(str(R.string.budgets), Icons.Rounded.Savings, Routes.BUDGETS),
    Entry(str(R.string.loans_emis), Icons.Rounded.RequestQuote, Routes.LOANS),
    Entry(str(R.string.debts), Icons.Rounded.Handshake, Routes.DEBTS),
    Entry(str(R.string.recurring), Icons.Rounded.EventRepeat, Routes.RECURRING),
    Entry(str(R.string.backup_restore), Icons.Rounded.Backup, Routes.BACKUP),
    Entry(str(R.string.settings), Icons.Rounded.Settings, Routes.SETTINGS),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(nav: NavHostController, outer: PaddingValues, onBack: (() -> Unit)? = null) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(str(R.string.more), onBack) },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, outer.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries) { e ->
                TintCard(Modifier.fillMaxWidth(), onClick = { nav.go(e.route) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveIcon(e.icon, entries.indexOf(e))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BudgetsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val start = LocalMonthStart.current
    val summary = remember(d.categories, d.expenses, start) { budgets(d.categories, d.expenses, currentPeriod(start), start = start) }
    val others = remember(d.categories) { d.categories.filter { !it.isBudget } }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val c = MaterialTheme.colorScheme
    Scaffold(topBar = { BackTopBar(str(R.string.budgets), onBack) }) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The ₹0 of ₹0 summary says nothing until a budget exists.
            if (summary.rows.isNotEmpty()) item {
                TintCard(Modifier.fillMaxWidth(), color = c.primaryContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Text(str(R.string.this_month), style = MaterialTheme.typography.labelLarge)
                        Text(str(R.string.x_of_x, money(summary.spent), money(summary.budget)), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Spacer(Modifier.height(10.dp))
                        com.paisaplus.compose.ui.components.WavyProgress(
                            if (summary.budget > 0) (summary.spent / summary.budget).toFloat() else 0f,
                            color = if (summary.spent > summary.budget) c.error else c.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        val left = summary.budget - summary.spent
                        Text(if (left < 0) str(R.string.over_by_x, money(-left)) else str(R.string.x_left, money(left)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (summary.rows.isEmpty()) item { EmptyState(Icons.Rounded.Savings, str(R.string.no_budgets_yet_tap_set)) }
            items(summary.rows, key = { it.category.id }) { BudgetCard(it) { editing = it.category } }
            if (others.isNotEmpty()) {
                item { SectionTitle(str(R.string.without_budget)) }
                items(others, key = { "o" + it.id }) { cat ->
                    TintCard(Modifier.fillMaxWidth(), onClick = { editing = cat }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(paisaIcon(cat.icon), Color(cat.color), 36.dp, autoHue = true)
                            Spacer(Modifier.width(12.dp))
                            Text(cat.name, Modifier.weight(1f))
                            Text(str(R.string.set_budget), color = c.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    editing?.let { cat -> BudgetSheet(cat, onDismiss = { editing = null }) { vm.saveCategory(it); editing = null } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BudgetCard(row: BudgetRow, onClick: () -> Unit) {
    val cat = row.category
    val color = Color(cat.color)
    val c = MaterialTheme.colorScheme
    TintCard(Modifier.fillMaxWidth(), color = if (row.over) c.errorContainer else color.copy(alpha = 0.12f), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(paisaIcon(cat.icon), color, autoHue = true)
                Spacer(Modifier.width(14.dp))
                Text(cat.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${money(row.spent)} / ${money(cat.budget)}", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            com.paisaplus.compose.ui.components.WavyProgress(
                if (cat.budget > 0) (row.spent / cat.budget).toFloat() else 1f,
                color = if (row.over) c.error else color,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (row.over) str(R.string.over_budget_by_x, money(-row.left)) else str(R.string.x_left, money(row.left)),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.over) c.onErrorContainer else c.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(cat: CategoryEntity, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(cat.budget.plainOrEmpty()) }
    val value = amount.toAmount()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.budget_x, cat.name), style = MaterialTheme.typography.titleLarge)
            AmountField(amount, { amount = it }, str(R.string.monthly_budget), isError = amount.isNotBlank() && value == null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cat.isBudget) OutlinedButton(onClick = { onSave(cat.copy(isBudget = false)) }) { Text(str(R.string.remove_budget)) }
                Button(
                    onClick = { onSave(cat.copy(isBudget = true, budget = value!!)) },
                    enabled = value != null && value > 0,
                    modifier = Modifier.weight(1f),
                ) { Text(str(R.string.save)) }
            }
        }
    }
}
