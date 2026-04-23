# Changelog

All notable changes to `ro.amsemnat:am-semnat-sdk` are documented in this file.

The format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Version numbers ship in lockstep with the sibling SDKs
(`am-semnat-ios-sdk`, `@amsemnat/expo-sdk`, `@amsemnat/verifier-node`)
through the 0.x cycle.

## 0.1.0-SNAPSHOT — unreleased

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
