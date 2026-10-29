package com.paisaplus.compose.messages.optional

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import java.io.File

/** Original GPT-2 byte-level BPE; no tokenizer native library is needed on Android. */
class Gpt2Tokenizer(directory: File) {
    private val vocab = Json.parseToJsonElement(File(directory, "vocab.json").readText()).jsonObject.mapValues { it.value.jsonPrimitive.int }
    private val reverse = vocab.entries.associate { it.value to it.key }
    private val ranks = File(directory, "merges.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapIndexed { index, line -> line to index }.toMap()
    private val byteChars: Map<Int, Char>
    private val charBytes: Map<Char, Int>
    private val pattern = Regex("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")

    init {
        require(vocab.size == 50257 && reverse.size == 50257 && ranks.size in 49_000..50_001) { "Unsupported GPT-2 tokenizer" }
        val bytes = ((33..126).toList() + (161..172).toList() + (174..255).toList()).toMutableList()
        val chars = bytes.map { it.toChar() }.toMutableList()
        var extra = 0
        for (b in 0..255) if (b !in bytes) { bytes.add(b); chars.add((256 + extra++).toChar()) }
        byteChars = bytes.zip(chars).toMap(); charBytes = byteChars.entries.associate { it.value to it.key }
    }

    fun encode(text: String): List<Long> = buildList {
        for (match in pattern.findAll(text)) {
            var symbols = match.value.toByteArray(Charsets.UTF_8).map { byteChars.getValue(it.toInt() and 255).toString() }
            while (symbols.size > 1) {
                val best = (0 until symbols.lastIndex).minByOrNull { ranks["${symbols[it]} ${symbols[it + 1]}"] ?: Int.MAX_VALUE } ?: break
                if (ranks["${symbols[best]} ${symbols[best + 1]}"] == null) break
                val a = symbols[best]; val b = symbols[best + 1]
                symbols = buildList {
                    var i = 0
                    while (i < symbols.size) {
                        if (i + 1 < symbols.size && symbols[i] == a && symbols[i + 1] == b) { add(a + b); i += 2 }
                        else { add(symbols[i]); i++ }
                    }
                }
            }
            symbols.forEach { add(vocab.getValue(it).toLong()) }
        }
    }

    fun decode(ids: List<Long>): String {
        val bytes = ids.filter { it != 50256L }.flatMap { reverse.getValue(it.toInt()).map { c -> charBytes.getValue(c).toByte() } }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
