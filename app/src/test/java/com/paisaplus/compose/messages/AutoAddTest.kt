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

    @Test fun noDigitsSavedFallsBackToBankName() {
        val list = listOf(AccountEntity(1, "ICICI Savings", 0, "ICICI Bank", "", CardType.BANK), AccountEntity(2, "Federal Savings", 0, "Federal Bank", "", CardType.BANK))
        assertEquals(1L, AutoAdd.account(list, "002", "ICICI Bank Acct XX002 debited for Rs 10.00")?.id)
        assertNull(AutoAdd.account(list, "002", "HDFC Bank A/c XX002 debited"))
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
        assertNull(AutoAdd.method("Spent on Credit Card XX4006", isUpi = false))
        assertNull(AutoAdd.method("Payment of INR 34,000.00 has been received on your ICICI Bank Credit Card", isUpi = false))
        assertEquals("TAP", AutoAdd.method("contactless payment on card XX1", isUpi = false))
        assertNull(AutoAdd.method("NEFT credit", isUpi = false))
    }

    @Test fun payeeFromVpa() = assertEquals("Swiggy", AutoAdd.payee("swiggy@icici", ""))

    @Test fun completesCutOffNames() {
        val known = setOf("Karthik Janardanan", "Talk of Thrissur", "Karthik J", "Karthik Janardanan")
        assertEquals("Karthik Janardanan", AutoAdd.complete("Karthik Janarda", known))
        assertEquals("Swiggy", AutoAdd.complete("Swiggy", known))
        assertEquals("Talk of Thrissur", AutoAdd.complete("Talk Of Thrissu", known))
    }

    @Test fun crypticStatementNames() {
        assertEquals(true, AutoAdd.crypticName("UPI q692232694"))
        assertEquals(true, AutoAdd.crypticName("Paytm d19587428510"))
        assertEquals(false, AutoAdd.crypticName("Swiggy"))
    }

    @Test fun upiAppNotices() {
        AutoAdd.upiNotice("Paid ₹250 to Swiggy successfully")!!.let { assertEquals(25000L, it.amountPaise); assertEquals("Swiggy", it.payee); assertEquals(TransactionType.EXPENSE, it.type) }
        AutoAdd.upiNotice("₹1,200 received from Rahul Kumar")!!.let { assertEquals(120000L, it.amountPaise); assertEquals(TransactionType.INCOME, it.type) }
        assertNull(AutoAdd.upiNotice("Rahul requested ₹500 from you"))
        assertNull(AutoAdd.upiNotice("You won ₹10 cashback"))
    }

    @Test fun payeeFromBankCodes() {
        assertEquals("Thrikkaka", AutoAdd.payee("VPS*Thrikkaka", "debited"))
        assertNull(AutoAdd.payee("BIL*INFT*FJD4", "debited"))
        assertEquals("Salary", AutoAdd.payee("INF*INFT*046078", "Acc XX002 is credited with salary of Rs. 1,999.00"))
        assertEquals("Credit card payment", AutoAdd.payee(null, "Payment of INR 34,000.00 has been received on your ICICI Bank Credit Card Account 5xxx1007"))
    }

    @Test fun categoryFollowsPayeeHistory() {
        val cats = listOf(CategoryEntity(1, "Food", icon = 0), CategoryEntity(2, "Other", icon = 0))
        val history = listOf(ExpenseEntity(name = "Swiggy", amount = 1.0, time = 1, type = TransactionType.EXPENSE, accountId = 1, categoryId = 1))
        assertEquals(1L, AutoAdd.category("swiggy", "", cats, history)?.id)
        assertEquals(2L, AutoAdd.category("New Shop", "", cats, history)?.id)
    }
}
