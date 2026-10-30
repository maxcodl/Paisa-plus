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

    @Test fun pixNoticesAndCardPayees() {
        val n = AutoAdd.upiNotice("Você enviou R$ 1.234,56 para João Silva")!!
        assertEquals(123456L, n.amountPaise)
        assertEquals("João Silva", n.payee)
        assertEquals(TransactionType.INCOME, AutoAdd.upiNotice("Você recebeu uma transferência de R$ 50,00 de Maria")?.type)
        assertEquals(25000L, AutoAdd.upiNotice("Paid ₹250 to Swiggy")?.amountPaise)
        assertEquals("Credit card payment", AutoAdd.payee("SBI CARD", "debited"))
        assertEquals(true, AutoAdd.cardBill("ICICI CARD", "x"))
        assertEquals(false, AutoAdd.cardBill("Swiggy", "x"))
    }

    @Test fun savedEntryNames() {
        assertEquals(true, AutoAdd.sameName("BARKATH RESTAUR", "Barkath Restaurant"))
        assertEquals(true, AutoAdd.sameName("ANUSURI SRI KOU", "Anusuri SRI kousk"))
        assertEquals(false, AutoAdd.sameName("RAHUL C R", "Transfer to Federal 6405"))
        assertEquals(false, AutoAdd.sameName("AKHIL C PRAKASH", "Anil C A"))
        assertEquals(false, AutoAdd.hasName("dispute call 18001080/SMS BLOCK 4006"))
        assertEquals(true, AutoAdd.hasName("RAHUL C R"))
    }

    @Test fun aliasesMapAlertNamesToSavedNames() {
        val list = listOf(PayeeAliasEntity("rahulcr", "RAHUL C R", "Rahul"), PayeeAliasEntity("karthikjanarda", "KARTHIK JANARDA", "Karthik J"))
        assertEquals("Rahul", AutoAdd.aliasFor(list, "Rahul C R"))
        assertEquals("Karthik J", AutoAdd.aliasFor(list, "KARTHIK JANARDANAN"))
        assertNull(AutoAdd.aliasFor(list, "RAHUL C RAJ"))
        assertNull(AutoAdd.aliasFor(list, null))
    }

    @Test fun plausibleAliases() {
        assertEquals(true, AutoAdd.plausibleAlias("COCHIN RAHUMANI", "Cochin Rahumania Biriyani"))
        assertEquals(true, AutoAdd.plausibleAlias("RAHUL C R", "Rahul Chandran"))
        assertEquals(true, AutoAdd.plausibleAlias("RELIANCE RETAIL", "Reliance Retail Alappuzha"))
        assertEquals(false, AutoAdd.plausibleAlias("VEEKAY FUELS", "A Somanathan"))
        assertEquals(false, AutoAdd.plausibleAlias("ASTER PHARMACY", "Amina B"))
        assertEquals(false, AutoAdd.plausibleAlias("MUKESH SINGH", "Cochin Caf"))
    }

    @Test fun amazonStoreNotice() {
        val n = AutoAdd.storeNotice("Your Mobile Prepaid payment is successful. Your Mobile Prepaid payment of Rs 49 is successful. Please click here to check order details.")!!
        assertEquals(4900L, n.amountPaise)
        assertEquals("Mobile Recharge", n.payee)
        assertNull(AutoAdd.storeNotice("Payment success using Amazon Pay Later: Your EMIs will start from 05-Nov for 1 month(s)."))
        assertEquals("Amazon", AutoAdd.storeNotice("Your Order payment of Rs 1,299.50 is successful.")?.payee)
    }

    @Test fun amazonPayLater() {
        val body = "Thank you for availing Pay Later credit of Rs233.95. For more info click https://u.axio.ac/axo/vIWIZ8NdTJJm To report misuse call 180012001637 -axio"
        val p = (BankMessageParser.parse(body) as MessageParseResult.Candidate).transaction
        assertEquals(23395L, p.amountPaise)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals(true, p.hasLink)
        val list = listOf(AccountEntity(1, "Amazon Pay Later", 0, "Amazon Pay", "", CardType.CREDIT), AccountEntity(2, "ICICI Savings", 0, "ICICI Bank", "", CardType.BANK))
        assertEquals(1L, AutoAdd.account(list, null, body)?.id)
        assertEquals(23700L, (BankMessageParser.parse("Thank you for availing Pay Later credit of Rs237.0. For more info click https://u.axio.ac/axo/x") as MessageParseResult.Candidate).transaction.amountPaise)
    }

    @Test fun secondLegIsOwnAccount() {
        val p = (BankMessageParser.parse("ICICI Bank Acct XXX002 debited with INR 10730.00 on 12-Oct-26. Acct XXX626 credited.UPI:665177020519.") as MessageParseResult.Candidate).transaction
        assertEquals("626", p.counterSuffix)
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
