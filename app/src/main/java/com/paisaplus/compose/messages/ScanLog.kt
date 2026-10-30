package com.paisaplus.compose.messages

import android.content.Context
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * What the last "Scan past SMS" did with each message, kept in a file (tab-separated: TAG, date, message, why) so
 * Settings › SMS & AI › Scan log can show it after the scan. The repository adds [note]s while it judges a message;
 * the scan loop turns them into one line per message.
 */
object ScanLog {
    private val lines = ArrayList<String>()
    private val pending = StringBuilder()
    @Volatile var running = false
        private set

    private val _live = kotlinx.coroutines.flow.MutableStateFlow<List<List<String>>>(emptyList())
    /** The latest lines (tag, time, message, why) as the scan writes them; the scan card shows these live. */
    val live = _live.asStateFlow()
    private val _progress = kotlinx.coroutines.flow.MutableStateFlow(0 to 0)
    /** Messages read so far and in total. */
    val progress = _progress.asStateFlow()

    fun progress(done: Int, total: Int) { _progress.value = done to total }

    fun start() {
        synchronized(lines) { lines.clear() }; synchronized(pending) { pending.setLength(0) }
        _live.value = emptyList(); _progress.value = 0 to 0
        running = true
    }

    /** A reason for the message being judged right now (only while a scan runs). */
    fun note(s: String) { if (running) synchronized(pending) { if (pending.isNotEmpty()) pending.append("; "); pending.append(s) } }

    fun takeNotes(): String = synchronized(pending) { pending.toString().also { pending.setLength(0) } }

    fun add(tag: String, at: Long, text: String, why: String) {
        if (!running) return
        val row = listOf(tag, at.toString(), text, why).map { it.replace('\t', ' ').replace('\n', ' ') }
        synchronized(lines) { lines.add(row.joinToString("\t")) }
        _live.value = (_live.value + listOf(row)).takeLast(LIVE_MAX)
    }

    /** Writes the log; [summary] is its first line. */
    fun finish(ctx: Context, summary: String) {
        running = false
        runCatching { File(ctx.filesDir, FILE).writeText(listOf("SUMMARY\t0\t$summary\t") .plus(synchronized(lines) { lines.toList() }).joinToString("\n")) }
    }

    /** Parsed lines of the last scan (tag, time, message, why); empty if none yet. */
    fun read(ctx: Context): List<List<String>> =
        runCatching { File(ctx.filesDir, FILE).readLines().map { l -> l.split('\t').let { it + List((4 - it.size).coerceAtLeast(0)) { "" } } } }.getOrDefault(emptyList())

    private const val FILE = "scan-log.tsv"
    private const val LIVE_MAX = 300
}
