package com.paisaplus.compose.messages.optional

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelPackTest {
    private val catalogue = File("src/main/assets/model-catalog.json").readText()
    @Test fun cataloguePinsTheModelRelease() {
        val spec = parseCatalog(catalogue)
        assertEquals("banking-v8-int4-v1", spec.id)
        assertTrue(spec.bytes < 60_000_000)
        assertTrue(spec.installedBytes < 70_000_000)
        assertTrue(spec.url.endsWith("/models-v1/banking-v8-int4-v1.zip"))
    }
    @Test fun catalogueRejectsUntrustedUrlsAndSizes() {
        listOf(
            catalogue.replace("https://github.com/", "http://github.com/"),
            catalogue.replace("maxcodl/Paisa-plus", "attacker/other"),
            catalogue.replace(parseCatalog(catalogue).bytes.toString(), "9999999999"),
            catalogue.replace("onnx-gpt2-bpe-v1", "arbitrary-code-v1"),
        ).forEach { text -> assertThrows(IllegalArgumentException::class.java) { parseCatalog(text) } }
    }
    private fun zip(vararg files: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output -> files.forEach { (name, body) ->
            output.putNextEntry(ZipEntry(name)); output.write(body.toByteArray()); output.closeEntry()
        } }
        return bytes.toByteArray()
    }
    @Test fun archiveRejectsTraversalAndCleansStaging() {
        val root = Files.createTempDirectory("paisa-pack-test").toFile()
        try {
            val staging = File(root, "staging")
            assertThrows(IllegalArgumentException::class.java) { unpackModelPack(ByteArrayInputStream(zip("../escaped" to "bad")), staging) }
            assertFalse(File(root, "escaped").exists())
            assertFalse(staging.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun archiveRejectsIncompletePacks() {
        val root = Files.createTempDirectory("paisa-pack-test").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) { unpackModelPack(ByteArrayInputStream(zip("model.onnx" to "incomplete")), File(root, "staging")) }
            assertFalse(File(root, "staging").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun archiveChecksEveryHashAndFile() {
        val root = Files.createTempDirectory("paisa-pack-test").toFile()
        try {
            val names = listOf("model.onnx", "vocab.json", "merges.txt", "MODEL_CARD.md")
            val files = names.map { name -> File(root, name).apply { writeText("synthetic $name") } }
            val manifest = """{"formatVersion":1,"id":"test-model","name":"Test model","runtime":"onnx-gpt2-bpe-v1","contextTokens":384,"maxNewTokens":96,"files":{${files.joinToString(",") { "\"${it.name}\":{\"bytes\":${it.length()},\"sha256\":\"${sha256(it)}\"}" }}}}"""
            val entries = files.map { it.name to it.readText() } + ("manifest.json" to manifest)
            val installed = unpackModelPack(ByteArrayInputStream(zip(*entries.toTypedArray())), File(root, "good"))
            assertEquals("test-model", installed.id)
            val corrupt = entries.map { if (it.first == "model.onnx") it.first to "tampered" else it }
            assertThrows(IllegalArgumentException::class.java) { unpackModelPack(ByteArrayInputStream(zip(*corrupt.toTypedArray())), File(root, "bad")) }
            assertFalse(File(root, "bad").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun exportedReleasePackMatchesPinnedCatalogueWhenAvailable() {
        val archive = File("../build/optional-model/banking-v8-int4-v1.zip")
        org.junit.Assume.assumeTrue("Run the export script to verify release pack", archive.exists())
        val root = Files.createTempDirectory("paisa-exported-pack-test").toFile()
        try {
            val spec = parseCatalog(catalogue)
            assertEquals(spec.bytes, archive.length())
            assertEquals(spec.sha256, sha256(archive))
            val manifest = archive.inputStream().use { unpackModelPack(it, File(root, "staging")) }
            assertEquals(spec.id, manifest.id)
            val tokenizer = Gpt2Tokenizer(File(root, "staging"))
            val ids = tokenizer.encode("Hello world")
            assertEquals(listOf(15496L, 995L), ids)
            assertEquals("Hello world", tokenizer.decode(ids))
        } finally { root.deleteRecursively() }
    }
    @Test fun generationStopsOnlyAfterCompleteObject() {
        assertTrue(ExperimentalModelEngine.hasCompleteObject("prefix {\"a\":\"}quoted{\",\"b\":{\"x\":1}}"))
        assertFalse(ExperimentalModelEngine.hasCompleteObject("{\"a\":\"}quoted{\""))
        assertFalse(ExperimentalModelEngine.hasCompleteObject("nothing generated"))
    }
}
