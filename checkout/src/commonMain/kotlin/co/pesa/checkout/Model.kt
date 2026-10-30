package co.pesa.checkout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where the intent is. Only [OPEN] accepts a card. */
@Serializable
enum class IntentState {
    @SerialName("open") OPEN,
    @SerialName("authorized") AUTHORIZED,
    @SerialName("settled") SETTLED,
    @SerialName("expired") EXPIRED,
    @SerialName("cancelled") CANCELLED;

    val isFinal: Boolean get() = this != OPEN
}

/** Where the latest card attempt is. */
@Serializable
enum class AttemptState {
    @SerialName("pending") PENDING,
    @SerialName("requires_3ds") REQUIRES_3DS,
    @SerialName("authorized") AUTHORIZED,
    @SerialName("captured") CAPTURED,
    @SerialName("declined") DECLINED,
    @SerialName("failed") FAILED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("refunded") REFUNDED,
    @SerialName("unknown") UNKNOWN;

    /** True once the acquirer has answered and nothing more will happen without the app. */
    val isSettled: Boolean get() = this == AUTHORIZED || this == CAPTURED || this == DECLINED || this == FAILED || this == CANCELLED || this == REFUNDED
}

@Serializable
data class CardSummary(
    val brand: String = "",
    val last4: String = "",
    val expiry: String = "",
    val holderName: String = "",
    val token: String = "",
)

@Serializable
data class Attempt(
    val state: AttemptState,
    val challengeUrl: String = "",
    val card: CardSummary = CardSummary(),
    val failureReason: String = "",
    val declineCode: String = "",
)

@Serializable
data class Merchant(
    val displayName: String = "",
    val logoUrl: String = "",
    val supportUrl: String = "",
)

/** Whether the form asks the CVV again for a saved card. An unknown value reads as [OPTIONAL]. */
@Serializable
enum class SavedCardSecurityCode {
    @SerialName("required") REQUIRED,
    @SerialName("optional") OPTIONAL,
    @SerialName("never") NEVER,
}

/** A card the customer kept on file. [token] is opaque: pass it to [PesaCheckout.payWithSavedCard] and nowhere else. */
@Serializable
data class SavedCard(
    val token: String = "",
    val brand: String = "",
    val last4: String = "",
    val expiry: String = "",
    val holderName: String = "",
) {
    val cardBrand: CardBrand get() = CardBrand.entries.firstOrNull { it.wire == brand } ?: CardBrand.UNKNOWN

    override fun toString(): String = "SavedCard($brand •••• $last4, $expiry)"
}

/** The checkout view of an intent, as the edge answers it. */
@Serializable
data class CheckoutSession(
    val intentId: String,
    val state: IntentState,
    val amount: String,
    val currency: String,
    val description: String = "",
    val descriptor: String = "",
    val merchant: Merchant = Merchant(),
    val methods: List<String> = emptyList(),
    val needsEmail: Boolean = false,
    val returnUrl: String = "",
    val cancelUrl: String = "",
    val attempt: Attempt? = null,
    val expiresAt: String = "",
    val canSaveCard: Boolean = false,
    val savedCards: List<SavedCard> = emptyList(),
    val savedCardSecurityCode: SavedCardSecurityCode = SavedCardSecurityCode.OPTIONAL,
) {
    val acceptsCard: Boolean get() = state == IntentState.OPEN && "card" in methods && attempt?.state.let { it == null || it == AttemptState.DECLINED || it == AttemptState.FAILED }
}

/**
 * What [PesaCheckout.payWithCard] resolves with. It is the client's view
 * only: the merchant backend credits on the gateway's event or on
 * `GET /v1/intents/{reference}`, never on this.
 */
data class CheckoutResult(
    val state: IntentState,
    val intentId: String,
    val attempt: Attempt?,
) {
    val succeeded: Boolean get() = state == IntentState.SETTLED || state == IntentState.AUTHORIZED
}

/** The errors the SDK raises. [code] is stable; [message] is safe to show. */
class PesaCheckoutException(val code: Code, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Code {
        INVALID_CARD,
        INVALID_REQUEST,
        NOT_FOUND,
        INTENT_CLOSED,
        ATTEMPT_IN_PROGRESS,
        RATE_LIMITED,
        NETWORK,
        TIMEOUT,
        SERVER,
    }
}

@Serializable
internal data class CardChargeRequest(
    val clientSecret: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val securityCode: String,
    val holderName: String,
    val email: String? = null,
    val saveCard: Boolean = false,
)

@Serializable
internal data class SavedCardChargeRequest(
    val clientSecret: String,
    val savedCard: SavedCardReference,
    val email: String? = null,
)

@Serializable
internal data class SavedCardReference(val token: String, val securityCode: String? = null)

@Serializable
internal data class ErrorBody(val error: ErrorDetail = ErrorDetail())

@Serializable
internal data class ErrorDetail(val code: String = "", val message: String = "", val field: String = "")
