# CLAUDE.md

Guidance for Claude Code when working in `am-semnat-sdk/android/`.

## What this is

Standalone Android library for reading + signing with Romanian CEI eID cards
over NFC. Maven Central target: `ro.amsemnat:am-semnat-sdk`. Extracted from
`../../am-semnat/modules/expo-cei-reader/android/`, which still ships in the
app and runs in parallel until the `am-semnat` app finishes migrating to
this SDK.

The public surface is **frozen** for 0.x. Don't change type names, method
signatures, enum variants, or `AmSemnatError` cases without updating the
iOS / Expo / verifier siblings (future `../ios/`, `../expo/`,
`../verifier-node/`) in lockstep — they ship API-compatible symbols.

## Build & test

Single-module Gradle project (`:sdk`), no app, no instrumented tests.

```bash
./gradlew :sdk:assembleRelease     # compile + package .aar
./gradlew :sdk:test                # unit tests (pure JVM)
./gradlew :sdk:lint
./gradlew :sdk:publishToMavenLocal # 0.1.0-SNAPSHOT → ~/.m2
```

Requires JDK 17+ (Android Studio's bundled JBR works) and Android SDK 36.
NFC paths aren't covered in CI — verified manually against a real card via
the am-semnat app after integration. `PassiveVerifier` self-generates its
CMS fixtures with BouncyCastle, so no pre-baked test vectors.

## Layout

`sdk/src/main/kotlin/ro/amsemnat/sdk/` — public surface (`AmSemnat`,
`AmSemnatError`, the data classes, `DataGroup`, `*Progress`, `AmSemnatLogger`).

`…/internal/` — orchestration (`CardReader`, `CardSigner`), card sub-apps
(`EDataReader`, `SigningReader`), `NfcSession`, `InputValidation`,
`PinStatusMapping`, internal exception types.

`…/internal/passive/` — offline passive auth: `SodParser`, `ChainValidator`,
`DgHasher`, `PassiveVerifier`.

Read flow: `AmSemnat.readIdentity` → `withNfcSession` acquires `IsoDep` →
PACE → DG reads → chip auth → eDATA → flat `RomanianIdentity`. Internal
`EData*` / `Signing*` exceptions are caught at the `CardReader` /
`CardSigner` boundary and re-thrown as typed `AmSemnatError`s. Don't leak
internal exception types past that boundary.

## Rules

- **Public surface is frozen.** See `sdk-api-surface.md` and the parity
  checklist at the bottom of that doc.
- **JMRTD + SCUBA are LGPL-2.1.** Don't patch them locally — contribute
  upstream. Our own Kotlin is Apache-2.0.
- **R8/ProGuard relinking obligation.** `sdk/consumer-rules.pro` keeps
  `org.jmrtd.**` + `net.sf.scuba.**`. Add a matching `-keep` for any new
  LGPL dep.
- **Core library desugaring is on.** `java.time.Instant` is in the public
  API and `java.util.Base64` is used internally; both work on `minSdk 24`
  only because of desugaring. Consumers must enable it (README says so).

## Gotchas

- **DG iteration follows enum declaration order**
  (`DG1 → DG2 → DG7 → DG14`). DG14 must be read before chip auth;
  preserve that if you reorder. Active Authentication (DG15) is
  deliberately out of 0.x — see the "What's not in 0.x" subsection of
  `sdk-api-surface.md` before reintroducing it.
- **Low-level `IsoDep` overloads don't own the tag lifecycle.** Caller
  owns the tag; the SDK just calls `connect()` if needed and sets a 10s
  timeout.
- **Input validation lives in `internal/InputValidation.kt`,** not as
  private helpers on `AmSemnat`, so tests can hit it directly (`IsoDep`
  has no public constructor and can't be stubbed).
- **Signing-attribute DER must build with raw BouncyCastle ASN.1**
  (`DERSequence` / `DERSet`), not CMS-level helpers. This mirrors the
  iOS / JS side where pkijs round-tripping produces different bytes on
  Hermes. See `SigningReader.buildSignedAttributes`.
- **Don't reintroduce Expo `CodedException` types here** — the old module
  threw them with Romanian messages; that's the bridge layer's job, not
  the SDK's.
- Minor: `AmSemnatError` singletons use `data object` (not plain `object`
  as the spec shows) for a friendlier `toString()`. Same singleton
  semantics; keep the form if you touch the file.
