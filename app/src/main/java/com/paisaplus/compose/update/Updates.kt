package com.paisaplus.compose.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paisaplus.compose.MainActivity
import com.paisaplus.compose.R
import com.paisaplus.compose.data.SettingsStore
import com.paisaplus.compose.data.UpdateFrequency
import com.paisaplus.compose.messages.optional.sha256
import com.paisaplus.compose.str
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val REPO = "maxcodl/Paisa-plus"
private const val WORK = "app-update-check"
private const val CHANNEL = "updates"
private const val NOTIFY_ID = 4201

/** A newer app release on GitHub with its APK; [sha256] is GitHub's own asset digest, required before installing. */
data class AppRelease(val version: String, val name: String, val notes: String, val page: String, val apkUrl: String, val apkBytes: Long, val sha256: String?)

/** App releases are tagged `v1.2.3` (model packs live in the same repo as `models-v1` and are skipped). */
private val appTag = Regex("""^v(\d+(?:\.\d+)*)$""")

private fun versionParts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

/** Numeric dotted compare: 0.10.0 > 0.9.2. */
internal fun newer(candidate: String, installed: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

fun installedVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()

object UpdateChecker {
    /** The newest app release above the installed version, or null when up to date. Throws on network / API errors. */
    suspend fun check(ctx: Context, includeBeta: Boolean): AppRelease? = withContext(Dispatchers.IO) {
        val c = URI("https://api.github.com/repos/$REPO/releases?per_page=30").toURL().openConnection() as HttpURLConnection
        val body = try {
            c.connectTimeout = 15_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "PaisaPlus-Updater")
            // 404 = the repository is private or has no releases yet.
            require(c.responseCode == 200) { if (c.responseCode == 404) str(R.string.update_repo_unavailable) else "GitHub returned HTTP ${c.responseCode}" }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
        val current = installedVersion(ctx)
        Json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
            .filter { r ->
                val tag = r.str("tag_name")
                appTag.matches(tag) && !r.bool("draft") && (includeBeta || !r.bool("prerelease")) && newer(tag, current)
            }
            .maxWithOrNull { a, b -> if (newer(a.str("tag_name"), b.str("tag_name"))) 1 else if (newer(b.str("tag_name"), a.str("tag_name"))) -1 else 0 }
            ?.let { r ->
                // Releases ship one APK per ABI (paisa-plus-0.9.0-arm64-v8a.apk ...): take this phone's, else a universal one.
                val apks = r["assets"]?.jsonArray?.map { it.jsonObject }?.filter { it.str("name").endsWith(".apk") }.orEmpty()
                val apk = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> apks.firstOrNull { abi in it.str("name") } }
                    ?: apks.firstOrNull { "universal" in it.str("name") } ?: apks.singleOrNull() ?: return@let null
                AppRelease(
                    version = r.str("tag_name").removePrefix("v"),
                    name = r.str("name").ifBlank { r.str("tag_name") },
                    notes = r.str("body").take(4000),
                    page = r.str("html_url"),
                    apkUrl = apk.str("browser_download_url"),
                    apkBytes = apk["size"]?.jsonPrimitive?.long ?: 0,
                    sha256 = apk.str("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
                )
            }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.bool(k: String) = this[k]?.jsonPrimitive?.boolean ?: false
}

/** (Re)schedules the periodic check for [f]; Off cancels it. Runs only with a network. */
fun scheduleUpdateChecks(ctx: Context, f: UpdateFrequency) {
    val wm = WorkManager.getInstance(ctx)
    if (f == UpdateFrequency.OFF) { wm.cancelUniqueWork(WORK); return }
    wm.enqueueUniquePeriodicWork(
        WORK, ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<UpdateCheckWorker>(f.days.toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build(),
    )
}

/** Background check: notifies once per new version; tapping the notification opens Settings › App updates. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val settings: SettingsStore,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = settings.updates.first()
        val release = runCatching { UpdateChecker.check(applicationContext, prefs.beta) }.getOrElse { return Result.retry() }
        settings.setUpdates(prefs.copy(lastCheck = System.currentTimeMillis()))
        if (release != null && release.version != prefs.notified) {
            notifyUpdate(applicationContext, release)
            settings.setUpdates(settings.updates.first().copy(notified = release.version))
        }
        return Result.success()
    }
}

private fun notifyUpdate(ctx: Context, r: AppRelease) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.app_updates), NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_UPDATES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    nm.notify(
        NOTIFY_ID,
        NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_paisa)
            .setContentTitle(str(R.string.update_x_available, r.version))
            .setContentText(str(R.string.tap_to_review_and_install))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build(),
    )
}

object UpdateInstaller {
    /** GitHub serves release assets from these hosts after a redirect. */
    private val trustedHosts = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /** Downloads, checks size and GitHub's SHA-256, then hands the APK to the system installer (it asks the user). */
    suspend fun install(ctx: Context, r: AppRelease, shizuku: Boolean = false, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val expected = r.sha256 ?: error(str(R.string.update_no_checksum))
        val apk = File(ctx.cacheDir, "update-${r.version}.apk")
        try {
            download(r.apkUrl, apk, r.apkBytes, progress)
            require(sha256(apk).equals(expected, ignoreCase = true)) { str(R.string.update_checksum_failed) }
            if (shizuku && ShizukuInstall.install(ctx, apk)) return@withContext
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                // Silent when Paisa+ installed the current version itself (Android 12+); otherwise Android still asks.
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("paisa.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val done = PendingIntent.getBroadcast(
                    ctx, id, Intent(ctx, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                s.commit(done.intentSender)
            }
        } finally { apk.delete() }
    }

    private suspend fun download(start: String, target: File, expected: Long, progress: (Float) -> Unit) {
        var url = URI(start).toURL()
        repeat(6) {
            require(url.protocol == "https" && url.host in trustedHosts) { "Untrusted download host" }
            val c = url.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "PaisaPlus-Updater")
                val status = c.responseCode
                if (status in 300..399) { url = URI(url.toString()).resolve(c.getHeaderField("Location") ?: error("Missing redirect")).toURL(); return@repeat }
                require(status == 200) { "Download failed (HTTP $status)" }
                var count = 0L
                c.inputStream.use { input -> target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); count += n
                        require(expected <= 0 || count <= expected) { "Download larger than expected" }
                        if (expected > 0) progress(count.toFloat() / expected)
                    }
                } }
                require(expected <= 0 || count == expected) { "Download was interrupted; try again" }
                return
            } finally { c.disconnect() }
        }
        error("Too many redirects")
    }
}

/** System installer callback: shows its confirm screen when needed, otherwise nothing (the app restarts updated). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
            ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsStore,
) : ViewModel() {
    val prefs = settings.updates.stateIn(viewModelScope, SharingStarted.Eagerly, com.paisaplus.compose.data.UpdatePrefs())
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()
    val version = installedVersion(ctx)

    fun setPrefs(p: com.paisaplus.compose.data.UpdatePrefs) = viewModelScope.launch { settings.setUpdates(p) }

    /** A release to offer on opening ([UpdatePrompt]); null when none or already offered. */
    private val _prompt = MutableStateFlow<AppRelease?>(null)
    val prompt = _prompt.asStateFlow()

    /** Every app start checks (one small GitHub request) unless updates are Off; silent on errors. */
    fun checkOnOpen() = viewModelScope.launch {
        val p = settings.updates.first()
        if (p.frequency == com.paisaplus.compose.data.UpdateFrequency.OFF) return@launch
        val r = runCatching { UpdateChecker.check(ctx, p.beta) }.getOrNull()
        settings.setUpdates(settings.updates.first().copy(lastCheck = System.currentTimeMillis()))
        if (r != null) {
            _state.value = UpdateState.Available(r)
            if (r.version != p.prompted) _prompt.value = r
        }
    }

    /** Later: not offered on opening again for this version. */
    fun later(r: AppRelease) = viewModelScope.launch {
        _prompt.value = null
        settings.setUpdates(settings.updates.first().copy(prompted = r.version))
    }

    fun check() = viewModelScope.launch {
        _state.value = UpdateState.Checking
        _state.value = runCatching { UpdateChecker.check(ctx, prefs.value.beta) }
            .fold({ it?.let(UpdateState::Available) ?: UpdateState.UpToDate }, { UpdateState.Failed(it.message ?: str(R.string.update_check_failed)) })
        settings.setUpdates(settings.updates.first().copy(lastCheck = System.currentTimeMillis()))
    }

    /** False when Android still needs "Install unknown apps" for Paisa+ (the screen sends the user there). */
    fun canInstall() = ctx.packageManager.canRequestPackageInstalls()

    fun install(r: AppRelease) = viewModelScope.launch {
        _state.value = UpdateState.Downloading(r, 0f)
        runCatching { UpdateInstaller.install(ctx, r, prefs.value.shizuku) { p -> _state.value = UpdateState.Downloading(r, p) } }
            .onSuccess { _state.value = UpdateState.Available(r) }
            .onFailure { _state.value = UpdateState.Failed(it.message ?: str(R.string.update_install_failed), r) }
    }
}
