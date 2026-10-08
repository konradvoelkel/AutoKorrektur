# AutoKorrektur 2.0 — your street without cars, computed on your own device

AutoKorrektur finds the parked cars in a photo and makes them disappear. The machine learning runs
entirely on the phone: no account, no upload, no tracking. The result is a before/after image ready
to share, in two taps. Android app, free software (AGPLv3).

[autokorrektur.org](https://autokorrektur.org) (German, [English](https://autokorrektur.org/en.html)) ·
[Download](https://autokorrektur.org/en.html#android) ·
[Privacy policy](https://autokorrektur.org/privacy-en) ·
[Changelog](CHANGELOG.md)

<table>
  <tr>
    <td><img src="media/image_1_with_car_640x640.png" alt="Street with a parked car" width="400"/></td>
    <td><img src="media/image_1_without_car_640x640.png" alt="The same street, the car removed" width="400"/></td>
  </tr>
  <tr>
    <td align="center"><b>Before: the original photo</b></td>
    <td align="center"><b>After: car-free vision</b></td>
  </tr>
</table>

## What it is for

You walk through your neighbourhood and see places full of parked vehicles — places that would be
nicer and more liveable without the mass of cars. Because that is so hard to picture, we need images
of a possible future. This app helps you find the shots worth making: it produces a preview, right on
the phone, of how the place could look without the cars. The best ones you share on Instagram or
Signal, or hand to a professional who turns them into a high-quality visualisation.

Made for mobility activists, urban planners and anyone who wants to show how much room a street has
once the metal is gone. The "magic eraser" features of Apple, Google and Samsung retouch any object
but compute wholly or partly in the cloud; AutoKorrektur limits itself to vehicles, and in return
needs no selection step, no server and no advertising.

## What the app does

- **Photo in, cars out.** Take a photo or pick one from the gallery. A segmentation network finds
  the vehicles, an inpainting network fills the gap with plausible road, greenery and pavement.
- **Before / after.** A slider shows the difference. The finished split card ("BEFORE / CAR-FREE",
  "VORHER / AUTOFREI" on German devices) goes out through Android's ordinary share sheet, or is
  saved to the Pictures folder. A small in-app gallery keeps your car-free shots.
- **A preview, not a print-quality montage.** The network invents shadows, reflections and hidden
  details plausibly, but not always correctly or beautifully.

## How it computes

The whole pipeline is on-device; `StaticImagePipeline` orchestrates it, `ARCHITECTURE.md` has the
contracts.

1. **Load.** The photo is decoded, EXIF-rotated and letterboxed to the 640×640 input the detector
   expects (OpenCV, from Maven).
2. **Segment.** YOLO11s-seg runs on TensorFlow Lite. Only the COCO classes car, motorcycle, bus and
   truck are kept, so people, bicycles and cargo bikes stay in the picture.
3. **Build the mask.** Each detection's prototype coefficients are multiplied into a mask logit
   map, upscaled while still continuous, thresholded, closed and dilated a little so contact
   shadows and anti-aliased edges are swallowed too. The union of all detections is then snapped
   to the vehicles' contours with an edge-preserving guided filter against the photo. Polarity is a
   strict contract: 0 is hole, 255 is kept context (`ARCHITECTURE.md` §2).
4. **Inpaint.** MI-GAN (512×512) runs on ONNX Runtime and fills the hole.
5. **Compose.** The inpainted region is scaled back to the photo's resolution and blended into the
   original through the mask; the slider view and the split-card export read from that result.

Models and runtimes: YOLO11s-seg exported to TFLite (Ultralytics, AGPL-3.0), MI-GAN (Picsart AI
Research, MIT), ONNX Runtime, TensorFlow Lite, OpenCV 5. UI in Kotlin, partly Jetpack Compose. The
models ship inside the APK, which is why it is a few hundred megabytes and why the app needs no
internet connection after installing. Requirements: Android 10 or newer, 64-bit ARM; 4 GB of RAM or
more is recommended, older devices simply take longer.

## Privacy, enforced by the build

The published app has **no `INTERNET` permission**. It cannot send or receive anything, photos
included, and the privacy policy, the Play listing and the Data Safety answers all say so. The
manifest is held to an exact permission allowlist in `app/build.gradle.kts`: `verify<Variant>Permissions`
runs from every `assemble`, `bundle` and `check`, so a permission merged in by a library fails the
build instead of appearing on the store page.

Photos come in through the Android photo picker (only the selected image, never the whole gallery)
or from the camera app, and leave only through the share sheet to the app you pick there. There is
no account, no advertising, no analytics SDK. Optional diagnostics (compute times, image size,
device model — never images) can be switched on in the menu; they are off by default, stay in a
file on the device and are exported only by you. [PRIVACY_POLICY.md](PRIVACY_POLICY.md) is the
binding German text, [PRIVACY_POLICY.en.md](PRIVACY_POLICY.en.md) the translation; both are rendered
onto the website by `site/build.sh` so the hosted text cannot drift.

## Install

AutoKorrektur is distributed as a signed APK from
[autokorrektur.org](https://autokorrektur.org/en.html#android), with a `SHA256SUMS` file and the
signing certificate's fingerprint next to it; the same file is attached to the matching
[GitHub release](https://github.com/konradvoelkel/AutoKorrektur/releases). Every release is signed
with the same certificate.
Google Play (package `de.konradvoelkel.android.autokorrektur`) comes later — it needs a beta-test
programme first. If you would like to take part, with your local transport-transition initiative for
example, [get in touch](https://www.konradvoelkel.com/pages/contact).

## Build from source

The models and the test fixtures are **not in git**. They live on the
[`assets-v1`](https://github.com/konradvoelkel/AutoKorrektur/releases/tag/assets-v1) release, pinned
by SHA-256 in `scripts/assets.manifest`; Gradle's `verifyAssets` fails the build with the fix command
if anything is missing.

```bash
scripts/fetch_assets.sh                      # download + verify; idempotent
./gradlew :app:assembleCoreDebug             # the published flavor
./gradlew :app:testFullDebugUnitTest         # JVM tests
./gradlew :app:connectedFullDebugAndroidTest # instrumented tests (device or emulator)
./gradlew :app:bundleCoreRelease             # the release bundle (R8)
./gradlew detekt :app:lintFullDebug          # static analysis
```

The app builds as four product flavors, so every Gradle task needs a flavor prefix. **Only `core`
is published** and this README describes `core`. The other three (`plus`, `beta`, `full`) are
supersets for the maintainer and opt-in testers and carry experimental features behind
`BuildConfig` flags — extra export layouts, high-resolution tile inpainting, a mask brush, batch
processing, a client for an optional cloud inpainting service, a live AR viewfinder with short video
snippets. `full` has everything on and is the CI and development baseline; the emulator is x86_64,
so pass `-PscreenshotAbi=x86_64` for emulator work and never for a release bundle.
[docs/PRODUCT_TIERS.md](docs/PRODUCT_TIERS.md) has the matrix and the reasoning; `app/build.gradle.kts`
is the authority for the flags.

| Where | What |
|---|---|
| `app/src/main/java/.../pipeline/` | `StaticImagePipeline` — load → segment → inpaint → compose |
| `app/src/main/java/.../ml/` | engines, mask maths, pre- and post-processing |
| `app/src/main/java/.../telemetry/` | the opt-in, on-device diagnostics |
| `app/src/main/res/` | strings (English default, complete German override, parity enforced by a test), derived OKLCH colour palette |
| `scripts/` | `fetch_assets.sh` and the asset manifest |
| `site/` | autokorrektur.org — `build.sh` renders the pages and the privacy policy, `deploy.sh` ships them |
| `docs/` | [INDEX.md](docs/INDEX.md) is the map |

Most useful next: [ARCHITECTURE.md](ARCHITECTURE.md) (pipeline, mask polarity, colour spaces,
coordinate transforms), [TESTING.md](TESTING.md) (suites and the invariants they assert),
[CLAUDE.md](CLAUDE.md) (what bites in this repository), [TODO.md](TODO.md) (roadmap). CI runs lint,
unit tests and the emulator suite against `full`, then builds the `core` release bundle.

## How it was built

This repository is also an experiment in agentic software engineering: the native app was designed,
implemented, tested and released in pair-programming between its maintainer and AI coding agents,
with the agents running builds, test suites, ADB sessions and screenshot checks themselves. The
guard-rails that make this workable are in the repository — the permission allowlist, the
localization parity test, the asset manifest, the mask-polarity contract — and `CLAUDE.md` is the
orientation an agent (or a person) gets on arrival.

## Background and attribution

AutoKorrektur 2.0 is the native Android rewrite of Benjamin Beckers'
[browser version (1.0)](https://github.com/BenB2/AutoKorrektur) and a private project by
Konrad Völkel in Düsseldorf. It keeps the idea and the on-device principle and replaces the
implementation. Both versions build on two bachelor theses at Heinrich Heine University Düsseldorf,
supervised by Konrad Völkel:

- **Till Schellscheidt**, *Autokorrektur – Automatisierte Objektersetzung in Fotos* (2024):
  the concept of automated vehicle removal for mobility activism, a two-cycle latent-diffusion
  inpainting workflow with environmental conditioning, the first mask padding and shadow-expansion
  rules, and a four-criteria qualitative evaluation (instance segmentation, realism, consistency,
  naturalness).
- **Ben Beckers**, *Autokorrektur – Inpainting auf mobilen Endgeräten* (2025):
  the move from cloud GPUs to 100 % on-device inference with ONNX Runtime Web and OpenCV.js, the
  YOLOv11-seg model-size trade-off on Mapillary Vistas, MI-GAN 512×512 for sub-5-second inpainting,
  and the mask scaling and shadow-downshift transformations the Android app started from.

```bibtex
@bachelorthesis{schellscheidt2024autokorrektur,
  author = {Till Schellscheidt},
  title  = {Autokorrektur -- Automatisierte Objektersetzung in Fotos},
  school = {Heinrich-Heine-Universit{\"a}t D{\"u}sseldorf},
  year   = {2024}, month = {February}, type = {Bachelor's Thesis}
}
@bachelorthesis{beckers2025autokorrektur,
  author = {Ben Beckers},
  title  = {Autokorrektur -- Inpainting auf mobilen Endger{\"a}ten},
  school = {Heinrich-Heine-Universit{\"a}t D{\"u}sseldorf},
  year   = {2025}, month = {May}, type = {Bachelor's Thesis},
  url    = {https://github.com/BenB2/AutoKorrektur}
}
```

Questions and bug reports: [GitHub issues](https://github.com/konradvoelkel/AutoKorrektur/issues) or
the contact in the [Impressum](https://autokorrektur.org/impressum).

## License

GNU AGPLv3, see [LICENSE](LICENSE). The segmentation model (YOLO11s-seg, Ultralytics) is itself
AGPL-3.0, which fixes the licence of the whole; MI-GAN is MIT. Provenance and licences of the models
are listed in the asset release notes and in the app's "About & Licenses" dialog.
