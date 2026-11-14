package co.pesa.checkout

/** The JVM has no cardholder in front of it; tests pass their own presenter. */
actual fun challengePresenter(surface: ChallengeSurface, context: Any?, title: String?): ChallengePresenter = ChallengeOpener { }
