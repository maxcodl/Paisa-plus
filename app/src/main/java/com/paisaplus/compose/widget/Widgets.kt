package com.paisaplus.compose.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.paisaplus.compose.MainActivity
import com.paisaplus.compose.R
import com.paisaplus.compose.data.BudgetSummary
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.data.balanceSummary
import com.paisaplus.compose.data.budgets
import com.paisaplus.compose.data.currentPeriod
import com.paisaplus.compose.str
import com.paisaplus.compose.data.DebtType
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.data.monthTotals
import com.paisaplus.compose.data.salaryIds
import com.paisaplus.compose.data.transferIds
import com.paisaplus.compose.ui.DebtRow
import com.paisaplus.compose.data.ThemeMode
import com.paisaplus.compose.data.WidgetStyle
import android.os.Build
import com.paisaplus.compose.ui.theme.paisaScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.glance.layout.ColumnScope
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One mode's widget colours. [bar]: progress fill; [btn]: the Add / + button. */
internal data class WidgetRoles(val card: Color, val fg: Color, val btn: Color, val onBtn: Color, val bar: Color, val error: Color, val track: Color)

/** App colours: the app's own scheme (accent / Monet, variant, contrast). */
private fun ColorScheme.roles(opacity: Float) =
    WidgetRoles(primaryContainer.copy(alpha = opacity), onPrimaryContainer, primary, onPrimary, primary, error, onPrimaryContainer.copy(alpha = 0.2f))

private val NothingRed = Color(0xFFD71921)

/** Nothing OS look: flat grey card, monochrome text and bars, red accent. */
private fun nothingRoles(dark: Boolean, opacity: Float) =
    if (dark) WidgetRoles(Color(0xFF1B1B1B).copy(alpha = opacity), Color.White, NothingRed, Color.White, Color.White, NothingRed, Color.White.copy(alpha = 0.15f))
    else WidgetRoles(Color(0xFFF2F2F2).copy(alpha = opacity), Color(0xFF111111), NothingRed, Color.White, Color(0xFF111111), NothingRed, Color.Black.copy(alpha = 0.12f))

internal class WidgetPalette(private val day: WidgetRoles, private val night: WidgetRoles, val sheen: Boolean, val addButton: Boolean) {
    private fun p(f: (WidgetRoles) -> Color) = ColorProvider(day = f(day), night = f(night))
    val card = p { it.card }
    val fg = p { it.fg }
    val btn = p { it.btn }
    val onBtn = p { it.onBtn }
    val bar = p { it.bar }
    val error = p { it.error }
    val track = p { it.track }
}

internal val LocalPalette = staticCompositionLocalOf<WidgetPalette> { error("WidgetTheme missing") }
internal val WidgetFg: ColorProvider @Composable get() = LocalPalette.current.fg
internal val WidgetBtn: ColorProvider @Composable get() = LocalPalette.current.btn
internal val WidgetOnBtn: ColorProvider @Composable get() = LocalPalette.current.onBtn
private val WidgetError: ColorProvider @Composable get() = LocalPalette.current.error
private val WidgetTrack: ColorProvider @Composable get() = LocalPalette.current.track

/** Palette for Settings › Home-screen widgets and the app theme; Light / Dark theme mode pins the widget too, System follows the launcher. */
internal suspend fun palette(context: Context): WidgetPalette {
    val s = entryPoint(context).settings()
    val t = s.theme.first()
    val w = s.widget.first()
    fun roles(dark: Boolean) = if (w.style == WidgetStyle.NOTHING) nothingRoles(dark, w.opacity) else paisaScheme(context, t, dark).roles(w.opacity)
    val (day, night) = when (t.mode) {
        ThemeMode.LIGHT -> roles(false).let { it to it }
        ThemeMode.DARK -> roles(true).let { it to it }
        ThemeMode.SYSTEM -> roles(false) to roles(true)
    }
    return WidgetPalette(day, night, sheen = w.style == WidgetStyle.APP && w.sheen, addButton = w.addButton)
}

/** The launcher's own widget corner radius (Android 12+), so the cards match the rest of the home screen. */
private fun GlanceModifier.corners() =
    if (Build.VERSION.SDK_INT >= 31) cornerRadius(android.R.dimen.system_app_widget_background_radius) else cornerRadius(28.dp)

@Composable
internal fun WidgetTheme(p: WidgetPalette, content: @Composable () -> Unit) =
    GlanceTheme { CompositionLocalProvider(LocalPalette provides p, content = content) }

internal fun openApp(context: Context) = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun addExpense(context: Context) = Intent(context, MainActivity::class.java)
    .putExtra(MainActivity.EXTRA_ADD, true)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** The card every widget sits on: palette tint at the chosen opacity, optionally under a glass sheen + rim; tapping it opens [open]. */
@Composable
internal fun Card(open: Intent, padding: Dp = 16.dp, center: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Box(GlanceModifier.fillMaxSize().background(LocalPalette.current.card).corners().clickable(actionStartActivity(open))) {
        val inner = GlanceModifier.fillMaxSize()
        Column(
            (if (LocalPalette.current.sheen) inner.background(ImageProvider(R.drawable.widget_glass)).corners() else inner).padding(padding),
            verticalAlignment = if (center) Alignment.CenterVertically else Alignment.Top,
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            content = content,
        )
    }

private fun entryPoint(context: Context) = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

/** Money formatter honouring "Hide amounts". */
private suspend fun moneyOf(context: Context): (Double) -> String {
    val s = entryPoint(context).settings()
    val hidden = s.hideBalance.first()
    Money.use(s.money.first())
    return { if (hidden) "••••••" else Money.format(it) }
}

/** This month's budgets, same numbers as Settings › Budgets. */
private suspend fun budgetSummary(context: Context): BudgetSummary {
    val ep = entryPoint(context)
    val start = ep.settings().monthStart.first()
    return budgets(ep.db().categories().list(), ep.db().expenses().list(), currentPeriod(start), start = start)
}

/** 1×1: total balance; the amount shrinks to fit the cell. */
class TotalBalanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        val m = moneyOf(context)
        val ep = entryPoint(context)
        val zone = ZoneId.systemDefault()
        val amount = m(balanceSummary(ep.db().accounts().list(), ep.db().expenses().list(), ep.db().loans().list(), LocalDate.now(zone), zone).total)
        provideContent {
            WidgetTheme(p) {
                val fit = ((LocalSize.current.width.value - 24) / (amount.length * 0.62f)).coerceIn(11f, 26f)
                Card(openApp(context), padding = 12.dp) {
                    Text(str(R.string.total_balance), style = TextStyle(color = WidgetFg, fontSize = 11.sp), maxLines = 2)
                    Text(amount, style = TextStyle(color = WidgetFg, fontSize = fit.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Bar(spent: Double, budget: Double) {
    val over = spent > budget
    LinearProgressIndicator(
        progress = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f,
        modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
        color = if (over) WidgetError else LocalPalette.current.bar,
        backgroundColor = WidgetTrack,
    )
}

@Composable
private fun NoBudgets() = Text(str(R.string.no_budgets_yet_tap_set), style = TextStyle(color = WidgetFg, fontSize = 12.sp), maxLines = 3)

/** 2×2: this month's spending against all budgets combined. */
class BudgetTrackerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        val m = moneyOf(context)
        val b = budgetSummary(context)
        val left = b.budget - b.spent
        provideContent {
            WidgetTheme(p) {
                Card(openApp(context)) {
                    Text(str(R.string.budgets), style = TextStyle(color = WidgetFg, fontSize = 13.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    if (b.rows.isEmpty()) NoBudgets() else {
                        Text(m(b.spent), style = TextStyle(color = WidgetFg, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(str(R.string.budget_x, m(b.budget)), style = TextStyle(color = WidgetFg, fontSize = 12.sp), maxLines = 1)
                        Spacer(GlanceModifier.height(10.dp))
                        Bar(b.spent, b.budget)
                        Spacer(GlanceModifier.height(6.dp))
                        Text(
                            if (left < 0) str(R.string.over_by_x, m(-left)) else str(R.string.x_left, m(left)),
                            style = TextStyle(color = if (left < 0) WidgetError else WidgetFg, fontSize = 12.sp), maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 3×2: every budget category with its own bar, scrollable. */
class BudgetSummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        val m = moneyOf(context)
        val b = budgetSummary(context)
        provideContent {
            WidgetTheme(p) {
                Card(openApp(context)) {
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text(str(R.string.budgets), GlanceModifier.defaultWeight(), style = TextStyle(color = WidgetFg, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        if (b.rows.isNotEmpty()) Text(str(R.string.x_of_x, m(b.spent), m(b.budget)), style = TextStyle(color = WidgetFg, fontSize = 12.sp), maxLines = 1)
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    if (b.rows.isEmpty()) NoBudgets() else LazyColumn {
                        items(b.rows, itemId = { it.category.id }) { r ->
                            Column(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(GlanceModifier.fillMaxWidth()) {
                                    Text(r.category.name, GlanceModifier.defaultWeight(), style = TextStyle(color = WidgetFg, fontSize = 12.sp), maxLines = 1)
                                    Text(str(R.string.x_of_x, m(r.spent), m(r.category.budget)), style = TextStyle(color = if (r.over) WidgetError else WidgetFg, fontSize = 12.sp), maxLines = 1)
                                }
                                Spacer(GlanceModifier.height(4.dp))
                                Bar(r.spent, r.category.budget)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 1×1: one tap to the add sheet. */
class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        provideContent {
            WidgetTheme(p) {
                Card(addExpense(context), center = true) {
                    Box(GlanceModifier.size(44.dp).background(WidgetBtn).cornerRadius(22.dp), contentAlignment = Alignment.Center) {
                        Text("+", style = TextStyle(color = WidgetOnBtn, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String, end: String? = null) = Row(GlanceModifier.fillMaxWidth()) {
    Text(text, GlanceModifier.defaultWeight(), style = TextStyle(color = WidgetFg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    if (end != null) Text(end, style = TextStyle(color = WidgetFg, fontSize = 12.sp), maxLines = 1)
}

@Composable
private fun Line(label: String, value: String, color: androidx.glance.unit.ColorProvider = WidgetFg) = Row(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
    Text(label, GlanceModifier.defaultWeight(), style = TextStyle(color = WidgetFg, fontSize = 12.sp), maxLines = 1)
    Text(value, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM")

/** 2×2: this month's income, expense and net, same numbers as Home. */
class ThisMonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        val m = moneyOf(context)
        val ep = entryPoint(context)
        val start = ep.settings().monthStart.first()
        val categories = ep.db().categories().list()
        val skip = if (ep.settings().excludeTransfers.first()) transferIds(categories) else emptySet()
        val t = monthTotals(ep.db().expenses().list(), currentPeriod(start), ZoneId.systemDefault(), skip, salaryIds(categories), start)
        provideContent {
            WidgetTheme(p) {
                Card(openApp(context)) {
                    Title(str(R.string.this_month))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(m(t.net), style = TextStyle(color = if (t.net < 0) WidgetError else WidgetFg, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(str(R.string.net), style = TextStyle(color = WidgetFg, fontSize = 12.sp))
                    Spacer(GlanceModifier.height(8.dp))
                    Bar(t.expense, t.income)
                    Spacer(GlanceModifier.height(8.dp))
                    Line(str(R.string.income), m(t.income))
                    Line(str(R.string.expense), m(t.expense))
                }
            }
        }
    }
}

/** 3×2: what's due next: recurring rules, open loans / EMIs and the credit card bill, soonest first. */
class UpcomingWidget : GlanceAppWidget() {
    private data class Due(val name: String, val amount: Double, val date: LocalDate, val income: Boolean = false)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        val m = moneyOf(context)
        val ep = entryPoint(context)
        val zone = ZoneId.systemDefault()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val loans = ep.db().loans().list()
        val s = balanceSummary(ep.db().accounts().list(), ep.db().expenses().list(), loans, LocalDate.now(zone), zone)
        val due = (ep.db().recurring().list().map { Due(it.name, it.amount, day(it.next), it.type == TransactionType.INCOME) } +
            loans.filter { !it.closed }.map { Due(it.name, it.emi, day(it.nextDue)) } +
            listOfNotNull(s.dueDate?.takeIf { s.due > 0 }?.let { Due(str(R.string.card_bill), s.due, it) }))
            .sortedBy { it.date }.take(8)
        provideContent {
            WidgetTheme(p) {
                Card(openApp(context)) {
                    Title(str(R.string.upcoming))
                    Spacer(GlanceModifier.height(8.dp))
                    if (due.isEmpty()) Text(str(R.string.nothing_due_soon), style = TextStyle(color = WidgetFg, fontSize = 12.sp))
                    else LazyColumn {
                        items(due) { d -> Line("${d.date.format(dayFmt)} · ${d.name}", (if (d.income) "+" else "") + m(d.amount)) }
                    }
                }
            }
        }
    }
}

/** 2×2: lent and borrowed still outstanding, and the next one due. */
class DebtsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        val m = moneyOf(context)
        val ep = entryPoint(context)
        val byDebt = ep.db().debts().allTransactions().groupBy { it.debtId }
        val open = ep.db().debts().list().map { DebtRow(it, byDebt[it.id].orEmpty()) }.filter { it.remaining > 0 }
        val lent = open.filter { it.debt.debtType == DebtType.CREDIT }.sumOf { it.remaining }
        val owed = open.filter { it.debt.debtType == DebtType.DEBT }.sumOf { it.remaining }
        val zone = ZoneId.systemDefault()
        val next = open.minByOrNull { it.debt.expiryDateTime }
        provideContent {
            WidgetTheme(p) {
                Card(openApp(context)) {
                    Title(str(R.string.debts))
                    Spacer(GlanceModifier.defaultWeight())
                    Line(str(R.string.owed_to_you), m(lent))
                    Line(str(R.string.you_owe), m(owed), if (owed > 0) WidgetError else WidgetFg)
                    if (next != null) Text(
                        "${next.debt.name} · " + str(R.string.due_x, Instant.ofEpochMilli(next.debt.expiryDateTime).atZone(zone).toLocalDate().format(dayFmt)),
                        style = TextStyle(color = WidgetFg, fontSize = 11.sp), maxLines = 1,
                    )
                }
            }
        }
    }
}

class ThisMonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThisMonthWidget()
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}

class DebtsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DebtsWidget()
}

class TotalBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TotalBalanceWidget()
}

class BudgetTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetTrackerWidget()
}

class BudgetSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetSummaryWidget()
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
