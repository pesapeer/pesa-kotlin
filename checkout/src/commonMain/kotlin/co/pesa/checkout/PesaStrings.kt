package co.pesa.checkout

/**
 * Every sentence the SDK shows, in one language. The SDK ships [en], [fr],
 * [frCA] and [zhHans]. Get one with [resolve]. A host may [copy] one to
 * change a sentence.
 *
 * Templates hold named placeholders such as `{amount}`. Use the functions
 * below to fill them.
 */
data class PesaStrings(
    /** The BCP 47 tag of this table, such as `fr-CA`. Money is formatted for it. */
    val tag: String,
    /** The card payment method, for the host's own method list. */
    val card: String,
    val cardNumber: String,
    val expiryFormat: String,
    val cvv: String,
    val cid: String,
    val holderName: String,
    val email: String,
    val securityCode: String,
    val securityCodeOptional: String,
    /** `{date}` */
    val expires: String,
    val expired: String,
    val useAnotherCard: String,
    val saveCard: String,
    /** `{amount}` */
    val pay: String,
    /** `{amount}`, `{last4}` */
    val payWithSavedCard: String,
    /** `{title}`, `{last4}` */
    val titleWithSavedCard: String,
    val numberEmpty: String,
    val numberInvalid: String,
    val expiryInvalid: String,
    val cardExpired: String,
    /** `{length}` */
    val securityCodeLength: String,
    val holderNameLength: String,
    val emailInvalid: String,
    val errorInvalidCard: String,
    val errorSavedCard: String,
    val errorInvalidRequest: String,
    val errorNotFound: String,
    val errorIntentClosed: String,
    val errorAttemptInProgress: String,
    val errorRateLimited: String,
    val errorNetwork: String,
    val errorTimeout: String,
    val errorServer: String,
) {
    /** [amount] (a decimal string such as `2.00`) in [currency], formatted by the platform for [tag]. */
    fun formatAmount(amount: String, currency: String): String =
        if (PLAIN_DECIMAL.matches(amount)) formatMoney(amount, currency, tag) else "$amount $currency"

    /** "Pay £2.00", or "Pay £2.00 with •••• 0165" when [last4] is set. */
    fun payLabel(amount: String, currency: String, last4: String? = null): String {
        val money = formatAmount(amount, currency)
        return if (last4 == null) pay.fill("amount" to money) else payWithSavedCard.fill("amount" to money, "last4" to last4)
    }

    fun expiresOn(date: String): String = expires.fill("date" to date)

    fun enterSecurityCode(length: Int): String = securityCodeLength.fill("length" to length.toString())

    /** The sentence for an SDK error. [field] is the gateway's field for [PesaCheckoutException.Code.INVALID_CARD]. */
    fun errorMessage(code: PesaCheckoutException.Code, field: String = ""): String = when (code) {
        PesaCheckoutException.Code.INVALID_CARD -> when (field) {
            "cardNumber" -> numberInvalid
            "expiryMonth", "expiryYear" -> expiryInvalid
            "holderName" -> holderNameLength
            "savedCard.token" -> errorSavedCard
            else -> errorInvalidCard
        }
        PesaCheckoutException.Code.INVALID_REQUEST -> errorInvalidRequest
        PesaCheckoutException.Code.NOT_FOUND -> errorNotFound
        PesaCheckoutException.Code.INTENT_CLOSED -> errorIntentClosed
        PesaCheckoutException.Code.ATTEMPT_IN_PROGRESS -> errorAttemptInProgress
        PesaCheckoutException.Code.RATE_LIMITED -> errorRateLimited
        PesaCheckoutException.Code.NETWORK -> errorNetwork
        PesaCheckoutException.Code.TIMEOUT -> errorTimeout
        PesaCheckoutException.Code.SERVER -> errorServer
    }

    internal fun entries(): Map<String, String> = mapOf(
        "tag" to tag, "card" to card, "cardNumber" to cardNumber, "expiryFormat" to expiryFormat,
        "cvv" to cvv, "cid" to cid, "holderName" to holderName, "email" to email,
        "securityCode" to securityCode, "securityCodeOptional" to securityCodeOptional,
        "expires" to expires, "expired" to expired, "useAnotherCard" to useAnotherCard, "saveCard" to saveCard,
        "pay" to pay, "payWithSavedCard" to payWithSavedCard, "titleWithSavedCard" to titleWithSavedCard,
        "numberEmpty" to numberEmpty, "numberInvalid" to numberInvalid, "expiryInvalid" to expiryInvalid,
        "cardExpired" to cardExpired, "securityCodeLength" to securityCodeLength,
        "holderNameLength" to holderNameLength, "emailInvalid" to emailInvalid,
        "errorInvalidCard" to errorInvalidCard, "errorSavedCard" to errorSavedCard,
        "errorInvalidRequest" to errorInvalidRequest, "errorNotFound" to errorNotFound,
        "errorIntentClosed" to errorIntentClosed, "errorAttemptInProgress" to errorAttemptInProgress,
        "errorRateLimited" to errorRateLimited, "errorNetwork" to errorNetwork,
        "errorTimeout" to errorTimeout, "errorServer" to errorServer,
    )

    companion object {
        val en: PesaStrings get() = stringsEn
        val fr: PesaStrings get() = stringsFr
        val frCA: PesaStrings get() = stringsFrCA
        val zhHans: PesaStrings get() = stringsZhHans
        val all: List<PesaStrings> get() = listOf(en, fr, frCA, zhHans)

        /**
         * The shipped tag for [tag], or null when the SDK has no table for it.
         * Close tags are mapped: `fr-BE` to `fr`, `fr_CA` to `fr-CA`, any
         * `zh` (`zh-CN`, `zh-Hant`) to `zh-Hans`, `en-GB` to `en`.
         */
        fun supportedTag(tag: String?): String? {
            val parts = tag?.trim()?.replace('_', '-')?.lowercase()?.split('-')?.filter { it.isNotEmpty() }
            return when (parts?.firstOrNull()) {
                "en" -> "en"
                "fr" -> if ("ca" in parts.drop(1)) "fr-CA" else "fr"
                "zh" -> "zh-Hans"
                else -> null
            }
        }

        /** The first table found for: [locale] from the host, then [sessionLocale], then the device, then English. */
        fun resolve(locale: String? = null, sessionLocale: String? = null): PesaStrings =
            resolve(locale, sessionLocale, deviceLocaleTag())

        internal fun resolve(locale: String?, sessionLocale: String?, device: String?): PesaStrings =
            when (supportedTag(locale) ?: supportedTag(sessionLocale) ?: supportedTag(device)) {
                "fr" -> fr
                "fr-CA" -> frCA
                "zh-Hans" -> zhHans
                else -> en
            }

        private val PLAIN_DECIMAL = Regex("""-?\d+(\.\d+)?""")
    }
}

internal fun String.fill(vararg values: Pair<String, String>): String =
    values.fold(this) { text, (name, value) -> text.replace("{$name}", value) }

/** The device's preferred language as a BCP 47 tag. */
internal expect fun deviceLocaleTag(): String?

/** The platform's currency format for [localeTag]. [amount] is a plain decimal string. */
internal expect fun formatMoney(amount: String, currency: String, localeTag: String): String
