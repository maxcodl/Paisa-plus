package com.paisaplus.compose.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.data.BankApp
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.ChoiceChips
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.display
import com.paisaplus.compose.ui.icon
import com.paisaplus.compose.ui.paisaIcon

/**
 * Transfer between your own accounts or a credit card bill payment (opened from the + menu, or "Pay bill" on a card):
 * one expense on the paying account and one income on the receiving one, both Transfer. [initialTo]: the card to pay.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransferScreen(vm: AppViewModel, initialTo: Long, onDone: () -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var fromId by rememberSaveable { mutableLongStateOf(-1L) }
    var toId by rememberSaveable { mutableLongStateOf(-1L) }
    var app by rememberSaveable { mutableStateOf("") } // "" = not set
    LaunchedEffect(d.loading, d.accounts.size) {
        if (toId == -1L) toId = d.accounts.firstOrNull { it.account.id == initialTo }?.account?.id
            ?: d.accounts.firstOrNull { it.account.cardType == CardType.CREDIT }?.account?.id ?: -1L
        if (fromId == -1L) fromId = d.accounts.firstOrNull { it.account.cardType == CardType.BANK && it.account.id != toId }?.account?.id
            ?: d.accounts.firstOrNull { it.account.id != toId }?.account?.id ?: -1L
    }
    val value = amount.replace(',', '.').toDoubleOrNull()
    val valid = value != null && value > 0 && fromId != toId && d.accountById.containsKey(fromId) && d.accountById.containsKey(toId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.transfer), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back)) } },
            )
        },
        bottomBar = {
            Button(
                onClick = { vm.saveTransfer(fromId, toId, value!!, System.currentTimeMillis(), app.ifEmpty { null }); onDone() },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
            ) { Text(str(R.string.save), Modifier.padding(vertical = 6.dp)) }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text(str(R.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, textStyle = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(str(R.string.from_account))
            AccountChips(d.accounts.map { it.account }, fromId, disabled = toId) { fromId = it }
            SectionTitle(str(R.string.to_account))
            AccountChips(d.accounts.map { it.account }, toId, disabled = fromId) { toId = it }
            // The banking app it was paid from (optional): tap the chosen one again to clear it.
            Text(str(R.string.paid_with_bank_app), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(BankApp.entries.map { it.name }, app, { n -> BankApp.valueOf(n).display }, { app = if (app == it) "" else it }, contentPadding = PaddingValues(0.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(accounts: List<com.paisaplus.compose.data.AccountEntity>, selected: Long, disabled: Long, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { a ->
            FilterChip(
                selected = a.id == selected, onClick = { onPick(a.id) }, enabled = a.id != disabled,
                label = { Text(a.name) }, leadingIcon = { Icon(paisaIcon(a.icon, a.cardType.icon), null) },
            )
        }
    }
}
