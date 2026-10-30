package com.paisaplus.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.R
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.Avatar
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.incomeColor
import com.paisaplus.compose.ui.money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val lastFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

/** One person or payee: [latestId] opens their payee page; null for someone only in Debts. */
private class Person(val name: String, val count: Int, val paid: Double, val received: Double, val last: Long, val latestId: Long?)

/**
 * Everyone you've paid or been paid by, plus people in Debts, A–Z or most transactions first, and searchable.
 * Tapping a payee opens their page (all transactions, rename / merge); someone only in Debts opens Debts.
 */
@Composable
fun PeopleScreen(vm: AppViewModel, onBack: () -> Unit, onOpenPayee: (Long) -> Unit, onOpenDebts: () -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val people = remember(d.expenses, debts) {
        val payees = d.expenses.filter { it.name.isNotBlank() }.groupBy { it.name.trim().lowercase() }.map { (_, list) ->
            val latest = list.maxBy { it.time }
            Person(
                latest.name.trim(), list.size,
                list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                latest.time, latest.id,
            )
        }
        val known = payees.map { it.name.lowercase() }.toSet()
        val debtOnly = debts.filter { it.debt.name.isNotBlank() && it.debt.name.trim().lowercase() !in known }
            .distinctBy { it.debt.name.trim().lowercase() }
            .map { Person(it.debt.name.trim(), 0, 0.0, 0.0, it.debt.dateTime, null) }
        (payees + debtOnly).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    var byCount by rememberSaveable { mutableStateOf(false) }
    val aliases by vm.aliases.collectAsStateWithLifecycle()
    val shown = remember(people, q, byCount, aliases) {
        // Also finds someone by a name their bank alerts use.
        val found = if (q.isBlank()) people else people.filter { p -> p.name.contains(q.trim(), ignoreCase = true) || aliases.any { it.name.equals(p.name, true) && it.alias.contains(q.trim(), ignoreCase = true) } }
        if (byCount) found.sortedByDescending { it.count } else found
    }
    val green = incomeColor()

    Scaffold(topBar = { BackTopBar(str(R.string.people), onBack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
                    placeholder = { Text(str(R.string.search_people_x, people.size)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!byCount, { byCount = false }, { Text(str(R.string.name)) })
                    FilterChip(byCount, { byCount = true }, { Text(str(R.string.transactions)) })
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Rounded.People, str(R.string.no_people)) }
            items(shown, key = { it.name.lowercase() }) { p ->
                TintCard(Modifier.fillMaxWidth().clickable { p.latestId?.let(onOpenPayee) ?: onOpenDebts() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("", p.name, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (p.latestId == null) str(R.string.in_debts)
                                else str(R.string.x_transactions_last_x, p.count, Instant.ofEpochMilli(p.last).atZone(ZoneId.systemDefault()).format(lastFmt)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (p.paid > 0) Text("−" + money(p.paid), style = MaterialTheme.typography.bodyMedium)
                            if (p.received > 0) Text("+" + money(p.received), style = MaterialTheme.typography.bodyMedium, color = green)
                        }
                    }
                }
            }
        }
    }
}
