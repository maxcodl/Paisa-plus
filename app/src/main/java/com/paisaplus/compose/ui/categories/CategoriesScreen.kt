package com.paisaplus.compose.ui.categories

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import com.paisaplus.compose.ui.components.EmptyState
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.FloatingActionButton
import com.paisaplus.compose.ui.theme.LocalMonthStart
import com.paisaplus.compose.data.currentPeriod
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.paisaplus.compose.ui.components.rememberSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.data.spendByCategory
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.components.IconBadge
import com.paisaplus.compose.ui.MDI_TAG
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.money
import com.paisaplus.compose.ui.paisaIcon
import com.paisaplus.compose.ui.pickerIcons
import java.time.YearMonth

private val palette = listOf(
    0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2, 0xFF5C6BC0, 0xFF42A5F5, 0xFF29B6F6, 0xFF26C6DA,
    0xFF26A69A, 0xFF66BB6A, 0xFF9CCC65, 0xFFD4E157, 0xFFFFEE58, 0xFFFFC107, 0xFFFFA726, 0xFFFF7043,
    0xFF8D6E63, 0xFF78909C,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoriesScreen(vm: AppViewModel, outer: PaddingValues, onBack: (() -> Unit)? = null) {
    val d by vm.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val start = LocalMonthStart.current
    val spent = remember(d.expenses, start) { spendByCategory(d.expenses, currentPeriod(start), start = start) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { com.paisaplus.compose.ui.components.BackTopBar(str(R.string.categories), onBack) },
        floatingActionButton = {
            Box(Modifier.padding(bottom = outer.calculateBottomPadding())) {
                FloatingActionButton(onClick = { editing = CategoryEntity(name = "", icon = MDI_TAG) }) { Icon(Icons.Rounded.Add, str(R.string.add_category)) }
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, outer.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (d.categories.isEmpty()) item { EmptyState(Icons.Rounded.Category, str(R.string.no_categories_yet)) }
            // Theme surfaces (not each category's colour at 12%, which went muddy), biggest spend first,
            // "This month" said once with the total, and a share bar so the list reads at a glance.
            val total = d.categories.sumOf { spent[it.id] ?: 0.0 }
            if (d.categories.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(str(R.string.this_month), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(total), style = MaterialTheme.typography.headlineMediumEmphasized)
                }
            }
            items(d.categories.sortedByDescending { spent[it.id] ?: 0.0 }, key = { it.id }) { c ->
                val color = Color(c.color)
                val used = spent[c.id] ?: 0.0
                TintCard(Modifier.fillMaxWidth().animateItem(), onClick = { editing = c }, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(paisaIcon(c.icon), color, autoHue = true)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                c.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            Text(money(used), style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                        if (!(c.isBudget && c.budget > 0) && total > 0 && used > 0) {
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (used / total).toFloat() }, Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {},
                            )
                        }
                        if (c.isBudget && c.budget > 0) {
                            Spacer(Modifier.height(12.dp))
                            com.paisaplus.compose.ui.components.WavyProgress(
                                (used / c.budget).toFloat(),
                                color = if (used > c.budget) MaterialTheme.colorScheme.error else color,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (used > c.budget) str(R.string.over_budget_by_x, money(used - c.budget)) else str(R.string.x_left_of_x, money(c.budget - used), money(c.budget)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    editing?.let { c ->
        CategorySheet(
            c,
            txCount = d.expenses.count { it.categoryId == c.id },
            onDismiss = { editing = null },
            onSave = { vm.saveCategory(it); editing = null },
            onDelete = { vm.deleteCategory(c); editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(c: CategoryEntity, txCount: Int, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit, onDelete: () -> Unit) {
    val isNew = c.id == 0L
    var name by rememberSaveable { mutableStateOf(c.name) }
    var desc by rememberSaveable { mutableStateOf(c.description.orEmpty()) }
    var isBudget by rememberSaveable { mutableStateOf(c.isBudget) }
    var budget by rememberSaveable { mutableStateOf(if (c.budget == 0.0) "" else c.budget.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var color by rememberSaveable { mutableIntStateOf(c.color) }
    var icon by rememberSaveable { mutableIntStateOf(c.icon) }
    var confirmDelete by remember { mutableStateOf(false) }
    val budgetValue = budget.replace(',', '.').toDoubleOrNull()
    val budgetOk = !isBudget || (budgetValue != null && budgetValue > 0)
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberSheetState()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(paisaIcon(icon), Color(color), 52.dp)
                Spacer(Modifier.width(14.dp))
                Text(if (isNew) str(R.string.add_category) else str(R.string.edit_category), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(name, { name = it }, label = { Text(str(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text(str(R.string.description_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.monthly_budget), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(isBudget, { isBudget = it })
            }
            if (isBudget) OutlinedTextField(
                budget, { budget = it }, label = { Text(str(R.string.budget)) }, isError = !budgetOk,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text(str(R.string.colour), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (if (color in palette) palette else palette + color).forEach { p ->
                    Box(
                        Modifier.size(36.dp).background(Color(p), CircleShape)
                            .border(if (p == color) 3.dp else 0.dp, if (p == color) cs.onSurface else Color.Transparent, CircleShape)
                            .clickable { color = p },
                    )
                }
            }
            Text(str(R.string.icon), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pickerIcons.forEach { (code, vector) ->
                    val on = paisaIcon(code) == paisaIcon(icon)
                    Box(
                        Modifier.size(44.dp).background(if (on) Color(color).copy(alpha = 0.3f) else cs.surfaceContainerHigh, CircleShape)
                            .clickable { icon = code },
                        Alignment.Center,
                    ) { Icon(vector, null, tint = if (on) cs.onSurface else cs.onSurfaceVariant) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) OutlinedButton(onClick = { confirmDelete = true }) { Text(str(R.string.delete)) }
                Button(
                    onClick = {
                        onSave(
                            c.copy(
                                name = name.trim(), description = desc.trim().ifEmpty { null }, isBudget = isBudget,
                                budget = if (isBudget) budgetValue ?: 0.0 else c.budget, color = color, icon = icon,
                            ),
                        )
                    },
                    enabled = name.isNotBlank() && budgetOk,
                    modifier = Modifier.weight(1f),
                ) { Text(str(R.string.save)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(str(R.string.delete_x, c.name)) },
            text = { Text(if (txCount > 0) str(R.string.deletes_x_transactions_and_reverses, txCount) else str(R.string.this_can_t_be_undone)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(str(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
        )
    }
}

