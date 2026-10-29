package com.paisaplus.compose.ui.settings

import android.app.NotificationManager
import android.content.ComponentName
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.messages.AutoAdd
import com.paisaplus.compose.messages.BankNotificationListener
import com.paisaplus.compose.messages.MessageInboxEntity
import com.paisaplus.compose.messages.MessageInboxViewModel
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.ListHeader
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TransactionRow
import com.paisaplus.compose.ui.components.rememberSheetState
import com.paisaplus.compose.ui.incomeColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val AUTO = "Auto-added"
private val whenFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")

/**
 * An automatically added payment still missing something you'd want to know: a real category (not the "Other"
 * fallback) or, for UPI, which app. Cleared by editing it or by "Looks right".
 */
fun needsTagging(e: ExpenseEntity, fallbackCategories: Set<Long>) =
    e.note?.startsWith(AUTO) == true && (e.categoryId in fallbackCategories || (e.method == "UPI" && e.upiApp == null))

/** The categories auto-add falls back to when it can't tell what a payment was for. */
fun fallbackCategoryIds(categories: List<CategoryEntity>) =
    categories.filter { c -> listOf("Other", "Others", "Miscellaneous", "Misc").any { c.name.trim().equals(it, ignoreCase = true) } }.map { it.id }.toSet()

/** Home's bell and Settings › SMS & AI › Review: new alerts to confirm, then auto-added payments missing details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsInboxScreen(app: AppViewModel, onBack: () -> Unit, onOpenTx: (Long) -> Unit, inbox: MessageInboxViewModel = hiltViewModel()) {
    val d by app.data.collectAsStateWithLifecycle()
    val pending by inbox.pending.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val fallback = remember(d.categories) { fallbackCategoryIds(d.categories) }
    val untagged = remember(d.expenses, fallback) { d.expenses.filter { needsTagging(it, fallback) }.sortedByDescending { it.time } }
    val accounts = remember(d.accounts) { d.accounts.map { it.account } }
    fun accessOn() = ctx.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(ctx, BankNotificationListener::class.java))
    var access by remember { mutableStateOf(accessOn()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = accessOn() }
    var paste by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = {
        BackTopBar(str(R.string.payments_to_review), onBack) {
            IconButton({ paste = true }) { Icon(Icons.Rounded.ContentPaste, str(R.string.message_paste)) }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!access) item {
                TintCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, onClick = { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsOff, null)
                        Spacer(Modifier.width(12.dp))
                        Text(str(R.string.alerts_off_turn_on), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            inbox.feedback?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (pending.isEmpty() && untagged.isEmpty()) item { EmptyState(Icons.Rounded.DoneAll, str(R.string.nothing_to_review)) }
            if (pending.isNotEmpty()) {
                item { ListHeader(str(R.string.new_payments_x, pending.size)) }
                items(pending, key = { it.fingerprint }) { row -> ReviewCard(row, accounts, d.categories, d.expenses, inbox) }
            }
            if (untagged.isNotEmpty()) {
                item { ListHeader(str(R.string.needs_tagging_x, untagged.size)) }
                items(untagged, key = { it.id }) { e ->
                    TintCard(Modifier.fillMaxWidth()) {
                        Column {
                            TransactionRow(e, d.categoryById[e.categoryId], accounts.firstOrNull { it.id == e.accountId }?.name, Modifier.clickable { onOpenTx(e.id) })
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    listOfNotNull(
                                        str(R.string.category).takeIf { e.categoryId in fallback },
                                        str(R.string.upi_app).takeIf { e.method == "UPI" && e.upiApp == null },
                                    ).joinToString(" · ", prefix = str(R.string.missing) + ": "),
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton({ app.saveExpense(e.copy(note = e.note?.replaceFirst(AUTO, "Added"))) }) { Text(str(R.string.looks_right)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (paste) ModalBottomSheet({ paste = false }, sheetState = rememberSheetState()) {
        var text by remember { mutableStateOf("") }
        Column(Modifier.padding(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.message_paste), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(text, { text = it.take(16_384) }, Modifier.fillMaxWidth(), minLines = 3)
            Button({ inbox.parse(text); paste = false }, Modifier.fillMaxWidth(), enabled = text.isNotBlank() && !inbox.busy) { Text(str(R.string.message_parse)) }
        }
    }
}

/** A chip that shows the current pick and opens a menu; red outline until something is chosen. */
@Composable
private fun <T> PickChip(label: String, options: List<T>, name: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip({ open = true }, { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingIcon = { Icon(Icons.Rounded.UnfoldMore, null) })
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(name(o)) }, { open = false; onPick(o) }) }
        }
    }
}

/**
 * One alert: amount and direction, payee, where it came from and when, the original text (tap to expand),
 * account and category chips pre-filled from the alert and payee history, then Dismiss / Save.
 */
@Composable
private fun ReviewCard(row: MessageInboxEntity, accounts: List<AccountEntity>, categories: List<CategoryEntity>, history: List<ExpenseEntity>, vm: MessageInboxViewModel) {
    val guessAccount = remember(row.fingerprint, accounts) { AutoAdd.account(accounts, row.accountSuffix, row.body)?.id ?: 0L }
    val merchant = remember(row.fingerprint) { (com.paisaplus.compose.messages.BankMessageParser.parse(row.body) as? com.paisaplus.compose.messages.MessageParseResult.Candidate)?.transaction?.merchant ?: row.merchant }
    var name by rememberSaveable(row.fingerprint) { mutableStateOf(AutoAdd.payee(merchant, row.body) ?: merchant.orEmpty()) }
    val guessCategory = remember(row.fingerprint, categories) { AutoAdd.category(name, row.body, categories, history)?.id ?: 0L }
    var accountId by rememberSaveable(row.fingerprint, guessAccount) { mutableStateOf(guessAccount) }
    var categoryId by rememberSaveable(row.fingerprint, guessCategory) { mutableStateOf(guessCategory) }
    var expanded by rememberSaveable(row.fingerprint) { mutableStateOf(false) }
    val income = row.type == TransactionType.INCOME
    val tint = if (income) incomeColor() else MaterialTheme.colorScheme.onSurface
    TintCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (income) Icons.AutoMirrored.Rounded.CallReceived else Icons.AutoMirrored.Rounded.CallMade, null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Text((if (income) "+" else "−") + Money.format(row.amountPaise / 100.0), style = MaterialTheme.typography.titleLarge, color = tint)
                Spacer(Modifier.weight(1f))
                Text(Instant.ofEpochMilli(row.receivedAt).atZone(ZoneId.systemDefault()).format(whenFmt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(str(R.string.message_payee)) })
            Text(
                row.body, Modifier.clickable { expanded = !expanded }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickChip(accounts.firstOrNull { it.id == accountId }?.name ?: str(R.string.message_choose_account), accounts, { it.name }, { accountId = it.id }, Modifier.weight(1f, fill = false))
                PickChip(categories.firstOrNull { it.id == categoryId }?.name ?: str(R.string.message_choose_category), categories, { it.name }, { categoryId = it.id }, Modifier.weight(1f, fill = false))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton({ vm.dismiss(row.fingerprint) }, enabled = !vm.busy) { Text(str(R.string.message_dismiss)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    { vm.confirm(row.fingerprint, accountId, categoryId, name) },
                    enabled = !vm.busy && name.isNotBlank() && accounts.any { it.id == accountId } && categories.any { it.id == categoryId },
                ) { Text(str(R.string.message_save)) }
            }
        }
    }
}
