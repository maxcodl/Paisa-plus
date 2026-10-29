package com.paisaplus.compose.messages

import com.paisaplus.compose.data.TransactionType
import org.junit.Assert.*
import org.junit.Test

class BankMessageParserTest {
    private fun parsed(text: String): ParsedBankMessage {
        val result = BankMessageParser.parse(text)
        assertTrue("Expected transaction for $text but got $result", result is MessageParseResult.Candidate)
        return (result as MessageParseResult.Candidate).transaction
    }
    private fun ignored(text: String) = assertTrue("Must ignore: $text", BankMessageParser.parse(text) is MessageParseResult.Ignored)

    @Test fun extractsUserExample() {
        val p = parsed("Your A/c XX4920 has been debited by INR 1,500.00 on 29-Oct-2026. Info: Amazon Pay.")
        assertEquals(150000L, p.amountPaise)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("4920", p.accountSuffix)
        assertEquals("Amazon Pay", p.merchant)
    }
    @Test fun creditAndReference() {
        val p = parsed("INR 25,000.50 credited to account XX1234 from Demo Employer. UPI Ref 928374610123.")
        assertEquals(2500050L, p.amountPaise)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("928374610123", p.reference)
        assertTrue(p.isUpi)
    }
    @Test fun ignoresBalanceAndKeepsPurchase() {
        val p = parsed("Rs. 499.00 debited from A/c **1234 at Sample Store. Available balance INR 95,000.00.")
        assertEquals(49900L, p.amountPaise)
    }
    @Test fun creditCardIsExpense() {
        val p = parsed("You spent INR 399.50 using credit card ending 7123 at Sample Store.")
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals("7123", p.accountSuffix)
    }
    @Test fun refundsAreIncome() {
        assertEquals(TransactionType.INCOME, parsed("INR 700.00 refunded to card ending 1357 for Online Shop. Ref 928374610123.").type)
    }
    /** Real bank alerts often carry a dispute link: parsed, but flagged so they only go to Review. */
    @Test fun linkedAlertsAreFlaggedForReview() {
        assertTrue(parsed("INR 1500 debited from account XX1234. Visit bank-example.in.").hasLink)
        assertFalse(parsed("INR 1500 debited from account XX1234. Ref 123456789.").hasLink)
    }

    /** Formats seen in real ICICI / Federal / SBI alerts. */
    @Test fun realWorldFormats() {
        assertEquals(TransactionType.INCOME, parsed("Hi,Rs.2900credited in your A/c XX6405 on 11OCT2026 15:00:38 using cash deposit machine at FBL-LULU MALL KOCHI. Current Bal: Rs.10167.94 - Federal Bank").type)
        assertEquals("002", parsed("ICICI Bank Acc XX002 debited Rs. 34,000.00 on 09-Oct-26 InfoBIL*INFT*FJD4.Avl Bal Rs. 31,484.06.").accountSuffix)
        parsed("ICICI Bank Acct XXX002 debited with INR 10730.00 on 12-Oct-26. Acct XXX626 credited.UPI:665177020519.").let {
            assertEquals(TransactionType.EXPENSE, it.type); assertEquals(1073000L, it.amountPaise)
        }
        assertEquals(TransactionType.INCOME, parsed("Dear Customer, Payment of INR 34,000.00 has been received on your ICICI Bank Credit Card Account 5xxx1007 on 09-OCT-26.Thank you.").type)
        assertEquals("0324", parsed("Payment of Rs 5,838.83 is received towards your credit card number ending with 0324 on 22/09/26.").accountSuffix)
        assertEquals(TransactionType.INCOME, parsed("Dear Customer, your ICICI Bank Account XXX002 has been credited with Rs 300.00 on 08-Oct-26 as reversal of transaction with UPI: 664779930898.").type)
        assertEquals(200L, parsed("Debited Rs 2.00 from a/c X6405 for UPI mandate towards Google Cloud on 06-10-2026 17:28:47. Ref: 664570602553.").amountPaise)
        ignored("UPI ASPRESENTED mandate created towards Google Cloud till 31-12-2036. Max amount Rs 15000.00. Ref: 627978413797.")
    }

    @Test fun dangerousAndInformationalMessagesNeverBook() {
        listOf(
            "Your account XX1234 balance is INR 5000.",
            "OTP 123456 for INR 1500 spent on card XX1234.",
            "INR 1500 debited from account XX1234? Share your password for refund.",
            "Payment failed. INR 1500 not debited from A/c XX1234.",
            "INR 1500 will be debited from account XX1234 tomorrow.",
            "INR 1500 reversed to account XX1234 after it was debited.",
            "Your credit card bill INR 1500 is due. You spent INR 5000 this month.",
            "INR 1500 debited from A/c XX1234 and credited to another account.",
            "Payment request: INR 1500 to be paid from account XX1234.",
            "INR 1500 debited from account XX1234. USD 18.00 charged.",
            "Rs 1500 debited from account XX1234. INR 25 fee charged.",
            "Rs 1500 debited at Sample Store.",
            "INR 0.00 debited from account XX1234.",
            "Rs 1,2.00 debited from account XX1234.",
            "Rs 12.345 debited from account XX1234.",
        ).forEach(::ignored)
    }
    @Test fun supportsIndianGroupingAndRupeeSymbol() {
        assertEquals(12345678L, parsed("₹1,23,456.78 debited from account XX1234 at Sample Store.").amountPaise)
    }
    @Test fun handlesWhitespace() {
        assertEquals(12500L, parsed("Rs.\u00a0125.00\ncredited to account XX1234 from Demo Employer.").amountPaise)
    }
    @Test fun replayFingerprintIsStableAcrossChannelsWithReference() {
        val a = "Rs 125 debited from account XX1234 at Sample Store. Ref 928374610123."
        val b = "Debit alert. INR 125.00 debited from A/c XX1234 at Sample Store. Ref 928374610123."
        assertEquals(messageFingerprint(a, parsed(a), 1000), messageFingerprint(b, parsed(b), 5000))
    }
    @Test fun differentReferencesAndTransferLegsStaySeparate() {
        val a = "Rs 125 debited from account XX1234. Ref 928374610123."
        val b = "Rs 125 credited to account XX4321. Ref 928374610123."
        val c = a.replace("928374610123", "928374610124")
        assertNotEquals(messageFingerprint(a, parsed(a), 1000), messageFingerprint(b, parsed(b), 1000))
        assertNotEquals(messageFingerprint(a, parsed(a), 1000), messageFingerprint(c, parsed(c), 1000))
    }
    @Test fun malformedAndOversizedInputDoesNotCrash() {
        listOf("", "💰".repeat(9000), "Rs 999999999999999999999999999 debited from account XX1234.", "<html>hello</html>").forEach(::ignored)
    }
}
