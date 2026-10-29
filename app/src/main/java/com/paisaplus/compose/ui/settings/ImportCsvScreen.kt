package com.paisaplus.compose.ui.settings

import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.paisaplus.compose.data.STATEMENT_PROMPT
import com.paisaplus.compose.data.CSV_HEADER
import com.paisaplus.compose.data.CSV_SAMPLE
import com.paisaplus.compose.data.csvTable
import com.paisaplus.compose.ui.components.SegmentedTabs
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import kotlinx.coroutines.withContext
import com.paisaplus.compose.ui.AppViewModel
import com.paisaplus.compose.ui.UiMessage
import com.paisaplus.compose.ui.components.AmountText
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.PillButton
import com.paisaplus.compose.ui.components.SectionTitle
import com.paisaplus.compose.ui.components.TintCard
import com.paisaplus.compose.ui.components.WavyProgress
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private enum class Method(val label: String) { AI(str(R.string.use_an_ai_assistant)), TEMPLATE(str(R.string.fill_a_template)) }

/**
 * Guided import: 1 download the bank statement, 2 turn it into Paisa CSV (AI assistant + prompt, or the
 * template filled by hand, fully offline), 3 pick the CSV → preview → confirm.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportCsvScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = vm.csvPreview
    val progress = vm.csvProgress
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var method by rememberSaveable { mutableStateOf(Method.AI) }
    // The guide folds away on "Choose CSV file" so the preview and Confirm come into view.
    var guide by rememberSaveable { mutableStateOf(true) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::previewCsv) }
    var saving by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(saving.toByteArray()) } } }.isSuccess
            vm.messages.tryEmit(UiMessage(if (ok) str(R.string.saved) else str(R.string.couldn_t_save_the_file)))
        }
    }
    fun download(name: String, text: String) { saving = text; save.launch(name) }

    Scaffold(
        topBar = { BackTopBar(str(R.string.import_bank_statement), onBack) },
        // Pinned so it can't be missed below a long preview.
        bottomBar = {
            if (p != null && progress == null) PillButton(str(R.string.confirm_import), vm::commitCsv, Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), enabled = !vm.csvBusy && p.toAdd.isNotEmpty())
        },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TintCard(Modifier.fillMaxWidth(), onClick = { guide = !guide }) {
                    Row(Modifier.padding(16.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(str(R.string.how_to_get_your_csv), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(if (guide) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, str(if (guide) R.string.hide_guide else R.string.show_guide))
                    }
                }
            }
            if (guide) item {
                Step(1, str(R.string.download_your_statement), str(R.string.from_your_bank_s_app))
            }
            if (guide) item {
                TintCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StepTitle(2, str(R.string.turn_it_into_a_paisa))
                        SegmentedTabs(Method.entries, method, { it.label }, { method = it })
                        when (method) {
                            Method.AI -> {
                                Bullets(
                                    str(R.string.copy_the_prompt_below),
                                    str(R.string.open_an_ai_assistant_claude),
                                    str(R.string.save_its_reply_as_a),
                                )
                                Text(
                                    str(R.string.your_statement_is_shared_with),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton({
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText(str(R.string.paisa_prompt), STATEMENT_PROMPT)))
                                        vm.messages.tryEmit(UiMessage(str(R.string.prompt_copied)))
                                    }
                                }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.padding(end = 8.dp)); Text(str(R.string.copy_prompt))
                                }
                            }
                            Method.TEMPLATE -> {
                                Bullets(
                                    str(R.string.download_the_template_and_open),
                                    str(R.string.add_one_row_per_transaction),
                                    str(R.string.export_or_save_it_as),
                                )
                                Text(
                                    str(R.string.nothing_leaves_your_phone_use),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton({ download("paisa-template.csv", CSV_HEADER + "\n") }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Icon(Icons.Rounded.Download, null, Modifier.padding(end = 8.dp)); Text(str(R.string.download_template))
                                }
                                OutlinedButton({ download("paisa-sample.csv", CSV_SAMPLE) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Icon(Icons.Rounded.TableView, null, Modifier.padding(end = 8.dp)); Text(str(R.string.download_sample))
                                }
                            }
                        }
                        Text(str(R.string.example_sample_data), style = MaterialTheme.typography.titleSmall)
                        Sample()
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Step(3, str(R.string.import_the_csv), str(R.string.pick_the_file_check_the))
                    PillButton(str(R.string.choose_csv_file), { guide = false; pick.launch(arrayOf("text/*", "application/csv", "*/*")) }, Modifier.fillMaxWidth(), enabled = !vm.csvBusy, icon = Icons.Rounded.UploadFile)
                }
            }
            if (vm.csvBusy) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (progress == null) LoadingIndicator() else WavyProgress(progress)
                    val total = p?.toAdd?.size ?: 0
                    Text(if (progress == null) str(R.string.reading_statement) else str(R.string.importing_x_of_x, (progress * total).toInt(), total))
                }
            }
            if (p != null && progress == null) {
                item {
                    SectionTitle(str(R.string.preview))
                    Text(str(R.string.x_to_import_x_duplicates, p.toAdd.size, p.duplicates, p.errors.size), style = MaterialTheme.typography.titleSmall)
                    if (p.newAccounts.isNotEmpty()) Text(str(R.string.new_accounts_x, p.newAccounts.joinToString()), style = MaterialTheme.typography.bodyMedium)
                    if (p.newCategories.isNotEmpty()) Text(str(R.string.new_categories_x, p.newCategories.joinToString()), style = MaterialTheme.typography.bodyMedium)
                    if (p.newLoans.isNotEmpty()) Text(str(R.string.new_loans_emis_x, p.newLoans.joinToString()), style = MaterialTheme.typography.bodyMedium)
                }
                items(p.errors.take(10)) { Text(csvError(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                items(p.toAdd.take(10)) { r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.date.format(DateTimeFormatter.ISO_LOCAL_DATE)} · ${r.category} · ${r.account}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AmountText(r.amount, type = r.type, signed = true)
                    }
                }
                if (p.toAdd.size > 10) item { Text(str(R.string.and_x_more, p.toAdd.size - 10), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// Matches the English messages parseStatementCsv produces; anything else shows as-is.
private val lineError = Regex("""Line (\d+): (bad date|bad amount) "(.*)"""")
private fun csvError(e: String): String {
    lineError.matchEntire(e)?.let { m ->
        val (line, kind, value) = m.destructured
        return str(if (kind == "bad date") R.string.csv_bad_date else R.string.csv_bad_amount, line, value)
    }
    Regex("""Line (\d+): name/account required""").matchEntire(e)?.let { return str(R.string.csv_name_account_required, it.groupValues[1]) }
    if (e.startsWith("Missing columns: ")) return str(R.string.csv_missing_columns, e.removePrefix("Missing columns: "))
    if (e == "File is empty") return str(R.string.csv_file_empty)
    return e
}

@Composable
private fun StepTitle(n: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape), Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    TintCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepTitle(n, title)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bullets(vararg lines: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEachIndexed { i, l ->
            Row {
                Text("${i + 1}.", Modifier.width(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(l, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The required columns of [CSV_SAMPLE] as a small scrollable table. */
@Composable
private fun Sample() {
    val rows = remember { csvTable(CSV_SAMPLE).map { it.take(6) } }
    val widths = listOf(88, 132, 72, 64, 96, 168)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEachIndexed { r, cells ->
                Row {
                    cells.forEachIndexed { i, c ->
                        Text(
                            c, Modifier.width(widths[i].dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = if (r == 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                            color = if (r == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Text(str(R.string.s_7_optional_columns_bank_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
