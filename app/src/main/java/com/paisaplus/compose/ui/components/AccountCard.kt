package com.paisaplus.compose.ui.components

import com.paisaplus.compose.data.cardNetwork
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.CardGroup
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Color
import com.paisaplus.compose.ui.AccountRow
import com.paisaplus.compose.ui.icon
import com.paisaplus.compose.ui.label
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.moneyOrHidden
import com.paisaplus.compose.ui.theme.LocalGlass
import com.paisaplus.compose.ui.theme.glass

/**
 * Account drawn as a physical card (Flutter AccountHeroCard): gradient from primary (credit) or
 * secondary (others) toward tertiary, two faint rings, bank + type icon, balance, holder + masked number.
 */
@Composable
fun AccountHeroCard(row: AccountRow, hidden: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val a = row.account
    val c = MaterialTheme.colorScheme
    val credit = a.cardType == CardType.CREDIT
    val bg = if (credit) c.primary else c.secondary
    val fg = if (credit) c.onPrimary else c.onSecondary
    val last4 = a.number.takeLast(4)
    val masked = if (credit) "•••• ${last4.ifBlank { "----" }}" else if (last4.isNotBlank()) "·· $last4" else a.cardType.label
    val owed = if (credit) row.totalOutstanding else a.amount
    val u = row.credit
    val spoken = buildString {
        append("${a.name}, ${a.cardType.label}")
        if (a.bankName.isNotBlank()) append(", ${a.bankName}")
        append(if (credit) str(R.string.total_outstanding_2) else str(R.string.balance_2))
        append(if (hidden) str(R.string.amount_hidden) else money(owed))
        if (u != null && !hidden) append(str(R.string.available_x_of_x, money(u.available), money(u.limit)))
    }
    HeroFrame(bg, fg, spoken, modifier, onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.bankName.ifBlank { a.cardType.label }, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // A credit card shows its network (picked or named) where the generic card icon sits.
                val network = if (credit) cardNetwork(a) else null
                if (network != null) NetworkBadge(network, fg.copy(alpha = 0.9f), 16.dp)
                else Icon(a.cardType.icon, null, tint = fg.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
            }
            Column(Modifier.padding(vertical = 12.dp)) {
            Text(if (credit) str(R.string.total_outstanding) else str(R.string.balance), style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.75f))
            AmountText(owed, style = MaterialTheme.typography.headlineLargeEmphasized, color = fg, hidden = hidden)
            if (credit) Text(
                str(R.string.owed_x_emi_x, moneyOrHidden(row.owed, hidden), moneyOrHidden(row.emiRemaining, hidden)),
                style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (u != null) Text(
                str(R.string.available_x_of_x_2, moneyOrHidden(u.available, hidden), money(u.limit)) +
                    (u.shared.takeIf { it.isNotEmpty() }?.let { s -> str(R.string.shared_with_x, s.joinToString { it.name }) } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text(masked, style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.2.sp), color = fg.copy(alpha = 0.85f), maxLines = 1)
            }
    }
}

/** Card-shaped frame shared by the account, group and member cards: gradient (or glass tint), two faint rings. */
@Composable
private fun HeroFrame(bg: Color, fg: Color, spoken: String, modifier: Modifier, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val fill = Brush.linearGradient(listOf(bg, lerp(bg, c.tertiary, 0.7f)), start = Offset.Zero, end = Offset.Infinite)
    val glass = LocalGlass.current
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            // Glass: the gradient becomes a strong tint so the card still reads as the account's colour.
            .then(if (glass != null) Modifier.glass(glass, shape, fill, alpha = 0.72f, lens = 24.dp).clip(shape) else Modifier.clip(shape).background(fill))
            .drawBehind {
                val ring = Stroke(28.dp.toPx())
                drawCircle(fg.copy(alpha = 0.10f), 100.dp.toPx(), Offset(size.width + 40.dp.toPx() - 100.dp.toPx(), -60.dp.toPx() + 100.dp.toPx()), style = ring)
                drawCircle(fg.copy(alpha = 0.07f), 90.dp.toPx(), Offset(size.width - 40.dp.toPx() - 90.dp.toPx(), size.height + 90.dp.toPx() - 90.dp.toPx()), style = ring)
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = spoken; if (onClick != null) role = Role.Button }
            .padding(22.dp),
    ) {
        // 200dp card minus 2×22dp padding; grows at large font scales instead of clipping.
        Column(Modifier.fillMaxWidth().heightIn(min = 156.dp), verticalArrangement = Arrangement.SpaceBetween, content = content)
    }
}

fun AccountEntity.last4Label() = number.takeLast(4).ifBlank { null }?.let { "•••• $it" } ?: name

/** A joint card (limitGroup): combined outstanding, owed + EMI, available of the shared limit, and its cards. */
@Composable
fun GroupHeroCard(g: CardGroup, hidden: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val fg = c.onPrimary
    val u = g.usage
    val spoken = "${g.name}, " + str(R.string.total_outstanding_2) + (if (hidden) str(R.string.amount_hidden) else money(u.used)) +
        (if (hidden) "" else str(R.string.available_x_of_x, money(u.available), money(u.limit)))
    HeroFrame(c.primary, fg, spoken, modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(CardType.CREDIT.icon, null, tint = fg.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
        }
        Column(Modifier.padding(vertical = 12.dp)) {
            val small = MaterialTheme.typography.labelMedium
            Text(str(R.string.total_outstanding), style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.75f))
            AmountText(u.used, style = MaterialTheme.typography.headlineLargeEmphasized, color = fg, hidden = hidden)
            Text(str(R.string.owed_x_emi_x, moneyOrHidden(u.owed, hidden), moneyOrHidden(u.emi, hidden)), style = small, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(str(R.string.available_x_of_x_2, moneyOrHidden(u.available, hidden), money(u.limit)), style = small, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Each card of the group: its network logo (when known) and last 4.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            g.members.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    cardNetwork(m)?.let { NetworkBadge(it, fg.copy(alpha = 0.85f), 10.dp); Spacer(Modifier.width(8.dp)) }
                    Text(m.last4Label(), style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.2.sp), color = fg.copy(alpha = 0.85f), maxLines = 1)
                }
            }
        }
    }
}
