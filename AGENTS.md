# Instructions for AI agents

This file is for coding agents that integrate `co.pesa:checkout` into an
Android or Kotlin Multiplatform app, or that change this repository.
Humans: read README.md first.

## What this package is

- `co.pesa:checkout`: client, card validation, 3-D Secure opener.
  Targets: Android, iOS, JVM.
- `co.pesa:checkout-ui`: Compose Multiplatform `PesaCardForm` and
  `PesaSecureScreen`.
- Native card fields. 3-D Secure in Custom Tabs (Android) or
  `SFSafariViewController` (iOS). SPKI-pinned TLS to `checkout.pesa.co`.
- Native iOS apps use pesa-ios instead. Flutter apps use pesa-flutter.

## Hard rules

1. Never log, store or send `clientSecret` anywhere except to the SDK.
2. Never fulfil an order from `CheckoutResult`. The server fulfils only on
   `intent.state_changed` or `GET /v1/intents/{reference}`.
3. Never put the Pesa API key in the app.
4. Never keep `CardDetails` after `payWithCard` returns. Call
   `formState.clear()`.
5. Never ship a release build with an empty `pins` list.
6. Never add a web view for card entry.

## Integrate into an app (steps in order)

1. Server: create the intent with `POST /v1/intents`. Set `returnUrl` to
   an https app link of the app. Return `id` and `clientSecret` to the
   app.
2. App: add `co.pesa:checkout` and, for the Compose form,
   `co.pesa:checkout-ui`. KMP apps add them to `commonMain`.
3. App: `val checkout = PesaCheckout(PesaCheckoutConfig(pins = listOf("<pin 1>", "<pin 2>")))`.
   Keep one instance. Call `checkout.close()` when the app no longer
   needs it.
4. App: `val session = checkout.session(intentId, clientSecret)` to get
   the amount, currency, `needsEmail` and offered methods for the screen.
5. App: show `PesaCardForm(state = formState)` inside `PesaSecureScreen {}`.
6. App: on pay, `checkout.payWithCard(intentId, clientSecret,
   formState.toCard(), email = ..., openChallenge = defaultChallengeOpener(activity)::open)`.
   Then `formState.clear()` in a `finally` block.
7. App: switch on `result.state`: `SETTLED` or `AUTHORIZED` is success,
   `OPEN` is a decline (the customer can try again), `EXPIRED` or
   `CANCELLED` is closed.
8. App: handle the app link. If the app restarted during the challenge,
   call `checkout.await(intentId, clientSecret)` to continue the wait.
9. Server: handle the `intent.state_changed` webhook. Fulfil on
   `settled`, or on `authorized` when the merchant captures by hand.

A full example is in `README.md`. A skill with the same steps is in
`skills/pesa-checkout-kotlin/SKILL.md`.

## Change this repository

Layout:

| Path | Content |
|---|---|
| `checkout/src/commonMain/.../PesaCheckout.kt` | `PesaCheckout`, `PesaCheckoutConfig`: session, pay, poll, await. |
| `checkout/src/commonMain/.../Model.kt` | Session, attempt, result, `IntentState`, `PesaCheckoutException`. |
| `checkout/src/commonMain/.../Card.kt` | Card validation, `CardDetails`. |
| `checkout/src/commonMain/.../PesaStrings.kt` | `PesaStrings`: language resolution, money format, error messages. |
| `checkout/src/commonMain/.../strings_*.kt` | One table per language: `en`, `fr`, `frCA`, `zhHans`. |
| `checkout/src/commonMain/.../ChallengeOpener.kt` | `expect` 3-D Secure opener. |
| `checkout/src/{androidMain,iosMain,jvmMain}/` | Ktor engine with pinning; challenge opener per platform. |
| `checkout-ui/src/commonMain/.../PesaCardForm.kt` | Compose form, `PesaCardFormState`, `PesaSecureScreen` expect. |
| `checkout-ui/src/{androidMain,iosMain,jvmMain}/` | `PesaSecureScreen` per platform. |
| `checkout/src/commonTest/` | Tests. |
| `.github/workflows/ci.yml` | Tests and compiles on push and PR. |
| `.github/workflows/release.yml` | Maven Central upload on a signed `v*` tag. |

Commands:

```
./gradlew :checkout:jvmTest :checkout:testDebugUnitTest
./gradlew :checkout:compileReleaseKotlinAndroid :checkout-ui:compileReleaseKotlinAndroid
./gradlew :checkout:compileKotlinIosArm64 :checkout-ui:compileKotlinIosArm64   # macOS only
```

Use `-Pskip.android=true`, `-Pskip.ios=true` or `-Pskip.compose=true` on a
machine without that toolchain.

Rules for changes:

- Run the tests and the Android compile before you finish. All must
  pass.
- Add a test in `checkout/src/commonTest/` for every behaviour change.
- Keep platform code in the platform source sets. `commonMain` must not
  import Android or iOS APIs.
- Keep error codes in step with pesa-ios and pesa-flutter. The three
  SDKs share one error vocabulary.
- Put every user-visible string in `PesaStrings` and in all four tables.
  `PesaStringsTest` checks the keys and placeholders.
- Add a line to `CHANGELOG.md` under "Unreleased" for every user-visible
  change.
- Do not change the Maven coordinates or the POM in `build.gradle.kts`.
- Do not edit `.github/allowed_signers` or `.github/verify-tag.sh`.
- Do not create tags. A maintainer signs and pushes release tags.
- Do not add `Co-Authored-By` or similar trailers to commit messages.

## Terms

| Term | Meaning |
|---|---|
| intent | A payment the merchant server created on the Pesa API. |
| client secret | A per-intent token for the app. It is not the API key. |
| attempt | One try to pay an intent. |
| checkout service | `checkout.pesa.co`. It takes the card from the SDK. |
| gateway | The Pesa API that owns intents and sends events. |
| pin | Base64 SHA-256 of the checkout service's SubjectPublicKeyInfo. |
