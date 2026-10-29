package com.paisaplus.compose.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ColumnScope
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
import androidx.graphics.shapes.toPath
import com.paisaplus.compose.R
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.balanceSummary
import com.paisaplus.compose.plural
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.components.iconHues
import com.paisaplus.compose.ui.components.iconShapes
import com.paisaplus.compose.ui.theme.tonalColors
import java.time.LocalDate
import java.time.ZoneId

/** One overview card: same icon, title, caption and amount as Home › Overview. [index] picks the icon's shape and hue. */
class Tile internal constructor(val icon: ImageVector, val index: Int, val title: String, val caption: String?, val amount: String?)

/** Home › Overview's cards, same numbers. */
internal suspend fun overviewTiles(context: Context): List<Tile> {
    val ep = entryPoint(context)
    val m = moneyOf(context)
    val zone = ZoneId.systemDefault()
    val accounts = ep.db().accounts().list()
    val loans = ep.db().loans().list()
    val categories = ep.db().categories().list()
    val s = balanceSummary(accounts, ep.db().expenses().list(), loans, LocalDate.now(zone), zone)
    val month = monthOf(context)
    val cards = accounts.count { it.cardType == CardType.CREDIT }
    val budgets = categories.filter { it.isBudget }
    val open = loans.filter { !it.closed }
    return listOf(
        Tile(Icons.Rounded.AccountBalance, 0, str(R.string.accounts), plural(R.plurals.accounts_count, accounts.size - cards), m(s.bank)),
        Tile(Icons.Rounded.CreditCard, 1, str(R.string.credit_cards), plural(R.plurals.cards_count, cards), m(s.cardOwed)),
        Tile(Icons.Rounded.Insights, 2, str(R.string.reports), plural(R.plurals.transactions_count, month.count), m(month.expense)),
        Tile(Icons.Rounded.Savings, 3, str(R.string.budgets), plural(R.plurals.budgets_count, budgets.size), m(budgets.sumOf { it.budget })),
        Tile(Icons.Rounded.RequestQuote, 4, str(R.string.loans), str(R.string.x_active, open.size), m(open.sumOf { it.remaining })),
        Tile(Icons.Rounded.EventRepeat, 5, str(R.string.recurring), plural(R.plurals.rules_count, ep.db().recurring().list().size), null),
        Tile(Icons.Rounded.Category, 6, str(R.string.categories), plural(R.plurals.categories_count, categories.size), null),
    )
}

private val balanceIcons = listOf(Icons.Rounded.AccountBalance, Icons.Rounded.AccountBalanceWallet, Icons.Rounded.CreditCard, Icons.Rounded.Insights)

/**
 * The app's expressive icon as a bitmap (widgets can't draw shapes or vectors): Colourful icons get the
 * hue's shaped tonal pair like Home; Monet / Plain get a circle in the palette's secondary container.
 */
private fun iconBitmap(icon: ImageVector, index: Int, colorful: Boolean, r: WidgetRoles, px: Int): Bitmap {
    val (bg, fg) = if (colorful) tonalColors(Color(iconHues[index.mod(iconHues.size)]), r.dark).let { it.content to it.container }
    else r.scheme.secondaryContainer to r.scheme.onSecondaryContainer
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg.toArgb() }
    if (colorful) canvas.drawPath(iconShapes[index.mod(iconShapes.size)].toPath().apply { transform(Matrix().apply { setScale(px.toFloat(), px.toFloat()) }) }, paint)
    else canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
    val glyph = px * 0.5f
    canvas.translate((px - glyph) / 2, (px - glyph) / 2)
    canvas.scale(glyph / icon.viewportWidth, glyph / icon.viewportHeight)
    paint.color = fg.toArgb()
    fun draw(g: VectorGroup) {
        g.forEach { n ->
            when (n) {
                is VectorPath -> canvas.drawPath(n.pathData.toPath().asAndroidPath(), paint)
                is VectorGroup -> draw(n)
            }
        }
    }
    draw(icon.root)
    return bmp
}

/** Icon row (with optional [nav] at the end), title, caption, amount; [compact] for grid cells. */
@Composable
private fun ColumnScope.TileBody(context: Context, t: Tile, compact: Boolean, nav: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    val size = if (compact) 36 else 44
    val px = (size * context.resources.displayMetrics.density).toInt()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(iconBitmap(t.icon, t.index, p.colorfulIcons, p.current(context), px)), null, GlanceModifier.size(size.dp))
        Spacer(GlanceModifier.defaultWeight())
        nav?.invoke()
    }
    Spacer(GlanceModifier.height(if (compact) 6.dp else 10.dp))
    Text(t.title, style = TextStyle(color = WidgetFg, fontSize = if (compact) 15.sp else 20.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    Spacer(GlanceModifier.defaultWeight())
    if (t.caption != null) Text(t.caption, style = TextStyle(color = WidgetFg, fontSize = if (compact) 11.sp else 13.sp), maxLines = if (t.amount == null) 2 else 1)
    if (t.amount != null) Text(t.amount, style = TextStyle(color = p.amount, fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
private fun Arrow(label: String, delta: Int) = Box(
    GlanceModifier.size(36.dp).cornerRadius(18.dp).clickable(actionRunCallback<TilePage>(actionParametersOf(DeltaKey to delta))),
    contentAlignment = Alignment.Center,
) { Text(label, style = TextStyle(color = WidgetFg, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }

/**
 * 2×2, the "anything" widget: one overview card or balance page at a time, ‹ › to switch.
 * Each placed widget keeps its own page, so several can show different things.
 */
class OverviewTileWidget : PaisaWidget<List<Tile>>() {
    override suspend fun load(context: Context) =
        overviewTiles(context) + balancePages(context).map { Tile(balanceIcons[it.mode.ordinal], it.mode.ordinal, it.title, it.detail, it.amount) }

    @Composable
    override fun Content(context: Context, data: List<Tile>) {
        val t = data[(currentState(PageKey) ?: 0).mod(data.size)]
        Card(openApp(context)) {
            TileBody(context, t, compact = false) { Arrow("‹", -1); Arrow("›", 1) }
        }
    }
}

/** 4×3: every overview card in two columns, like Home; scrolls when the widget is smaller. */
class OverviewGridWidget : PaisaWidget<List<Tile>>() {
    override suspend fun load(context: Context) = overviewTiles(context)

    @Composable
    override fun Content(context: Context, data: List<Tile>) {
        LazyVerticalGrid(GridCells.Fixed(2), GlanceModifier.fillMaxSize()) {
            items(data) { t ->
                Box(GlanceModifier.padding(4.dp)) {
                    Card(openApp(context), GlanceModifier.fillMaxWidth().height(140.dp), padding = 14.dp) { TileBody(context, t, compact = true) }
                }
            }
        }
    }
}

internal val DeltaKey = ActionParameters.Key<Int>("delta")

/** ‹ / › on the overview tile: previous / next card for this widget instance. */
class TilePage : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[PageKey] = (it[PageKey] ?: 0) + (parameters[DeltaKey] ?: 1) }
        OverviewTileWidget().update(context, glanceId)
    }
}

class OverviewTileWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewTileWidget()
}

class OverviewGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewGridWidget()
}
