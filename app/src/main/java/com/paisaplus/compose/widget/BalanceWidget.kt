package com.paisaplus.compose.widget

import com.paisaplus.compose.str

import com.paisaplus.compose.data.currentPeriod
import com.paisaplus.compose.data.monthTotals
import com.paisaplus.compose.data.salaryIds
import com.paisaplus.compose.data.transferIds
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier

import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Row
import androidx.glance.layout.size
import com.paisaplus.compose.data.BalanceMode
import com.paisaplus.compose.data.balanceSummary
import java.time.LocalDate
import com.paisaplus.compose.R
import com.paisaplus.compose.data.Money
import com.paisaplus.compose.data.PaisaDatabase
import com.paisaplus.compose.data.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import java.time.ZoneId

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun db(): PaisaDatabase
    fun settings(): SettingsStore
}

/** One widget page: title, big amount, one detail line. */
private data class Page(val title: String, val amount: String, val detail: String?)

/**
 * Home-screen card: the balance pages chosen in Settings › Balance card (masked when "Hide amounts" is on),
 * › cycles them (widgets can't take swipes; the page is per widget), Add expense.
 */
class BalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = palette(context)
        val ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val hidden = ep.settings().hideBalance.first()
        Money.use(ep.settings().money.first())
        val modes = ep.settings().balanceModes.first()
        val zone = ZoneId.systemDefault()
        val expenses = ep.db().expenses().list()
        val s = balanceSummary(ep.db().accounts().list(), expenses, ep.db().loans().list(), LocalDate.now(zone), zone)
        val start = ep.settings().monthStart.first()
        val month = currentPeriod(start, zone)
        val categories = ep.db().categories().list()
        val skip = if (ep.settings().excludeTransfers.first()) transferIds(categories) else emptySet()
        val spent = monthTotals(expenses, month, zone, skip = skip, salary = salaryIds(categories), start = start).expense
        fun m(v: Double) = if (hidden) "••••••" else Money.format(v)
        val pages = modes.map { mode ->
            when (mode) {
                BalanceMode.BANK -> Page(mode.label, m(s.bank), str(R.string.spent_this_month_x, m(spent)))
                BalanceMode.TOTAL -> Page(mode.label, m(s.total), str(R.string.bank_x_cards_owed_x, m(s.bank), m(s.cardOwed)))
                BalanceMode.CREDIT -> Page(mode.label, m(s.cardOwed), str(R.string.available_x_due_x, m(s.cardAvailable), m(s.due)) + (s.dueDate?.let { " · ${it.format(dueFmt)}" } ?: ""))
                BalanceMode.NET_WORTH -> Page(mode.label, m(s.netWorth), str(R.string.assets_x_owed_x, m(s.bank), m(s.liabilities)))
            }
        }
        provideContent {
            val index = currentState(PageKey) ?: 0
            WidgetTheme(p) {
                Content(
                    page = pages[index.mod(pages.size)],
                    paged = pages.size > 1,
                    open = openApp(context),
                    add = addExpense(context),
                )
            }
        }
    }

    @Composable
    private fun Content(page: Page, paged: Boolean, open: Intent, add: Intent) {
        val fg = WidgetFg
        val btn = WidgetBtn
        val onBtn = WidgetOnBtn
        Card(open) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(page.title, GlanceModifier.defaultWeight(), style = TextStyle(color = fg, fontSize = 13.sp))
                if (paged) Box(
                    GlanceModifier.size(48.dp).cornerRadius(24.dp).clickable(actionRunCallback<NextPage>()),
                    contentAlignment = Alignment.Center,
                ) { Text("›", style = TextStyle(color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)) }
            }
            Text(page.amount, style = TextStyle(color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (page.detail != null) Text(page.detail, style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (LocalPalette.current.addButton) Box(
                GlanceModifier.fillMaxWidth().height(44.dp).background(btn).cornerRadius(22.dp).clickable(actionStartActivity(add)),
                contentAlignment = Alignment.Center,
            ) { Text(str(R.string.add_expense), style = TextStyle(color = onBtn, fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
        }
    }

    companion object {
        /** Redraws every data widget (Quick add has no data). */
        suspend fun refresh(context: Context) = runCatching {
            BalanceWidget().updateAll(context)
            TotalBalanceWidget().updateAll(context)
            BudgetTrackerWidget().updateAll(context)
            BudgetSummaryWidget().updateAll(context)
            ThisMonthWidget().updateAll(context)
            UpcomingWidget().updateAll(context)
            DebtsWidget().updateAll(context)
        }
    }
}

private val PageKey = intPreferencesKey("page")
private val dueFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")

/** › on the widget: next balance page for this widget instance. */
class NextPage : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[PageKey] = (it[PageKey] ?: 0) + 1 }
        BalanceWidget().update(context, glanceId)
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
