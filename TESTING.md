# Testing Guide

How the AutoKorrektur Android client is tested and how to run each suite. The optional cloud
service and the desktop ML benchmark moved to
[konradvoelkel/autokorrektur-backend](https://github.com/konradvoelkel/autokorrektur-backend) and
are tested there.

> **Before the first run**: `scripts/fetch_assets.sh`. The models and the 50 reference triples are
> not in git (`scripts/assets.manifest`); one archive is extracted into both places that need it —
> `app/src/androidTest/assets/triples/` and `app/src/test/resources/triples/`. Gradle fails with
> that command if anything is missing.

The app builds as four product flavors (`docs/PRODUCT_TIERS.md`), so Gradle test tasks
need a flavor prefix — bare `testDebugUnitTest` does not resolve. The commands below use `full`,
the flavor that exercises every code path; substitute `core`/`plus`/`beta` to test a tier.

---

## 1. Suites

| Suite | Where | What it covers |
| :--- | :--- | :--- |
| **JVM unit tests** | `app/src/test/java/…` | ViewModel and UI state, quota/consent managers, mask and image maths, diagnostics store, string-resource contract. MockK; no emulator. |
| **Instrumented tests** | `app/src/androidTest/java/…` | Real model execution, mask quality against the ground-truth triples, colour fidelity, end-to-end workflows, Espresso UI flows. |
| **Static analysis** | — | `detekt` for Kotlin, Android Lint with a baseline for pre-existing debt. |

```bash
./gradlew :app:testFullDebugUnitTest          # JVM tests
./gradlew :app:connectedFullDebugAndroidTest  # instrumented (device or emulator)
./gradlew detekt :app:lintFullDebug           # static analysis
./gradlew jacocoTestReport                    # coverage (full flavor)
```

A single instrumented class:

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=de.konradvoelkel.android.autokorrektur.ml.MaskQualityBenchmarkTest
```

CI (`.github/workflows/ci.yml`) runs lint, unit tests and the emulator suite against `full`, then
builds the `core` release bundle.

## 2. Invariants worth knowing about

These are the assertions that encode hard-won knowledge; the test files themselves are the
reference for the exact thresholds.

- **Mask polarity and colour spaces** — `ColorSpacePreservationTest`, `VehicleMaskSegmentationTest`:
  RGBA↔RGB conversions must not swap channels, and the mask convention in `ARCHITECTURE.md` §2 holds
  end to end.
- **Untouched pixels stay untouched** — `InpaintingQualityBenchmarkTest`: outside the car mask the
  output must match the input to a high PSNR floor.
- **Shadows and clutter** — `VehicleShadowSegmentationTest`, `MultiVehicleClutteredSceneTest`: cast
  shadows and tire contact points are removed without eating the pavement, and multiple vehicles
  are detected as distinct instances.
- **Lifecycle and failure paths** — `RotationLifecycleInferenceTest` (rotation mid-inference keeps
  the result, runs inference once), `ServerSdxlApiFallbackTest` (network failure preserves the
  daily quota).
- **Localization contract** — `StringResourceLocalizationTest`: `values/strings.xml` is English and
  complete, `values-de/` a complete override, no `values-en/`, placeholders match. Strict since
  2026-09-21; it exists because the two files once covered complementary halves of the key space,
  so every locale other than de/en got a mixed-language UI.
- **Diagnostics** — `TelemetryStoreTest`: JSON Lines encoding, the size cap, and that the store
  degrades to a no-op when disabled. On a device: menu → Diagnostics → on, then
  `adb shell run-as de.konradvoelkel.android.autokorrektur cat files/telemetry/events.jsonl`.

## 3. Getting pipeline output off a device

`connectedAndroidTest` uninstalls the app when it finishes, which deletes the app's private
storage with it — a test that writes PNGs to `appContext.cacheDir` leaves nothing to look at. To
eyeball what the pipeline actually produced, drive the instrumentation by hand instead, which
leaves both APKs installed:

```bash
./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest
adb install -r -t app/build/outputs/apk/full/debug/app-full-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk
adb shell am instrument -w -e class <fully.qualified.TestClass> \
  de.konradvoelkel.android.autokorrektur.full.test/androidx.test.runner.AndroidJUnitRunner
adb shell 'run-as de.konradvoelkel.android.autokorrektur.full cat cache/<file>.png' > out.png
```

Note the flavor's `applicationIdSuffix` in both the `run-as` target and the runner component —
`full` is `…autokorrektur.full`, and `run-as` reports "unknown package" rather than anything
helpful when you get it wrong.

A file pushed to the device is invisible to the photo picker until MediaStore indexes it:

```bash
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d file:///sdcard/Download/<name>.png
```

## 4. The options menu

`OverflowMenuSmokeTest` (instrumented) opens every entry of `menu_main.xml` and walks what it
opens. It exists because the menu was untested surface until 2026-10-08, and that is where the
published APK died on its first contact with a phone: `about_dialog_content` carried an unescaped
`%`, so `getString(id, versionName)` threw `UnknownFormatConversionException` the moment
"About & Licenses" was tapped (`a7f59c5`).

Two things about it are deliberate.

- `overflowMenu_hasExactlyTheEntriesThisSuiteCovers` asserts the live toolbar menu holds exactly
  the ids the suite walks. Add a third entry and the suite fails until it is covered here too,
  rather than quietly testing two of three.
- Both negative controls were run before the suite was committed, because a checker that has never
  rejected anything is not known to work. Re-arming the `%` in `about_dialog_content` fails
  `aboutEntry_opensAndRendersItsFormattedBody` with the original
  `UnknownFormatConversionException: Conversion = 'O'`; removing the `Telemetry.setEnabled(false)`
  from the delete path fails `diagnosticsDelete_turnsRecordingOffAndAcknowledgesIt` with the switch
  still checked. If you change this suite, re-arm them.

The `%` is now caught twice over — `StringResourceLocalizationTest` check 5 covers the string, this
suite covers the path — which is the point: a format bug present in both locales agrees with itself
perfectly in any parity check, and only opening the screen proves the screen opens.

## 5. Running on a physical device

Two things that cost a suite each on 2026-10-08, the first full arm64 walk (FT-01).

**Unlock the phone, and keep it awake.** Behind a keyguard no activity reaches RESUMED, so every
Espresso interaction fails with `NoActivityResumedException` — a message that blames the test and
says nothing about the device. Worse, it is *intermittent*: it depends on whether the screen
timeout fires mid-run. The Pixel 10 Pro has a 30-second timeout and connects over wireless
debugging, so `stay_on_while_plugged_in` never applies; a 14-minute suite produced exactly one
such failure. `UnlockedDeviceRule` now turns that into a precondition failure that names itself,
on every Espresso class. It deliberately does not try to unlock anything — a secured keyguard
needs its owner.

```
adb -s <serial> shell settings put system screen_off_timeout 1800000   # restore afterwards
# then unlock the device by hand; a secured keyguard cannot be dismissed by adb
```

**Pin one device.** A phone on wireless debugging can be attached over two transports at once
(`adb devices` shows both), and Gradle fans out to all of them — the second install then fails
with `INSTALL_FAILED_DUPLICATE_PACKAGE`. Use `ANDROID_SERIAL`.

**Flavor.** `connectedFullDebugAndroidTest` installs `…autokorrektur.full`, which coexists with a
sideloaded `core` release. Testing `core` on a phone that carries the published APK would collide
on the applicationId and force its removal.

**Never pass `-PscreenshotAbi=x86_64` for a phone.** It is for emulator work; on arm64 it strips
the libraries the ML path needs.

## 6. Hardware caveat

x86_64 emulators software-emulate NNAPI and translate arm64 code, so delegate fallback and native
crashes behave differently there than on real hardware — the TFLite interpreter, for instance,
segfaults under arm64 translation on the Pixel AVD. Model-execution changes need a physical ARM64
device before release; every Espresso suite additionally asserts that no error Snackbar appears on
launch, which catches initialization failures that would otherwise pass silently.

## 7. Proposed: config-matrix screenshots and accessibility checks

Field testing surfaced "lots of minor issues, too much to report" — presentation bugs across
locale × theme × width, which no one sweeps by hand. Two cheap additions would catch them:
[Paparazzi](https://github.com/cashapp/paparazzi) golden screenshots rendered on the JVM across
those axes, and AndroidX's `AccessibilityChecks` enabled for the existing Espresso suite (one line,
flags touch targets under 48 dp, missing content descriptions, poor contrast). Neither is
implemented yet.
