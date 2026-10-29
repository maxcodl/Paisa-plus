package com.paisaplus.compose.messages

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.paisaplus.compose.data.PaisaDatabase_AutoMigration_6_7_Impl
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MessageInboxMigrationTest {
    @Test fun upgradePreservesLedgerAndCreatesDeduplicatedInbox() {
        val schema = Json.parseToJsonElement(File("schemas/com.paisaplus.compose.data.PaisaDatabase/6.json").readText()).jsonObject
        BundledSQLiteDriver().open(":memory:").use { connection ->
            fun exec(sql: String) = connection.prepare(sql).use { it.step() }
            fun count(table: String) = connection.prepare("SELECT COUNT(*) FROM $table").use { it.step(); it.getLong(0) }
            for (entity in schema.getValue("database").jsonObject.getValue("entities").jsonArray) {
                val row = entity.jsonObject
                exec(row.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", row.getValue("tableName").jsonPrimitive.content))
            }
            exec("INSERT INTO expenses (name, amount, time, type, accountId, categoryId) VALUES ('Existing payment', 100, 1234, 'EXPENSE', 1, 1)")
            PaisaDatabase_AutoMigration_6_7_Impl().migrate(connection)
            assertEquals(1L, count("expenses"))
            assertEquals(0L, count("message_inbox"))
            val insert = "INSERT OR IGNORE INTO message_inbox (fingerprint, source, body, receivedAt, amountPaise, type, isUpi, score, status) VALUES ('same-alert', 'SMS', 'Synthetic example', 1234, 10000, 'EXPENSE', 1, 0.99, 'PENDING')"
            exec(insert)
            exec(insert)
            assertEquals(1L, count("message_inbox"))
            assertEquals(1L, count("expenses"))
        }
    }
}
