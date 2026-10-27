package co.pesa.checkout

/**
 * Opens the 3-D Secure challenge page for the cardholder. The page is the
 * issuer's, so it is shown in the system browser sheet (Custom Tabs,
 * SFSafariViewController), never in a web view the app controls. The
 * checkout edge's return page then sends the browser to the intent's
 * `returnUrl`, which is the app's own link, and [PesaCheckout.payWithCard]
 * keeps polling meanwhile.
 */
fun interface ChallengeOpener {
    suspend fun open(url: String)
}

/** The platform's default opener. [context] is the Android Activity; ignored elsewhere. */
expect fun defaultChallengeOpener(context: Any? = null): ChallengeOpener
