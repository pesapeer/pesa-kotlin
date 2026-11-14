package co.pesa.checkout

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.lang.ref.WeakReference

/**
 * [ChallengeSurface.IN_APP] is [PesaChallengeActivity], a full-screen web
 * view on the app's task; [ChallengeSurface.BROWSER] is Chrome Custom Tabs,
 * on the app's task so the app's link brings the user back. [context] must
 * be the Activity.
 */
actual fun challengePresenter(surface: ChallengeSurface, context: Any?, title: String?): ChallengePresenter {
    val activity = requireNotNull(context as? Activity) { "challengePresenter needs the Activity on Android" }
    return when (surface) {
        ChallengeSurface.IN_APP -> WebViewChallengePresenter(activity, title)
        ChallengeSurface.BROWSER -> ChallengeOpener { url ->
            CustomTabsIntent.Builder()
                .setShowTitle(false)
                .setUrlBarHidingEnabled(false)
                .build()
                .launchUrl(activity, Uri.parse(url))
        }
    }
}

internal class WebViewChallengePresenter(activity: Activity, private val title: String? = null) : ChallengePresenter {
    private val host = WeakReference(activity)

    override suspend fun open(url: String, onCancelled: () -> Unit) {
        val activity = host.get() ?: return
        ChallengeSession.start(onCancelled)
        activity.startActivity(
            Intent(activity, PesaChallengeActivity::class.java)
                .putExtra(PesaChallengeActivity.EXTRA_URL, url)
                .putExtra(PesaChallengeActivity.EXTRA_TITLE, title),
        )
    }

    override fun close() = ChallengeSession.close()
}

/**
 * The one challenge in flight, shared between the presenter and the
 * activity: the activity reports how it ended, the presenter closes it.
 */
internal object ChallengeSession {
    private var onCancelled: (() -> Unit)? = null
    private var activity: WeakReference<Activity>? = null
    private var open = false

    fun start(onCancelled: () -> Unit) {
        close()
        this.onCancelled = onCancelled
        open = true
    }

    /** The activity is up. Closed before it got here: it goes straight away. */
    fun attach(activity: Activity) {
        if (!open) {
            activity.finish()
            return
        }
        this.activity = WeakReference(activity)
    }

    /** The checkout, or the issuer's return, ended the page. */
    fun finished() {
        open = false
        onCancelled = null
        val a = activity?.get()
        activity = null
        a?.finish()
    }

    /** The cardholder left the page before it finished. */
    fun cancelled() {
        val cancel = onCancelled
        open = false
        onCancelled = null
        activity = null
        cancel?.invoke()
    }

    fun close() = finished()
}
