package com.paisaplus.compose.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PayeesTest {
    private fun tx(id: Long, name: String) =
        ExpenseEntity(id = id, name = name, amount = 1.0, time = 100 - id, type = TransactionType.EXPENSE, accountId = 1, categoryId = 1)

    private val e = listOf(tx(1, "Akbar Ali C K"), tx(2, "akbar ali c k "), tx(3, "Olive Foods"), tx(4, "Akbar"))

    @Test fun groupsByNameIgnoringCaseAndSpaces() {
        assertEquals(listOf(1L, 2L), payeeTransactions(e, " AKBAR ALI C K").map { it.id })
        assertEquals(listOf(3L), payeeTransactions(e, "olive foods").map { it.id })
    }

    @Test fun noteAndPaymentMethodSurviveDeleteAndUndo() {
        val e = tx(5, "Zepto").copy(note = "weekly", method = PaymentMethod.UPI.name, upiApp = UpiApp.GPAY.name)
        assertEquals(e, DeletedExpenseEntity.of(e, 0).toExpense())
    }

    @Test fun renameOntoExistingNameMerges() {
        assertEquals(1, mergeCount(e, "Akbar Ali C K", "akbar"))        // Akbar exists: merge
        assertEquals(0, mergeCount(e, "Akbar Ali C K", "Akbar Ali"))    // new name: plain rename
        assertEquals(0, mergeCount(e, "Akbar Ali C K", "AKBAR ALI C K")) // case change only
        // After the rename both groups share one key, i.e. one payee.
        val renamed = e.map { if (payeeKey(it.name) == payeeKey("Akbar Ali C K")) it.copy(name = "Akbar") else it }
        assertEquals(listOf(1L, 2L, 4L), payeeTransactions(renamed, "Akbar").map { it.id })
    }
}
