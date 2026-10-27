package co.pesa.checkout

import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * Presents the challenge in SFSafariViewController on the key window's top
 * view controller, or on [context] when it is a UIViewController. The app's
 * universal link (the intent's returnUrl) dismisses it when the issuer is done.
 */
actual fun defaultChallengeOpener(context: Any?): ChallengeOpener = ChallengeOpener { url ->
    val nsUrl = NSURL.URLWithString(url) ?: return@ChallengeOpener
    val presenter = (context as? UIViewController) ?: topViewController() ?: return@ChallengeOpener
    val safari = SFSafariViewController(uRL = nsUrl)
    presenter.presentViewController(safari, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
