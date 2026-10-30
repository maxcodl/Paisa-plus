package com.paisaplus.compose.ui.more

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.ui.components.EmptyState
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.paisaplus.compose.data.LoanEntity
import com.paisaplus.compose.data.LoanKind
import com.paisaplus.compose.data.dayOfMonth
import com.paisaplus.compose.ui.AmountField
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.DateField
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.plainOrEmpty
import com.paisaplus.compose.ui.toAmount
import java.time.Instant
import java.time.ZoneId

@Composable
fun LoansScreen(vm: AppViewModel, onBack: () -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<LoanEntity?>(null) }
    val (closed, active) = remember(d.loans) { d.loans.partition { it.closed } }
    Scaffold(
        topBar = {
            BackTopBar(str(R.string.loans_emis), onBack) {
                IconButton(onClick = { vm.postDueNow() }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, str(R.string.post_due_now)) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = LoanEntity(name = "", kind = LoanKind.LOAN, account = d.accounts.firstOrNull()?.account?.name.orEmpty(), emi = 0.0, tenure = 0, nextDue = System.currentTimeMillis())
            }) { Icon(Icons.Rounded.Add, str(R.string.add_loan)) }
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                TintCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Text(str(R.string.monthly_installments), style = MaterialTheme.typography.labelLarge)
                        Text(money(active.sumOf { it.emi }), style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(str(R.string.x_active_x_remaining, active.size, money(active.sumOf { it.remaining })), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (d.loans.isEmpty()) item { EmptyState(Icons.Rounded.RequestQuote, str(R.string.no_loans_or_emis_yet)) }
            items(active, key = { it.id }) { LoanCard(it) { editing = it } }
            if (closed.isNotEmpty()) {
                item { SectionTitle(str(R.string.closed)) }
                items(closed, key = { it.id }) { LoanCard(it) { editing = it } }
            }
        }
    }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoanCard(l: LoanEntity, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val next = Instant.ofEpochMilli(l.nextDue).atZone(ZoneId.systemDefault()).format(dayFmt)
    TintCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (l.kind == LoanKind.EMI) str(R.string.card_emi) else str(R.string.loan)) + " · ${l.account}" + if (!l.autoPost) str(R.string.from_imports) else "",
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1,
                    )
                }
                Text(str(R.string.x_mo, money(l.emi)), style = MaterialTheme.typography.titleSmall)
            }
            if (l.tenure > 0) {
                Spacer(Modifier.height(12.dp))
                com.paisaplus.compose.ui.components.WavyProgress(l.paid.toFloat() / l.tenure)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(
                    if (l.tenure > 0) str(R.string.x_x_paid, l.paid, l.tenure) else str(R.string.x_paid, l.paid),
                    if (l.closed) str(R.string.closed) else str(R.string.next_x, next),
                    if (l.tenure > 0) str(R.string.x_left, money(l.remaining)) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LoanSheet(l: LoanEntity, accounts: List<String>, onDismiss: () -> Unit, onSave: (LoanEntity) -> Unit, onDelete: () -> Unit, names: List<String> = emptyList()) {
    val isNew = l.id == 0L
    var kind by rememberSaveable { mutableStateOf(l.kind) }
    var name by rememberSaveable { mutableStateOf(l.name) }
    var account by rememberSaveable { mutableStateOf(l.account) }
    var emi by rememberSaveable { mutableStateOf(l.emi.plainOrEmpty()) }
    var tenure by rememberSaveable { mutableStateOf(if (l.tenure == 0) "" else l.tenure.toString()) }
    var paid by rememberSaveable { mutableStateOf(l.paid.toString()) }
    var principal by rememberSaveable { mutableStateOf(l.principal.plainOrEmpty()) }
    var next by rememberSaveable { mutableLongStateOf(l.nextDue) }
    var autoPost by rememberSaveable { mutableStateOf(l.autoPost) }
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
            com.paisaplus.compose.ui.components.NameField(name, { name = it }, str(R.string.name), names)
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
                                name = name.trim(), kind = kind, account = account, emi = emiV!!, tenure = tenureV!!, paid = paidV!!,
                                principal = principal.toAmount() ?: 0.0, nextDue = next, autoPost = autoPost,
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
