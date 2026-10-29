package com.paisaplus.compose.messages.optional

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.LongBuffer

/** Experimental drafts only. No call path from this engine can write ledger data. */
object ExperimentalModelEngine {
    suspend fun generate(directory: File, manifest: InstalledModel, text: String): String {
        require(text.isNotBlank() && text.length <= 4096) { "Paste one short banking alert" }
        val tokenizer = Gpt2Tokenizer(directory)
        val ids = tokenizer.encode(text + "|").toMutableList()
        require(ids.size + manifest.maxNewTokens <= manifest.contextTokens) { "Message is too long for this optional model" }
        val generated = mutableListOf<Long>()
        val env = OrtEnvironment.getEnvironment()
        val started = System.nanoTime()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            env.createSession(File(directory, "model.onnx").absolutePath, options).use { session ->
                repeat(manifest.maxNewTokens) {
                    currentCoroutineContext().ensureActive()
                    require(System.nanoTime() - started < 60_000_000_000L) { "Model took too long. Try a shorter message." }
                    OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, ids.size.toLong())).use { input ->
                        session.run(mapOf("input_ids" to input)).use { result ->
                            val values = (result[0] as OnnxTensor).floatBuffer
                            var best = -Float.MAX_VALUE; var token = 50256L; var index = 0L
                            while (values.hasRemaining()) {
                                val value = values.get()
                                if (value > best) { best = value; token = index }
                                index++
                            }
                            require(index == 50257L && best.isFinite()) { "Model returned invalid scores" }
                            if (token == 50256L) return tokenizer.decode(generated).ifBlank { "Model returned no draft" }
                            generated.add(token); ids.add(token)
                        }
                    }
                    val draft = tokenizer.decode(generated)
                    if (hasCompleteObject(draft)) return draft
                }
            }
        }
        return tokenizer.decode(generated) + "\n[Generation stopped at the token limit; draft may be incomplete]"
    }

    internal fun hasCompleteObject(text: String): Boolean {
        val start = text.indexOf('{'); if (start < 0) return false
        var depth = 0; var quoted = false; var escaped = false
        for (c in text.substring(start)) {
            if (escaped) { escaped = false; continue }
            if (c == '\\' && quoted) { escaped = true; continue }
            if (c == '"') quoted = !quoted
            if (!quoted) when (c) { '{' -> depth++; '}' -> { depth--; if (depth == 0) return true } }
        }
        return false
    }
}
