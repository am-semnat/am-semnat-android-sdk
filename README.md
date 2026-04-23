# am-semnat-android-sdk

Android SDK for reading and signing with Romanian electronic identity cards
(CEI / eID) over NFC. Wraps the eMRTD / Romanian national applet protocols
behind a small public API focused on `readIdentity` and `sign`.

## Status

`0.1.0-SNAPSHOT` — pre-stable. The underlying protocol code is production-tested
(used by the [am-semnat app](https://amsemnat.ro)); the public `ro.amsemnat.sdk`
surface is new and may change ahead of `1.0.0`.

## Requirements

- `minSdk 24`
- Kotlin 1.9+
- Device with an NFC chip that supports ISO-DEP (practically every Android
  phone with NFC)
- `<uses-permission android:name="android.permission.NFC" />` (already declared
  in the SDK's manifest and merged into consumers)
- **Core library desugaring** enabled in your app module. The SDK's public API
  uses `java.time.Instant`, which requires desugaring on `minSdk < 26`:

  ```groovy
  android {
      compileOptions {
          coreLibraryDesugaringEnabled true
      }
  }
  dependencies {
      coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.1.5'
  }
  ```

## Installation

Once published, add to `dependencies`:

```groovy
dependencies {
    implementation 'ro.amsemnat:am-semnat-sdk:0.1.0'
}
```

Pre-publication: `./gradlew publishToMavenLocal` then add `mavenLocal()` to
your repositories.

## Quick start

### Read identity

```kotlin
import kotlinx.coroutines.launch
import ro.amsemnat.sdk.AmSemnat
import ro.amsemnat.sdk.AmSemnatError
import ro.amsemnat.sdk.DataGroup

class ScanActivity : AppCompatActivity() {
    fun scan() = lifecycleScope.launch {
        try {
            val identity = AmSemnat.readIdentity(
                activity = this@ScanActivity,
                can = "123456",     // 6-digit CAN from the card
                pin1 = "1234",      // optional PIN1 for eDATA (empty = skip)
                dataGroups = DataGroup.DEFAULT,
                onProgress = { step -> log("step: $step") },
            )
            // identity.cnp, identity.firstName, identity.lastName, ...
            // identity.chipAuthenticated (local anti-clone check; UX signal only)
            // identity.rawSod / .rawDg1 / .rawDg2 (for server-side passive auth)
        } catch (e: AmSemnatError.PinVerifyFailed) {
            showRetries(e.retriesRemaining)
        } catch (e: AmSemnatError.PaceAuthFailed) {
            promptForCan()
        } catch (e: AmSemnatError) {
            handle(e)
        }
    }
}
```

### Sign a PDF byte-range hash (PAdES)

```kotlin
val sig = AmSemnat.sign(
    activity = this,
    can = "123456",
    pin2 = "123456",                  // 6-digit PIN2
    pdfHash = byteRangeSha384,        // 48 bytes; SHA-384 of the PDF byte range
    signingTime = Instant.now(),
    onProgress = { step -> log("step: $step") },
)
// sig.signature          (96 bytes, raw ECDSA P-384 r||s)
// sig.certificate        (DER-encoded signing cert)
// sig.signedAttributes   (DER-encoded SET of CMS signed attributes)
```

Assemble a CMS `SignedData` on the client or upload the three blobs for server-
side assembly.

### Low-level (supply your own `IsoDep`)

If your app already runs its own `NfcAdapter.enableReaderMode` loop, skip the
SDK's reader session and pass the `IsoDep` directly:

```kotlin
AmSemnat.readIdentity(isoDep = existingIsoDep, can = "...", pin1 = "...")
```

### Rendering the progress UI

Android has no system NFC sheet in reader mode — `NfcAdapter.enableReaderMode`
hands the tag to the app and the progress UI is entirely the app's
responsibility. The SDK's `readIdentity` / `sign` therefore take no
`messages` argument the way the iOS sibling does; instead, your screen
renders its own bottom sheet / overlay / full-screen spinner keyed off
the `onProgress` callback:

```kotlin
AmSemnat.readIdentity(
    activity = this,
    can = can, pin1 = pin1,
    onProgress = { step ->
        lifecycleScope.launch(Dispatchers.Main) {
            binding.statusLabel.text = when (step) {
                ReadProgress.PACE_ESTABLISHING    -> getString(R.string.nfc_authenticating)
                ReadProgress.CHIP_AUTHENTICATING  -> getString(R.string.nfc_authenticating)
                ReadProgress.READING_DG1,
                ReadProgress.READING_DG2,
                ReadProgress.READING_DG7,
                ReadProgress.READING_DG11,
                ReadProgress.READING_DG14         -> getString(R.string.nfc_reading)
                ReadProgress.READING_EDATA        -> getString(R.string.nfc_authenticating)
                ReadProgress.COMPLETE             -> getString(R.string.nfc_done)
            }
        }
    },
)
```

The SDK ships no Romanian copy — all on-screen strings belong in the
consumer app's string resources.

### Cancelling the in-flight session

`NfcAdapter.enableReaderMode` is Activity-scoped, so a reader-mode
session survives in-app navigation. Call `AmSemnat.cancelCurrentOp()`
from your screen's teardown (Fragment `onDestroyView`,
Compose `DisposableEffect` cleanup, etc.) to free the reader mode and
surface `SessionCancelled` to the awaiting caller:

```kotlin
DisposableEffect(Unit) {
    onDispose { AmSemnat.cancelCurrentOp() }
}
```

Equivalent idiomatic option: cancel the `Job` returned by
`lifecycleScope.launch { AmSemnat.readIdentity(...) }` yourself — the
coroutine's existing cancellation handler calls
`NfcAdapter.disableReaderMode` either way. `cancelCurrentOp` is a
no-op when nothing is in flight and for the `IsoDep` overloads
(caller owns the session).

## Authenticity verification

Two questions with two different answers:

- **Is the chip genuine (not a clone)?** → `RomanianIdentity.chipAuthenticated`
  is set from the on-device EAC-Chip-Authentication challenge/response. It's a
  UX signal, not a legal trust decision.

- **Does the card data match what MAI signed?** → Passive Authentication. The
  SDK does **not** set a `passiveAuthenticated` flag — any flag the client
  writes can be forged by a compromised client. For load-bearing checks (KYC,
  qualified signing), verify **server-side** against your own MAI CSCA
  masterlist.

  The SDK exposes raw bytes on `RomanianIdentity` (`rawSod`, `rawDg1`,
  `rawDg2`, `rawDg14`) that you can upload and verify wherever the decision
  actually matters. A companion `@amsemnat/verifier-node` reference
  implementation is on the roadmap.

  For offline / research / air-gapped use, the SDK ships
  `AmSemnat.verifyPassiveOffline(rawSod, dataGroups, trustAnchors)`. The caller
  supplies the CSCA trust anchors; freshness and revocation become the
  caller's problem.

  ```kotlin
  // `identity` from AmSemnat.readIdentity(...); `cscaAnchors` is your MAI
  // CSCA masterlist as a list of DER-encoded X.509 certificates.
  val result = AmSemnat.verifyPassiveOffline(
      rawSod = identity.rawSod ?: error("SOD missing"),
      dataGroups = buildMap {
          identity.rawDg1?.let { put(DataGroup.DG1, it) }
          identity.rawDg2?.let { put(DataGroup.DG2, it) }
          identity.rawDg14?.let { put(DataGroup.DG14, it) }
      },
      trustAnchors = cscaAnchors,
  )

  if (result.valid) {
      // result.signerCommonName — DSC subject CN
      // result.signedAt         — SOD signing time, if present
  } else {
      // result.errors — non-empty list describing each failure
  }
  ```

Neither the SDK nor the companion verifier bundles any MAI CSCA certificates.
Fetch the current trust chain from MAI's official CEI cert download page:
<https://hub.mai.gov.ro/cei/info/descarca-cert>. Your app owns freshness and
revocation — re-fetch on a cadence appropriate for your trust window.

## Licensing and attribution

This SDK is Apache-2.0. It depends on [JMRTD](https://jmrtd.org) and
[SCUBA](http://scuba.sourceforge.net) (**LGPL-2.1**) plus
[Bouncy Castle](https://www.bouncycastle.org) (MIT). LGPL via a Maven
dependency is compatible with closed-source apps, and the SDK ships
`consumer-rules.pro` so R8/ProGuard relinking compliance is automatic.

**What you need to do:** surface third-party attribution in your app. If
you already have an "Open-Source Licenses" screen (Play Services, Firebase,
most AndroidX libraries), JMRTD and SCUBA will appear in it automatically
once you add this SDK.

See [ATTRIBUTION.md](ATTRIBUTION.md) for the full guide: adding a licenses
screen, a copy-paste snippet for custom screens, LGPL §6 source
availability, and a legal-review FAQ.

## License

This SDK is licensed under the Apache License, Version 2.0. See `LICENSE`.
Third-party component licenses are in `LICENSE-THIRD-PARTY`.

## Support

"Maintained, best-effort." Used in production for
[am-semnat](https://amsemnat.ro). Issues and PRs welcome; no SLA guarantees.
