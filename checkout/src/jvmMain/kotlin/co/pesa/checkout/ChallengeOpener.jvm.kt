package co.pesa.checkout

/** The JVM has no cardholder in front of it; tests pass their own opener. */
actual fun defaultChallengeOpener(context: Any?): ChallengeOpener = ChallengeOpener { }
