# 🏛️ AutoKorrektur: Completed Development Milestones Archive

Completed, verified work moved out of `TODO.md`, which carries only what is still open.
Sections 1 and 2 are the 1.0 milestones and hardening phases; sections 3 to 6 are the 2.0
repository move, the two usability runs, the release plumbing that is finished, and the field
testing on real hardware.

Findings and decisions are summarised here — the full record stays where it was written:
the git log. The usability runs' scenarios and reports were removed from the working tree on
2026-10-09 and live in history: `git show c739cf6:reports/improvements-001.adoc`,
`git show ddaed40:reports/improvements-002.adoc`; `git ls-tree -r --name-only ddaed40 reports scenarios`
lists the rest.

---

## 1. Core ML & Pipeline Milestones (Verified in v1.0.0)

- [x] **M1. Uninitialized YoloService & Lifecycle Resolution (TDD)**
  - Added lazy auto-initialization in `StaticImagePipeline.kt` and decoupled asynchronous ML setup from UI buttons. Verified via `UninitializedYoloServiceUsageTest`.
- [x] **M2. Inpainting Color Space Fidelity (RGB vs RGBA)**
  - Fixed OpenCV Android SDK color space conversions (`COLOR_RGBA2RGB` and `COLOR_RGB2RGBA`) preventing yellow/blue channel permutations. Verified via `ColorFidelityAndMaskOverlayInstrumentedTest`.
- [x] **M3. Inverted Mask Blending Matrix Resolution**
  - Inverted mask blending logic in `MiGanInference.kt` (`Core.bitwise_not`), ensuring generated inpainting is copied strictly onto vehicle pixels rather than overwriting background.
- [x] **M4. EXIF Orientation Normalization**
  - Normalized camera EXIF orientation across `ImageProcessor`, `BeforeAfterSliderView`, and disk JPEG exports so portrait photos remain 100% upright throughout processing.
- [x] **M5. High-Resolution Continuous Logit Upscaling (`YoloMaskAssembler`)**
  - Replaced coarse binary thresholding on 160x160 prototypes with high-res `INTER_CUBIC` continuous probability upscaling and morphological closing (`MORPH_CLOSE`), eliminating jagged staircase boundaries.
- [x] **M6. Bounding Box Coordinate Normalization (`YoloPostprocessor`)**
  - Fixed 640x640 proposal coordinate normalization to $[0..1]$ ratio, preventing prototype crop clamping and eliminating non-car over-masking on background buildings, trees, and sky.
- [x] **M7. Quantitative Benchmark & Regression Suites**
  - Implemented `MaskQualityBenchmarkTest` (calculates $IoU$ & Dice scores) and `NonCarOverMaskingTest` (validates background isolation across multi-image datasets).
- [x] **M8. Backend Hardening & Code Quality**
  - 100% pass on 71 pytest unit/contract tests, ruff linting, and mypy static typing with memory-only GDPR guarantees.

---

## 2. Hardening & Performance Phases (Completed & Pushed)

### ✅ Phase 1: Ship-Blocking Stability & Memory
- [x] **C1 (Video PTS Timestamps)**: Switched `VideoEncoder.kt` to buffer mode with exact `presentationTimeUs` to guarantee constant 30 FPS playback.
- [x] **C4 (OpenCV Mat Leak)**: Wrapped all intermediate native Mat allocations in `MiGanInference.kt` in `try/finally` blocks.
- [x] **C5 (Native Pointer Safety)**: Replaced dangerous `finalize()` in `TemporalBackgroundAccumulator.kt` with deterministic `AutoCloseable.close()`.
- [x] **C6 (GDPR Zero-Storage)**: Set `spool_max_size = 15MB` in `backend/server.py` so uploads stay in volatile RAM and never touch `/tmp` disk.
- [x] **C7 (AR GC Thrashing)**: Pre-allocated reusable bitmap buffer via `AtomicReference` in `ArCameraActivity.kt` to eliminate 30 FPS allocations.
- [x] **H4 (Bitmap Leaks)**: Added explicit `recycle()` calls across `FirstFragment.kt` and `StaticImagePipeline.kt`.
- [x] **H5 (ANR Protection)**: Offloaded batch queue file I/O to `Dispatchers.IO` in `MainViewModel.kt`.
- [x] **H6 (Thread Safety)**: Synchronized camera frame access using `AtomicReference<Bitmap?>`.
- [x] **H8 & H9**: Added `MediaCodec` resource leak guards and fixed Redis authentication in `docker-compose.yml`.
- [x] **M2 & M3**: Enforced Material Design 48dp minimum touch targets across all layouts and fixed Android 12+ exported activity intent rules.

### ✅ Phase 2: Quality, I18n & Performance
- [x] **M1 (I18n / Localized Strings)**: Fully externalized all UI strings into default German and English.
- [x] **C2 (Video Decoding)**: Replaced slow `MediaMetadataRetriever` extraction with high-speed `MediaExtractor` + `MediaCodec` sequential decoding in `VideoInpaintProcessor.kt`.
- [x] **C3 (Temporal AR Consistency)**: Implemented persistent temporal background plate accumulation in `TemporalBackgroundAccumulator.kt`.
- [x] **H1–H3 (Backend Security)**: Fixed rate-limiting bypass (`X-Forwarded-For`), added Play Integrity `/v1/nonce` validation, and installed early ASGI `413 Payload Too Large` DoS protection.
- [x] **H7 & M6 (ML Acceleration)**: Memory-mapped TFLite model loading via `AssetFileDescriptor` and replaced per-pixel normalization with native OpenCV C++ SIMD `convertTo(CV_32FC3, 1.0/255.0)`.
- [x] **M11 (APK Footprint)**: Removed ~150 MB of dead/unused ONNX weights from `assets/model/`.

### ✅ Phase 3: Architecture, Testing & CI/CD
- [x] **L1 (Dynamic Versioning)**: Integrated configuration-cache-safe Git commit count and tag resolution providers in `app/build.gradle.kts`.
- [x] **L4 (CI/CD Pipeline)**: Added caching (`astral-sh/setup-uv`, `gradle/actions/setup-gradle`) and automated test/coverage/AAB artifact uploads in `.github/workflows/ci.yml`.
- [x] **L6 & L7 (Backend Production Readiness)**: Configured FastAPI `CORSMiddleware` and added healthcheck service blocks for Redis and backend in `backend/docker-compose.yml`.
- [x] **L8 (Privacy by Design)**: Cleaned `data_extraction_rules.xml` to explicitly exclude private cache and ML weights from cloud backup.
- [x] **M7 (Tile Inpainting Optimization)**: Cleaned `ProgressiveTileInpainter.kt` `createFeatheredMask` by removing dead Mat allocations.
- [x] **L3 (Unit Test Suite Expansion)**: Added academic validation tests verifying resolution modes, shadow expansion, pedestrian protection, and boundary continuity.

---

## 3. AutoKorrektur 2.0: repository move (Milestone 0, 2026-09-24)

- [x] **REPO-01. Slim the repository** — binaries, owner-only notes and the cloud backend out; a clone is ~13 MB, the tracked tree under 3 MB.
- [x] **REPO-02. Rebrand to 2.0** — README lineage, changelog headings, version fallback, store listing and website. The [browser version by Benjamin Beckers](https://github.com/BenB2/AutoKorrektur) is 1.0; this native rewrite is 2.0.
- [x] **REPO-03. Canonical repository is `konradvoelkel/AutoKorrektur`** — a fork of the unmaintained `xamde/AutoKorrektur`, which receives no further pushes. The fork marker costs nothing new, since that repo was already a fork of the 1.0 browser version.
- [x] **REPO-04. Actions enabled** — lint, unit tests, the instrumented emulator suite and the release bundle all run on the canonical repo.

`REPO-05` (asking GitHub Support to garbage-collect the fork network, so pre-rewrite objects stop
being reachable by SHA) is still open and stays in `TODO.md`.

---

## 4. Usability runs 001 and 002 (UX-01 to UX-25, 2026-09-28/29)

Two think-aloud runs, five fictional personas each, walking the live site and the `core` build.
The full record — scenarios, per-persona step-by-step reports, ranked findings, and the findings
that were **discarded** as test-environment artefacts — is in git history, not in the tree
(`git show c739cf6:reports/improvements-001.adoc`, `git show ddaed40:reports/improvements-002.adoc`;
the per-persona reports and scenarios sit next to them in those commits). Everything below shipped.

**Run 001 (UX-01 to UX-10)** — the app showed its working instead of its result, and the site
addressed a more technical reader than the one it was written for.

- [x] **UX-01, UX-02, UX-03, UX-04 (app)** — the result screen led with the red mask preview and the novice shared *that*; zero detections produced no message at all, under a caption asserting the opposite; Download and Share were loud primary buttons before there was anything to export; and "Für Instagram exportieren" was the only share control anywhere. Now: comparison first, mask demoted, "Keine Fahrzeuge erkannt", actions disabled until there is a result, and a plain "Bild teilen".
- [x] **UX-05, UX-06, UX-07, UX-08, UX-09 (site)** — the crash-risk warning sat 4.8 screens below the download button at 375 px; the install route was labelled "Sideloading, Prüfsumme, Zertifikat"; the English path dead-ended in a German-only Impressum; the verification instructions named `apksigner` without saying where it comes from; and "in zwei Tipps" was measured at four taps plus a scroll.
- [x] **UX-10. `/download/` returned 404** while the files under it were fine — it met exactly the reader who trims a URL to look for the checksum. Fixed in the infrastructure repo (`~/files/work/server`, `ef3be16`, item B13): `browse` on a matcher for exactly `/download` and `/download/`, deliberately not `/download/*`, so one intentionally public directory lists itself and no other path does.

**Run 002 (UX-11 to UX-25)** — a narrower theme: *the app stated an outcome but not its
consequence*. Run 001's nine fixes were re-confirmed blind, by fresh personas denied access to the
repository, including `TODO.md` and the scenarios' own regression notes.

- [x] **UX-11. Deleting diagnostics looked like it had failed.** It worked, but left the switch on, so `clear()` immediately minted a new install id and wrote a new `session_start` — a non-zero count and a brand-new id, seconds after the user confirmed "delete everything". Now: switch off first, then clear, and a dialog says both happened. The persona filed this as a no-op Critical; he was wrong about the mechanism and right about what the screen told him.
- [x] **UX-12, UX-21, UX-23. Diagnostics was unreadable from inside the app** — a count, a size, a truncated id, and a `.jsonl` export the share sheet offers no way to open. Now the dialog lists events in words and local time, and the install id is shown in full and long-press-copyable.
- [x] **UX-13, UX-19, UX-20. The no-detection result.** Disabled actions said nothing when tapped; the slider compared a photo with itself, sometimes without even its badges; and inpainting still ran on an empty mask (measured 1090 ms → 3 ms once skipped).
- [x] **UX-14, UX-16, UX-17, UX-18, UX-24. Wording and language.** Dialog buttons came from `android.R.string` and so followed the *device* language while the body followed the *app* language ("Tamam" under an English dialog on a Turkish phone); the diagnostics description named six fields fewer than the file carries; "100% … without data collection" sat against the diagnostics feature; nothing explained why the app wanted the camera; and the overflow icon was described in the device's language.
- [x] **UX-15, UX-22, UX-25 (site).** "Was das heißt" pointed at the *install* heading rather than the explanation it promised; the English contact made an English reader decode the German word "punkt"; and a RAM figure came with no way to check your own phone.

Two lessons from the fixing, not the finding. Adding the event list made the diagnostics dialog
taller than a 720x1280 screen and pushed **its own Delete button off the bottom** — the control
UX-11 is about, unreachable on the screen size the run used, and invisible in code review; the body
is a height-capped `ScrollView` now and Delete sits in it rather than in the button row. And
`TODO.md` had claimed run 001's site fixes were awaiting a deploy when they had already shipped —
the repo under-reporting its own state, which is run 001's finding 3 in reverse.

Worth preserving, confirmed by run 002 and not to be "fixed": the declined-camera-permission path
(plain snackbar, app still usable, one-tap recovery); diagnostics genuinely off by default, with a
description that held up under an adversarial reading; live percentage progress on every stage; the
thoroughness of About & Licenses; and the site's German/English switch. The novice flow completes
with no wrong turns — the Turkish-locale persona finished it without reading any English at all.

---

## 5. Release plumbing (Milestone 2, completed parts)

- [x] **REL-02. Privacy policy hosting** — live since 2026-09-21 at https://autokorrektur.org/privacy (`/privacy-en` in English), redeployed by `site/deploy.sh`. Pasting the URL into Play Console → App content is still a manual step and belongs to `REL-01`.
- [x] **REL-04. Direct APK download on autokorrektur.org** (since 2026-09-28) — `/download/` serves the signed `core` release APK (arm64-v8a) named with its versionName, plus `SHA256SUMS`. `site/build.sh` copies it out of `app/build/outputs/apk/core/release/` and **refuses to publish one that is stale, x86_64 (`-PscreenshotAbi`) or debug-signed**; `DOWNLOAD_VERSION`, `APK_BUILD_DATE` and `deploy.sh KEEP_DOWNLOAD=1` allow a pages-only deploy that leaves published bytes alone. The rule those guards exist for: **never republish different bytes under a name people have checksummed** — a new version gets a new filename, and the old one is deleted rather than overwritten.

---

## 6. Field testing on real hardware (Milestone 1, FT-01, 2026-10-08)

Three questions, all answered on a Pixel 10 Pro (Android 17) in one day. What was left of the
milestone, FT-02 and FT-03, tests `beta`/`full` features and moved to the deferred section of
`TODO.md`.

- [x] **FT-01a. The instrumented suite on arm64.** `connectedFullDebugAndroidTest` ran natively for
      the first time — TFLite and ONNX Runtime included, the 50-image pipeline benchmark, mask
      quality, YOLO, MI-GAN — with no ML or native failure. The one failure was a locked screen,
      fixed in `e24ef1d`; an emulator baseline failed a *different* GUI test, and both were
      assertions about the device dressed as assertions about the app. The options-menu blind spot
      behind `a7f59c5` is closed by `OverflowMenuSmokeTest` (`de9abdc`, `TESTING.md` §4).
      Device-run preconditions: `TESTING.md` §5.
- [x] **FT-01b. Real streets.** The owner's verdict after numerous field tests: the output is good
      enough to view on a phone and to *choose* by — to pick the shot worth handing to a graphics
      designer — and is not itself the finished image. That is `ARCHITECTURE.md` §4 exactly: MI-GAN
      generates at 512 px, so on a 2040 px photo the patch is a 4× upscale and a removed car can
      leave a soft or iridescent ghost at full size that is invisible at phone size. **Gate
      cleared:** the ghost shows on a minority of shots, so `core` goes public without the
      progressive path; `HIGH_RES_PROGRESSIVE` stays `plus`-and-up. The website says the same
      honest limit ("a preview, not a print-quality montage") since `6fa7081`/`8f6c882`.
- [x] **FT-01c. The release build on the phone.** `2.0.0-37-g8f6c882`, `core`, release-built and
      release-signed (`run-as` refused it as not debuggable). Both menu entries, the delete flow
      end to end, a street photo processed, no crash. First coverage of the **Compose dialogs on
      arm64, R8-minified**: About & Licenses opened without `UnknownFormatConversionException`, the
      regression behind `a7f59c5`; UX-11 and UX-01 held. Found by doing it: the phone runs dark
      mode and every emulator pass had been light, so the theme's dark branch rendered for the
      first time here — correct, by luck rather than coverage (now `UI-02` in `TODO.md`).
      Published as the byte-identical APK that ran on the phone (`3f41163`), not a rebuild: R8 is
      not reproducible, and a new version gets a new filename.
