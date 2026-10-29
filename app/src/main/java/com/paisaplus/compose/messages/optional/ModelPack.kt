package com.paisaplus.compose.messages.optional

import kotlinx.serialization.json.*
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

const val MODEL_CATALOG_URL = "https://github.com/maxcodl/Paisa-plus/releases/download/models-v1/model-catalog.json"
private const val RELEASE_PREFIX = "https://github.com/maxcodl/Paisa-plus/releases/download/models-v1/"
const val MAX_PACK_BYTES = 256L * 1024 * 1024
private const val MAX_UNPACKED_BYTES = 384L * 1024 * 1024
private val allowedFiles = setOf("model.onnx", "vocab.json", "merges.txt", "MODEL_CARD.md")
private val digestPattern = Regex("[a-f0-9]{64}")
private val idPattern = Regex("[a-z0-9][a-z0-9-]{0,79}")

fun sha256(file: File): String {
    val hash = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream -> val b = ByteArray(64 * 1024); while (true) { val n = stream.read(b); if (n < 0) break; hash.update(b, 0, n) } }
    return hash.digest().joinToString("") { "%02x".format(it) }
}

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

fun parseCatalog(text: String): ModelDownload {
    val json = Json.parseToJsonElement(text).jsonObject
    require(json.long("formatVersion") == 1L) { "Unsupported model catalogue" }
    val model = json.getValue("models").jsonArray.first().jsonObject
    val id = model.string("id")
    require(idPattern.matches(id) && model.string("runtime") == "onnx-gpt2-bpe-v1") { "Unsupported model" }
    val spec = ModelDownload(id, model.string("name"), model.long("downloadBytes"), model.long("installedBytes"), model.string("sha256"), model.string("url"))
    require(spec.bytes in 1..MAX_PACK_BYTES && spec.installedBytes in 1..MAX_UNPACKED_BYTES && digestPattern.matches(spec.sha256)) { "Invalid model size or checksum" }
    require(spec.url == "$RELEASE_PREFIX$id.zip") { "Model must come from the Paisa+ model release" }
    return spec
}

data class ModelDownload(val id: String, val name: String, val bytes: Long, val installedBytes: Long, val sha256: String, val url: String)
data class PackFile(val bytes: Long, val sha256: String)
data class InstalledModel(val id: String, val name: String, val contextTokens: Int, val maxNewTokens: Int, val files: Map<String, PackFile>)

fun parseManifest(text: String): InstalledModel {
    val j = Json.parseToJsonElement(text).jsonObject
    require(j.long("formatVersion") == 1L && j.string("runtime") == "onnx-gpt2-bpe-v1") { "Unsupported model pack" }
    val files = j.getValue("files").jsonObject.mapValues { (_, value) ->
        val f = value.jsonObject
        PackFile(f.long("bytes"), f.string("sha256"))
    }
    val result = InstalledModel(j.string("id"), j.string("name"), j.long("contextTokens").toInt(), j.long("maxNewTokens").toInt(), files)
    require(idPattern.matches(result.id) && result.name.length in 1..160) { "Invalid model identity" }
    require(result.contextTokens in 128..512 && result.maxNewTokens in 16..128 && result.maxNewTokens < result.contextTokens) { "Unsupported model context" }
    require(files.keys == allowedFiles && files.values.all { it.bytes in 1..MAX_UNPACKED_BYTES && digestPattern.matches(it.sha256) } && files.values.sumOf { it.bytes } <= MAX_UNPACKED_BYTES) { "Invalid model files" }
    return result
}

/** Only fixed flat files are accepted; archives cannot write paths or expand without a bound. */
fun unpackModelPack(input: InputStream, staging: File): InstalledModel {
    require(staging.mkdir()) { "Could not create model staging directory" }
    val seen = mutableSetOf<String>()
    var total = 0L
    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!entry.isDirectory && name in allowedFiles + "manifest.json" && seen.add(name)) { "Unexpected or duplicate model file" }
                File(staging, name).outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    val limit = when (name) { "manifest.json" -> 64L * 1024; "vocab.json", "merges.txt", "MODEL_CARD.md" -> 8L * 1024 * 1024; else -> MAX_UNPACKED_BYTES }
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n; size += n
                        require(total <= MAX_UNPACKED_BYTES && size <= limit) { "Model pack exceeds its size limit" }
                        output.write(buffer, 0, n)
                    }
                }
            }
        }
        require(seen == allowedFiles + "manifest.json") { "Incomplete model pack" }
        val manifest = parseManifest(File(staging, "manifest.json").readText())
        manifest.files.forEach { (name, spec) ->
            val f = File(staging, name)
            require(f.length() == spec.bytes && sha256(f) == spec.sha256) { "Model checksum failed: $name" }
        }
        return manifest
    } catch (e: Exception) { staging.deleteRecursively(); throw e }
}
