# Releasing Kosh (signed, small APK)

## What already shrinks the release build

| Setting | Where | Effect |
|---|---|---|
| R8 minify + full mode | `app/build.gradle.kts`, `gradle.properties` | removes unused code, renames and merges classes |
| Resource shrinking | `app/build.gradle.kts` | removes unreferenced drawables/strings/layouts |
| `resourceConfigurations = ["en"]` | `app/build.gradle.kts` | drops library translations Kosh doesn't ship |
| Packaging excludes | `app/build.gradle.kts` | skips license/metadata files inside the APK |
| `dependenciesInfo` off | `app/build.gradle.kts` | no dependency-metadata blob in the APK |
| Kotlin null-check flags | `app/build.gradle.kts` | smaller bytecode |
| `proguard-rules.pro` | `app/` | repackaging, log stripping, Room keep rules |

Kosh has no native libraries, so ABI splits would not save anything.
If you publish to Google Play, upload an **AAB** (`gradle :app:bundleRelease`); Play then delivers a smaller per-device APK.

## One-time: create a signing key

```bash
keytool -genkeypair -v -keystore kosh-release.jks -alias kosh \
  -keyalg RSA -keysize 4096 -validity 10000
```

Back this file up and remember both passwords. If you lose the key you can never update an installed app.

## Sign locally

1. Copy `keystore.properties.example` to `keystore.properties` and fill it in (it is git-ignored).
2. `gradle :app:assembleRelease`
3. The signed APK is in `app/build/outputs/apk/release/`.

Without `keystore.properties` the same command produces an **unsigned** APK.

## Sign in GitHub Actions

1. `base64 -w0 kosh-release.jks` (macOS: `base64 -i kosh-release.jks`) and copy the output.
2. Repository → Settings → Secrets and variables → Actions → add:
   `KOSH_KEYSTORE_BASE64`, `KOSH_KEYSTORE_PASSWORD`, `KOSH_KEY_ALIAS`, `KOSH_KEY_PASSWORD`.
3. Run **Release APK (signed)** from the Actions tab, or push a tag like `v0.1.0`
   (tag builds also attach the APK to a GitHub Release).

The workflow runs the unit tests, builds, checks the signature with `apksigner` and prints the APK size.

## After building

* Install the release APK and click through every tab. R8 problems only show up in release builds
  (a missing keep rule usually appears as a crash on first use of a feature).
* Keep `mapping.txt` from the same build (the workflow uploads it) to read release crash reports.
* Bump `versionCode` and `versionName` in `app/build.gradle.kts` for each release.
