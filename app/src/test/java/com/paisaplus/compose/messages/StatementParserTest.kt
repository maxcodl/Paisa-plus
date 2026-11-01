package com.paisaplus.compose.messages

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class StatementParserTest {
    @Test fun loanEmiDue() {
        val m = StatementParser.parse("EMI of INR 4917.00 for Axis Bank Loan A/c XX9692 is due on 05-11-26. Maintain adequate balance prior to the due date to avoid lien / bounce / penal charges.")
        assertEquals(StatementMessage.LoanDue(4917.0, "9692", "Axis Bank", LocalDate.of(2026, 11, 5)), m)
    }

    @Test fun payLaterBill() {
        val m = StatementParser.parse("Your Pay Later bill of Rs 16559 will be debited on 5th of this month from registered bank a/c. View Bill https://u.axio.ac/axo/x -axio")
        assertEquals(StatementMessage.PayLater(16559.0, 5), m)
    }

    @Test fun plainTransactionIsNotAStatement() {
        assertNull(StatementParser.parse("Rs 500 debited from A/c XX1234 on 01-11-26 to merchant"))
    }
}
