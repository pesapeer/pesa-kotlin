package co.pesa.checkout

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PesaCheckoutTest {
    private val intent = "int_01J"
    private val secret = "cs_test_secret"

    private fun session(state: String, attempt: String? = null): String {
        val a = attempt?.let { ""","attempt":{"state":"$it","challengeUrl":"https://checkout.test/c/$intent/challenge?t=$secret","card":{"brand":"visa","last4":"1111"}}""" } ?: ""
        return """{"intentId":"$intent","state":"$state","amount":"25.00","currency":"GBP","methods":["card"],"needsEmail":false,"returnUrl":"https://app.test/return"$a}"""
    }

    private fun client(script: MutableList<String>, posts: MutableList<HttpRequestData> = mutableListOf(), status: (HttpRequestData) -> HttpStatusCode = { HttpStatusCode.OK }): PesaCheckout {
        val engine = MockEngine { req ->
            when (req.method) {
                HttpMethod.Post -> {
                    posts += req
                    respond(script.removeAt(0), HttpStatusCode.Accepted, headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> {
                    assertEquals(secret, req.headers["X-Client-Secret"])
                    respond(script.removeAt(0), status(req), headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        return PesaCheckout(PesaCheckoutConfig(checkoutUrl = "https://checkout.test", pollInterval = 0, maxPolls = 5, challengePolls = 8), engine) { 2026 to 10 }
    }

    private fun card() = CardDetails("4111 1111 1111 1111", 12, 2028, "000", "A Cardholder")

    @Test
    fun saleWithoutChallenge() = runTest {
        val posts = mutableListOf<HttpRequestData>()
        val c = client(mutableListOf(session("open", "pending"), session("open", "pending"), session("settled", "captured")), posts)
        val card = card()
        val r = c.payWithCard(intent, secret, card, openChallenge = { error("no challenge expected") })
        assertEquals(IntentState.SETTLED, r.state)
        assertTrue(r.succeeded)
        assertEquals("1111", r.attempt?.card?.last4)
        assertEquals("", card.digits(), "card wiped after submit")

        val body = Json.parseToJsonElement((posts.single().body as TextContent).text).jsonObject
        assertEquals("4111111111111111", body["cardNumber"]!!.jsonPrimitive.content)
        assertEquals(secret, body["clientSecret"]!!.jsonPrimitive.content)
        assertEquals("https://checkout.test/c/$intent/card", posts.single().url.toString())
    }

    @Test
    fun challengeIsOpenedOnceThenPollingContinues() = runTest {
        var opened = 0
        val c = client(mutableListOf(
            session("open", "pending"), session("open", "requires_3ds"), session("open", "requires_3ds"),
            session("open", "pending"), session("settled", "captured"),
        ))
        val r = c.payWithCard(intent, secret, card()) { url ->
            opened++
            assertTrue(url.startsWith("https://checkout.test/c/$intent/challenge"))
        }
        assertEquals(1, opened)
        assertEquals(IntentState.SETTLED, r.state)
    }

    @Test
    fun declineLeavesTheIntentOpen() = runTest {
        val c = client(mutableListOf(session("open", "pending"), session("open", "declined")))
        val r = c.payWithCard(intent, secret, card()) { }
        assertEquals(IntentState.OPEN, r.state)
        assertEquals(AttemptState.DECLINED, r.attempt?.state)
        assertTrue(!r.succeeded)
    }

    @Test
    fun manualCaptureEndsAuthorized() = runTest {
        val c = client(mutableListOf(session("open", "pending"), session("authorized", "authorized")))
        val r = c.payWithCard(intent, secret, card()) { }
        assertEquals(IntentState.AUTHORIZED, r.state)
        assertTrue(r.succeeded)
    }

    @Test
    fun pollingCeiling() = runTest {
        val script = MutableList(20) { session("open", "pending") }
        val c = client(script)
        val e = assertFailsWith<PesaCheckoutException> { c.payWithCard(intent, secret, card()) { } }
        assertEquals(PesaCheckoutException.Code.TIMEOUT, e.code)
        assertEquals(20 - 1 - 5, script.size, "one post, then maxPolls reads")
    }

    @Test
    fun invalidCardNeverLeavesTheDevice() = runTest {
        val posts = mutableListOf<HttpRequestData>()
        val c = client(mutableListOf(), posts)
        val e = assertFailsWith<PesaCheckoutException> {
            c.payWithCard(intent, secret, CardDetails("4111111111111112", 12, 2028, "000", "A Cardholder")) { }
        }
        assertEquals(PesaCheckoutException.Code.INVALID_CARD, e.code)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun errorMapping() = runTest {
        suspend fun codeFor(status: HttpStatusCode, body: String = """{"error":{"code":"x","message":"Nope."}}"""): PesaCheckoutException.Code {
            val c = client(mutableListOf(body)) { status }
            return assertFailsWith<PesaCheckoutException> { c.session(intent, secret) }.code
        }
        assertEquals(PesaCheckoutException.Code.NOT_FOUND, codeFor(HttpStatusCode.NotFound))
        assertEquals(PesaCheckoutException.Code.INTENT_CLOSED, codeFor(HttpStatusCode.Gone))
        assertEquals(PesaCheckoutException.Code.ATTEMPT_IN_PROGRESS, codeFor(HttpStatusCode.Conflict))
        assertEquals(PesaCheckoutException.Code.RATE_LIMITED, codeFor(HttpStatusCode.TooManyRequests))
        assertEquals(PesaCheckoutException.Code.SERVER, codeFor(HttpStatusCode.BadGateway, "upstream down"))
        assertEquals(PesaCheckoutException.Code.INVALID_REQUEST, codeFor(HttpStatusCode.BadRequest))
        assertEquals(PesaCheckoutException.Code.INVALID_CARD, codeFor(HttpStatusCode.BadRequest, """{"error":{"code":"invalid_request","message":"Bad card.","field":"cardNumber"}}"""))
    }

    @Test
    fun sessionReadsTheLatestAttempt() = runTest {
        val c = client(mutableListOf(session("open", "requires_3ds")))
        val s = c.session(intent, secret)
        assertEquals(AttemptState.REQUIRES_3DS, s.attempt?.state)
        assertTrue(!s.acceptsCard)
        val fresh = Json { ignoreUnknownKeys = true }.decodeFromString(CheckoutSession.serializer(), session("open"))
        assertTrue(fresh.acceptsCard)
    }
}
