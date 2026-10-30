package co.pesa.checkout

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The JDK's CLDR data, pinned. fr and fr-CA put a no-break space (U+00A0)
 * before the symbol, not a narrow one (U+202F), and fr (France) writes the
 * pound as "£GB". The SDK shows what the platform gives.
 */
class MoneyFormatJvmTest {
    @Test
    fun platformCurrencyFormat() {
        assertEquals("£2.00", PesaStrings.en.formatAmount("2.00", "GBP"))
        assertEquals("£2.00", PesaStrings.zhHans.formatAmount("2.00", "GBP"))
        assertEquals("2,00\u00A0£", PesaStrings.frCA.formatAmount("2.00", "GBP"))
        assertEquals("2,00\u00A0£GB", PesaStrings.fr.formatAmount("2.00", "GBP"))
        assertEquals("1\u202F234,50\u00A0€", PesaStrings.fr.formatAmount("1234.5", "EUR"))
        assertEquals("2.00 XXZ", PesaStrings.en.formatAmount("2.00", "XXZ"))
    }

    @Test
    fun everyFieldIsListedInEntries() {
        val fields = PesaStrings::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(fields.map { it.name }.toSet(), PesaStrings.en.entries().keys)
    }
}
