package co.pesa.checkout

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens the challenge in Chrome Custom Tabs. [context] must be an Activity
 * so the tab sits on the app's task and the app's link brings the user back.
 */
actual fun defaultChallengeOpener(context: Any?): ChallengeOpener {
    val ctx = requireNotNull(context as? Context) { "defaultChallengeOpener needs the Activity on Android" }
    return ChallengeOpener { url ->
        CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(false)
            .build()
            .launchUrl(ctx, Uri.parse(url))
    }
}
