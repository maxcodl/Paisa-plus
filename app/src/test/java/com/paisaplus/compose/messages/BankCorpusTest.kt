package com.paisaplus.compose.messages

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Authored bank mocks exercise extraction, not real-world accuracy or bank authenticity. */
@RunWith(Parameterized::class)
class BankCorpusTest(private val id: String, private val fixture: JsonObject) {
    @Test fun parsesExpectedFieldsOrRejectsNonTransaction() {
        val result = BankMessageParser.parse(fixture.getValue("body").jsonPrimitive.content)
        val expected = fixture.getValue("expected").jsonObject
        if (!expected.getValue("bookable").jsonPrimitive.boolean) {
            assertTrue("$id: $result", result is MessageParseResult.Ignored)
            return
        }
        assertTrue("$id: $result", result is MessageParseResult.Candidate)
        val p = (result as MessageParseResult.Candidate).transaction
        assertEquals(expected.getValue("amount_paise").jsonPrimitive.long, p.amountPaise)
        assertEquals(expected.getValue("type").jsonPrimitive.content, p.type.name)
        assertEquals(expected["account_suffix"]?.jsonPrimitive?.contentOrNull, p.accountSuffix)
        assertEquals(expected.getValue("reference").jsonPrimitive.content, p.reference)
        expected["merchant"]?.let { assertEquals(it.jsonPrimitive.content, p.merchant) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = Json.parseToJsonElement(
            File("../tools/message_model/bank-fixtures.json").readText(),
        ).jsonObject.getValue("fixtures").jsonArray.map { row ->
            arrayOf(row.jsonObject.getValue("id").jsonPrimitive.content, row.jsonObject)
        }
    }
}
