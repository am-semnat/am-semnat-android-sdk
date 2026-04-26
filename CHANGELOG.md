# Changelog

All notable changes to `ro.amsemnat:am-semnat-sdk` are documented in this file.

The format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Version numbers ship in lockstep with the sibling SDKs
(`am-semnat-ios-sdk`, `@amsemnat/expo-sdk`, `@amsemnat/verifier-node`)
through the 0.x cycle.

## 0.1.1 — unreleased

### Fixed

- `readIdentity` now always attempts chip authentication, regardless of
  whether `DataGroup.DG14` is in the requested `dataGroups` set. Matches
  the iOS SDK's vendored `PassportReader`, which reads DG14 and runs
  EAC-CA whenever `skipCA` is false (the default). Previously, Android
  callers that didn't explicitly request `DG14` received
  `RomanianIdentity.chipAuthenticated = false` on cards that actually
  support it. Failure remains silent — `chipAuthenticated` stays `false`,
  no new error variant.

## 0.1.0 — 2026-04-24

Initial release.

### Added

- `AmSemnat.readIdentity`, `AmSemnat.sign`, `AmSemnat.verifyPassiveOffline`,
  `AmSemnat.isNfcAvailable`, `AmSemnat.isNfcEnabled`,
  `AmSemnat.cancelCurrentOp`, and the injectable `AmSemnat.logger` /
  `AmSemnatLogger` interface.
- `RomanianIdentity`, `RomanianSignature`, `PassiveVerificationResult`
  data classes and the `DataGroup` / `ReadProgress` / `SignProgress`
  public enums.
- Full 14-variant `AmSemnatError` taxonomy including the structured
  `PinVerifyFailed(retriesRemaining)` and `InvalidInput(parameter, detail)`
  variants.
- `AmSemnat.cancelCurrentOp()` cancels the in-flight session-owned
  `readIdentity` / `sign` by cancelling the coroutine `Job`; the
  existing `withNfcSession` cancellation handler calls
  `NfcAdapter.disableReaderMode` and the awaiting suspend function
  raises `AmSemnatError.SessionCancelled`. No-op for the `IsoDep`
  overloads — those don't go through the internal session helper.

### Fixed

- `readIdentity` now reads the card's `EF.COM` manifest first and
  intersects the caller-requested `DataGroup` set with the DGs the card
  actually advertises. Without this, requesting a DG the card doesn't
  ship would fire a misleading `readingDgN` progress event before the
  read itself failed silently. Matches the iOS SDK's behavior, which
  has always pre-filtered against `EF.COM`. If `EF.COM` can't be read
  the caller's set is used unfiltered — better to attempt the reads
  than drop them all.
