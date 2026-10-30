package com.paisaplus.compose.ui.more

import androidx.compose.foundation.lazy.itemsIndexed
import com.paisaplus.compose.ui.components.segmented
import androidx.compose.foundation.clickable
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import com.paisaplus.compose.ui.theme.LocalMonthStart
import com.paisaplus.compose.data.currentPeriod
import com.paisaplus.compose.data.periodOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.groupKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.data.dailyTotals
import com.paisaplus.compose.data.salaryIds
import com.paisaplus.compose.data.date
import com.paisaplus.compose.data.monthTotals
import com.paisaplus.compose.data.spendByCategory
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.IconBadge
import com.paisaplus.compose.ui.components.SegmentedTabs
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TransactionRow
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.incomeColor
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.paisaIcon
import com.paisaplus.compose.ui.plainOrEmpty
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val chipFmt = DateTimeFormatter.ofPattern("MMM yyyy")
private val monthFmt = DateTimeFormatter.ofPattern("MMM")

/** insights_page.dart: month chips, net this month, daily bars, top categories. */
@Composable
fun ReportsScreen(vm: AppViewModel, onBack: (() -> Unit)?, outer: PaddingValues) {
    val d by vm.data.collectAsStateWithLifecycle()
    val skip by vm.totalsSkip.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val start = LocalMonthStart.current
    val months = remember(d.expenses, start) { (d.expenses.map { periodOf(it.date(zone), start) } + currentPeriod(start)).distinct().sortedDescending() }
    var key by rememberSaveable(start) { mutableStateOf(currentPeriod(start).toString()) }
    val month = YearMonth.parse(key)
    val scopes = remember(d.accounts) { accountScopes(d.accounts.map { it.account }) }
    var scopeKey by rememberSaveable { mutableStateOf("all") }
    val scope = scopes.firstOrNull { it.key == scopeKey } ?: scopes.first()
    val expenses = remember(d.expenses, scope) { scope.ids?.let { ids -> d.expenses.filter { it.accountId in ids } } ?: d.expenses }
    val totals = remember(expenses, d.categories, month, skip, start) { monthTotals(expenses, month, zone, skip, salaryIds(d.categories), start) }
    val days = remember(expenses, month, skip) { dailyTotals(expenses, month, zone, skip) }
    val cats = remember(expenses, month, skip, start) { spendByCategory(expenses, month, zone, skip, start).toList().sortedByDescending { it.second } }
    val c = MaterialTheme.colorScheme
    val green = incomeColor()

    Scaffold(topBar = { BackTopBar(str(R.string.reports), onBack) }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(contentPadding = PaddingValues(0.dp, pad.calculateTopPadding(), 0.dp, pad.calculateBottomPadding() + outer.calculateBottomPadding() + 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(months) { m -> FilterChip(selected = m == month, onClick = { key = m.toString() }, label = { Text(m.format(chipFmt)) }) }
                }
            }
            if (scopes.size > 1) item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scopes, key = { it.key }) { s -> FilterChip(selected = s == scope, onClick = { scopeKey = s.key }, label = { Text(s.label) }) }
                }
            }
            item {
                TintCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = c.primaryContainer) {
                    Column(Modifier.padding(20.dp)) {
                        Text(if (month == currentPeriod(start)) str(R.string.net_this_month) else str(R.string.net_for_x, month.format(chipFmt)), style = MaterialTheme.typography.labelLarge)
                        Text(money(totals.net), style = MaterialTheme.typography.headlineMediumEmphasized)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text(str(R.string.income_x, money(totals.income)), color = green, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(str(R.string.expense_x, money(totals.expense)), color = c.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                // Six months ending now; picking an older month from the chips slides the window back to it.
                val cur = currentPeriod(start)
                val end = if (month > cur.minusMonths(6)) cur else month
                val window = remember(expenses, d.categories, skip, start, end) {
                    (5 downTo 0).map { end.minusMonths(it.toLong()) }.map { it to monthTotals(expenses, it, zone, skip, salaryIds(d.categories), start).expense }
                }
                SpendSummary(month, totals.expense, window) { key = it.toString() }
            }
            item {
                TintCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(str(R.string.daily_spending), style = MaterialTheme.typography.titleMedium)
                        Text(str(R.string.x_transactions, totals.count), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        val max = days.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1.0)
                        val red = c.error
                        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                            val slot = size.width / days.size
                            val bar = (slot / 2.5f).coerceAtMost(8.dp.toPx())
                            days.forEachIndexed { i, (out, inc) ->
                                val x = i * slot + slot / 2
                                listOf(out to red, inc to green).forEachIndexed { j, (v, col) ->
                                    val h = (v / max * size.height).toFloat()
                                    if (h > 0) drawRoundRect(col, Offset(x - bar + j * bar, size.height - h), Size(bar * 0.9f, h), CornerRadius(bar / 2))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            listOf(1, 6, 11, 16, 21, 26).forEach { Text("$it", style = MaterialTheme.typography.labelSmall) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Legend(red, str(R.string.expense)); Spacer(Modifier.width(16.dp)); Legend(green, str(R.string.income))
                        }
                    }
                }
            }
            item { SectionTitle(str(R.string.top_categories), Modifier.padding(horizontal = 16.dp)) }
            if (cats.isEmpty()) item { Text(if (month == currentPeriod(start)) str(R.string.no_spending_this_month) else str(R.string.no_spending_in_x, month.format(chipFmt)), Modifier.padding(horizontal = 20.dp), color = c.onSurfaceVariant) }
            items(cats.take(8)) { (id, spent) ->
                val cat = d.categoryById[id]
                val color = cat?.let { Color(it.color) } ?: c.primary
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(paisaIcon(cat?.icon ?: 0), color, 36.dp, autoHue = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(cat?.name ?: str(R.string.unlinked), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(money(spent), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { (spent / totals.expense.coerceAtLeast(1.0)).toFloat() }, color = color, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Which accounts Reports counts: [ids] null = all. */
private data class Scope(val key: String, val label: String, val ids: Set<Long>?)

/** All, all bank accounts, all credit cards, each combined-limit card group, then each account. */
private fun accountScopes(accounts: List<AccountEntity>): List<Scope> {
    fun List<AccountEntity>.ids() = map { it.id }.toSet()
    val bank = accounts.filter { it.cardType == CardType.BANK }
    val cards = accounts.filter { it.cardType == CardType.CREDIT }
    val groups = cards.groupBy { groupKey(it) }.filter { (k, v) -> k != null && v.size > 1 }
    return listOf(Scope("all", str(R.string.all), null)) +
        listOfNotNull(
            Scope("bank", str(R.string.bank), bank.ids()).takeIf { bank.size > 1 },
            Scope("cards", str(R.string.credit_cards), cards.ids()).takeIf { cards.size > 1 },
        ) +
        groups.map { (k, v) -> Scope("g:$k", v.first().limitGroup!!.trim(), v.ids()) } +
        accounts.map { Scope("a:${it.id}", it.name, setOf(it.id)) }
}

/** The month's spending, six months as bars (tap one to pick it) and a dashed six-month average. */
@Composable
private fun SpendSummary(month: YearMonth, spent: Double, window: List<Pair<YearMonth, Double>>, onPick: (YearMonth) -> Unit) {
    val c = MaterialTheme.colorScheme
    val max = window.maxOf { it.second }.coerceAtLeast(1.0)
    val avg = window.sumOf { it.second } / window.size
    val barMax = 150.dp
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
    val line = c.onSurfaceVariant
    val idle = SolidColor(c.onSurface.copy(alpha = 0.08f))
    val picked = Brush.verticalGradient(listOf(c.primary, c.primary.copy(alpha = 0.3f)))
    TintCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(str(R.string.spend_summary), style = MaterialTheme.typography.titleMedium)
            Text(month.format(chipFmt), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Text(money(spent), style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().drawWithContent {
                    drawContent()
                    val y = size.height - (avg / max * barMax.toPx()).toFloat()
                    drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                window.forEach { (m, v) ->
                    Column(Modifier.weight(1f).clickable { onPick(m) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Money.compact(v), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth(0.6f).height((barMax * (v / max).toFloat()).coerceAtLeast(8.dp)).background(if (m == month) picked else idle, RoundedCornerShape(50)))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                window.forEach { (m, _) ->
                    Text(
                        m.format(monthFmt), Modifier.weight(1f).clickable { onPick(m) }, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium, fontWeight = if (m == month) FontWeight.Bold else null,
                        color = if (m == month) c.onSurface else c.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(16.dp, 2.dp)) { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))) }
                Spacer(Modifier.width(6.dp))
                Text(str(R.string.avg_x, Money.compact(avg)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelMedium)
}

private enum class Kind(val label: String) { ALL(str(R.string.all)), EXPENSE(str(R.string.expenses)), INCOME(str(R.string.income)) }

/** Search by name, amount, category or account; type and date-range filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel, onBack: (() -> Unit)?, outer: PaddingValues, onOpen: (Long) -> Unit) {
    val d by vm.data.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var q by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Kind.ALL) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) } // UTC day millis from the picker
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var pick by rememberSaveable { mutableStateOf(false) }
    val day = { m: Long -> Instant.ofEpochMilli(m).atZone(ZoneOffset.UTC).toLocalDate() }
    val results = remember(d.expenses, q, kind, from, to) {
        val needle = q.trim().lowercase()
        val range = from?.let { f -> day(f)..(to?.let(day) ?: day(f)) }
        d.expenses.filter { e ->
            (kind == Kind.ALL || (kind == Kind.INCOME) == (e.type == TransactionType.INCOME)) &&
                (range == null || e.date(zone) in range) &&
                (needle.isEmpty() || e.name.lowercase().contains(needle) ||
                    e.amount.plainOrEmpty().contains(needle) || money(e.amount).contains(needle) ||
                    d.categoryById[e.categoryId]?.name?.lowercase()?.contains(needle) == true ||
                    d.accountById[e.accountId]?.name?.lowercase()?.contains(needle) == true)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (q.isEmpty()) focus.requestFocus() }
    Scaffold(topBar = { BackTopBar(str(R.string.search), onBack) }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + outer.calculateBottomPadding() + 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        placeholder = { Text(str(R.string.name_amount_category_or_account)) },
                        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, str(R.string.clear)) } },
                    )
                    SegmentedTabs(Kind.entries, kind, { it.label }, { kind = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pick = true }, Modifier.weight(1f)) {
                            Icon(Icons.Rounded.DateRange, null, Modifier.padding(end = 8.dp))
                            Text(from?.let { f -> listOfNotNull(day(f), to?.let(day)).joinToString(" – ") { it.format(dayFmt) } } ?: str(R.string.any_date))
                        }
                        if (from != null) IconButton(onClick = { from = null; to = null }) { Icon(Icons.Rounded.Close, str(R.string.clear_dates)) }
                    }
                    Text(str(R.string.x_results, results.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val shown = results.take(300)
            itemsIndexed(shown, key = { _, e -> e.id }) { i, e ->
                Box(Modifier.padding(horizontal = 16.dp).segmented(i, shown.size).clickable { onOpen(e.id) }) {
                    TransactionRow(e, d.categoryById[e.categoryId], d.accountById[e.accountId]?.name)
                }
            }
        }
    }
    if (pick) {
        val s = rememberDateRangePickerState(from, to)
        DatePickerDialog(
            onDismissRequest = { pick = false },
            confirmButton = { TextButton(onClick = { from = s.selectedStartDateMillis; to = s.selectedEndDateMillis; pick = false }) { Text(str(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { pick = false }) { Text(str(R.string.cancel)) } },
        ) { DateRangePicker(s, Modifier.weight(1f)) }
    }
}

