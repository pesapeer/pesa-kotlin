package co.pesa.checkout

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages

internal actual fun deviceLocaleTag(): String? =
    NSLocale.preferredLanguages.firstOrNull() as? String ?: NSLocale.currentLocale.localeIdentifier

internal actual fun formatMoney(amount: String, currency: String, localeTag: String): String {
    val format = NSNumberFormatter()
    format.numberStyle = NSNumberFormatterCurrencyStyle
    format.locale = NSLocale(localeIdentifier = localeTag)
    format.currencyCode = currency.uppercase()
    val number = NSDecimalNumber(string = amount, locale = NSLocale(localeIdentifier = "en_US_POSIX"))
    return format.stringFromNumber(number) ?: "$amount $currency"
}
