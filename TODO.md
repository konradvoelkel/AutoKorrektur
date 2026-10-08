# AutoKorrektur — Roadmap

> **Current version**: `2.0.0-37-g8f6c882`, published 2026-10-08 as a direct download on
> autokorrektur.org; the APK served is the one that was smoke-tested on the phone. Not on the Play
> Store yet — that is Milestone 2.  
> **Archive**: [docs/ARCHIVE_TODO.md](docs/ARCHIVE_TODO.md) — everything finished, including the
> 1.0 milestones, the repository move, both usability runs, the release plumbing that is done and
> the field testing on real hardware (FT-01).

---

## Active

### Milestone 4: UI migration to Jetpack Compose (decision 2026-10-08)

Done so far: the theme (`AutoKorrekturTheme`, reading the derived palette from `values/colors.xml`)
and the two global dialogs, About & Licenses and Diagnostics, hosted by a zero-sized `ComposeView`
in `activity_main.xml` (`82a30a7`). `OverflowMenuSmokeTest` is half Espresso, half Compose, and
stays green through the interop.

- [ ] **UI-01. Migrate the remaining `core` screens.** Still Views: the toolbar and navigation host
      in `MainActivity`, `FirstFragment` (capture → result → share, the whole critical path), the
      vision-gallery sheet and the export sheet. Surfaces that exist only in `beta`/`full` — AR
      viewfinder, video preview, mask brush, consent dialog — move last, or when they are touched
      anyway. Rules learned on the first two steps: read strings with `stringResource` at
      composition time, never through `LocalContext` in a callback — the app switches language at
      runtime and that is a configuration change (`e90d311`); keep the usability-run behaviour
      (UX-01, UX-11, UX-12, UX-14, UX-21) under test while moving it; run the lint task CI runs
      (`lintFullDebug`), not a related one.
- [ ] **UI-02. Light/dark, locale and width are an untested axis.** The theme's dark branch first
      rendered on the owner's phone, by luck. Paparazzi golden screenshots across theme × locale ×
      width, as proposed in `TESTING.md` §7, plus `AccessibilityChecks` on the Espresso suite.

### Milestone 2: Google Play Store release

- [ ] **REL-01. Play Console listing setup.** Paste the prepared German and English metadata from
      [docs/PLAY_STORE_LISTING.md](docs/PLAY_STORE_LISTING.md), and the privacy-policy URL into
      Play Console → App content.
- [ ] **REL-03. Release app bundle.** `./gradlew :app:bundleCoreRelease` (`core` is the Play
      flavor), upload to Internal Testing.
  - **Decide on Play App Signing before the first upload.** If Play generates its own app signing
    key, the Play build and the APK on autokorrektur.org carry different signatures, and anyone who
    sideloaded must uninstall before they can take a Play update. Uploading the existing release
    key (`~/.android-signing/autokorrektur`, `keystore.properties`) as the app signing key keeps
    the two interchangeable. The certificate fingerprint is printed on the website, so it is a
    promise to users either way.

### Milestone 0: repository move — one item left

- [ ] **REPO-05. Ask GitHub Support to garbage-collect the fork network.** Pre-rewrite objects are
      still reachable by SHA (`.../commit/<old-sha>`), so `TODO-for-human.md` and
      `HUMAN_RELEASE_CHECKLIST.md` can still be fetched from old commits. Only a support-side GC —
      or a fresh repository pushed from the rewritten history — removes them. Archiving or deleting
      `xamde/AutoKorrektur` does **not**: the objects live in the shared network pool.

---

## Deferred

None of this is in `core`, so none of it blocks Milestone 2.

- **Milestone 3: optional cloud inpainting.** The SDXL service lives in
  [konradvoelkel/autokorrektur-backend](https://github.com/konradvoelkel/autokorrektur-backend) and
  is not part of any published build. Reviving it means, in this repo: point `BACKEND_URL` (release
  build type) at the live host, and update the privacy policy, the Data Safety answers and the
  listing copy **first** — all three currently state that the published app has no network access.
- **FT-02. Batch telemetry and CSV metric collection** (`beta`/`full`, `FEATURE_BATCH_PROCESSING`):
  multi-photo batches across varied lighting, execution CSVs exported. `core` has the opt-in
  on-device diagnostics instead (menu → Diagnostics; `PRIVACY_POLICY.md` §5).
- **FT-03. Social-media export trials** (`plus`-and-up, `FEATURE_EXTRA_EXPORT_LAYOUTS`): 4:5
  carousels and animated sweep MP4s on real street photos. `core` ships the single
  split card, which the field tests already covered.
