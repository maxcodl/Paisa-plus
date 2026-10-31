@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.paisaplus.compose.ui.more

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.paisaplus.compose.data.DebtEntity
import com.paisaplus.compose.data.DebtType
import com.paisaplus.compose.ui.AmountField
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.DateField
import com.paisaplus.compose.ui.DebtRow
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.IconBadge
import com.paisaplus.compose.ui.components.PillButton
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TonalCard
import com.paisaplus.compose.ui.components.WavyProgress
import com.paisaplus.compose.ui.components.segmented
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.plainOrEmpty
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import com.paisaplus.compose.ui.theme.TonalColors
import com.paisaplus.compose.ui.theme.rememberTonal
import com.paisaplus.compose.ui.toAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt


private val DebtRow.progress get() = if (debt.amount > 0) (paid / debt.amount).toFloat().coerceIn(0f, 1f) else 0f
private val DebtRow.daysLeft get() = ZoneId.systemDefault().let { z ->
    ChronoUnit.DAYS.between(LocalDate.now(z), Instant.ofEpochMilli(debt.expiryDateTime).atZone(z).toLocalDate())
}
internal fun dateText(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dayFmt)

@Composable
internal fun seedOf(type: DebtType) = LocalPaisaColors.current.let { if (type == DebtType.CREDIT) it.income else it.expense }


internal fun newDebt(type: DebtType): DebtEntity {
    val now = System.currentTimeMillis()
    return DebtEntity(name = "", description = "", amount = 0.0, dateTime = now, expiryDateTime = now + 30L * 86_400_000, debtType = type)
}

@Composable
internal fun Stat(
    label: String, v: Double, color: Color?, modifier: Modifier,
    align: Alignment.Horizontal = Alignment.Start, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMediumEmphasized,
    suffix: String? = null,
) {
    Column(modifier, horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            AmountText(v, style = style, color = color ?: androidx.compose.material3.LocalContentColor.current)
            if (suffix != null) Text(suffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
    }
}

@Composable
internal fun DebtCard(r: DebtRow, onClick: () -> Unit) {
    TonalCard(seedOf(r.debt.debtType), Modifier.fillMaxWidth(), onClick) { t ->
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(t.content, RoundedCornerShape(14.dp)), Alignment.Center) {
                    Icon(r.debt.debtType.icon, null, tint = t.container)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.debt.name.ifBlank { str(R.string.unnamed) }, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.debt.description.isNotBlank()) Text(r.debt.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!r.settled) DaysPill(r.daysLeft, t, left = false)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.height(IntrinsicSize.Min)) {
                Stat(str(R.string.paid), r.paid, t.content, Modifier.weight(1f))
                VerticalDivider(color = t.track)
                Stat(str(R.string.total), r.debt.amount, t.content, Modifier.weight(1f).padding(start = 16.dp))
            }
            Spacer(Modifier.height(12.dp))
            WavyProgress(r.progress, colors = t)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (r.settled) str(R.string.settled) else str(R.string.due_x, dateText(r.debt.expiryDateTime)),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                Text("${(r.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private val DebtType.icon get() = if (this == DebtType.CREDIT) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown

/** Shared by every pill so one tap switches them all; persisted in the "ui" prefs. 0 days, 1 months, 2 years. */
private val PillUnit = mutableIntStateOf(-1)

/**
 * Time until the end date: "N days" in cards, "N days left" on detail pages; red when late.
 * Tap cycles days → months → years.
 */
@Composable
internal fun DaysPill(days: Long, t: TonalColors, left: Boolean) {
    val c = MaterialTheme.colorScheme
    val late = days < 0
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("ui", 0)
    if (PillUnit.intValue < 0) PillUnit.intValue = prefs.getInt("pill_unit", 0)
    val unit = PillUnit.intValue
    val n = kotlin.math.abs(days)
    val span = when (unit) {
        1 -> str(R.string.x_months, (n * 12 / 365.25).roundToInt())
        2 -> str(R.string.x_years, "%.1f".format(n / 365.25))
        else -> str(R.string.x_days, n)
    }
    Pill(
        Icons.Rounded.Timer,
        when { late -> str(R.string.x_late, span); days == 0L -> str(R.string.due_today); left -> str(R.string.x_left, span); else -> span },
        if (late) c.errorContainer else t.track, if (late) c.onErrorContainer else t.content,
        onClick = { PillUnit.intValue = (unit + 1) % 3; prefs.edit().putInt("pill_unit", PillUnit.intValue).apply() },
    )
}

@Composable
internal fun Pill(icon: ImageVector, text: String, bg: Color, fg: Color, onClick: (() -> Unit)? = null) {
    Surface(onClick = onClick ?: {}, enabled = onClick != null, color = bg, contentColor = fg, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One debt: progress, details, Mark as done, payment history; Edit opens [DebtSheet]. */
@Composable
fun DebtDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val rows by vm.debts.collectAsStateWithLifecycle()
    val r = rows.firstOrNull { it.debt.id == id } ?: return
    val d = r.debt
    val lending = d.debtType == DebtType.CREDIT
    val seed = seedOf(d.debtType)
    val t = rememberTonal(seed)
    val c = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar("", onBack) {
                if (!r.settled) IconButton({ paying = true }) { Icon(Icons.Rounded.Add, str(R.string.add_payment)) }
                IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, str(R.string.delete_debt)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = true }, icon = { Icon(Icons.Rounded.Edit, null) }, text = { Text(str(R.string.edit)) })
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(d.name.ifBlank { str(R.string.unnamed) }, style = MaterialTheme.typography.displaySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(d.debtType.icon, if (lending) str(R.string.lending) else str(R.string.borrowing), t.container, t.content)
                        if (r.settled) Pill(Icons.Rounded.Check, str(R.string.settled), c.secondaryContainer, c.onSecondaryContainer)
                        else DaysPill(r.daysLeft, t, left = true)
                    }
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(if (lending) str(R.string.amount_lent) else str(R.string.amount_borrowed), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                        AmountText(d.amount, style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
            item {
                TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp)) {
                        Text(str(R.string.repayment_progress), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        WavyProgress(r.progress, colors = t)
                        Spacer(Modifier.height(16.dp))
                        Row {
                            Stat(str(R.string.paid), r.paid, null, Modifier.weight(1f))
                            Stat(str(R.string.remaining), r.remaining, seed, Modifier.weight(1f), Alignment.CenterHorizontally)
                            Stat(str(R.string.total), d.amount, null, Modifier.weight(1f), Alignment.End)
                        }
                    }
                }
            }
            item { SectionTitle(str(R.string.details)) }
            item {
                TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column {
                        DetailRow(Icons.Rounded.Event, str(R.string.start_date), dateText(d.dateTime))
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        DetailRow(Icons.Rounded.EventAvailable, str(R.string.end_date), dateText(d.expiryDateTime))
                        if (d.description.isNotBlank()) {
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                            DetailRow(Icons.AutoMirrored.Rounded.Notes, str(R.string.notes), d.description)
                        }
                    }
                }
            }
            if (!r.settled) item {
                FilledTonalButton(
                    { vm.addPayment(d.id, r.remaining, System.currentTimeMillis()) },
                    Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 56.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.mark_as_done), style = MaterialTheme.typography.titleMedium)
                }
            }
            item { SectionTitle(str(R.string.payment_history)) }
            val pays = r.payments.sortedByDescending { it.time }
            if (pays.isEmpty()) item { Text(str(R.string.no_payments_yet), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant) }
            itemsIndexed(pays, key = { _, p -> p.id }) { i, p ->
                Row(Modifier.segmented(i, pays.size).padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Payments, seed)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(str(R.string.payment), style = MaterialTheme.typography.bodyLarge)
                        Text(dateText(p.time), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                    AmountText(p.amount, color = seed)
                    IconButton({ vm.deletePayment(p) }) { Icon(Icons.Rounded.Close, str(R.string.delete_payment_of_x, money(p.amount))) }
                }
            }
        }
    }
    if (editing) DebtSheet(d, vm) { editing = false }
    if (paying) PaymentSheet(r, vm) { paying = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(str(R.string.delete_x, d.name)) },
        text = { Text(str(R.string.removes_its_payments_too)) },
        confirmButton = { TextButton({ confirmDelete = false; vm.deleteDebt(d); onBack() }) { Text(str(R.string.delete)) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(str(R.string.cancel)) } },
    )
}

@Composable
internal fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(20.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSheet(r: DebtRow, vm: AppViewModel, onClose: () -> Unit) {
    var pay by rememberSaveable { mutableStateOf(r.remaining.plainOrEmpty()) }
    var at by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val v = pay.toAmount()
    ModalBottomSheet(onClose, sheetState = rememberSheetState()) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.add_payment), style = MaterialTheme.typography.headlineSmall)
            Text(str(R.string.x_paid_x_left, money(r.paid), money(r.remaining)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountField(pay, { pay = it }, str(R.string.payment), Modifier.fillMaxWidth())
            DateField(str(R.string.paid_on), at, { at = it }, Modifier.fillMaxWidth())
            PillButton(str(R.string.save), { vm.addPayment(r.debt.id, v!!, at); onClose() }, Modifier.fillMaxWidth(), enabled = v != null && v > 0)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtSheet(d: DebtEntity, vm: AppViewModel, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(d.name) }
    var desc by rememberSaveable { mutableStateOf(d.description) }
    var amount by rememberSaveable { mutableStateOf(d.amount.plainOrEmpty()) }
    var start by rememberSaveable { mutableLongStateOf(d.dateTime) }
    var due by rememberSaveable { mutableLongStateOf(d.expiryDateTime) }
    var type by rememberSaveable { mutableStateOf(d.debtType) }
    val value = amount.toAmount()
    val valid = name.isNotBlank() && value != null && value > 0

    ModalBottomSheet(onClose, sheetState = rememberSheetState()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (d.id == 0L) str(R.string.new_debt) else str(R.string.edit_debt), style = MaterialTheme.typography.headlineSmall)
            SegmentedTabs(DebtType.entries.reversed(), type, { if (it == DebtType.CREDIT) str(R.string.i_lent) else str(R.string.i_borrowed) }, { type = it })
            val names by vm.names.collectAsStateWithLifecycle()
            com.paisaplus.compose.ui.components.NameField(name, { name = it }, if (type == DebtType.CREDIT) str(R.string.who_owes_you) else str(R.string.who_you_owe), names)
            OutlinedTextField(desc, { desc = it }, label = { Text(str(R.string.note_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            AmountField(amount, { amount = it }, str(R.string.amount), isError = amount.isNotEmpty() && (value == null || value <= 0))
            DateField(str(R.string.started), start, { start = it }, Modifier.fillMaxWidth())
            DateField(str(R.string.due), due, { due = it }, Modifier.fillMaxWidth())
            PillButton(str(R.string.save), {
                vm.saveDebt(d.copy(name = name.trim(), description = desc.trim(), amount = value!!, dateTime = start, expiryDateTime = due, debtType = type))
                onClose()
            }, Modifier.fillMaxWidth(), enabled = valid)
            Spacer(Modifier.height(16.dp))
        }
    }
}
