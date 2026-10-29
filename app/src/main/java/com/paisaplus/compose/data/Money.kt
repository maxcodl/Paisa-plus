package com.paisaplus.compose.data

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** The one money formatter. [use] is called from the root whenever the setting changes. */
object Money {
    fun formatter(m: MoneyFormat): NumberFormat =
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag(m.locale)).apply {
            runCatching { currency = Currency.getInstance(m.currency) }
            maximumFractionDigits = currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
            minimumFractionDigits = maximumFractionDigits
        }

    @Volatile var format: MoneyFormat = MoneyFormat()
        private set
    private var nf: NumberFormat = formatter(format)

    fun use(m: MoneyFormat) = synchronized(this) { format = m; nf = formatter(m) }

    // NumberFormat isn't thread-safe; workers and the widget format too.
    fun format(v: Double): String = synchronized(this) { nf.format(v) }

    /** Short chart label, e.g. ₹54.9K. chisle: symbol always leads, even in locales that put it after. */
    fun compact(v: Double): String = synchronized(this) {
        val loc = Locale.forLanguageTag(format.locale)
        val short = android.icu.text.CompactDecimalFormat.getInstance(loc, android.icu.text.CompactDecimalFormat.CompactStyle.SHORT)
        (nf.currency?.getSymbol(loc) ?: "") + short.apply { maximumFractionDigits = 1 }.format(v)
    }

    /** Common picks first; then every ISO currency the JDK knows. */
    val currencies: List<Currency> by lazy {
        val top = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY").map(Currency::getInstance)
        top + (Currency.getAvailableCurrencies() - top.toSet()).sortedBy { it.currencyCode }
    }

    val locales = listOf("en-IN", "hi-IN", "en-US", "en-GB", "de-DE", "fr-FR", "es-ES", "ja-JP", "ar-AE", "pt-BR")
}
