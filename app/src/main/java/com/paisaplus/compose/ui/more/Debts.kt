package com.paisaplus.compose.ui.more

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.ui.theme.LocalPaisaColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.DebtEntity
import com.paisaplus.compose.data.DebtType
import com.paisaplus.compose.ui.AmountField
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.DateField
import com.paisaplus.compose.ui.DebtRow
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.PillButton
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TonalCard
import com.paisaplus.compose.ui.components.WavyProgress
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.plainOrEmpty
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import com.paisaplus.compose.ui.toAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val DebtType.tab get() = if (this == DebtType.CREDIT) str(R.string.lending) else str(R.string.borrowing)

/** debits: money lent (CREDIT) and borrowed (DEBT), with partial payments. */
@Composable
fun DebtsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val rows by vm.debts.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(DebtType.CREDIT) }
    var editing by remember { mutableStateOf<DebtEntity?>(null) }
    val p = LocalPaisaColors.current
    val lent = rows.filter { it.debt.debtType == DebtType.CREDIT }.sumOf { it.remaining }
    val owed = rows.filter { it.debt.debtType == DebtType.DEBT }.sumOf { it.remaining }
    val shown = rows.filter { it.debt.debtType == type }
    val (open, settled) = shown.partition { !it.settled }

    Scaffold(
        topBar = { BackTopBar(str(R.string.debts), onBack) },
        floatingActionButton = {
            FloatingActionButton({ editing = newDebt(type) }) { Icon(Icons.Rounded.Add, if (type == DebtType.CREDIT) str(R.string.add_money_lent) else str(R.string.add_money_borrowed)) }
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TintCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                            Total(str(R.string.owed_to_you), lent, p.income, Modifier.weight(1f))
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Total(str(R.string.you_owe), owed, p.expense, Modifier.weight(1f))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(str(R.string.net), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            AmountText(lent - owed, color = if (lent >= owed) p.income else p.expense, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            item { SegmentedTabs(DebtType.entries.reversed(), type, { it.tab }, { type = it }) }
            if (shown.isEmpty()) item {
                EmptyState(
                    Icons.Rounded.Handshake,
                    if (type == DebtType.CREDIT) str(R.string.nothing_lent) else str(R.string.nothing_borrowed),
                )
            }
            if (open.isNotEmpty()) item { SectionTitle(str(R.string.active)) }
            items(open, key = { it.debt.id }) { DebtCard(it) { editing = it.debt } }
            if (settled.isNotEmpty()) item { SectionTitle(str(R.string.settled)) }
            items(settled, key = { it.debt.id }) { DebtCard(it) { editing = it.debt } }
        }
    }
    editing?.let { d -> DebtSheet(d, rows.firstOrNull { it.debt.id == d.id }, vm) { editing = null } }
}

private fun newDebt(type: DebtType): DebtEntity {
    val now = System.currentTimeMillis()
    return DebtEntity(name = "", description = "", amount = 0.0, dateTime = now, expiryDateTime = now + 30L * 86_400_000, debtType = type)
}

@Composable
private fun Total(label: String, v: Double, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(v, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
private fun DebtCard(r: DebtRow, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val due = Instant.ofEpochMilli(r.debt.expiryDateTime).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(LocalDate.now(zone), due)
    val paisa = LocalPaisaColors.current
    val seed = if (r.debt.debtType == DebtType.CREDIT) paisa.income else paisa.expense
    TonalCard(seed, Modifier.fillMaxWidth(), onClick) { t ->
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.debt.name.ifBlank { str(R.string.unnamed) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.debt.description.isNotBlank()) Text(r.debt.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!r.settled) {
                    Surface(color = if (days < 0) MaterialTheme.colorScheme.errorContainer else t.track, shape = MaterialTheme.shapes.small) {
                        Text(
                            when { days < 0 -> str(R.string.x_days_late, -days); days == 0L -> str(R.string.due_today); else -> str(R.string.x_days, days) },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (days < 0) MaterialTheme.colorScheme.onErrorContainer else t.content,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(str(R.string.x_of_x_paid, money(r.paid), money(r.debt.amount)), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            WavyProgress(if (r.debt.amount > 0) (r.paid / r.debt.amount).toFloat() else 0f, colors = t)
            Spacer(Modifier.height(8.dp))
            Text(if (r.settled) str(R.string.settled) else str(R.string.due_x, due.format(dayFmt)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtSheet(d: DebtEntity, row: DebtRow?, vm: AppViewModel, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(d.name) }
    var desc by rememberSaveable { mutableStateOf(d.description) }
    var amount by rememberSaveable { mutableStateOf(d.amount.plainOrEmpty()) }
    var start by rememberSaveable { mutableLongStateOf(d.dateTime) }
    var due by rememberSaveable { mutableLongStateOf(d.expiryDateTime) }
    var type by rememberSaveable { mutableStateOf(d.debtType) }
    var pay by rememberSaveable { mutableStateOf("") }
    var payAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = amount.toAmount()
    val valid = name.isNotBlank() && value != null && value > 0

    ModalBottomSheet(onClose, sheetState = rememberSheetState()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (d.id == 0L) str(R.string.new_debt) else str(R.string.edit_debt), style = MaterialTheme.typography.headlineSmall)
            SegmentedTabs(DebtType.entries.reversed(), type, { if (it == DebtType.CREDIT) str(R.string.i_lent) else str(R.string.i_borrowed) }, { type = it })
            OutlinedTextField(name, { name = it }, label = { Text(if (type == DebtType.CREDIT) str(R.string.who_owes_you) else str(R.string.who_you_owe)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text(str(R.string.note_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            AmountField(amount, { amount = it }, str(R.string.amount), isError = amount.isNotEmpty() && (value == null || value <= 0))
            DateField(str(R.string.started), start, { start = it }, Modifier.fillMaxWidth())
            DateField(str(R.string.due), due, { due = it }, Modifier.fillMaxWidth())
            PillButton(str(R.string.save), {
                vm.saveDebt(d.copy(name = name.trim(), description = desc.trim(), amount = value!!, dateTime = start, expiryDateTime = due, debtType = type))
                onClose()
            }, Modifier.fillMaxWidth(), enabled = valid)

            if (row != null) {
                SectionTitle(str(R.string.payments))
                Text(str(R.string.x_paid_x_left, money(row.paid), money(row.remaining)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField(pay, { pay = it }, str(R.string.payment), Modifier.weight(1f))
                    Button(
                        { pay.toAmount()?.let { vm.addPayment(d.id, it, payAt) }; pay = "" },
                        Modifier.heightIn(min = 56.dp),
                        enabled = (pay.toAmount() ?: 0.0) > 0,
                    ) { Text(str(R.string.add)) }
                }
                DateField(str(R.string.paid_on), payAt, { payAt = it }, Modifier.fillMaxWidth())
                row.payments.forEach { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Instant.ofEpochMilli(t.time).atZone(ZoneId.systemDefault()).format(dayFmt), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        AmountText(t.amount)
                        IconButton({ vm.deletePayment(t) }) { Icon(Icons.Rounded.Close, str(R.string.delete_payment_of_x, money(t.amount))) }
                    }
                }
                TextButton({ confirmDelete = true }, Modifier.heightIn(min = 48.dp)) { Text(str(R.string.delete_debt), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.width(1.dp).height(16.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(str(R.string.delete_x, d.name)) },
        text = { Text(str(R.string.removes_its_payments_too)) },
        confirmButton = { TextButton({ vm.deleteDebt(d); confirmDelete = false; onClose() }) { Text(str(R.string.delete)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(str(R.string.cancel)) } },
    )
}
