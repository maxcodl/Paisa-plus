package com.paisaplus.compose.ui.home

import com.paisaplus.compose.openPayee
import com.paisaplus.compose.plural
import com.paisaplus.compose.ui.components.segmented
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.ui.unit.Dp
import com.paisaplus.compose.ui.components.LocalTopBarBehavior
import com.paisaplus.compose.ui.components.topBarContainer
import com.paisaplus.compose.ui.theme.LocalMonthStart
import com.paisaplus.compose.data.currentPeriod
import com.paisaplus.compose.data.CardBill
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.widthIn
import com.paisaplus.compose.ui.accounts.accountsShowCards
import com.paisaplus.compose.data.CardType
import androidx.compose.material.icons.rounded.CreditCard
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.paisaplus.compose.Routes
import com.paisaplus.compose.Tab
import com.paisaplus.compose.data.MonthTotals
import com.paisaplus.compose.data.BalanceMode
import com.paisaplus.compose.data.balanceSummary
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import java.time.LocalDate
import java.time.ZoneId
import com.paisaplus.compose.data.monthTotals
import com.paisaplus.compose.data.salaryIds
import com.paisaplus.compose.editTransaction
import com.paisaplus.compose.openTab
import com.paisaplus.compose.ui.AccountRow
import com.paisaplus.compose.ui.AppData
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.WrapText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.TransactionRow
import com.paisaplus.compose.ui.components.WavyProgress
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.dayFmt
import com.paisaplus.compose.ui.moneyOrHidden
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import com.paisaplus.compose.ui.transactions.AddFab
import com.paisaplus.compose.ui.theme.LocalGlass
import com.paisaplus.compose.ui.components.ExpressiveIcon
import com.paisaplus.compose.go
import com.paisaplus.compose.ui.theme.glass
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController, outer: PaddingValues) {
    val d by vm.data.collectAsStateWithLifecycle()
    val hidden by vm.hideBalance.collectAsStateWithLifecycle()
    val modes by vm.balanceModes.collectAsStateWithLifecycle()
    val skip by vm.totalsSkip.collectAsStateWithLifecycle()
    val name by vm.userName.collectAsStateWithLifecycle()
    val image by vm.userImage.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) if (d.accounts.isEmpty()) vm.import(uri) else confirm = uri
    }
    val pickBackup = { picker.launch(arrayOf("application/json", "*/*")) }
    val c = MaterialTheme.colorScheme
    // Transparent background = a custom backdrop is showing; don't cover it.
    val homeBg = if (c.background.alpha == 0f) Color.Transparent else c.surfaceContainerLow

    Scaffold(
        containerColor = homeBg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                scrollBehavior = LocalTopBarBehavior.current,
                expandedHeight = 96.dp,
                title = {
                    Text(
                        buildAnnotatedString {
                            // At large text sizes the full greeting would truncate the name away.
                            append(if (name.isNotBlank() && LocalDensity.current.fontScale > 1.3f) str(R.string.hi) else greeting())
                            if (name.isNotBlank()) {
                                append(", ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name.trim()) }
                            }
                        },
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (homeBg.alpha == 0f) topBarContainer() else homeBg, scrolledContainerColor = c.surfaceContainer),
                actions = {
                    // Payments waiting for review plus auto-added ones still missing a category, method or app.
                    val inbox: com.paisaplus.compose.messages.MessageInboxViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                    val pending by inbox.pending.collectAsStateWithLifecycle()
                    val toTag = remember(d.expenses, d.categories) {
                        val fallback = com.paisaplus.compose.ui.settings.fallbackCategoryIds(d.categories)
                        d.expenses.count { com.paisaplus.compose.ui.settings.needsTagging(it, fallback) }
                    }
                    val count = pending.size + toTag
                    // Badge outside the button: an IconButton clips to its circle and would cut the count off.
                    androidx.compose.material3.BadgedBox(
                        badge = { if (count > 0) androidx.compose.material3.Badge(Modifier.offset(x = (-10).dp, y = 8.dp)) { Text(if (count > 99) "99+" else "$count") } },
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        IconButton(onClick = { nav.go(Routes.PAYMENTS) }, Modifier.semantics { contentDescription = str(R.string.payments_to_review_x, count) }) {
                            Icon(if (count > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications, null)
                        }
                    }
                    Box(
                        Modifier.padding(end = 12.dp).size(52.dp).clip(CircleShape)
                            .clickable(role = Role.Button) { nav.navigate(Routes.PROFILE) }
                            .semantics { contentDescription = str(R.string.profile) },
                        Alignment.Center,
                    ) { com.paisaplus.compose.ui.components.Avatar(image, name, 44.dp) }
                },
            )
        },
        floatingActionButton = { if (d.accounts.isNotEmpty()) Box(Modifier.padding(bottom = outer.calculateBottomPadding())) { AddFab({ nav.navigate("transfer") }) { nav.editTransaction(type = it) } } },
    ) { pad ->
        when {
            d.loading || vm.importing -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { LoadingIndicator() }
            d.accounts.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                EmptyState(
                    Icons.Rounded.AccountBalance,
                    str(R.string.no_accounts_yet),
                    action = str(R.string.restore_backup),
                    onAction = pickBackup,
                )
            }
            else -> Dashboard(d, skip, name, hidden, modes, pad, outer.calculateBottomPadding(), vm::toggleHideBalance, nav)
        }
    }

    confirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(str(R.string.replace_all_data)) },
            text = { Text(str(R.string.replaces_all_data_export_first)) },
            confirmButton = { TextButton(onClick = { vm.import(uri); confirm = null }) { Text(str(R.string.replace)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(str(R.string.cancel)) } },
        )
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> str(R.string.good_morning)
    in 12..16 -> str(R.string.good_afternoon)
    else -> str(R.string.good_evening)
}

@Composable
private fun Dashboard(d: AppData, skip: Set<Long>, name: String, hidden: Boolean, modes: List<BalanceMode>, pad: PaddingValues, bar: Dp, toggleHidden: () -> Unit, nav: NavHostController) {
    val start = LocalMonthStart.current
    val now = remember(start) { currentPeriod(start) }
    val salary = remember(d.categories) { salaryIds(d.categories) }
    val month = remember(d.expenses, skip, salary, now) { monthTotals(d.expenses, now, skip = skip, salary = salary, start = start) }
    val last = remember(d.expenses, skip, salary, now) { monthTotals(d.expenses, now.minusMonths(1), skip = skip, salary = salary, start = start) }
    val credit = remember(d.accounts) {
        // One row per limit group (or ungrouped card).
        d.accounts.filter { it.credit != null }.distinctBy { it.account.limitGroup?.trim()?.lowercase()?.ifEmpty { null } ?: "#${it.account.id}" }
    }
    val c = MaterialTheme.colorScheme
    val oneColumn = LocalDensity.current.fontScale > 1.5f
    val budgets = d.categories.filter { it.isBudget }
    val loans = d.loans.filter { !it.closed }
    val (cards, banks) = d.accounts.partition { it.account.cardType == CardType.CREDIT }
    val tiles = listOf(
        Tile(Icons.Rounded.AccountBalance, str(R.string.accounts), plural(R.plurals.accounts_count, banks.size), banks.sumOf { it.account.amount }) { accountsShowCards = false; nav.openTab(Tab.ACCOUNTS) },
        Tile(Icons.Rounded.CreditCard, str(R.string.credit_cards), plural(R.plurals.cards_count, cards.size), cards.sumOf { it.totalOutstanding }) { accountsShowCards = true; nav.openTab(Tab.ACCOUNTS) },
        Tile(Icons.Rounded.RequestQuote, str(R.string.loans), str(R.string.x_active, loans.size), loans.sumOf { it.remaining }) { nav.navigate(Routes.LOANS) },
        Tile(Icons.Rounded.Insights, str(R.string.reports), plural(R.plurals.transactions_count, month.count), month.expense) { nav.openTab(Tab.REPORTS) },
        Tile(Icons.Rounded.Savings, str(R.string.budgets), plural(R.plurals.budgets_count, budgets.size), budgets.sumOf { it.budget }) { nav.navigate(Routes.BUDGETS) },
        Tile(Icons.Rounded.EventRepeat, str(R.string.recurring), plural(R.plurals.rules_count, d.rules.size), null) { nav.navigate(Routes.RECURRING) },
        Tile(Icons.Rounded.Category, str(R.string.categories), plural(R.plurals.categories_count, d.categories.size), null) { nav.navigate(Routes.CATEGORIES) },
    )
    LazyColumn(
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, bar + 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BalancePager(d, modes, month, last, hidden, toggleHidden) }
        item { SectionTitle(str(R.string.overview), Modifier.semantics { heading() }) }
        (if (oneColumn) tiles.chunked(1) else tiles.chunked(2)).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { OverviewTile(it, tiles.indexOf(it), hidden, Modifier.weight(1f)) }
                }
            }
        }
        if (credit.isNotEmpty()) {
            item { SectionTitle(str(R.string.credit_cards), Modifier.semantics { heading() }) }
            itemsIndexed(credit, key = { _, r -> "cc" + r.account.id }) { i, r ->
                CreditSummary(r, d.accounts.filter { it.account.id == r.account.id || it.account in r.credit!!.shared }, i, hidden)
            }
        }
        item {
            SectionTitle(str(R.string.recent_transactions), Modifier.semantics { heading() }) {
                OutlinedButton(onClick = { nav.navigate(Routes.TRANSACTIONS) }, Modifier.heightIn(min = 48.dp)) { Text(str(R.string.see_all)) }
            }
        }
        val recent = d.expenses.take(5)
        if (recent.isEmpty()) item { Text(str(R.string.no_transactions_yet), color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        // Same day groups as the Transactions screen.
        com.paisaplus.compose.data.groupByDay(recent).forEach { (day, list) ->
            item(key = "h$day") { com.paisaplus.compose.ui.transactions.DayHeader(day, list, inset = 4.dp) }
            list.forEachIndexed { i, e ->
                item(key = e.id) {
                    Box(Modifier.segmented(i, list.size).clickable { nav.openPayee(e) }) {
                        TransactionRow(e, d.categoryById[e.categoryId], d.accountById[e.accountId]?.name)
                    }
                }
            }
        }
    }
}


private class Tile(val icon: ImageVector, val title: String, val caption: String, val amount: Double?, val onClick: () -> Unit)

/** Signed % change vs last month; null when last month was zero but this month wasn't. */
private fun change(now: Double, before: Double): Int? = when {
    before == 0.0 && now == 0.0 -> 0
    before == 0.0 -> null
    else -> ((now - before) / before * 100).roundToInt()
}

private val dueFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")

/** Balance card pages (Settings › Balance card); swipe to switch. */
@Composable
private fun BalancePager(d: AppData, modes: List<BalanceMode>, month: MonthTotals, last: MonthTotals, hidden: Boolean, toggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    val s = remember(d) { balanceSummary(d.accounts.map { it.account }, d.expenses, d.loans, LocalDate.now(zone), zone) }
    val pager = rememberPagerState { modes.size }
    Column {
        // Each page keeps its own height (Credit cards lists every card); the change animates.
        HorizontalPager(pager, Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { i ->
            val m = modes[i]
            val amount = when (m) {
                BalanceMode.TOTAL -> s.total
                BalanceMode.BANK -> s.bank
                BalanceMode.CREDIT -> s.cardOwed
                BalanceMode.NET_WORTH -> s.netWorth
            }
            val bills: (@Composable () -> Unit)? = if (m == BalanceMode.CREDIT && s.bills.isNotEmpty()) ({ CardBills(s.bills, hidden) }) else null
            BalancePage(m.label, amount, hidden, toggle, bills) {
                when (m) {
                    // Bank is the main page: balance with this month's income and expense.
                    BalanceMode.BANK -> {
                        MonthStat(str(R.string.income), month.income, last.income, hidden, good = true, Modifier.weight(1f))
                        MonthStat(str(R.string.expense), month.expense, last.expense, hidden, good = false, Modifier.weight(1f))
                    }
                    BalanceMode.TOTAL -> {
                        Stat(str(R.string.bank_cash), s.bank, null, hidden, Modifier.weight(1f))
                        Stat(str(R.string.cards_owed), s.cardOwed, null, hidden, Modifier.weight(1f))
                    }
                    BalanceMode.CREDIT -> {
                        Stat(str(R.string.available), s.cardAvailable, str(R.string.of_x_limit, moneyOrHidden(s.cardLimit, hidden)), hidden, Modifier.weight(1f))
                        Stat(str(R.string.next_bills), s.due, if (s.bills.isEmpty()) str(R.string.set_statement_days_on_your) else if (s.bills.size == 1) str(R.string.s_1_card) else str(R.string.x_cards, s.bills.size), hidden, Modifier.weight(1f))
                    }
                    BalanceMode.NET_WORTH -> {
                        Stat(str(R.string.assets), s.bank, str(R.string.bank_cash_wallets), hidden, Modifier.weight(1f))
                        Stat(str(R.string.liabilities), s.liabilities, str(R.string.cards_loans), hidden, Modifier.weight(1f))
                    }
                }
            }
        }
        if (modes.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(modes.size) { i ->
                val on = i == pager.currentPage
                Box(Modifier.padding(3.dp).size(if (on) 8.dp else 6.dp).background(if (on) c.primary else c.outlineVariant, CircleShape))
            }
        }
    }
}

@Composable
private fun BalancePage(title: String, amount: Double, hidden: Boolean, toggle: () -> Unit, below: (@Composable () -> Unit)?, stats: @Composable RowScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val glass = LocalGlass.current
    Surface(
        color = if (glass != null) Color.Transparent else c.primaryContainer,
        contentColor = c.onPrimaryContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
            .then(if (glass != null) Modifier.glass(glass, shape, c.primaryContainer, alpha = 0.7f, lens = 24.dp) else Modifier),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = LocalContentColor.current.copy(alpha = 0.78f), modifier = Modifier.weight(1f))
                IconButton(onClick = toggle) {
                    Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (hidden) str(R.string.show_amounts) else str(R.string.hide_amounts))
                }
            }
            AmountText(amount, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.displayMediumEmphasized, hidden = hidden)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), content = stats)
            below?.invoke()
        }
    }
}

/** Each card on its own cycle: statement date and bill, soonest first. */
@Composable
private fun CardBills(bills: List<CardBill>, hidden: Boolean) {
    val dim = LocalContentColor.current.copy(alpha = 0.78f)
    Column(Modifier.padding(top = 12.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.15f))
        bills.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.card, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(str(R.string.statement_x, b.date.format(dueFmt)), style = MaterialTheme.typography.bodySmall, color = dim)
                }
                AmountText(b.amount, Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.titleSmall, hidden = hidden)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Double, sub: String?, hidden: Boolean, modifier: Modifier) {
    val dim = LocalContentColor.current.copy(alpha = 0.78f)
    Column(modifier) {
        WrapText(label, style = MaterialTheme.typography.bodyMedium, color = dim)
        AmountText(value, style = MaterialTheme.typography.titleSmall, hidden = hidden)
        if (sub != null) WrapText(sub, style = MaterialTheme.typography.bodySmall, color = dim)
    }
}

@Composable
private fun MonthStat(label: String, now: Double, before: Double, hidden: Boolean, good: Boolean, modifier: Modifier) {
    val p = LocalPaisaColors.current
    val dim = LocalContentColor.current.copy(alpha = 0.78f)
    val pct = change(now, before)
    Column(modifier) {
        WrapText(label, style = MaterialTheme.typography.bodyMedium, color = dim)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmountText(now, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, hidden = hidden)
            if (pct != null && !hidden) {
                // Income going up is good; expense going up is bad. A tonal chip toned for the card under it
                // (dark text = light card, whatever the theme): readable even green-on-green on a bright accent card.
                val onLight = LocalContentColor.current.luminance() < 0.5f
                val t = com.paisaplus.compose.ui.theme.tonalColors(if (pct == 0 || (pct > 0) == good) p.income else p.expense, dark = !onLight)
                Spacer(Modifier.width(6.dp))
                Row(
                    Modifier.background(t.container, CircleShape).padding(start = 4.dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (pct >= 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, Modifier.size(12.dp), tint = t.content)
                    Spacer(Modifier.width(2.dp))
                    Text("${abs(pct)}%", style = MaterialTheme.typography.labelSmall, color = t.content)
                }
            }
        }
        WrapText(str(R.string.last_month_x, moneyOrHidden(before, hidden)), style = MaterialTheme.typography.bodySmall, color = dim)
    }
}

@Composable
private fun OverviewTile(t: Tile, index: Int, hidden: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    TintCard(modifier.heightIn(min = 168.dp), onClick = t.onClick) {
        // Icon row on top, then a large title that may wrap: the text fills the tile instead of leaving a gap.
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveIcon(t.icon, index, 44.dp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            WrapText(t.title, style = MaterialTheme.typography.headlineSmall, color = c.onSurface)
            Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
            WrapText(t.caption, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            if (t.amount != null) AmountText(t.amount, style = MaterialTheme.typography.headlineSmallEmphasized, color = c.primary, hidden = hidden)
        }
    }
}

@Composable
private fun CreditSummary(row: AccountRow, members: List<AccountRow>, index: Int, hidden: Boolean) {
    val u = row.credit ?: return
    val c = MaterialTheme.colorScheme
    val names = (listOf(row.account) + u.shared).joinToString { it.name }
    TintCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveIcon(Icons.Rounded.CreditCard, index + 1, 48.dp)
                Spacer(Modifier.width(12.dp))
                WrapText(names, Modifier.weight(1f), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Spacer(Modifier.height(16.dp))
            Text(str(R.string.available), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            AmountText(u.available, style = MaterialTheme.typography.headlineLargeEmphasized, color = if (u.available < 0) c.error else c.primary, hidden = hidden)
            Spacer(Modifier.height(16.dp))
            WavyProgress((u.used / u.limit).toFloat().coerceIn(0f, 1f))
            Spacer(Modifier.height(16.dp))
            Row {
                CardStat(str(R.string.used), u.used, hidden, Modifier.weight(1f))
                CardStat(str(R.string.limit), u.limit, false, Modifier.weight(1f))
                if (u.emi > 0) CardStat(str(R.string.emi), u.emi, hidden, Modifier.weight(1f))
            }
            members.mapNotNull { it.nextBill }.minByOrNull { it.date }?.let { b ->
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().background(c.surfaceContainerHigh, MaterialTheme.shapes.large).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(20.dp), tint = c.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(str(R.string.next_bill_x_x, b.date.format(dayFmt), moneyOrHidden(b.total, hidden)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (com.paisaplus.compose.ui.theme.LocalDebug.current) Text("Owed ${money(u.owed)} + EMI left ${money(u.emi)} = used ${money(u.used)}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = c.error)
        }
    }
}

@Composable
private fun CardStat(label: String, v: Double, hidden: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(v, style = MaterialTheme.typography.titleMediumEmphasized, hidden = hidden)
    }
}
