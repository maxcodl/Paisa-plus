package com.paisaplus.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties

/**
 * A name field that suggests names you've already used (payees, people in Debts, recurring and loan names) as you
 * type: names starting with the text first, then ones containing it, at most six. Picking one calls [onPick].
 * The list doesn't take focus, so typing carries on while it's open.
 */
@Composable
fun NameField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    names: List<String>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit = onChange,
) {
    var focused by remember { mutableStateOf(false) }
    // Closed after a pick or a tap outside, until the text changes again.
    var closedFor by remember { mutableStateOf<String?>(null) }
    val q = value.trim()
    val matches = remember(q, names) {
        if (q.isEmpty()) emptyList()
        else names.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
            .sortedBy { if (it.startsWith(q, ignoreCase = true)) 0 else 1 }.take(6)
    }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }, label = { Text(label) }, singleLine = true)
        DropdownMenu(
            expanded = focused && matches.isNotEmpty() && closedFor != value,
            onDismissRequest = { closedFor = value },
            properties = PopupProperties(focusable = false),
        ) {
            matches.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { closedFor = m; onPick(m) }) }
        }
    }
}
