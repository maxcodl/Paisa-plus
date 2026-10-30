package com.paisaplus.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.ui.paisaIcon

/** Category chips; tapping the selected one again clears it (-1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(categories: List<CategoryEntity>, selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { c ->
            FilterChip(
                selected = c.id == selected,
                onClick = { onSelect(if (selected == c.id) -1 else c.id) },
                label = { Text(c.name) },
                // The same badge colours as the lists (an uncoloured category gets its icon's hue there too).
                leadingIcon = { IconBadge(paisaIcon(c.icon), Color(c.color), 22.dp, autoHue = true) },
            )
        }
    }
}
