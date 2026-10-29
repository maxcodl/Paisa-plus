package com.paisaplus.compose.messages.optional

import android.content.Context
import android.net.Uri
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OptionalModelStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.noBackupFilesDir, "optional-models").apply { mkdirs() }
    private val active = File(root, "active")
    private val mutex = Mutex()
    val specification: ModelDownload by lazy { parseCatalog(context.assets.open("model-catalog.json").bufferedReader().use { it.readText() }) }
    private val _installed = MutableStateFlow(readInstalled())
    val installed = _installed.asStateFlow()

    private fun readInstalled(): InstalledModel? = runCatching {
        val previous = File(root, "previous")
        if (!active.exists() && previous.exists()) previous.renameTo(active)

        parseManifest(File(active, "manifest.json").readText()).also { m ->
            require(m.id == specification.id)
            require(m.files.all { (name, spec) -> File(active, name).length() == spec.bytes })
        }
    }.getOrNull()

    suspend fun install(archive: File) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val spec = specification
            require(archive.length() == spec.bytes && sha256(archive) == spec.sha256) { "Model download is incomplete or its checksum does not match" }
            require(root.usableSpace > spec.installedBytes + 32L * 1024 * 1024) { "Not enough space to install this model" }
            val staging = File(root, "staging-${java.util.UUID.randomUUID()}")
            try {
                val manifest = archive.inputStream().use { unpackModelPack(it, staging) }
                require(manifest.id == spec.id) { "Unexpected model pack" }
                validateRuntime(staging)
                currentCoroutineContext().ensureActive()
                // A directory rename is atomic. Keep the old pack until its replacement is valid.
                val old = File(root, "previous")
                old.deleteRecursively()
                if (active.exists()) require(active.renameTo(old)) { "Could not replace the existing model" }
                if (!staging.renameTo(active)) {
                    old.renameTo(active)
                    error("Could not install the model")
                }
                old.deleteRecursively()
                _installed.value = manifest
            } finally { staging.deleteRecursively() }
        }
    }

    suspend fun importPack(uri: Uri) = withContext(Dispatchers.IO) {
        val archive = File(context.cacheDir, "model-import-${java.util.UUID.randomUUID()}.zip")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        total += n
                        require(total <= specification.bytes) { "This is not the expected model pack" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            install(archive)
        } finally { archive.delete() }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(!active.exists() || active.deleteRecursively()) { "Could not remove model files" }
            _installed.value = null
        }
    }

    suspend fun generate(text: String): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val manifest = _installed.value ?: error("Download or import the optional model first")
            // Never keep native sessions in memory while the model is idle.
            ExperimentalModelEngine.generate(active, manifest, text)
        }
    }

    private fun validateRuntime(directory: File) {
        Gpt2Tokenizer(directory)
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            OrtEnvironment.getEnvironment().createSession(File(directory, "model.onnx").absolutePath, options).use { session ->
                require(session.inputNames == setOf("input_ids") && session.outputNames == setOf("logits")) { "Unsupported inference graph" }
            }
        }
    }
}
