package co.pesa.checkout

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal actual fun deviceLocaleTag(): String? = Locale.getDefault().toLanguageTag()

internal actual fun formatMoney(amount: String, currency: String, localeTag: String): String = try {
    val unit = Currency.getInstance(currency.uppercase())
    val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(localeTag))
    format.currency = unit
    if (unit.defaultFractionDigits >= 0) {
        format.minimumFractionDigits = unit.defaultFractionDigits
        format.maximumFractionDigits = unit.defaultFractionDigits
    }
    format.format(BigDecimal(amount))
} catch (e: IllegalArgumentException) {
    "$amount $currency"
}
