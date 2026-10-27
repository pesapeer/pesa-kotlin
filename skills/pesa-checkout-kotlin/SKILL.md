---
name: pesa-checkout-kotlin
description: Add a native Pesa card payment screen to an Android or Kotlin Multiplatform app with co.pesa:checkout. Use when an Android or KMP app must take a card payment on a Pesa intent without a web view.
---

# Pesa Checkout on Android and Kotlin Multiplatform

## When to use this skill

The merchant has a Pesa account and an API key on the server. The app is
Android (Kotlin or Compose) or Kotlin Multiplatform. It must take a card
payment with native fields.

## Steps

1. Server: create an intent with `POST /v1/intents`. Set `returnUrl` to
   an https app link of the app. Send `id` and `clientSecret` to the app.
   Do not log `clientSecret`.

2. App: add the dependencies. KMP apps add them to `commonMain`.

   ```kotlin
   implementation("co.pesa:checkout:0.1.0")
   implementation("co.pesa:checkout-ui:0.1.0")
   ```

   Android: minSdk 24.

3. App: create one client.

   ```kotlin
   val checkout = PesaCheckout(PesaCheckoutConfig(pins = listOf("<pin 1>", "<pin 2>")))
   ```

4. App: build the payment screen.

   ```kotlin
   val formState = remember { PesaCardFormState(needsEmail = session.needsEmail) }

   PesaSecureScreen {
       Column {
           PesaCardForm(state = formState)
           Button(onClick = { scope.launch { pay() } }, enabled = formState.complete(nowYear, nowMonth)) { Text("Pay") }
       }
   }

   suspend fun pay() {
       val result = try {
           checkout.payWithCard(
               intentId, clientSecret, formState.toCard(),
               email = formState.email.takeIf { session.needsEmail },
               openChallenge = defaultChallengeOpener(activity)::open,
           )
       } catch (e: PesaCheckoutException) {
           // e.code: RATE_LIMITED -> wait and retry; NETWORK -> check connection or pins
           return
       } finally {
           formState.clear()
       }
       when (result.state) {
           IntentState.SETTLED, IntentState.AUTHORIZED -> showSuccess()
           IntentState.OPEN -> showDecline(result.attempt?.failureReason)
           else -> showClosed()
       }
   }
   ```

5. App: handle the app link. On a cold start from the link, call
   `checkout.await(intentId, clientSecret)` to continue the wait.

6. Server: handle the `intent.state_changed` webhook. Fulfil the order on
   `state: settled`. Fulfil on `authorized` only when the merchant
   captures by hand.

## Checks before you finish

- [ ] The API key is on the server only.
- [ ] `clientSecret` is not logged and not stored.
- [ ] `pins` has two values in release builds.
- [ ] `PesaSecureScreen` wraps the card screen.
- [ ] `formState.clear()` runs in `finally`.
- [ ] The app link is declared and verified (`assetlinks.json`).
- [ ] Order fulfilment uses the webhook or the intent read.

## Reference

Full API and error codes: `README.md` in https://github.com/pesapeer/pesa-kotlin.
