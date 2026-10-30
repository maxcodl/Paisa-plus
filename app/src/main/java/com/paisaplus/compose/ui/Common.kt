package com.paisaplus.compose.ui

import com.paisaplus.compose.data.UpiApp
import com.paisaplus.compose.data.BankApp
import com.paisaplus.compose.data.PaymentMethod
import com.paisaplus.compose.R
import com.paisaplus.compose.str

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.paisaplus.compose.data.CardType
import com.paisaplus.compose.data.ExpenseEntity
import com.paisaplus.compose.data.TransactionType
import com.paisaplus.compose.ui.theme.LocalPaisaColors
import java.time.format.DateTimeFormatter


fun money(v: Double): String = com.paisaplus.compose.data.Money.format(v)
fun moneyOrHidden(v: Double, hidden: Boolean) = if (hidden) "••••••" else money(v)

val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

val CardType.icon: ImageVector
    get() = when (this) {
        CardType.CASH -> Icons.Rounded.Payments
        CardType.BANK -> Icons.Rounded.AccountBalance
        CardType.WALLET -> Icons.Rounded.AccountBalanceWallet
        CardType.CREDIT -> Icons.Rounded.CreditCard
    }

val CardType.label get() = when (this) {
    CardType.CASH -> "Cash"
    CardType.BANK -> str(R.string.bank)
    CardType.WALLET -> str(R.string.wallet)
    CardType.CREDIT -> str(R.string.credit_card)
}

@Composable
fun incomeColor(): Color = LocalPaisaColors.current.income

@Composable
fun amountColor(type: TransactionType) =
    if (type == TransactionType.INCOME) incomeColor() else LocalPaisaColors.current.expense

/** Outlined button showing a date; opens a DatePickerDialog. Keeps the time-of-day of [millis]. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, millis: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val zone = java.time.ZoneId.systemDefault()
    val dt = java.time.Instant.ofEpochMilli(millis).atZone(zone)
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("$label: ${dt.format(dayFmt)}")
    }
    if (open) {
        val s = androidx.compose.material3.rememberDatePickerState(
            dt.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    s.selectedDateMillis?.let {
                        val day = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        onChange(java.time.LocalDateTime.of(day, dt.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                    }
                    open = false
                }) { Text(str(R.string.ok)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text(str(R.string.cancel)) } },
        ) { androidx.compose.material3.DatePicker(s) }
    }
}

/** Single-line decimal input; [value] is the raw text. */
@Composable
fun AmountField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false) =
    androidx.compose.material3.OutlinedTextField(
        value, { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
        label = { Text(label) },
        isError = isError,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )

fun String.toAmount() = replace(',', '.').toDoubleOrNull()
fun Double.plainOrEmpty() = if (this == 0.0) "" else toBigDecimal().stripTrailingZeros().toPlainString()

val PaymentMethod.label get() = str(when (this) {
    PaymentMethod.UPI -> R.string.upi; PaymentMethod.TAP -> R.string.tap_to_pay
    PaymentMethod.SWIPE -> R.string.swipe; PaymentMethod.INSERT -> R.string.insert_chip
    PaymentMethod.BANK_APP -> R.string.bank_app
})


val UpiApp.display get() = label.ifEmpty { str(R.string.other) }
val BankApp.display get() = label.ifEmpty { str(R.string.other) }

/** "UPI · Google Pay", "Bank app · iMobile Pay", "Tap to pay"; null when the transaction has no method. */
fun ExpenseEntity.methodLabel(): String? {
    val m = method?.let { n -> PaymentMethod.entries.firstOrNull { it.name == n } } ?: return null
    val app = when (m) {
        PaymentMethod.UPI -> upiApp?.let { n -> UpiApp.entries.firstOrNull { it.name == n } }?.display
        PaymentMethod.BANK_APP -> upiApp?.let { n -> BankApp.entries.firstOrNull { it.name == n } }?.display
        else -> null
    }
    return listOfNotNull(m.label, app).joinToString(" · ")
}
