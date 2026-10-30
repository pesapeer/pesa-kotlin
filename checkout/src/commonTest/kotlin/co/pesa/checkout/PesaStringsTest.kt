package co.pesa.checkout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PesaStringsTest {
    private val placeholder = Regex("""\{\w+\}""")

    @Test
    fun everyLocaleHasEveryKeyAndTheSamePlaceholders() {
        val en = PesaStrings.en.entries()
        for (strings in PesaStrings.all) {
            val entries = strings.entries()
            assertEquals(en.keys, entries.keys, strings.tag)
            for ((key, value) in entries) {
                assertTrue(value.isNotBlank(), "${strings.tag}.$key is blank")
                assertEquals(
                    placeholder.findAll(en.getValue(key)).map { it.value }.toSet(),
                    placeholder.findAll(value).map { it.value }.toSet(),
                    "${strings.tag}.$key placeholders",
                )
            }
        }
    }

    @Test
    fun translationsAreNotEnglish() {
        val same = setOf("cvv", "cid", "expiryFormat")
        for (strings in listOf(PesaStrings.fr, PesaStrings.frCA, PesaStrings.zhHans)) {
            for ((key, value) in strings.entries()) {
                if (key in same) continue
                assertNotEquals(PesaStrings.en.entries().getValue(key), value, "${strings.tag}.$key")
            }
        }
    }

    @Test
    fun quebecOverridesFrench() {
        assertEquals("Courriel pour votre reçu", PesaStrings.frCA.email)
        assertEquals("Carte de crédit ou de débit", PesaStrings.frCA.card)
        assertEquals(PesaStrings.fr.cardNumber, PesaStrings.frCA.cardNumber)
        assertEquals("fr-CA", PesaStrings.frCA.tag)
    }

    @Test
    fun closeTagsAreMapped() {
        val cases = mapOf(
            "en" to "en", "en-GB" to "en", "fr" to "fr", "fr-FR" to "fr", "fr-BE" to "fr",
            "fr-CA" to "fr-CA", "fr_CA" to "fr-CA", "FR-ca" to "fr-CA", "zh" to "zh-Hans",
            "zh-CN" to "zh-Hans", "zh-Hans-CN" to "zh-Hans", "zh-Hant" to "zh-Hans", "zh-TW" to "zh-Hans",
        )
        for ((tag, want) in cases) assertEquals(want, PesaStrings.supportedTag(tag), tag)
        for (tag in listOf(null, "", "de", "es-ES", "pt-BR")) assertNull(PesaStrings.supportedTag(tag), "$tag")
    }

    @Test
    fun resolutionOrderIsHostThenSessionThenDeviceThenEnglish() {
        assertEquals("fr", PesaStrings.resolve("fr-BE", "zh-Hans", "en-US").tag)
        assertEquals("zh-Hans", PesaStrings.resolve(null, "zh-Hans", "fr-FR").tag)
        assertEquals("zh-Hans", PesaStrings.resolve("de", "zh", "fr-FR").tag, "an unsupported host tag falls through")
        assertEquals("fr-CA", PesaStrings.resolve(null, null, "fr-CA").tag)
        assertEquals("en", PesaStrings.resolve(null, null, "de-DE").tag)
        assertEquals("en", PesaStrings.resolve(null, null, null).tag)
    }

    @Test
    fun validationMessagesFollowTheStrings() {
        assertEquals("Saisissez le numéro de carte.", CardValidation.number("", PesaStrings.fr)?.message)
        assertEquals("请输入卡号。", CardValidation.number("", PesaStrings.zhHans)?.message)
        assertEquals("Saisissez le code de sécurité à 4 chiffres.", CardValidation.securityCode("12", CardBrand.AMEX, PesaStrings.fr)?.message)
        assertEquals("请输入 3 位安全码。", CardValidation.securityCode("1", CardBrand.VISA, PesaStrings.zhHans)?.message)
        assertEquals("Cette carte a expiré.", CardValidation.expiry(9, 2026, 2026, 10, PesaStrings.frCA)?.message)
        assertEquals("Saisissez une adresse courriel valide.", CardValidation.email("x", PesaStrings.frCA)?.message)
        assertEquals("Enter the card number.", CardValidation.number("")?.message, "English by default")
    }

    @Test
    fun payLabels() {
        assertEquals("Pay £2.00", PesaStrings.en.payLabel("2.00", "GBP"))
        assertEquals("Pay £2.00 with •••• 0165", PesaStrings.en.payLabel("2.00", "GBP", "0165"))
        assertEquals("支付 £2.00", PesaStrings.zhHans.payLabel("2.00", "GBP"))
        assertEquals("使用 •••• 0165 支付 £2.00", PesaStrings.zhHans.payLabel("2.00", "GBP", "0165"))
        for (fr in listOf(PesaStrings.fr, PesaStrings.frCA)) {
            val label = fr.payLabel("2.00", "GBP", "0165")
            assertTrue(label.startsWith("Payer 2,00") && "£" in label && label.endsWith(" avec •••• 0165"), label)
        }
        assertEquals("Pay soon GBP", PesaStrings.en.payLabel("soon", "GBP"), "an amount that is not a decimal is shown as sent")
        assertEquals("Expire fin 09/28", PesaStrings.fr.expiresOn("09/28"))
    }

    @Test
    fun errorMessagesFollowTheCode() {
        assertEquals(PesaStrings.fr.errorNetwork, PesaStrings.fr.errorMessage(PesaCheckoutException.Code.NETWORK))
        assertEquals(PesaStrings.zhHans.numberInvalid, PesaStrings.zhHans.errorMessage(PesaCheckoutException.Code.INVALID_CARD, "cardNumber"))
        assertEquals(PesaStrings.en.errorSavedCard, PesaStrings.en.errorMessage(PesaCheckoutException.Code.INVALID_CARD, "savedCard.token"))
        assertEquals(PesaStrings.en.errorInvalidCard, PesaStrings.en.errorMessage(PesaCheckoutException.Code.INVALID_CARD, "other"))
    }
}
