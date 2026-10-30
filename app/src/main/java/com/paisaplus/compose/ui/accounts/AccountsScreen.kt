package com.paisaplus.compose.ui.accounts

import com.paisaplus.compose.data.CardNetwork
import com.paisaplus.compose.ui.components.GroupHeroCard
import com.paisaplus.compose.data.cardGroup
import com.paisaplus.compose.data.CardGroup
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.paisaplus.compose.ui.components.rememberSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.ui.AccountRow
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.AccountHeroCard
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.icon
import com.paisaplus.compose.ui.label
import com.paisaplus.compose.ui.mdiCode

private enum class Section(val label: String) { ACCOUNTS(str(R.string.accounts)), CARDS(str(R.string.credit_cards)) }

/** Set by Home's Accounts / Credit cards tiles: which section to show on the next visit (true = cards). */
var accountsShowCards: Boolean? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: AppViewModel, outer: PaddingValues, onBack: (() -> Unit)? = null, onOpen: (Long) -> Unit = {}) {
    val d by vm.data.collectAsStateWithLifecycle()
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var section by rememberSaveable { mutableStateOf(Section.ACCOUNTS) }
    // Home's Accounts / Credit cards tile picks the section; consumed once so the tab otherwise keeps its own.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        accountsShowCards?.let { section = if (it) Section.CARDS else Section.ACCOUNTS; accountsShowCards = null }
    }
    val cards = section == Section.CARDS
    val shown = d.accounts.filter { (it.account.cardType == CardType.CREDIT) == cards }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { com.paisaplus.compose.ui.components.BackTopBar(str(R.string.accounts), onBack) },
        floatingActionButton = {
            val type = if (cards) CardType.CREDIT else CardType.BANK
            Box(Modifier.padding(bottom = outer.calculateBottomPadding())) {
                FloatingActionButton(onClick = { editing = AccountEntity(name = "", icon = type.mdiCode, bankName = "", cardType = type) }) {
                    Icon(Icons.Rounded.Add, if (cards) str(R.string.add_credit_card) else str(R.string.add_account))
                }
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, outer.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SegmentedTabs(Section.entries, section, { it.label }, { section = it }) }
            item {
                SectionTitle(if (cards) str(R.string.total_outstanding) else str(R.string.balance)) {
                    AmountText(shown.sumOf { if (cards) it.totalOutstanding else it.account.amount }, hidden = hidden)
                }
            }
            if (shown.isEmpty()) item {
                EmptyState(if (cards) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalance, if (cards) str(R.string.no_credit_cards) else str(R.string.no_accounts))
            }
            // Cards sharing a limitGroup show once, as one combined card; every other account as before.
            val all = d.accounts.map { it.account }
            val tiles: List<Any> = shown.map { cardGroup(it.account, all, d.loans) ?: it }.distinct()
            items(tiles, key = { if (it is CardGroup) "g:${it.name}" else (it as AccountRow).account.id }) { t ->
                if (t is CardGroup) GroupHeroCard(t, hidden, Modifier.animateItem()) { onOpen(t.members.first().id) }
                else (t as AccountRow).let { r -> AccountHeroCard(r, hidden, Modifier.animateItem()) { onOpen(r.account.id) } }
            }
        }
    }
    editing?.let { a ->
        AccountSheet(
            a,
            txCount = d.expenses.count { it.accountId == a.id },
            onDismiss = { editing = null },
            onSave = { vm.saveAccount(it); editing = null },
            onDelete = { vm.deleteAccount(a); editing = null },
        )
    }
}

private fun Double.plain() = if (this == 0.0) "" else toBigDecimal().stripTrailingZeros().toPlainString()

/** Credit balance is entered as the amount owed (stored negative). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(a: AccountEntity, txCount: Int, onDismiss: () -> Unit, onSave: (AccountEntity) -> Unit, onDelete: () -> Unit) {
    val isNew = a.id == 0L
    var name by rememberSaveable { mutableStateOf(a.name) }
    var bank by rememberSaveable { mutableStateOf(a.bankName) }
    var number by rememberSaveable { mutableStateOf(a.number) }
    var type by rememberSaveable { mutableStateOf(a.cardType) }
    val initial = if (a.cardType == CardType.CREDIT) -a.amount else a.amount
    var balance by rememberSaveable { mutableStateOf(kotlin.math.abs(initial).plain()) }
    var negative by rememberSaveable { mutableStateOf(initial < 0) }
    var limit by rememberSaveable { mutableStateOf(a.creditLimit?.plain().orEmpty()) }
    var group by rememberSaveable { mutableStateOf(a.limitGroup.orEmpty()) }
    var network by rememberSaveable { mutableStateOf(a.network.orEmpty()) } // "" = Auto
    var stmt by rememberSaveable { mutableStateOf(a.statementDay?.toString().orEmpty()) }
    val stmtOk = stmt.isBlank() || stmt.toIntOrNull() in 1..31
    var confirmDelete by remember { mutableStateOf(false) }
    val num = { s: String -> s.replace(',', '.').toDoubleOrNull() }
    val balanceOk = balance.isBlank() || num(balance) != null
    val limitOk = limit.isBlank() || num(limit) != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isNew) str(R.string.add_account) else str(R.string.edit_account), style = MaterialTheme.typography.titleLarge)
            SegmentedTabs(CardType.entries, type, { if (it == CardType.CREDIT) str(R.string.credit) else it.label }, { type = it })
            OutlinedTextField(name, { name = it }, label = { Text(str(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bank, { bank = it }, label = { Text(str(R.string.bank_provider)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(number, { number = it }, label = { Text(str(R.string.number_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                balance, { balance = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text(if (type == CardType.CREDIT) str(R.string.amount_owed) else if (isNew) str(R.string.initial_balance) else str(R.string.current_balance)) },
                leadingIcon = {
                    TextButton(onClick = { negative = !negative }) {
                        Text(if (negative) "−" else "+", style = MaterialTheme.typography.titleLarge)
                    }
                },
                isError = !balanceOk,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (type == CardType.CREDIT) {
                OutlinedTextField(
                    limit, { limit = it }, label = { Text(str(R.string.credit_limit)) }, isError = !limitOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    group, { group = it }, label = { Text(str(R.string.shared_limit_group)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                // Shown as the card's logo on grouped-card transactions.
                Text(str(R.string.card_network), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedTabs(listOf("") + CardNetwork.entries.map { it.name }, network, { n -> CardNetwork.entries.firstOrNull { it.name == n }?.label ?: str(R.string.auto) }, { network = it })
                OutlinedTextField(
                    stmt, { stmt = it.filter(Char::isDigit).take(2) }, label = { Text(str(R.string.statement_day_1_31)) }, isError = !stmtOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) OutlinedButton(onClick = { confirmDelete = true }) { Text(str(R.string.delete)) }
                Button(
                    onClick = {
                        val b = (num(balance) ?: 0.0) * if (negative) -1 else 1
                        val credit = type == CardType.CREDIT
                        onSave(
                            a.copy(
                                name = name.trim(), bankName = bank.trim(), number = number.trim(), cardType = type,
                                icon = if (isNew || type != a.cardType) type.mdiCode else a.icon,
                                amount = if (credit) -b else b,
                                creditLimit = if (credit) num(limit) else null,
                                limitGroup = if (credit) group.trim().ifEmpty { null } else null,
                                statementDay = if (credit) stmt.toIntOrNull() else null,
                                network = if (credit) network.ifEmpty { null } else null,
                            ),
                        )
                    },
                    enabled = name.isNotBlank() && balanceOk && limitOk && (type != CardType.CREDIT || stmtOk),
                    modifier = Modifier.weight(1f),
                ) { Text(str(R.string.save)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.delete_x, a.name)) },
            text = { Text(if (txCount > 0) str(R.string.deletes_x_transactions_too_can, txCount) else str(R.string.can_t_be_undone)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(str(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
        )
    }
}
