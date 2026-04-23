# Contributing

Thanks for your interest. This SDK is used in production for
[am-semnat](https://amsemnat.ro); we maintain it on a best-effort basis and
welcome improvements.

## Ground rules

- **DCO-style sign-off.** By submitting a pull request, you certify that you
  wrote the code (or have permission to submit it under Apache-2.0). No CLA.
- **Match the existing style.** `kotlin.code.style=official`, no wildcard
  imports, 4-space indent.
- **Keep user-facing strings out of the SDK.** The SDK ships zero UI copy;
  consumer apps localize. Internal log messages stay English.
- **LGPL obligations apply** to `org.jmrtd.*` and `net.sf.scuba.*` —
  improvements there must be contributed upstream rather than vendored.

## Build + test

```bash
./gradlew :sdk:assembleRelease
./gradlew :sdk:lint
./gradlew :sdk:test
./gradlew :sdk:publishToMavenLocal
```

NFC protocol paths can't be exercised in unit tests. Real-card changes should
include a short note in the PR describing how you verified on hardware.

## Releasing

Version bumps happen in lockstep with the iOS / Expo / Node verifier SDKs
through `0.x`. After `1.0.0` the packages may version independently.
