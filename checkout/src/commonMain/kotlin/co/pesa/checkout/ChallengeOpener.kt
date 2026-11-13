package co.pesa.checkout

/**
 * Shows the 3-D Secure challenge page to the cardholder.
 *
 * [PesaCheckout.payWithCard] calls [open] once when the issuer asks for a
 * challenge, keeps polling the attempt meanwhile, and calls [close] once
 * the attempt has settled. The page is the checkout edge's, which posts to
 * the issuer and takes the cardholder back when they are done.
 */
interface ChallengePresenter {
    /**
     * Shows [url]. Returns once the page is up. [onCancelled] is called if
     * the cardholder dismisses the page before it finished; the payment
     * then ends with [PesaCheckoutException.Code.CHALLENGE_CANCELLED].
     */
    suspend fun open(url: String, onCancelled: () -> Unit)

    /** Hides the page. A no-op when nothing is open. */
    fun close() {}
}

/**
 * A presenter that only opens: the pre-0.4.0 contract, for a system
 * browser the SDK cannot close. The checkout's return page then sends the
 * browser to the intent's `returnUrl`, the app's own link.
 */
fun interface ChallengeOpener : ChallengePresenter {
    suspend fun open(url: String)

    override suspend fun open(url: String, onCancelled: () -> Unit) = open(url)
}

/** Where the 3-D Secure challenge shows. */
enum class ChallengeSurface {
    /** A full-screen web view inside the app. The cardholder never leaves. */
    IN_APP,

    /**
     * The system browser (Custom Tabs, `SFSafariViewController`). The
     * cardholder leaves the app for the challenge and the intent's
     * `returnUrl`, the app's own link, brings them back.
     */
    BROWSER,
}

/**
 * The platform's presenter for [surface]. [context] is the Android
 * Activity (required there); on iOS a `UIViewController` to present from,
 * or null for the top one; ignored on the JVM.
 */
expect fun challengePresenter(surface: ChallengeSurface = ChallengeSurface.IN_APP, context: Any? = null): ChallengePresenter
