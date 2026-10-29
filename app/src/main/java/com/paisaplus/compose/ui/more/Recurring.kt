package com.paisaplus.compose.ui.more

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.ui.components.EmptyState
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material3.FloatingActionButton
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.Frequency
import com.paisaplus.compose.data.RecurringRuleEntity
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.data.dayOfMonth
import com.paisaplus.compose.ui.AmountField
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.DateField
import com.paisaplus.compose.ui.components.IconBadge
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.amountColor
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.plainOrEmpty
import com.paisaplus.compose.ui.toAmount
import java.time.Instant
import java.time.ZoneId

private val Frequency.label get() = str(when (this) { Frequency.DAILY -> R.string.daily; Frequency.WEEKLY -> R.string.weekly; Frequency.MONTHLY -> R.string.monthly })

@Composable
fun RecurringScreen(vm: AppViewModel, onBack: () -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RecurringRuleEntity?>(null) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Scaffold(
        topBar = {
            BackTopBar(str(R.string.recurring), onBack) {
                IconButton(onClick = { vm.postDueNow() }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, str(R.string.post_due_now)) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = RecurringRuleEntity(
                    name = "", amount = 0.0, type = TransactionType.EXPENSE,
                    category = d.categories.firstOrNull()?.name.orEmpty(), account = d.accounts.firstOrNull()?.account?.name.orEmpty(),
                    frequency = Frequency.MONTHLY, next = System.currentTimeMillis(),
                )
            }) { Icon(Icons.Rounded.Add, str(R.string.add_recurring)) }
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (d.rules.isEmpty()) item { EmptyState(Icons.Rounded.EventRepeat, str(R.string.no_recurring_transactions_yet)) }
            items(d.rules, key = { it.id }) { r ->
                TintCard(Modifier.fillMaxWidth(), onClick = { editing = r }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.EventRepeat)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                str(R.string.x_next_x, r.frequency.label, Instant.ofEpochMilli(r.next).atZone(ZoneId.systemDefault()).format(dayFmt)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("${r.category} · ${r.account}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (r.remind) Icon(Icons.Rounded.NotificationsActive, str(R.string.reminder_on), Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(money(r.amount), color = amountColor(r.type), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    editing?.let { r ->
        RuleSheet(
            r, d.accounts.map { it.account.name }, d.categories.map { it.name },
            onDismiss = { editing = null },
            onSave = {
                if (it.remind && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.saveRule(it); editing = null
            },
            onDelete = { vm.deleteRule(r); editing = null },
            names = vm.names.collectAsStateWithLifecycle().value,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleSheet(
    r: RecurringRuleEntity, accounts: List<String>, categories: List<String>,
    onDismiss: () -> Unit, onSave: (RecurringRuleEntity) -> Unit, onDelete: () -> Unit,
    names: List<String> = emptyList(),
) {
    val isNew = r.id == 0L
    var name by rememberSaveable { mutableStateOf(r.name) }
    var amount by rememberSaveable { mutableStateOf(r.amount.plainOrEmpty()) }
    var type by rememberSaveable { mutableStateOf(r.type) }
    var category by rememberSaveable { mutableStateOf(r.category) }
    var account by rememberSaveable { mutableStateOf(r.account) }
    var freq by rememberSaveable { mutableStateOf(r.frequency) }
    var next by rememberSaveable { mutableLongStateOf(r.next) }
    var remind by rememberSaveable { mutableStateOf(r.remind) }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = amount.toAmount()

    @Composable
    fun Chips(options: List<String>, selected: String, onPick: (String) -> Unit) =
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (selected.isBlank() || options.any { it.equals(selected, true) }) options else options + selected).forEach { o ->
                FilterChip(selected = o.equals(selected, true), onClick = { onPick(o) }, label = { Text(o) })
            }
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isNew) str(R.string.new_recurring_transaction) else str(R.string.edit_recurring), style = MaterialTheme.typography.titleLarge)
            SegmentedTabs(TransactionType.entries, type, { if (it == TransactionType.EXPENSE) str(R.string.expense) else str(R.string.income) }, { type = it })
            com.paisaplus.compose.ui.components.NameField(name, { name = it }, str(R.string.name), names)
            AmountField(amount, { amount = it }, str(R.string.amount))
            SegmentedTabs(Frequency.entries, freq, { it.label }, { freq = it })
            DateField(str(R.string.next), next, { next = it }, Modifier.fillMaxWidth())
            Text(str(R.string.account), style = MaterialTheme.typography.titleSmall)
            Chips(accounts, account) { account = it }
            Text(str(R.string.category), style = MaterialTheme.typography.titleSmall)
            Chips(categories, category) { category = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.remind_on_due_day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(remind, { remind = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) OutlinedButton(onClick = { confirmDelete = true }) { Text(str(R.string.delete)) }
                Button(
                    onClick = { onSave(r.copy(name = name.trim(), amount = value!!, type = type, category = category, account = account, frequency = freq, next = next, remind = remind, dueDay = if (isNew || next != r.next) dayOfMonth(next) else r.dueDay)) },
                    enabled = name.isNotBlank() && value != null && value > 0 && account.isNotBlank() && category.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(str(R.string.save)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.delete_x, r.name)) },
            text = { Text(str(R.string.transactions_already_posted_stay)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(str(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
        )
    }
}
