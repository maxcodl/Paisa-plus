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
class Page internal constructor(val mode: BalanceMode, val title: String, val amount: String, val detail: String?)

/**
 * Home-screen card: the balance pages chosen in Settings › Home & navigation (masked when "Hide amounts" is on),
 * › cycles them (widgets can't take swipes; the page is per widget), Add expense.
 */
/** The balance pages chosen in settings, formatted (shared with the overview tile widget). */
internal suspend fun balancePages(context: Context): List<Page> {
        val ep = entryPoint(context)
        val m = moneyOf(context)
        val zone = ZoneId.systemDefault()
        val s = balanceSummary(ep.db().accounts().list(), ep.db().expenses().list(), ep.db().loans().list(), LocalDate.now(zone), zone)
        val spent = monthOf(context).expense
        return ep.settings().balanceModes.first().map { mode ->
            when (mode) {
                BalanceMode.BANK -> Page(mode, mode.label, m(s.bank), str(R.string.spent_this_month_x, m(spent)))
                BalanceMode.TOTAL -> Page(mode, mode.label, m(s.total), str(R.string.bank_x_cards_owed_x, m(s.bank), m(s.cardOwed)))
                BalanceMode.CREDIT -> Page(mode, mode.label, m(s.cardOwed), str(R.string.available_x_due_x, m(s.cardAvailable), m(s.due)) + (s.dueDate?.let { " · ${it.format(dayFmt)}" } ?: ""))
                BalanceMode.NET_WORTH -> Page(mode, mode.label, m(s.netWorth), str(R.string.assets_x_owed_x, m(s.bank), m(s.liabilities)))
            }
        }
}

class BalanceWidget : PaisaWidget<List<Page>>() {
    override suspend fun load(context: Context) = balancePages(context)

    @Composable
    override fun Content(context: Context, data: List<Page>) {
        val page = data[(currentState(PageKey) ?: 0).mod(data.size)]
        val fg = WidgetFg
        Card(openApp(context)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(page.title, GlanceModifier.defaultWeight(), style = TextStyle(color = fg, fontSize = 13.sp))
                if (data.size > 1) Box(
                    GlanceModifier.size(48.dp).cornerRadius(24.dp).clickable(actionRunCallback<NextPage>()),
                    contentAlignment = Alignment.Center,
                ) { Text("›", style = TextStyle(color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)) }
            }
            Text(page.amount, style = TextStyle(color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (page.detail != null) Text(page.detail, style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (LocalPalette.current.addButton) Box(
                GlanceModifier.fillMaxWidth().height(44.dp).background(WidgetBtn).cornerRadius(22.dp).clickable(actionStartActivity(addExpense(context))),
                contentAlignment = Alignment.Center,
            ) { Text(str(R.string.add_expense), style = TextStyle(color = WidgetOnBtn, fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = refreshWidgets(context)
    }
}

/** This widget instance's page (balance widget, overview tile); each instance keeps its own. */
internal val PageKey = intPreferencesKey("page")

/** › on the balance widget: next page for this widget instance. */
class NextPage : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[PageKey] = (it[PageKey] ?: 0) + 1 }
        BalanceWidget().update(context, glanceId)
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
