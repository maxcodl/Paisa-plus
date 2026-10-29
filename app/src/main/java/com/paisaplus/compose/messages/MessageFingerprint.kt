package com.paisaplus.compose.messages

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

fun messageFingerprint(body: String, p: ParsedBankMessage, receivedAt: Long): String {
    val identity = p.reference?.let { "ref:$it:${p.accountSuffix}:${p.amountPaise}:${p.type}" }
        ?: "text:${Instant.ofEpochMilli(receivedAt).atOffset(ZoneOffset.UTC).toLocalDate()}:${body.lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ").trim()}"
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
