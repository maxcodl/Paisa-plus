package com.paisaplus.compose.ui.transactions

import androidx.compose.foundation.layout.PaddingValues
import com.paisaplus.compose.ui.components.ChoiceChips
import com.paisaplus.compose.ui.display
import com.paisaplus.compose.ui.label
import com.paisaplus.compose.data.UpiApp
import com.paisaplus.compose.data.PaymentMethod
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.icon
import com.paisaplus.compose.ui.paisaIcon
import com.paisaplus.compose.ui.timeFmt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditScreen(vm: AppViewModel, id: Long, initialType: TransactionType, onDone: () -> Unit, initialAccount: Long = 0) {
    val d by vm.data.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var amount by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("") } // "" = not set
    var upiApp by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var accountId by rememberSaveable { mutableLongStateOf(-1L) }
    var categoryId by rememberSaveable { mutableLongStateOf(-1L) }
    var time by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val original = d.expenses.firstOrNull { it.id == id }

    LaunchedEffect(original, d.loading) {
        if (!loaded && original != null) {
            amount = original.amount.toBigDecimal().stripTrailingZeros().toPlainString()
            name = original.name
            note = original.note.orEmpty()
            method = original.method.orEmpty()
            upiApp = original.upiApp.orEmpty()
            type = original.type
            accountId = original.accountId
            categoryId = original.categoryId
            time = original.time
            loaded = true
        }
        if (accountId == -1L && id == 0L) accountId = d.accounts.firstOrNull { it.account.id == initialAccount }?.account?.id ?: d.accounts.firstOrNull()?.account?.id ?: -1L
    }

    val value = amount.replace(',', '.').toDoubleOrNull()
    val valid = value != null && value > 0 && d.accountById.containsKey(accountId)
    val dt = Instant.ofEpochMilli(time).atZone(zone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) str(R.string.add_transaction) else str(R.string.edit_transaction), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str(R.string.back)) } },
                actions = {
                    if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, str(R.string.delete)) }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    vm.saveExpense(
                        ExpenseEntity(id = original?.id ?: 0, name = name.trim(), amount = value!!, time = time, type = type, accountId = accountId, categoryId = categoryId, note = note.trim().ifEmpty { null },
                            method = method.ifEmpty { null }, upiApp = upiApp.takeIf { method == PaymentMethod.UPI.name }?.ifEmpty { null }),
                    )
                    onDone()
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
            ) { Text(str(R.string.save), Modifier.padding(vertical = 6.dp)) }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedTabs(TransactionType.entries, type, { if (it == TransactionType.EXPENSE) str(R.string.expense) else str(R.string.income) }, { type = it })
            OutlinedTextField(
                amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text(str(R.string.amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(name, { name = it }, label = { Text(str(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text(str(R.string.note_optional)) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
            // Payment method (optional): tap the chosen one again to clear it.
            Text(str(R.string.paid_with), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(PaymentMethod.entries.map { it.name }, method, { n -> PaymentMethod.valueOf(n).label }, { method = if (method == it) "" else it }, contentPadding = PaddingValues(0.dp))
            if (method == PaymentMethod.UPI.name) {
                ChoiceChips(UpiApp.entries.map { it.name }, upiApp, { n -> UpiApp.valueOf(n).display }, { upiApp = if (upiApp == it) "" else it }, contentPadding = PaddingValues(0.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.padding(end = 8.dp)); Text(dt.format(dayFmt))
                }
                OutlinedButton(onClick = { pickTime = true }) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.padding(end = 8.dp)); Text(dt.format(timeFmt))
                }
            }
            SectionTitle(str(R.string.account))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.accounts.forEach { r ->
                    val a = r.account
                    FilterChip(
                        selected = a.id == accountId,
                        onClick = { accountId = a.id },
                        label = { Text(a.name) },
                        leadingIcon = { Icon(paisaIcon(a.icon, a.cardType.icon), null) },
                    )
                }
            }
            SectionTitle(str(R.string.category))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                d.categories.forEach { c ->
                    FilterChip(
                        selected = c.id == categoryId,
                        onClick = { categoryId = if (categoryId == c.id) -1 else c.id },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(paisaIcon(c.icon), null, tint = Color(c.color)) },
                    )
                }
            }
            if (original != null && !d.accountById.containsKey(original.accountId)) {
                Text(str(R.string.account_deleted_pick_another), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (pickDate) {
        val local = dt.toLocalDate()
        val s = rememberDatePickerState(local.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    s.selectedDateMillis?.let {
                        val day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        time = LocalDateTime.of(day, dt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    pickDate = false
                }) { Text(str(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(str(R.string.cancel)) } },
        ) { DatePicker(s) }
    }
    if (pickTime) {
        val s = rememberTimePickerState(dt.hour, dt.minute)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(s) },
            confirmButton = {
                TextButton(onClick = {
                    time = dt.withHour(s.hour).withMinute(s.minute).toInstant().toEpochMilli()
                    pickTime = false
                }) { Text(str(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(str(R.string.cancel)) } },
        )
    }
    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.delete_transaction)) },
            confirmButton = { TextButton(onClick = { vm.deleteExpense(original); onDone() }) { Text(str(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
        )
    }
}

