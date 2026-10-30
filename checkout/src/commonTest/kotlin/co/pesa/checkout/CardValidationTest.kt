package co.pesa.checkout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardValidationTest {
    @Test
    fun luhnAcceptsTheTribeTestCards() {
        for (n in listOf("4111111111111111", "4012888888881881", "5555555555554444", "5105105105105100", "378282246310005", "6200000000000005")) {
            assertTrue(CardValidation.luhn(n), n)
        }
        assertTrue(!CardValidation.luhn("4111111111111112"))
        assertTrue(!CardValidation.luhn(""))
        assertTrue(!CardValidation.luhn("4111 1111"))
    }

    @Test
    fun brandFromLeadingDigits() {
        assertEquals(CardBrand.VISA, CardBrand.detect("4"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("5555"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("2221"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("2720"))
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect("2721"))
        assertEquals(CardBrand.AMEX, CardBrand.detect("37"))
        assertEquals(CardBrand.AMEX, CardBrand.detect("34"))
        assertEquals(CardBrand.UNIONPAY, CardBrand.detect("62"))
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect(""))
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect("9"))
    }

    @Test
    fun numberLengthFollowsTheBrand() {
        assertNull(CardValidation.number("4111 1111 1111 1111"))
        assertNull(CardValidation.number("3782 822463 10005"))
        assertNotNull(CardValidation.number("4111111111111"))
        assertNotNull(CardValidation.number("5555555555554444 1"))
    }

    @Test
    fun expiry() {
        assertEquals(12 to 2028, CardValidation.parseExpiry("12/28"))
        assertEquals(1 to 2030, CardValidation.parseExpiry("01/2030"))
        assertEquals(7 to 2027, CardValidation.parseExpiry("0727"))
        assertNull(CardValidation.parseExpiry("13/28"))
        assertNull(CardValidation.parseExpiry("1/28"))
        assertNull(CardValidation.expiry(10, 2026, 2026, 10))
        assertNotNull(CardValidation.expiry(9, 2026, 2026, 10))
        assertNotNull(CardValidation.expiry(1, 2060, 2026, 10))
    }

    @Test
    fun securityCodeLengthFollowsTheBrand() {
        assertNull(CardValidation.securityCode("123", CardBrand.VISA))
        assertNotNull(CardValidation.securityCode("1234", CardBrand.VISA))
        assertNull(CardValidation.securityCode("1234", CardBrand.AMEX))
        assertNull(CardValidation.securityCode("1234", CardBrand.UNKNOWN))
        assertNotNull(CardValidation.securityCode("12", CardBrand.UNKNOWN))
    }

    @Test
    fun holderAndEmail() {
        assertNull(CardValidation.holderName("A Cardholder"))
        assertNotNull(CardValidation.holderName("Al"))
        assertNotNull(CardValidation.holderName("X".repeat(27)))
        assertNull(CardValidation.email("a@b.co"))
        assertNotNull(CardValidation.email("ab.co"))
        assertNotNull(CardValidation.email(null))
    }

    @Test
    fun formatGroupsDigits() {
        assertEquals("4111 1111 1111 1111", CardValidation.format("4111111111111111"))
        assertEquals("3782 822463 10005", CardValidation.format("378282246310005"))
        assertEquals("6200 0000 0000 0000 005", CardValidation.format("6200000000000000005"))
        assertEquals("4111 11", CardValidation.format("4111-11"))
    }

    @Test
    fun cardDetailsWipeClearsTheNumber() {
        val card = CardDetails("4111 1111 1111 1111", 12, 2028, "000", "A Cardholder")
        assertEquals("1111", card.last4)
        assertEquals(CardBrand.VISA, card.brand)
        assertTrue(CardValidation.validate(card, 2026, 10).isEmpty())
        card.wipe()
        assertEquals("", card.digits())
        assertEquals("\u0000\u0000\u0000", card.code())
    }

    @Test
    fun brandFromItsName() {
        for (name in listOf("visa", "VISA", " Visa ")) assertEquals(CardBrand.VISA, CardBrand.parse(name), name)
        for (name in listOf("mastercard", "MasterCard", "master_card", "master-card", "mc", "MC")) assertEquals(CardBrand.MASTERCARD, CardBrand.parse(name), name)
        for (name in listOf("amex", "AMEX", "americanexpress", "American Express", "american_express")) assertEquals(CardBrand.AMEX, CardBrand.parse(name), name)
        for (name in listOf("unionpay", "UnionPay", "union-pay", "cup", "CUP", "chinaunionpay", "China UnionPay")) assertEquals(CardBrand.UNIONPAY, CardBrand.parse(name), name)
        for (name in listOf("", "diners", "card", "visa electron")) assertEquals(CardBrand.UNKNOWN, CardBrand.parse(name), name)
    }

    @Test
    fun savedCardExpiry() {
        val card = SavedCard(token = "t", brand = "visa", last4 = "0165", expiry = "09/2028")
        assertEquals("09/28", card.shortExpiry)
        assertTrue(card.isUsable(2028, 9), "usable through its expiry month")
        assertFalse(card.isUsable(2028, 10))
        assertFalse(card.isUsable(2029, 1))
        assertEquals("soon", SavedCard(token = "t", expiry = "soon").shortExpiry)
        assertTrue(SavedCard(token = "t", expiry = "soon").isUsable(2030, 1), "an unparsed expiry is left to the gateway")
    }
}
