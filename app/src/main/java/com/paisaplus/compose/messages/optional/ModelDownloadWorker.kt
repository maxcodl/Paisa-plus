package com.paisaplus.compose.messages.optional

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

const val MODEL_DOWNLOAD_WORK = "optional-banking-model-download"

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val store: OptionalModelStore,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val archive = File(applicationContext.cacheDir, "optional-model-$id.zip")
        try {
            val spec = store.specification
            require(archive.parentFile!!.usableSpace > spec.bytes + spec.installedBytes + 32L * 1024 * 1024) { "Not enough free space for model download and installation" }
            // Foreground with a progress notification so a slow 56 MB download isn't stopped; fine to skip if refused.
            runCatching { setForeground(progressInfo(0, spec.bytes)) }
            setProgress(workDataOf("stage" to "Downloading", "bytes" to 0L, "total" to spec.bytes))
            download(spec.url, archive, spec.bytes)
            currentCoroutineContext().ensureActive()
            setProgress(workDataOf("stage" to "Verifying and installing", "bytes" to spec.bytes, "total" to spec.bytes))
            store.install(archive)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "Model download failed").take(500))) }
        finally { archive.delete() }
    }

    private suspend fun download(start: String, target: File, expected: Long) {
        var url = URI(start).toURL()
        repeat(6) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000; connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "PaisaPlus-OptionalModels/1")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val next = URI(url.toString()).resolve(connection.getHeaderField("Location") ?: error("Missing download redirect")).toURL()
                    require(next.protocol == "https" && next.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) { "Untrusted download redirect" }
                    url = next
                } else {
                    require(status == 200) {
                        if (status == 404 || status == 403) "The model release is unavailable or private. Import its ZIP from Releases until this repository is public."
                        else "Model download failed (HTTP $status)"
                    }
                    val declared = connection.contentLengthLong
                    require(declared == -1L || declared == expected) { "Unexpected model download size" }
                    connection.inputStream.use { input -> target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024); var count = 0L; var lastProgress = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            count += n; require(count <= expected) { "Model download exceeds the expected size" }
                            output.write(buffer, 0, n)
                            if (count - lastProgress >= 512 * 1024) {
                                setProgress(workDataOf("stage" to "Downloading", "bytes" to count, "total" to expected)); lastProgress = count
                                runCatching { setForeground(progressInfo(count, expected)) }
                            }
                        }
                        require(count == expected) { "Model download was interrupted; try again" }
                    } }
                    return
                }
            } finally { connection.disconnect() }
        }
        error("Too many model download redirects")
    }

    private fun progressInfo(done: Long, total: Long): ForegroundInfo {
        val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel("downloads", com.paisaplus.compose.str(com.paisaplus.compose.R.string.downloads), android.app.NotificationManager.IMPORTANCE_LOW))
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        val n = androidx.core.app.NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(com.paisaplus.compose.R.drawable.ic_stat_paisa)
            .setContentTitle(com.paisaplus.compose.str(com.paisaplus.compose.R.string.optional_model_title))
            .setProgress(100, pct, total <= 0)
            .setOngoing(true).setOnlyAlertOnce(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(4202, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(4202, n)
    }
}
