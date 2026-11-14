package co.pesa.checkout

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSURL
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURLRequest
import platform.SafariServices.SFSafariViewController
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIAccessibilityPostNotification
import platform.UIKit.UIAccessibilityScreenChangedNotification
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UILabel
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * [ChallengeSurface.IN_APP] is a full-screen web view; [ChallengeSurface.BROWSER]
 * is SFSafariViewController, which the app's universal link (the intent's
 * returnUrl) returns from. Both present over [context] when it is a
 * `UIViewController`, else over the key window's top one.
 */
actual fun challengePresenter(surface: ChallengeSurface, context: Any?, title: String?): ChallengePresenter = when (surface) {
    ChallengeSurface.IN_APP -> WebViewChallengePresenter(context as? UIViewController, title)
    ChallengeSurface.BROWSER -> ChallengeOpener { url ->
        val nsUrl = NSURL.URLWithString(url) ?: return@ChallengeOpener
        val presenter = (context as? UIViewController) ?: topViewController() ?: return@ChallengeOpener
        val safari = SFSafariViewController(uRL = nsUrl)
        presenter.presentViewController(safari, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

@OptIn(ExperimentalForeignApi::class)
internal class WebViewChallengePresenter(
    private val host: UIViewController?,
    private val title: String? = null,
) : ChallengePresenter {
    private var page: ChallengeViewController? = null

    override suspend fun open(url: String, onCancelled: () -> Unit) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        if (nsUrl.scheme != "https") return
        val presenter = host ?: topViewController() ?: return
        close()
        val controller = ChallengeViewController(nsUrl, title, onCancelled)
        page = controller
        val nav = UINavigationController(rootViewController = controller)
        nav.modalPresentationStyle = UIModalPresentationFullScreen
        presenter.presentViewController(nav, animated = true, completion = null)
    }

    override fun close() {
        val controller = page ?: return
        page = null
        controller.endedByCheckout()
    }
}

/**
 * The page that hosts the challenge. Only `http(s)` loads; the checkout's
 * return page, or the bounce to the app's own scheme, ends it.
 *
 * The bar holds a close button and, only when the host gave one, a title:
 * the bank's page has its own heading. Until that page has loaded the page
 * says it is connecting. The close button asks before it cancels.
 */
@OptIn(ExperimentalForeignApi::class)
internal class ChallengeViewController(
    private val url: NSURL,
    private val barTitle: String?,
    private val onCancelled: () -> Unit,
) : UIViewController(nibName = null, bundle = null) {
    private val returnPath = url.path.orEmpty().replace(Regex("/challenge/?$"), "/return")
    private val strings = PesaStrings.resolve()
    private var done = false
    private val delegate = NavigationDelegate()
    private var web: WKWebView? = null
    private var connecting: UILabel? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        title = barTitle
        view.backgroundColor = UIColor.whiteColor
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemClose,
            target = this,
            action = NSSelectorFromString("closeTapped"),
        )
        val web = WKWebView(frame = view.bounds, configuration = WKWebViewConfiguration())
        web.navigationDelegate = delegate
        web.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(web)
        this.web = web
        val connecting = UILabel(frame = view.bounds)
        connecting.text = strings.challengeConnecting
        connecting.textAlignment = NSTextAlignmentCenter
        connecting.textColor = UIColor.blackColor
        connecting.backgroundColor = UIColor.whiteColor
        connecting.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(connecting)
        this.connecting = connecting
        web.loadRequest(NSURLRequest(uRL = url))
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        // The page has no title to read out, so it is named here.
        if (barTitle == null) UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification, strings.challengeLabel)
    }

    /** Leaving cancels the payment, so the cardholder is asked first. */
    @ObjCAction
    fun closeTapped() {
        if (done) return
        val question = UIAlertController.alertControllerWithTitle(
            title = strings.challengeCancelQuestion,
            message = null,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        question.addAction(UIAlertAction.actionWithTitle(strings.challengeKeepGoing, style = UIAlertActionStyleCancel, handler = null))
        question.addAction(
            UIAlertAction.actionWithTitle(strings.challengeCancel, style = UIAlertActionStyleDestructive) { _ ->
                if (!done) {
                    done = true
                    leave { onCancelled() }
                }
            },
        )
        presentViewController(question, animated = true, completion = null)
    }

    /** The checkout settled the attempt: nothing to tell the caller. */
    fun endedByCheckout() {
        if (done) return
        done = true
        leave(null)
    }

    /** Dismisses from the presenter, so a question still on screen goes with the page. */
    private fun leave(completion: (() -> Unit)?) {
        val presenter = navigationController?.presentingViewController ?: presentingViewController
        presenter?.dismissViewControllerAnimated(true, completion = completion)
    }

    private fun isReturn(target: NSURL): Boolean =
        target.host == url.host && target.path.orEmpty().trimEnd('/') == returnPath

    private inner class NavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            val target = decidePolicyForNavigationAction.request.URL
            if (target == null) {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                return
            }
            if (isReturn(target)) {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                endedByCheckout()
                return
            }
            if (target.scheme == "https" || target.scheme == "http") {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                return
            }
            // The issuer's 3-D Secure iframes are part of the challenge, not the end of it.
            if (decidePolicyForNavigationAction.targetFrame?.mainFrame != true) {
                val inFrame = target.scheme in setOf("about", "data", "blob")
                decisionHandler(
                    if (inFrame) WKNavigationActionPolicy.WKNavigationActionPolicyAllow
                    else WKNavigationActionPolicy.WKNavigationActionPolicyCancel,
                )
                return
            }
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            endedByCheckout()
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            connecting?.removeFromSuperview()
            connecting = null
        }
    }
}
