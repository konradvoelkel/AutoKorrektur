# AutoKorrektur — Active Roadmap & Next Milestones

> **Current version**: `2.0.0-25-gddaed40`, published 2026-09-29 as a direct download on
> autokorrektur.org. Not on the Play Store yet — that is Milestone 2.  
> **Status**: the ML pipeline, the AR viewfinder, the video pipeline, progressive tile inpainting,
> the privacy posture and CI are all implemented and green, and two usability runs have been
> acted on in full (UX-01 to UX-25, archived). On 2026-10-08 the whole instrumented suite ran on
> real arm64 hardware for the first time — 114 tests on a Pixel 10 Pro, no ML or native failure —
> so the *technical* arm64 risk is retired. The owner's field tests the same day answered the
> other half: the output is good enough to view on a phone and to choose by — good enough to pick
> the shot worth handing to a graphics designer, not itself the finished image. **The remaining
> gap is FT-01c**: the *release* build has still never run on a phone. Migrating the UI to Jetpack
> Compose began the same day (decision 2026-10-08).  
> **Archive**: [docs/ARCHIVE_TODO.md](docs/ARCHIVE_TODO.md) — completed milestones M1–M8,
> Phases 1–4, the repository move, both usability runs, and the release plumbing that is done.

---

## 🎯 Active Milestones

### 🏙️ Milestone 1: Field testing on real hardware

What is automated here is now done; what is left needs a person and a street.

- [x] **FT-01a. The suite on real arm64 hardware.** Done 2026-10-08 on a Pixel 10 Pro (Android 17,
      1080x2410): `connectedFullDebugAndroidTest`, **114 tests, zero ML or native failures**, 10½
      minutes. TFLite and ONNX Runtime both executed natively for the first time — the 50-image
      pipeline benchmark (5 min), mask quality, YOLO, MI-GAN, all green. The single failure was
      environmental (a locked screen) and is fixed in `e24ef1d`; an emulator baseline of the same
      114 tests failed a *different* GUI test, and both were assertions about the device wearing
      the costume of assertions about the app. The menu blind spot that produced `a7f59c5` is
      closed too (`de9abdc`, `TESTING.md` §4), and that suite passes on the phone.
      Device-run preconditions: `TESTING.md` §5.
- [x] **FT-01b. Walk real streets.** Done by the owner over numerous field tests (reported
      2026-10-08). **Verdict: it works well enough, with a specific shape worth keeping straight.**
      The output is good enough to view on a phone and to *choose* by — good enough to decide which
      shot is worth taking to a graphics designer for a high-quality version. It is not itself the
      high-quality version. That matches `ARCHITECTURE.md` §4 exactly: MI-GAN always generates at
      512 px, so on a 2040 px photo the patch is a 4x upscale and a removed car can leave a soft or
      iridescent ghost while the untouched background stays sharp — visible at full size, not at
      phone size.
  - **A gate this clears:** the ghost shows on a minority of shots, not most, so `core` does **not**
    need the progressive path before going public. That was the condition on this line and it is
    answered; `HIGH_RES_PROGRESSIVE` stays a `plus`-and-up feature rather than a release blocker.
  - **Still owed:** the website's "early test build" note was written partly because this item was
    open. Rewrite it — do not delete it — to say what the owner found, including the honest limit
    (phone-viewing and selection, not a finished high-quality image). Outward-facing, so it is a
    deliberate edit plus a `site/deploy.sh` run.
- [ ] **FT-01c. Smoke-test the *release* build on the phone.** `RELEASE_CHECKLIST.md` §5 asks for
      `:app:installCoreRelease` on physical hardware. FT-01a used the **full debug** build, which
      is a different flavor *and* a different build type: R8, the `core` feature flags and release
      signing are all untested on arm64. Cheap to do and it belongs before REL-03.
- [ ] **FT-02. Batch telemetry and CSV metric collection.** Run multi-photo batches across varied
      lighting and export execution CSVs. Since 2026-09-21 every flavor (`core` included) also has
      opt-in on-device diagnostics: menu → Diagnostics → switch on, use the app, Export. Off by
      default; `PRIVACY_POLICY.md` §5.
- [ ] **FT-03. Social-media split export trials.** Split cards, 4:5 carousels and animated sweep
      MP4s on real street photos, to verify Instagram readiness.

### 🚀 Milestone 2: Google Play Store release

- [ ] **REL-01. Play Console listing setup.** Paste the prepared German and English metadata from
      [docs/PLAY_STORE_LISTING.md](docs/PLAY_STORE_LISTING.md), and the privacy-policy URL into
      Play Console → App content.
- [ ] **REL-03. Release app bundle.** `./gradlew bundleCoreRelease` (`core` is the Play flavor),
      upload to Internal Testing.
  - **Decide on Play App Signing before the first upload.** If Play generates its own app signing
    key, the Play build and the APK on autokorrektur.org carry different signatures, and anyone who
    sideloaded must uninstall before they can take a Play update. Uploading the existing release
    key (`~/.android-signing/autokorrektur`, `keystore.properties`) as the app signing key keeps
    the two interchangeable. The certificate fingerprint is printed on the website, so it is a
    promise to users either way.

### 🏷️ Milestone 0: repository move — one item left

- [ ] **REPO-05. Ask GitHub Support to garbage-collect the fork network.** Pre-rewrite objects are
      still reachable by SHA (`.../commit/<old-sha>`), so `TODO-for-human.md` and
      `HUMAN_RELEASE_CHECKLIST.md` can still be fetched from old commits. Only a support-side GC —
      or a fresh repository pushed from the rewritten history — removes them. Archiving or deleting
      `xamde/AutoKorrektur` does **not**: the objects live in the shared network pool.

---

### ☁️ Milestone 3: Optional cloud inpainting (deferred)

The SDXL service lives in [konradvoelkel/autokorrektur-backend](https://github.com/konradvoelkel/autokorrektur-backend)
and is not part of any published build. Reviving it means, in this repo: point `BACKEND_URL`
(release build type) at the live host, and update the privacy policy, the Data Safety answers and
the listing copy **first** — all three currently state that the published app has no network access.
