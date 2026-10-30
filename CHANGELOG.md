# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Saved cards. `CheckoutSession` has `canSaveCard`, `savedCards` (`SavedCard`)
  and `savedCardSecurityCode` (`SavedCardSecurityCode`).
- `PesaCheckout.payWithSavedCard` pays with a saved card token and an
  optional CVV. It follows 3-D Secure and polls like `payWithCard`.
- `PesaCardForm` shows the saved cards, "Use another card", the CVV for a
  saved card and the "Save this card" switch. `PesaCardFormState(session)`
  sets them up from the session.

### Changed
- `payWithCard(saveCard)` is now `Boolean = false`. It was `Boolean?`.
- A 400 on `savedCard.token` or `savedCard.securityCode` maps to
  `INVALID_CARD`.

## [0.1.0] - 2026-10-24

### Added
- First release: card payment on a Pesa intent, 3-D Secure, pinned transport.
