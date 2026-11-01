package com.paisaplus.compose.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.data.StatementEntity
import com.paisaplus.compose.data.nextBill
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.AmountField
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.DateField
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.rememberSheetState
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.toAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** This cycle's estimate, the bank's real statement (which replaces it), and past statements for one card. */
@Composable
fun StatementScreen(vm: AppViewModel, accountId: Long, onBack: () -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val row = d.accounts.firstOrNull { it.account.id == accountId }
    var addFor by remember { mutableStateOf<Long?>(null) }
    if (row == null) return
    val acc = row.account
    val today = LocalDate.now(zone)
    // The estimate alone (no statements), to show what the bank's figure replaces.
    // Cards sharing a limit get one combined statement on top and each card's own below.
    val group = remember(d.accounts, d.loans, accountId) { com.paisaplus.compose.data.cardGroup(acc, d.accounts.map { it.account }, d.loans) }
    val members = group?.members ?: listOf(acc)
    val ids = members.map { it.id }.toSet()
    val est = remember(d, members) { com.paisaplus.compose.data.combineBills(members.mapNotNull { nextBill(it, d.expenses, d.loans, today, zone) }) }
    val bill = com.paisaplus.compose.data.combineBills(d.accounts.filter { it.account.id in ids }.mapNotNull { it.nextBill })
    val mine = d.statements.filter { it.accountId in ids }
    fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().format(dayFmt)
    Scaffold(topBar = { BackTopBar(group?.name ?: acc.name, onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TintCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str(R.string.this_cycle), style = MaterialTheme.typography.titleSmall)
                        if (bill == null) Text(str(R.string.statement_and_due))
                        else {
                            Text(money(bill.total), style = MaterialTheme.typography.headlineMedium)
                            Text(if (bill.actual) str(R.string.statement_total) else str(R.string.next_bill_est_on_x, bill.date.format(dayFmt)))
                            bill.due?.let { Text(str(R.string.payment_due_date) + ": " + it.format(dayFmt)) }
                            if (bill.minDue > 0) Text(str(R.string.minimum_due) + ": " + money(bill.minDue))
                            if (!bill.actual) Text(str(R.string.estimate_breakdown) + ": ${money(bill.charges)} + ${money(bill.emi)}", style = MaterialTheme.typography.bodySmall)
                            if (bill.actual && est != null) {
                                Text(str(R.string.app_estimate_x, money(est.total)), style = MaterialTheme.typography.bodySmall)
                                Text(str(R.string.bank_vs_estimate_x, money(bill.total - est.total)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (group == null) item { Button({ addFor = acc.id }, Modifier.fillMaxWidth()) { Text(str(R.string.add_bank_statement)) } }
            else items(members, key = { "m${it.id}" }) { m ->
                val b = d.accounts.firstOrNull { it.account.id == m.id }?.nextBill
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name + " " + m.number.takeLast(4).let { if (it.isBlank()) "" else "•••• $it" })
                        Text(
                            b?.let { money(it.total) + " · " + (it.due ?: it.date).format(dayFmt) } ?: "—",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton({ addFor = m.id }) { Text(str(R.string.add_bank_statement)) }
                }
            }
            if (mine.isNotEmpty()) item { Text(str(R.string.past_statements), style = MaterialTheme.typography.titleSmall) }
            items(mine, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(money(s.total) + (if (group != null) " · " + (members.firstOrNull { it.id == s.accountId }?.let { it.number.takeLast(4) } ?: "") else ""))
                        Text(
                            date(s.dueDate) + " · " + (if (s.source == "SMS") str(R.string.from_sms) else str(R.string.entered_by_you)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton({ vm.deleteStatement(s) }) { Text(str(R.string.delete)) }
                }
            }
        }
    }
    addFor?.let { id ->
        val b = d.accounts.firstOrNull { it.account.id == id }?.nextBill
        StatementSheet(id, b?.statementDate ?: today, b?.due ?: today.plusDays(20), { addFor = null }) { vm.saveStatement(it); addFor = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementSheet(accountId: Long, statement: LocalDate, due: LocalDate, onDismiss: () -> Unit, onSave: (StatementEntity) -> Unit) {
    val zone = ZoneId.systemDefault()
    fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    var sDate by remember { mutableLongStateOf(ms(statement)) }
    var dDate by remember { mutableLongStateOf(ms(due)) }
    var total by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    fun day(v: Long) = ms(Instant.ofEpochMilli(v).atZone(zone).toLocalDate())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.add_bank_statement), style = MaterialTheme.typography.titleLarge)
            AmountField(total, { total = it }, str(R.string.statement_total))
            AmountField(min, { min = it }, str(R.string.minimum_due))
            DateField(str(R.string.statement_date), sDate, { sDate = it }, Modifier.fillMaxWidth())
            DateField(str(R.string.payment_due_date), dDate, { dDate = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onDismiss, Modifier.weight(1f)) { Text(str(R.string.cancel)) }
                Button(
                    { onSave(StatementEntity(accountId = accountId, statementDate = day(sDate), dueDate = day(dDate), total = total.toAmount()!!, minDue = min.toAmount() ?: 0.0, source = "MANUAL")) },
                    Modifier.weight(1f), enabled = (total.toAmount() ?: 0.0) > 0,
                ) { Text(str(R.string.save)) }
            }
        }
    }
}
