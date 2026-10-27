package co.pesa.checkout

/** The card brands the checkout recognises from the number. */
enum class CardBrand(val wire: String, val cvvLength: Int, val lengths: IntRange) {
    VISA("visa", 3, 13..19),
    MASTERCARD("mastercard", 3, 16..16),
    AMEX("amex", 4, 15..15),
    UNIONPAY("unionpay", 3, 16..19),
    UNKNOWN("card", 3, 13..19);

    companion object {
        /** Detects the brand from the leading digits. Works on partial input. */
        fun detect(digits: CharSequence): CardBrand {
            val d = digits.filter { it.isDigit() }.toString()
            if (d.isEmpty()) return UNKNOWN
            val first2 = d.take(2).toIntOrNull() ?: -1
            val first4 = d.take(4).toIntOrNull() ?: -1
            return when {
                d[0] == '4' -> VISA
                first2 in 51..55 -> MASTERCARD
                d.length >= 4 && first4 in 2221..2720 -> MASTERCARD
                d.length < 4 && d[0] == '2' && (d.length == 1 || first2 in 22..27) -> MASTERCARD
                first2 == 34 || first2 == 37 -> AMEX
                first2 == 62 || first2 == 81 -> UNIONPAY
                else -> UNKNOWN
            }
        }
    }
}

/**
 * What the cardholder typed. The number and security code are char arrays so
 * the app can [wipe] them the moment the request is sent; strings would
 * linger in the heap until the collector got to them.
 */
class CardDetails(
    number: CharArray,
    val expiryMonth: Int,
    val expiryYear: Int,
    securityCode: CharArray,
    val holderName: String,
) {
    private val number: CharArray = number.copyOf()
    private val securityCode: CharArray = securityCode.copyOf()

    constructor(number: String, expiryMonth: Int, expiryYear: Int, securityCode: String, holderName: String) :
        this(number.toCharArray(), expiryMonth, expiryYear, securityCode.toCharArray(), holderName)

    val brand: CardBrand get() = CardBrand.detect(number.concatToString())
    val last4: String get() = digits().takeLast(4)

    internal fun digits(): String = number.filter { it.isDigit() }.joinToString("")
    internal fun code(): String = securityCode.concatToString()

    /** Overwrites the number and the security code. The object is unusable afterwards. */
    fun wipe() {
        number.fill('\u0000')
        securityCode.fill('\u0000')
    }

    override fun toString(): String = "CardDetails(${brand.wire} •••• $last4)"
}

/** A validation problem, named by the field it belongs to. */
data class CardFieldError(val field: CardField, val message: String)

enum class CardField { NUMBER, EXPIRY, SECURITY_CODE, HOLDER_NAME, EMAIL }

object CardValidation {
    fun luhn(digits: String): Boolean {
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return false
        var sum = 0
        var double = false
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (double) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            double = !double
        }
        return sum % 10 == 0
    }

    fun number(raw: CharSequence): CardFieldError? {
        val d = raw.filter { it.isDigit() }.toString()
        val brand = CardBrand.detect(d)
        return when {
            d.isEmpty() -> CardFieldError(CardField.NUMBER, "Enter the card number.")
            d.length !in brand.lengths || !luhn(d) -> CardFieldError(CardField.NUMBER, "This card number is not valid.")
            else -> null
        }
    }

    /** Accepts "MM/YY", "MM/YYYY", "MMYY" or "MMYYYY". Returns month to four-digit year. */
    fun parseExpiry(raw: CharSequence): Pair<Int, Int>? {
        val d = raw.filter { it.isDigit() }.toString()
        if (d.length != 4 && d.length != 6) return null
        val month = d.take(2).toInt()
        var year = d.drop(2).toInt()
        if (d.length == 4) year += 2000
        if (month !in 1..12) return null
        return month to year
    }

    fun expiry(month: Int, year: Int, nowYear: Int, nowMonth: Int): CardFieldError? = when {
        month !in 1..12 || year < 2000 -> CardFieldError(CardField.EXPIRY, "Enter the expiry as MM/YY.")
        year < nowYear || (year == nowYear && month < nowMonth) -> CardFieldError(CardField.EXPIRY, "This card has expired.")
        year > nowYear + 25 -> CardFieldError(CardField.EXPIRY, "Enter the expiry as MM/YY.")
        else -> null
    }

    fun securityCode(raw: CharSequence, brand: CardBrand): CardFieldError? {
        val d = raw.filter { it.isDigit() }.toString()
        val ok = if (brand == CardBrand.UNKNOWN) d.length in 3..4 else d.length == brand.cvvLength
        return if (ok) null else CardFieldError(CardField.SECURITY_CODE, "Enter the ${brand.cvvLength}-digit security code.")
    }

    fun holderName(raw: String): CardFieldError? {
        val t = raw.trim()
        return if (t.length in 3..26) null else CardFieldError(CardField.HOLDER_NAME, "Enter the name as it appears on the card, 3 to 26 characters.")
    }

    fun email(raw: String?): CardFieldError? {
        val t = raw?.trim().orEmpty()
        val at = t.indexOf('@')
        return if (at > 0 && at < t.length - 1 && t.length <= 50) null else CardFieldError(CardField.EMAIL, "Enter a valid email address.")
    }

    /** Every problem with the card, in field order. Empty means it may be sent. */
    fun validate(card: CardDetails, nowYear: Int, nowMonth: Int): List<CardFieldError> = listOfNotNull(
        number(card.digits()),
        expiry(card.expiryMonth, card.expiryYear, nowYear, nowMonth),
        securityCode(card.code(), card.brand),
        holderName(card.holderName),
    )

    /** Groups digits for display: 4-6-5 for Amex, 4-4-4-4(-3) otherwise. */
    fun format(raw: CharSequence): String {
        val d = raw.filter { it.isDigit() }.toString().take(19)
        val groups = if (CardBrand.detect(d) == CardBrand.AMEX) listOf(4, 6, 5) else listOf(4, 4, 4, 4, 3)
        val out = StringBuilder()
        var i = 0
        for (g in groups) {
            if (i >= d.length) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(d, i, minOf(i + g, d.length))
            i += g
        }
        return out.toString()
    }
}
