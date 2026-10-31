package com.paisaplus.compose.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Enums are stored by name (Room default). Dates are epoch millis.
// No foreign keys: Flutter backups can hold expenses pointing at deleted accounts/categories.

enum class TransactionType { EXPENSE, INCOME }

/** How a payment was made (stored by name on the transaction; null = not set). */
enum class PaymentMethod { UPI, TAP, SWIPE, INSERT, BANK_APP }
/** Banking apps a transfer or card bill can be paid from; saved in the same column as the UPI app (names don't overlap). */
enum class BankApp(val label: String) {
    IMOBILE("iMobile Pay"), FEDMOBILE("FedMobile"), SBICARD("SBI Card"), YONO("YONO SBI"), AXISMOBILE("Axis Mobile"),
    HDFC("HDFC Bank"), KOTAK("Kotak"), OTHER("")
}

/** Which app made a [PaymentMethod.UPI] payment (stored by name; null = not set). */
enum class UpiApp(val label: String) { GPAY("Google Pay"), SUPERMONEY("super.money"), PHONEPE("PhonePe"), PAYTM("Paytm"), BHIM("BHIM"), CRED("CRED"),
    MOBIKWIK("MobiKwik"), FREECHARGE("Freecharge"), NAVI("Navi"),
    NUBANK("Nubank"), MERCADOPAGO("Mercado Pago"), PICPAY("PicPay"), PAGBANK("PagBank"), INTER("Inter"), OTHER("") }
enum class CardType { CASH, BANK, WALLET, CREDIT }
enum class DebtType { DEBT, CREDIT }
enum class LoanKind { LOAN, EMI }
enum class Frequency { DAILY, WEEKLY, MONTHLY }

@Entity(tableName = "expenses", indices = [Index("accountId"), Index("categoryId"), Index("time")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val time: Long,
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long,
    val note: String? = null,
    val method: String? = null, // PaymentMethod name
    val upiApp: String? = null, // UpiApp name, only with method UPI
)

/** [amount] is the running balance (credit cards go negative when owed), as in the Flutter app. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: Int,
    val bankName: String,
    val number: String = "",
    val cardType: CardType,
    val amount: Double = 0.0,
    val creditLimit: Double? = null,
    val limitGroup: String? = null,
    val statementDay: Int? = null, // credit only, 1-31; null = no next-bill estimate
    val network: String? = null, // credit only: CardNetwork name picked in the edit sheet; null = guess from the name
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val icon: Int,
    val budget: Double = 0.0,
    val isBudget: Boolean = false,
    val color: Int = 0xFFFFC107.toInt(),
    val isDefault: Boolean = false,
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val amount: Double,
    val dateTime: Long,
    val expiryDateTime: Long,
    val debtType: DebtType,
)

@Entity(tableName = "debt_transactions", indices = [Index("debtId")])
data class DebtTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Double,
    val time: Long,
)

/** Account is referenced by name, matching the Flutter app and the CSV `loan` columns. */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: LoanKind,
    val account: String,
    val emi: Double,
    val tenure: Int, // 0 = unknown
    val paid: Int = 0,
    val principal: Double = 0.0,
    val nextDue: Long,
    val autoPost: Boolean = true, // imported loans = false
    @ColumnInfo(defaultValue = "0") val dueDay: Int = 0, // intended day-of-month; 0 = use nextDue's day
    @ColumnInfo(defaultValue = "") val label: String = "", // your own name ("Bike loan"); [name] stays the bank/import name installments match on
    @ColumnInfo(defaultValue = "0") val charged: Boolean = false, // the whole purchase is already a charge on the card: installments are tracked, never posted or added to used
) {
    val closed get() = tenure > 0 && paid >= tenure
    val remaining get() = if (tenure > 0) (tenure - paid).coerceIn(0, tenure) * emi else 0.0
    val category get() = if (kind == LoanKind.EMI) "EMI" else "Loan"
}

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val account: String,
    val frequency: Frequency,
    val next: Long,
    val remind: Boolean = true,
    @ColumnInfo(defaultValue = "0") val dueDay: Int = 0, // intended day-of-month for MONTHLY; 0 = use next's day
)

/** Safety net: the last [DeletedExpenseEntity.KEEP] deleted transactions, restorable. */
@Entity(tableName = "deleted_expenses")
data class DeletedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalId: Long,
    val name: String,
    val amount: Double,
    val time: Long,
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long,
    val deletedAt: Long,
    val note: String? = null,
    val method: String? = null,
    val upiApp: String? = null,
) {
    fun toExpense(id: Long = originalId) = ExpenseEntity(id, name, amount, time, type, accountId, categoryId, note, method, upiApp)

    companion object {
        const val KEEP = 20
        fun of(e: ExpenseEntity, at: Long) = DeletedExpenseEntity(0, e.id, e.name, e.amount, e.time, e.type, e.accountId, e.categoryId, at, e.note, e.method, e.upiApp)
    }
}
