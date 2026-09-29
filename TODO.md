# AutoKorrektur — Active Roadmap & Next Milestones

> **Current version**: `2.0.0-25-gddaed40`, published 2026-09-29 as a direct download on
> autokorrektur.org. Not on the Play Store yet — that is Milestone 2.  
> **Status**: the ML pipeline, the AR viewfinder, the video pipeline, progressive tile inpainting,
> the privacy posture and CI are all implemented and green, and two usability runs have been
> acted on in full (UX-01 to UX-25, archived). What is still unproven is on-device behaviour:
> the first arm64 hardware run was 2026-09-28 and it found a crash within a minute
> (`a7f59c5`, "About & Licenses"). **FT-01 is the one gap that matters.**  
> **Archive**: [docs/ARCHIVE_TODO.md](docs/ARCHIVE_TODO.md) — completed milestones M1–M8,
> Phases 1–4, the repository move, both usability runs, and the release plumbing that is done.

---

## 🎯 Active Milestones

### 🏙️ Milestone 1: Field testing on real hardware

The whole of this milestone is the same point: almost everything here has only ever run in an
x86_64 emulator.

- [ ] **FT-01. Physical field testing on device.**
  - Walk real urban environments (residential street, commercial parking, mixed bike/pedestrian
    zones), following [docs/FIELD_TESTING_AND_DATA_COLLECTION.md](docs/FIELD_TESTING_AND_DATA_COLLECTION.md).
  - **The known blind spot:** no automated test opens the options menu at all, so the overflow
    menu, the diagnostics dialog and About & Licenses are untested surface. That is exactly where
    the one real-hardware run so far broke — `about_dialog_content` had an unescaped `%` and the
    app died on the first menu item opened (`a7f59c5`). An instrumented smoke test that opens
    every menu entry would have caught it before the website did, and would be worth more than
    another manual pass.
  - Judge inpainting quality at full size, not on a phone screen: the generated patch is a 512 px
    upscale (`ARCHITECTURE.md` §4) and can leave a visible ghost. If that shows on most shots
    rather than a minority, `core` needs the progressive path before it goes public.
  - The website calls this an early test build *because* this item is open. Delete that note once
    a phone has genuinely run it.
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
