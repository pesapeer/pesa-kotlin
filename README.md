# Pesa Checkout for Android and Kotlin Multiplatform (co.pesa:checkout)

[![CI](https://github.com/pesapeer/pesa-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/pesapeer/pesa-kotlin/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/co.pesa/checkout.svg)](https://central.sonatype.com/artifact/co.pesa/checkout)

Native card payments on a Pesa intent for:

- Android apps (plain Kotlin or Compose).
- Kotlin Multiplatform apps. One coordinate resolves Android, iOS and JVM
  from `commonMain`.

Native iOS apps use [pesa-ios](https://github.com/pesapeer/pesa-ios)
instead. Flutter apps use [pesa-flutter](https://github.com/pesapeer/pesa-flutter).

The app shows its own card fields. The SDK sends the card once to
`checkout.pesa.co` over pinned TLS. It runs 3-D Secure in the system
browser sheet. It returns the result the device saw.

## Modules

| Artifact | Content | Targets |
|---|---|---|
| `co.pesa:checkout` | Client, card validation, 3-D Secure opener. | Android, iOS (static framework `PesaCheckout`), JVM. |
| `co.pesa:checkout-ui` | Compose Multiplatform card form (`PesaCardForm`) and `PesaSecureScreen`. | Android, iOS, JVM. |

## Install

Maven Central:

```kotlin
dependencies {
    implementation("co.pesa:checkout:0.1.0")      // client
    implementation("co.pesa:checkout-ui:0.1.0")   // Compose card form (optional)
}
```

Requirements: minSdk 24. For a Kotlin Multiplatform app, add the same
coordinates to `commonMain`.

## How a payment works

1. Your server creates an intent: `POST /v1/intents` on the Pesa API.
   Set `returnUrl` to an https app link of your app.
2. Your server gives the app `id` and `clientSecret`. Never the API key.
3. The app collects the card with `PesaCardForm` inside `PesaSecureScreen`.
   You can use your own fields instead.
4. The app calls `payWithCard`. The SDK posts the card, polls the result,
   and opens the 3-D Secure challenge when the issuer asks.
5. **Your server trusts only the gateway.** It fulfils the order on the
   `intent.state_changed` event or on `GET /v1/intents/{reference}`.
   It must not trust the result the app saw.

## Example

```kotlin
val checkout = PesaCheckout(PesaCheckoutConfig(pins = listOf("<pin 1>", "<pin 2>")))

// Read amount, currency, needsEmail and the offered methods for the screen.
val session = checkout.session(intentId, clientSecret)

val result = checkout.payWithCard(
    intentId, clientSecret, formState.toCard(),
    email = formState.email.takeIf { session.needsEmail },
    openChallenge = defaultChallengeOpener(activity)::open,  // Custom Tabs on Android
)
formState.clear()

when (result.state) {
    IntentState.SETTLED, IntentState.AUTHORIZED -> showSuccess()
    IntentState.OPEN -> showDecline(result.attempt?.failureReason)   // the customer can try another card
    else -> showClosed()                                             // EXPIRED or CANCELLED
}
```

If the system kills the app during the challenge, the app link brings the
user back. Then call `checkout.await(intentId, clientSecret)` to continue
the wait.

## Error codes

`PesaCheckoutException.code` is one of:

| Code | Meaning |
|---|---|
| `INVALID_CARD` | The card failed local validation. |
| `INVALID_REQUEST` | The checkout service refused the request. |
| `NOT_FOUND` | Unknown intent or wrong client secret. |
| `INTENT_CLOSED` | The intent is settled, cancelled or expired. |
| `ATTEMPT_IN_PROGRESS` | Another attempt is running on this intent. |
| `RATE_LIMITED` | Too many requests. Wait and retry. |
| `NETWORK` | Network error, including a pin mismatch. |
| `TIMEOUT` | No final state before the poll limit. |
| `SERVER` | The checkout service failed. |

## Certificate pins

A pin is the base64 SHA-256 of the SubjectPublicKeyInfo of the
`checkout.pesa.co` certificate. Get the current pin:

```
openssl s_client -connect checkout.pesa.co:443 -servername checkout.pesa.co </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
```

Rules:

- Ship two pins: current and backup. Pesa publishes the pins with each
  release.
- An empty list turns pinning off. Use that only for development.

## How the SDK keeps card data safe

- It never logs, caches or copies the card number or the security code.
- It never shows a web view for card entry.
- It wipes the card after the request. `CardDetails.wipe()` runs in a
  `finally` block.
- `PesaSecureScreen` sets `FLAG_SECURE` on Android. On iOS, the app hides
  the fields on screenshot and background notifications.

## For AI agents

Read [AGENTS.md](AGENTS.md). It has the integration steps in order and the
rules an agent must follow. A skill is in `skills/pesa-checkout-kotlin/`.

## Security

Read [SECURITY.md](SECURITY.md). Report vulnerabilities to
security@pesa.co. Do not open a public issue.

## Releasing

1. Move the Unreleased notes in `CHANGELOG.md`. The version comes from the
   tag.
2. Commit on `main` with an SSH key listed in `.github/allowed_signers`.
   Then `git tag vX.Y.Z && git push origin vX.Y.Z`. Only repository admins
   can push a `v*` tag.
3. `release.yml` verifies that the tagged commit is signed by a maintainer, runs the tests, and publishes
   `co.pesa:checkout` and `co.pesa:checkout-ui` (JVM, Android and iOS
   klibs) to Maven Central through the Central Portal. A maintainer then
   releases the deployment by hand in the portal.

Repository setup: secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
`SIGNING_KEY`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`; `.github/allowed_signers`.

## Build

```
./gradlew :checkout:jvmTest :checkout:testDebugUnitTest      # unit tests (JVM + Android)
./gradlew :checkout:compileReleaseKotlinAndroid :checkout-ui:compileReleaseKotlinAndroid
./gradlew :checkout:linkReleaseFrameworkIosArm64              # macOS only
```

`./gradlew publishToMavenLocal -Pversion=X.Y.Z` publishes to `~/.m2` for a
local consumer. Without `-Pversion` the build is `0.1.0-SNAPSHOT`.

Properties: `-Pskip.android=true`, `-Pskip.ios=true`, `-Pskip.compose=true`
drop a target or the UI module on a machine without its toolchain. iOS
targets resolve on Linux but compile only on macOS.

Versions: Kotlin 2.1.21, AGP 8.9.2, Ktor 3.1.3, Compose Multiplatform
1.8.1, minSdk 24, compileSdk 35.

## License

MIT. See [LICENSE](LICENSE).
