package com.paisaplus.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Phone-settings style (Android 16 / M3 Expressive): each row is its own tile, 2dp apart,
// large corners on the group's outside and small ones inside; rows lead with a coloured icon.

class ListGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { rows += content }
}

@Composable
fun ListGroup(modifier: Modifier = Modifier, build: ListGroupScope.() -> Unit) {
    val rows = ListGroupScope().apply(build).rows
    val outer = MaterialTheme.shapes.large
    val inner = CornerSize(4.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, row ->
            val first = i == 0
            val last = i == rows.lastIndex
            val shape = outer.copy(
                topStart = if (first) outer.topStart else inner,
                topEnd = if (first) outer.topEnd else inner,
                bottomEnd = if (last) outer.bottomEnd else inner,
                bottomStart = if (last) outer.bottomStart else inner,
            )
            TintCard(Modifier.fillMaxWidth(), shape = shape, content = row)
        }
    }
}

/** Section label above a [ListGroup]. */
@Composable
fun ListHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp).semantics { heading() },
)

/** Icon, title, optional summary and trailing; clickable when [onClick] is set. [hue] picks the icon colour. */
@Composable
fun PrefRow(
    icon: ImageVector,
    hue: Int,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val fg = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (summary == null) 56.dp else 72.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveIcon(icon, hue, 40.dp, shaped = false)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = fg)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fg.alpha))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun LinkRow(icon: ImageVector, hue: Int, title: String, summary: String? = null, onClick: () -> Unit) =
    PrefRow(icon, hue, title, summary, onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
fun SwitchRow(icon: ImageVector, hue: Int, title: String, summary: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) =
    PrefRow(icon, hue, title, summary, enabled, Role.Switch, onClick = { onChange(!checked) }) { Switch(checked, null, enabled = enabled) }

/** A row header with custom controls (sliders, chips) below; [inset] pads them to the row's edges. */
@Composable
fun PrefBlock(icon: ImageVector, hue: Int, title: String, summary: String? = null, inset: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        PrefRow(icon, hue, title, summary)
        Column(if (inset) Modifier.padding(horizontal = 16.dp) else Modifier, content = content)
    }
}
