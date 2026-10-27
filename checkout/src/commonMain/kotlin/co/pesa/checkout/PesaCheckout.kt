package co.pesa.checkout

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

/**
 * @property checkoutUrl The checkout edge. Only ever Pesa's.
 * @property pins SPKI pins (`sha256/…`) for the edge's certificate chain.
 * Two or more, rotated by SDK release. Empty disables pinning (tests only).
 * @property pollInterval How long between reads while the acquirer works.
 * @property maxPolls How many reads before giving up with [PesaCheckoutException.Code.TIMEOUT].
 * @property challengePolls How many reads while a 3-D Secure challenge is open with the cardholder.
 */
data class PesaCheckoutConfig(
    val checkoutUrl: String = "https://checkout.pesa.co",
    val pins: List<String> = emptyList(),
    val pollInterval: Long = 1_500,
    val maxPolls: Int = 60,
    val challengePolls: Int = 200,
)

/**
 * The Pesa checkout client. The app holds an intent id and a client secret
 * from its own backend, collects the card in its own native fields, and
 * calls [payWithCard]. The card number leaves the device once, to the
 * checkout edge, and the SDK keeps nothing.
 */
class PesaCheckout internal constructor(
    private val config: PesaCheckoutConfig,
    engine: HttpClientEngine,
    private val now: () -> Pair<Int, Int> = ::currentYearMonth,
) {
    constructor(config: PesaCheckoutConfig = PesaCheckoutConfig()) :
        this(config, httpClientEngine(config.pins))

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(this@PesaCheckout.json) }
    }
    private val base = config.checkoutUrl.trimEnd('/')

    /** Reads the checkout view of the intent: amount, methods, latest attempt. */
    suspend fun session(intentId: String, clientSecret: String): CheckoutSession {
        val res = transport { http.get("$base/c/$intentId/session") { header("X-Client-Secret", clientSecret) } }
        return parse(res)
    }

    /** The URL to open for a 3-D Secure challenge, when one is pending. */
    fun challengeUrl(intentId: String, clientSecret: String): String = "$base/c/$intentId/challenge?t=$clientSecret"

    /**
     * Charges the card and waits for the acquirer's answer.
     *
     * [openChallenge] is called at most once, with the page to show the
     * cardholder when the issuer asks them to authenticate; the app opens it
     * in Custom Tabs or SFSafariViewController (see [defaultChallengeOpener])
     * and this call keeps polling until the attempt settles. The card is
     * wiped before this function returns, whatever happened.
     */
    suspend fun payWithCard(
        intentId: String,
        clientSecret: String,
        card: CardDetails,
        email: String? = null,
        saveCard: Boolean? = null,
        openChallenge: suspend (url: String) -> Unit,
    ): CheckoutResult {
        try {
            val (y, m) = now()
            val problems = CardValidation.validate(card, y, m)
            if (problems.isNotEmpty()) throw PesaCheckoutException(PesaCheckoutException.Code.INVALID_CARD, problems.first().message)
            val res = transport { http.post("$base/c/$intentId/card") {
                contentType(ContentType.Application.Json)
                setBody(
                    CardChargeRequest(
                        clientSecret = clientSecret, cardNumber = card.digits(),
                        expiryMonth = card.expiryMonth, expiryYear = card.expiryYear,
                        securityCode = card.code(), holderName = card.holderName.trim(),
                        email = email?.trim()?.ifEmpty { null }, saveCard = saveCard,
                    ),
                )
            } }
            val first: CheckoutSession = parse(res)
            return await(intentId, clientSecret, first, openChallenge)
        } finally {
            card.wipe()
        }
    }

    /** Resumes waiting on an intent, e.g. after the app was backgrounded for the challenge. */
    suspend fun await(intentId: String, clientSecret: String, openChallenge: suspend (url: String) -> Unit = {}): CheckoutResult =
        await(intentId, clientSecret, session(intentId, clientSecret), openChallenge)

    private suspend fun await(
        intentId: String,
        clientSecret: String,
        initial: CheckoutSession,
        openChallenge: suspend (url: String) -> Unit,
    ): CheckoutResult {
        var s = initial
        var challengeOpened = false
        var polls = 0
        while (true) {
            val a = s.attempt
            when {
                s.state.isFinal && a?.state != AttemptState.REQUIRES_3DS -> return CheckoutResult(s.state, s.intentId, a)
                a == null -> return CheckoutResult(s.state, s.intentId, null)
                a.state.isSettled || a.state == AttemptState.UNKNOWN -> return CheckoutResult(s.state, s.intentId, a)
                a.state == AttemptState.REQUIRES_3DS && !challengeOpened -> {
                    challengeOpened = true
                    openChallenge(a.challengeUrl.ifEmpty { challengeUrl(intentId, clientSecret) })
                }
            }
            val limit = if (challengeOpened) config.challengePolls else config.maxPolls
            if (++polls > limit) throw PesaCheckoutException(PesaCheckoutException.Code.TIMEOUT, "The payment is taking longer than expected. Check its status before trying again.")
            delay(config.pollInterval)
            s = try {
                session(intentId, clientSecret)
            } catch (e: PesaCheckoutException) {
                if (e.code == PesaCheckoutException.Code.NETWORK) s else throw e
            }
        }
    }

    private suspend inline fun <reified T> parse(res: HttpResponse): T {
        if (res.status.value in 200..299) return res.body()
        val body = runCatching { json.decodeFromString(ErrorBody.serializer(), res.bodyAsText()) }.getOrNull()?.error
        val message = body?.message?.ifEmpty { null } ?: "The payment service answered ${res.status.value}."
        val code = when (res.status) {
            HttpStatusCode.BadRequest, HttpStatusCode.UnprocessableEntity ->
                if (body?.field in CARD_FIELDS) PesaCheckoutException.Code.INVALID_CARD else PesaCheckoutException.Code.INVALID_REQUEST
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.NotFound -> PesaCheckoutException.Code.NOT_FOUND
            HttpStatusCode.Conflict -> PesaCheckoutException.Code.ATTEMPT_IN_PROGRESS
            HttpStatusCode.Gone -> PesaCheckoutException.Code.INTENT_CLOSED
            HttpStatusCode.TooManyRequests -> PesaCheckoutException.Code.RATE_LIMITED
            else -> PesaCheckoutException.Code.SERVER
        }
        throw PesaCheckoutException(code, message)
    }

    fun close() = http.close()

    private companion object {
        val CARD_FIELDS = setOf("cardNumber", "expiryMonth", "expiryYear", "securityCode", "holderName")
    }
}

internal fun currentYearMonth(): Pair<Int, Int> {
    val d = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    return d.year to d.monthNumber
}

/** Builds the platform engine with certificate pinning on [pins]. */
internal expect fun httpClientEngine(pins: List<String>): HttpClientEngine

/** Wraps transport failures as [PesaCheckoutException.Code.NETWORK]. */
internal suspend inline fun <T> transport(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: PesaCheckoutException) {
    throw e
} catch (e: Exception) {
    throw PesaCheckoutException(PesaCheckoutException.Code.NETWORK, "Could not reach the payment service.", e)
}
