package co.pesa.checkout

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * The full-screen page that hosts the 3-D Secure challenge. Only `http(s)`
 * loads; the checkout's return page, or the bounce to the app's own
 * scheme, ends it. Back or the close button is a cancel.
 */
class PesaChallengeActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)?.let(Uri::parse)
        if (url == null || url.scheme != "https") {
            finish()
            return
        }
        ChallengeSession.attach(this)
        val returnUrl = url.buildUpon().clearQuery().path(url.path.orEmpty().replace(Regex("/challenge/?$"), "/return")).build()
        val strings = PesaStrings.resolve()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        root.addView(topBar(strings.challengeTitle), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                if (isReturn(target, returnUrl)) {
                    finishChallenge()
                    return true
                }
                if (target.scheme == "https" || target.scheme == "http") return false
                // The issuer's 3-D Secure iframes are part of the challenge, not the end of it.
                if (!request.isForMainFrame) return target.scheme !in setOf("about", "data", "blob")
                finishChallenge()
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = android.view.View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = android.view.View.INVISIBLE
            }
        }
        web.loadUrl(url.toString())
    }

    private fun isReturn(target: Uri, returnUrl: Uri): Boolean =
        target.host == returnUrl.host && target.path.orEmpty().trimEnd('/') == returnUrl.path.orEmpty()

    private fun finishChallenge() {
        if (done) return
        done = true
        ChallengeSession.finished()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        if (isFinishing && !done) {
            done = true
            ChallengeSession.cancelled()
        }
        super.onDestroy()
    }

    private fun topBar(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageButton(this@PesaChallengeActivity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            contentDescription = getString(android.R.string.cancel)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(TextView(this@PesaChallengeActivity).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "co.pesa.checkout.url"
    }
}
