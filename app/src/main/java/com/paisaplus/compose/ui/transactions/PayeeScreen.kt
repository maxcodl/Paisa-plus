package com.paisaplus.compose.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.data.mergeCount
import com.paisaplus.compose.data.payeeTransactions
import com.paisaplus.compose.plural
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TransactionRow
import com.paisaplus.compose.ui.components.segmented
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * Everyone you paid or got paid by is a payee: all transactions with [txId]'s name, by month, newest first.
 * Opened from any transaction; a row opens the usual edit screen; Rename renames (or merges) the whole payee.
 */
@Composable
fun PayeeScreen(vm: AppViewModel, txId: Long, onBack: () -> Unit, onOpenTx: (Long) -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    // Follows the transaction, so a rename keeps this page on the renamed payee.
    var name by rememberSaveable { mutableStateOf("") }
    d.expenses.firstOrNull { it.id == txId }?.name?.let { if (it != name) name = it }
    var renaming by remember { mutableStateOf(false) }
    val txs = remember(d.expenses, name) { payeeTransactions(d.expenses, name) }
    LaunchedEffect(txs.isEmpty(), d.loading) { if (txs.isEmpty() && !d.loading && name.isNotEmpty()) onBack() }
    val zone = ZoneId.systemDefault()
    val months = remember(txs) { txs.groupBy { YearMonth.from(Instant.ofEpochMilli(it.time).atZone(zone)) }.toList() }
    val p = LocalPaisaColors.current

    Scaffold(
        topBar = { BackTopBar(name.trim(), onBack) { IconButton({ renaming = true }) { Icon(Icons.Rounded.Edit, str(R.string.rename)) } } },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 24.dp)) {
            item {
                TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) {
                                Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name.trim(), style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2)
                                Text(plural(R.plurals.transactions_count, txs.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Total(str(R.string.sent), txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }, p.expense, Modifier.weight(1f))
                            Total(str(R.string.received), txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }, p.income, Modifier.weight(1f))
                        }
                        TextButton({ renaming = true }) {
                            Icon(Icons.Rounded.Edit, null, Modifier.padding(end = 8.dp)); Text(str(R.string.rename_or_merge))
                        }
                    }
                }
            }
            months.forEach { (month, rows) ->
                item(key = "m$month") { SectionTitle(month.format(monthFmt)) }
                itemsIndexed(rows, key = { _, e -> e.id }) { i, e ->
                    Box(Modifier.segmented(i, rows.size).clickable { onOpenTx(e.id) }) {
                        TransactionRow(e, d.categoryById[e.categoryId], d.accountById[e.accountId]?.name)
                    }
                }
            }
        }
    }

    if (renaming) {
        var to by rememberSaveable { mutableStateOf(name.trim()) }
        val merge = mergeCount(d.expenses, name, to)
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(str(R.string.rename_or_merge)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(to, { to = it }, label = { Text(str(R.string.name)) }, singleLine = true)
                    Text(
                        if (merge > 0) str(R.string.merge_into_x, to.trim(), plural(R.plurals.transactions_count, merge))
                        else str(R.string.renames_x, plural(R.plurals.transactions_count, txs.size)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (merge > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton({ vm.renamePayee(name, to.trim(), merge > 0); renaming = false }, enabled = to.isNotBlank() && to.trim() != name.trim()) {
                    Text(str(if (merge > 0) R.string.merge else R.string.rename))
                }
            },
            dismissButton = { TextButton({ renaming = false }) { Text(str(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Total(label: String, v: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(v, style = MaterialTheme.typography.titleLargeEmphasized, color = color)
    }
}
