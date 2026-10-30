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
    implementation("co.pesa:checkout:0.3.0")      // client
    implementation("co.pesa:checkout-ui:0.3.0")   // Compose card form (optional)
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

## Saved cards

A customer can keep a card for the next payment. Your server must set
`customerReference` on the intent. Without it, the session offers no saved
cards and ignores `saveCard`.

The session tells the form what to show:

- `canSaveCard`: the form may show the "Save this card" switch.
- `savedCards`: the customer's cards, most recently used first. Each has
  `brand`, `last4`, `expiry` (MM/YYYY) and `holderName`. The list is empty
  when there are none.
- `savedCardSecurityCode`: `REQUIRED`, `OPTIONAL` or `NEVER`. It tells
  the form when to ask for the CVV again. A missing or unknown value reads
  as `NEVER`.

### The picker

With saved cards, `PesaCardForm` is a payment sheet: one rounded list with
hairlines between the rows.

- One row per saved card: the brand on a small white chip, `•••• 0165` on
  the first line and `Expires 09/28` under it, and a radio at the end. The
  selected row has a filled radio and a semibold title.
- An expired card stays in the list, greyed, and reads `Expired`. It
  cannot be selected. The form selects the first card that has not
  expired.
- With `REQUIRED` or `OPTIONAL`, the selected row takes the CVV in a
  narrow field at its end. With `NEVER` there is no CVV field.
- "Use another card" is the last row. Selecting it opens the card fields
  in the same list. "Save this card for next time" is their last line
  while `canSaveCard` is true. Pass `showSaveCardSwitch = false` and place
  `PesaSaveCardSwitch(formState)` yourself if your screen has its own spot
  for it.
- Colours come from your `MaterialTheme.colorScheme` (`surface`,
  `outlineVariant`, `onSurfaceVariant`, `primary`, `error`), so a dark
  colour scheme gives a dark sheet. The brand chip stays white so the
  marks read on any surface.
- Brand names from the gateway are read in any case, with or without
  spaces, `_` or `-`. `mc`, `americanexpress`, `cup` and `chinaunionpay`
  are understood (`CardBrand.parse`).

Your pay button names the selected card.
`formState.payLabel("1.00", "GBP")` gives "Pay £1.00 with •••• 0165" while
a saved card is selected and "Pay £1.00" otherwise, in the form's
language. `formState.payLabel(title)` adds the card to your own title. `formState.selectedSavedCard` has the card if you
compose the label yourself.

```kotlin
Button(onClick = { pay() }, enabled = formState.complete(nowYear, nowMonth)) {
    Text(formState.payLabel("1.00", "GBP"))
}
```

### Paying

```kotlin
val formState = PesaCardFormState(session)

val saved = formState.selectedSavedCard
val result = try {
    if (saved != null) {
        checkout.payWithSavedCard(
            intentId, clientSecret, saved.token, formState.toSavedSecurityCode(),
            email = formState.email.takeIf { session.needsEmail },
            openChallenge = defaultChallengeOpener(activity)::open,
        )
    } else {
        checkout.payWithCard(
            intentId, clientSecret, formState.toCard(),
            email = formState.email.takeIf { session.needsEmail },
            saveCard = formState.savesCard,
            openChallenge = defaultChallengeOpener(activity)::open,
        )
    }
} finally {
    formState.clear()
}
```

Rules:

- The token is opaque. Do not log it, show it or put it in a URL.
- The SDK sends the token and the CVV in the request body only.
- `payWithSavedCard` wipes the CVV array before it returns.
- The gateway keeps a card only after a successful charge. A decline
  keeps nothing.
- A refused token gives `INVALID_CARD`. Ask the customer to choose
  another card.
- The 3-D Secure challenge and the result are the same as for a typed
  card.

## Language

The SDK ships four languages: `en`, `fr`, `fr-CA` and `zh-Hans`. All
strings are in `commonMain`, so iOS and KMP hosts get them too. The SDK
uses no Android resources.

The SDK picks the language in this order:

1. The `locale` that your app gives.
2. The session's `locale`. The merchant sets it on the intent.
3. The device language.
4. English.

Close tags are mapped: `fr-BE` gives `fr`, `zh` and `zh-Hant` give
`zh-Hans`. An unknown tag such as `de` goes to the next step.

```kotlin
val checkout = PesaCheckout(PesaCheckoutConfig(pins = pins, locale = "fr"))   // error messages
val formState = PesaCardFormState(session, locale = "fr")                     // the form
PesaCardForm(state = formState, nowYear = y, nowMonth = m)

Button(onClick = { pay() }) {
    Text(formState.payLabel(session.amount, session.currency))   // "Payer 2,00 £GB"
}
```

- Leave `locale` null to follow the session and then the device.
- `PesaCardForm(locale = ...)` overrides the language of one form.
- `LocalPesaStrings` gives the same strings to every Pesa composable
  below it.
- `PesaStrings.resolve(locale, session.locale)` gives the strings for your
  own screens. `formatAmount`, `payLabel` and `errorMessage` fill them.
- To change a sentence, copy a table:
  `PesaStrings.fr.copy(saveCard = "...")`.
- `CardValidation` uses English unless you pass `strings`.

Money uses the platform formatter (`java.text.NumberFormat` on Android
and the JVM, `NSNumberFormatter` on iOS) for the chosen language. The SDK
does not change its output. Known output for £2.00:

| Language | Output |
|---|---|
| `en`, `zh-Hans` | `£2.00` |
| `fr-CA` | `2,00 £` |
| `fr` | `2,00 £GB` |

In `fr` and `fr-CA` the space before the symbol is a no-break space
(U+00A0), not a narrow no-break space (U+202F). In `fr` the pound is
`£GB`. This is the CLDR data in the platform.

## Error codes

`PesaCheckoutException.code` is one of the codes below. `message` is in
the client's language and is safe to show. `gatewayMessage` is the
gateway's English sentence for support. It can be empty.

| Code | Meaning |
|---|---|
| `INVALID_CARD` | The card failed local validation, or the checkout service refused the card or the saved card. |
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
2. Tag with an SSH key listed in `.github/allowed_signers`:
   `git tag -s vX.Y.Z -m "vX.Y.Z"`. Push the tag.
3. `release.yml` verifies the tag signature, runs the tests, and publishes
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
local consumer. Without `-Pversion` the build is `0.3.0-SNAPSHOT`.

Properties: `-Pskip.android=true`, `-Pskip.ios=true`, `-Pskip.compose=true`
drop a target or the UI module on a machine without its toolchain. iOS
targets resolve on Linux but compile only on macOS.

Versions: Kotlin 2.1.21, AGP 8.9.2, Ktor 3.1.3, Compose Multiplatform
1.8.1, minSdk 24, compileSdk 35.

## License

MIT. See [LICENSE](LICENSE).
