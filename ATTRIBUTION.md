# Attribution guide

This SDK bundles three third-party libraries via Maven. Two of them
([JMRTD](https://jmrtd.org) and [SCUBA](http://scuba.sourceforge.net)) are
**LGPL-2.1**; [Bouncy Castle](https://www.bouncycastle.org) is MIT/X11.
Your app must surface attribution for these in a form end users can access.

This document covers:
- [LGPL obligations](#lgpl-obligations) and how they're satisfied
- [Adding an Open-Source Licenses screen](#adding-an-open-source-licenses-screen) (one-line Gradle plugin)
- [Rolling your own licenses screen](#rolling-your-own-licenses-screen) (copy-paste snippet)
- [Source code availability (LGPL §6)](#source-code-availability-lgpl-6)
- [Legal review FAQ](#legal-review-faq)

The canonical attribution text lives in
[`LICENSE-THIRD-PARTY`](LICENSE-THIRD-PARTY) at the repo root.

## LGPL obligations

Your own app code is not infected by LGPL — you can ship closed-source apps
linking this SDK. Three obligations apply:

1. **Attribution.** Include JMRTD's + SCUBA's license notice somewhere
   visible. An "Open-Source Licenses" screen is the standard pattern on
   Android; the recipes below show the two mainstream paths.
2. **Patched JMRTD/SCUBA.** If you fork those libraries, your fork remains
   LGPL and must be distributable on request. If you're just pulling them
   via Maven (the default), this does not apply.
3. **Relinking.** End users must be able to replace the JMRTD build with a
   different one. R8 / ProGuard minification can break this if it renames
   or inlines JMRTD classes. **This SDK ships a `consumer-rules.pro` with
   the necessary `-keep` directives, so most apps do not need to add
   anything.** If you use custom ProGuard rules, ensure these classes are
   preserved:

   ```proguard
   -keep class org.jmrtd.** { *; }
   -keep class net.sf.scuba.** { *; }
   ```

## Adding an Open-Source Licenses screen

### If your app already has one

Apps that already depend on Play Services, Firebase, Maps, or most AndroidX
libraries tend to have an "Open-Source Licenses" screen — the tooling that
generates it picks up any Maven dependency with a POM, including JMRTD and
SCUBA. **Search the generated screen for "JMRTD" after your next build to
verify.** If it shows up, you're done.

### If you don't have one yet

The shortest path is Google's OSS Licenses plugin.

Root `build.gradle` (or `settings.gradle` with the plugins DSL):

```groovy
plugins {
    id 'com.google.android.gms.oss-licenses-plugin' version '0.10.6' apply false
}
```

App module `build.gradle`:

```groovy
plugins {
    id 'com.google.android.gms.oss-licenses-plugin'
}

dependencies {
    implementation 'com.google.android.gms:play-services-oss-licenses:17.1.0'
}
```

Launch the generated Activity from your settings screen:

```kotlin
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

startActivity(Intent(this, OssLicensesMenuActivity::class.java))
```

The plugin scans the Maven dependency tree at build time and picks up
JMRTD, SCUBA, and Bouncy Castle automatically from their POM metadata — no
manual entries.

### Alternatives

- [AboutLibraries](https://github.com/mikepenz/AboutLibraries) — more
  customizable UI, Compose-first.
- [Licensee](https://github.com/cashapp/licensee) — emits JSON for
  custom-built screens.

Any tool that reads Maven POMs will surface the LGPL deps correctly.

## Rolling your own licenses screen

If you'd rather render attribution yourself — no plugin, no extra
dependency — paste this constant and one of the display snippets below. The
text covers JMRTD, SCUBA, and Bouncy Castle, which are the only third-party
libraries shipped with this SDK.

```kotlin
val AMSEMNAT_THIRD_PARTY_LICENSES = """
    am-semnat-android-sdk — third-party attribution

    JMRTD
      Upstream: https://jmrtd.org
      License:  LGPL-2.1-or-later
      Text:     https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

    SCUBA Smart Cards
      Upstream: http://scuba.sourceforge.net
      License:  LGPL-2.1
      Text:     https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

    Bouncy Castle
      Upstream: https://www.bouncycastle.org
      License:  MIT/X11

      Copyright (c) 2000 - 2024 The Legion of the Bouncy Castle Inc.

      Permission is hereby granted, free of charge, to any person obtaining
      a copy of this software and associated documentation files (the
      "Software"), to deal in the Software without restriction, including
      without limitation the rights to use, copy, modify, merge, publish,
      distribute, sublicense, and/or sell copies of the Software, and to
      permit persons to whom the Software is furnished to do so, subject to
      the following conditions:

      The above copyright notice and this permission notice shall be
      included in all copies or substantial portions of the Software.

      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
      EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
      MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
      IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
      CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
      TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
      SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
""".trimIndent()
```

Jetpack Compose:

```kotlin
@Composable
fun LicensesScreen() {
    Text(
        text = AMSEMNAT_THIRD_PARTY_LICENSES,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    )
}
```

Classic Views:

```xml
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:id="@+id/licenses"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:typeface="monospace"
        android:textIsSelectable="true" />
</ScrollView>
```

```kotlin
findViewById<TextView>(R.id.licenses).text = AMSEMNAT_THIRD_PARTY_LICENSES
```

The LGPL-2.1 full text is hosted at the URL above — linking to it satisfies
attribution in practice. If your legal posture requires the full license
body inline, copy the content from
[gnu.org/licenses/old-licenses/lgpl-2.1.txt](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt)
into the constant.

On SDK version bumps, re-check this file for any changes to the constant —
attribution is stable but not frozen.

## Source code availability (LGPL §6)

LGPL-2.1 §6 requires that end users can obtain the library source. JMRTD
and SCUBA source jars are published to Maven Central alongside their binary
artifacts, and the upstream project pages host the source tree. Pointing
users at the upstream URLs listed in [`LICENSE-THIRD-PARTY`](LICENSE-THIRD-PARTY)
satisfies the obligation — you do not need to mirror or redistribute the
source yourself.

## Legal review FAQ

### "Our legal team flagged LGPL — is it actually a problem?"

LGPL via dynamic linking (a Maven dependency you don't modify) is
compatible with closed-source commercial apps. The three obligations above
are either automated (attribution via the licenses plugin, relinking via
the shipped `-keep` rules) or inapplicable if you don't patch JMRTD
itself. The FSF's own
[LGPL-and-Java FAQ](https://www.gnu.org/licenses/lgpl-java.html) confirms
this pattern.

If your legal team still blocks it, [open an
issue](https://github.com/am-semnat/am-semnat-android-sdk/issues) — a
pure-BouncyCastle variant is on the roadmap.

### "Do I need to display attribution if my app has no UI / is a background service?"

LGPL attribution attaches to distribution, not runtime UI. A `NOTICE` file
bundled with the APK (or distributed alongside it, e.g. via your app's
documentation or website) is sufficient. The UI-screen pattern is the most
common because most apps have UI; it's not the only acceptable form.

### "Can I just link to this repository's `LICENSE-THIRD-PARTY`?"

For LGPL compliance of your distributed app, the attribution must travel
with your binary — linking from within your app to an external URL is a
gray area if that URL ever goes away. Either bundle the text (via a
licenses screen or a raw resource in your APK) or mirror
`LICENSE-THIRD-PARTY` into your own repo and link to it from inside the
app.
