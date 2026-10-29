package com.paisaplus.compose.messages

import com.paisaplus.compose.data.AccountEntity
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.CategoryEntity
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.data.UpiApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoAddTest {
    private fun acct(id: Long, number: String, type: CardType = CardType.BANK) = AccountEntity(id, "A$id", 0, "Bank", number, type)

    @Test fun accountByLastDigits() {
        val list = listOf(acct(1, "XXXX1234"), acct(2, "5678"))
        assertEquals(1L, AutoAdd.account(list, "1234", "debited from A/c XX1234")?.id)
    }

    @Test fun unknownOrMissingDigitsGoToReview() {
        val list = listOf(acct(1, "1234"))
        assertNull(AutoAdd.account(list, "9999", "x"))
        assertNull(AutoAdd.account(list, null, "x"))
    }

    @Test fun sameDigitsPreferCardWhenAlertSaysCard() {
        val list = listOf(acct(1, "4006"), acct(2, "4006", CardType.CREDIT))
        assertEquals(2L, AutoAdd.account(list, "4006", "Spent on ICICI Bank Credit Card XX4006")?.id)
        assertEquals(1L, AutoAdd.account(list, "4006", "debited from A/c XX4006")?.id)
    }

    @Test fun upiAppFromWordsThenHandle() {
        assertEquals(UpiApp.PHONEPE, AutoAdd.upiApp("paid via PhonePe UPI"))
        assertEquals(UpiApp.GPAY, AutoAdd.upiApp("to VPA shop@okaxis UPI Ref 123"))
        assertEquals(UpiApp.PHONEPE, AutoAdd.upiApp("to VPA 98765@ybl"))
        assertNull(AutoAdd.upiApp("NEFT credit from RAHUL"))
    }

    @Test fun methodFromText() {
        assertEquals("UPI", AutoAdd.method("UPI Ref 1234", isUpi = false))
        assertEquals("SWIPE", AutoAdd.method("Spent on Credit Card XX4006", isUpi = false))
        assertEquals("TAP", AutoAdd.method("contactless payment on card XX1", isUpi = false))
        assertNull(AutoAdd.method("NEFT credit", isUpi = false))
    }

    @Test fun payeeFromVpa() = assertEquals("Swiggy", AutoAdd.payee("swiggy@icici", ""))

    @Test fun categoryFollowsPayeeHistory() {
        val cats = listOf(CategoryEntity(1, "Food", icon = 0), CategoryEntity(2, "Other", icon = 0))
        val history = listOf(ExpenseEntity(name = "Swiggy", amount = 1.0, time = 1, type = TransactionType.EXPENSE, accountId = 1, categoryId = 1))
        assertEquals(1L, AutoAdd.category("swiggy", "", cats, history)?.id)
        assertEquals(2L, AutoAdd.category("New Shop", "", cats, history)?.id)
    }
}
