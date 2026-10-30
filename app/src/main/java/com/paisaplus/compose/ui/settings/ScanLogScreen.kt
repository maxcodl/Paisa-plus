package com.paisaplus.compose.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paisaplus.compose.R
import com.paisaplus.compose.messages.ScanLog
import com.paisaplus.compose.str
import com.paisaplus.compose.ui.components.BackTopBar
import com.paisaplus.compose.ui.components.EmptyState
import com.paisaplus.compose.ui.components.TintCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val whenFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

/** Settings › SMS & AI › Scan log: what the last past-SMS scan did with each message, and why. */
@Composable
fun ScanLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all = remember { ScanLog.read(ctx) }
    val summary = all.firstOrNull { it[0] == "SUMMARY" }?.get(2)
    val rows = remember(all) { all.filter { it[0] != "SUMMARY" }.sortedByDescending { it[1].toLongOrNull() ?: 0L } }
    val tags = remember(rows) { rows.map { it[0] }.distinct() }
    var tag by rememberSaveable { mutableStateOf("") } // "" = all
    val shown = if (tag.isEmpty()) rows else rows.filter { it[0] == tag }
    val c = MaterialTheme.colorScheme

    Scaffold(topBar = {
        BackTopBar(str(R.string.scan_log), onBack) {
            IconButton({
                val text = (listOfNotNull(summary) + shown.map { r -> "${r[0]} · ${stamp(r[1])} · ${r[2]}${r[3].takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()}" }).joinToString("\n")
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("scan log", text))
            }) { Icon(Icons.Rounded.ContentCopy, str(R.string.copy)) }
        }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (summary != null) item { Text(summary, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant) }
            if (tags.size > 1) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(tag.isEmpty(), { tag = "" }, { Text(str(R.string.all)) })
                    tags.forEach { t -> FilterChip(tag == t, { tag = t }, { Text("${t.lowercase().replaceFirstChar { it.titlecase() }} ${rows.count { it[0] == t }}") }) }
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Rounded.History, str(R.string.no_scan_log)) }
            items(shown) { r ->
                val color = when (r[0]) {
                    "ADDED" -> c.primary; "REVIEW" -> c.tertiary; "MERGED" -> c.secondary
                    "IGNORED", "ERROR" -> c.error; else -> c.onSurfaceVariant
                }
                TintCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(r[0], style = MaterialTheme.typography.labelMedium, color = color)
                            Text(stamp(r[1]), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        }
                        Text(r[2], style = MaterialTheme.typography.bodyMedium)
                        if (r[3].isNotEmpty()) Text(r[3], style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The scan's lines as they happen (newest last, following the end), with progress; stays after the scan until the next one. */
@Composable
fun LiveScanLog(busy: Boolean) {
    val live by ScanLog.live.collectAsStateWithLifecycle()
    val progress by ScanLog.progress.collectAsStateWithLifecycle()
    if (!busy && live.isEmpty()) return
    val state = rememberLazyListState()
    LaunchedEffect(live.size) { if (live.isNotEmpty()) state.scrollToItem(live.lastIndex) }
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        if (busy) {
            Text(str(R.string.scanning_x_of_y, progress.first, progress.second), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator({ if (progress.second > 0) progress.first / progress.second.toFloat() else 0f }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), state) {
            items(live) { r ->
                val color = when (r[0]) {
                    "ADDED" -> c.primary; "REVIEW" -> c.tertiary; "MERGED" -> c.secondary
                    "IGNORED", "ERROR" -> c.error; else -> c.onSurfaceVariant
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(r[0]) }
                        append("  "); append(r[2])
                        if (r[3].isNotEmpty()) withStyle(SpanStyle(color = c.onSurfaceVariant)) { append(" — "); append(r[3]) }
                    },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private fun stamp(ms: String) = ms.toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(whenFmt) }.orEmpty()
