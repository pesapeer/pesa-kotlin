# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Languages: `en`, `fr`, `fr-CA` and `zh-Hans`. `PesaStrings` in
  `commonMain` holds every string, one table per language
  (`PesaStrings.en`, `fr`, `frCA`, `zhHans`). `PesaStrings.resolve(locale,
  sessionLocale)` picks the host's locale, then the session's, then the
  device's, then English. Close tags are mapped (`fr-BE` to `fr`, `zh` and
  `zh-Hant` to `zh-Hans`).
- `CheckoutSession.locale`.
- `PesaCheckoutConfig.locale`, `PesaCardFormState(locale = ...)`,
  `PesaCardForm(locale = ...)` and `LocalPesaStrings`.
- `PesaStrings.formatAmount` formats money with the platform formatter for
  the language. `PesaCardFormState.payLabel(amount, currency)` gives "Pay
  £2.00" or "Pay £2.00 with •••• 0165".
- `CardValidation` functions take an optional `strings` (English by
  default).
- `PesaCheckoutException.gatewayMessage` keeps the gateway's sentence.
- Saved cards. `CheckoutSession` has `canSaveCard`, `savedCards` (`SavedCard`)
  and `savedCardSecurityCode` (`SavedCardSecurityCode`).
- `PesaCheckout.payWithSavedCard` pays with a saved card token and an
  optional CVV. It follows 3-D Secure and polls like `payWithCard`.
- `PesaCardForm` shows the saved cards, "Use another card", the CVV for a
  saved card and the "Save this card" switch. `PesaCardFormState(session)`
  sets them up from the session.
- The saved-card picker is a payment sheet: one rounded list with
  hairlines, two-line rows (brand chip, `•••• 0165`, `Expires 09/28`), a
  trailing radio, expired cards greyed and unselectable, the CVV inline
  on the selected row, and "Use another card" as the last row opening the
  card fields in place. Colours follow `MaterialTheme.colorScheme`.
- `PesaCardForm(showSaveCardSwitch = true)` and `PesaSaveCardSwitch`. The
  form draws "Save this card for next time" as the last line of the card
  fields. Hosts that place their own switch pass `showSaveCardSwitch =
  false` and drop their copy from under the form, or it shows twice.
- `PesaCardFormState.payLabel(title)` gives "Pay £1.00 with •••• 0165"
  while a saved card is selected.
- `CardBrand.parse(name)` reads brand names in any case, without spaces,
  `_` or `-`, and accepts `mc`, `americanexpress`, `cup` and
  `chinaunionpay`. `SavedCard.cardBrand` uses it.
- `SavedCard.shortExpiry` (MM/YY) and `SavedCard.isUsable(nowYear, nowMonth)`.

### Changed
- `PesaCheckoutException.message` comes from the error code in the
  client's language. It was the gateway's sentence. The codes do not
  change.
- `payLabel(title)` adds the card in the form's language.
- The card number field shows no brand until the brand is known. It
  showed "Card".
- The saved card's security code is read as "Security code" for Amex too.
  It was "Security code (CID)".
- Version 0.3.0.
- `savedCardSecurityCode` defaults to `NEVER` when missing or unknown, in
  `CheckoutSession` and `PesaCardFormState`. It was `OPTIONAL`.
- The form selects the first saved card that has not expired, and a
  selected expired card fails `complete()` with an expiry error.
- `payWithCard(saveCard)` is now `Boolean = false`. It was `Boolean?`.
- A 400 on `savedCard.token` or `savedCard.securityCode` maps to
  `INVALID_CARD`.

## [0.1.0] - 2026-10-24

### Added
- First release: card payment on a Pesa intent, 3-D Secure, pinned transport.
