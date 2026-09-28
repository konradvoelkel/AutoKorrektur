# AutoKorrektur — Active Roadmap & Next Milestones

> **Current Version**: 2.0.0 — tagged `v2.0.0`, not yet published.  
> **Status**: the ML pipeline, the AR viewfinder, the video pipeline, progressive tile inpainting,
> the privacy posture and CI are all implemented and green. What is unproven is on-device behaviour:
> the first arm64 hardware run happened on 2026-09-28 and found a crash within a minute
> (`a7f59c5`, "About & Licenses"); the walkthrough itself is still open (FT-01).  
> **Historical Archive**: See [docs/ARCHIVE_TODO.md](docs/ARCHIVE_TODO.md) for completed milestones M1–M8 and Phases 1–4.

---

## 🎯 Active Milestones

### 🏷️ Milestone 0: AutoKorrektur 2.0 and the move to an own repository
The [web version by Benjamin Beckers](https://github.com/BenB2/AutoKorrektur) is **AutoKorrektur 1.0**;
this native Android rewrite is **AutoKorrektur 2.0**.
- [x] **REPO-01. Slim the repository** — binaries, owner-only notes and the cloud backend are out;
      a clone is ~13 MB, the tracked tree under 3 MB (2026-09-24).
- [x] **REPO-02. Rebrand to 2.0** — README lineage, changelog headings, version fallback, store
      listing and website (2026-09-24).
- [x] **REPO-03. Canonical repository is `konradvoelkel/AutoKorrektur`** (2026-09-24). It is a fork
      of `xamde/AutoKorrektur`, which is no longer maintained and receives no further pushes — the
      repo was already a fork of [BenB2/AutoKorrektur](https://github.com/BenB2/AutoKorrektur) (the
      1.0 browser version), so the fork marker costs nothing new. A fresh, network-detached
      repository stays an option for a later major version.

- [x] **REPO-04. Actions enabled** on `konradvoelkel/AutoKorrektur` — lint, unit tests, the
      instrumented emulator suite and the release bundle all run there (2026-09-24).
- [ ] **REPO-05. Ask GitHub Support to garbage-collect the fork network.** The pre-rewrite objects
      are still reachable by SHA (`.../commit/<old-sha>`), so `TODO-for-human.md` and
      `HUMAN_RELEASE_CHECKLIST.md` can still be fetched from old commits. Only a support-side GC (or
      a fresh repository pushed from the rewritten history) removes them. Archiving or deleting
      `xamde/AutoKorrektur` does not: the objects live in the shared network pool.

### 🏙️ Milestone 1: Field Testing & Data Collection
- [ ] **FT-01. Physical Field Testing on Device**
  - Walk through real urban environments (residential street, commercial parking, mixed bike/pedestrian zones).
  - Execute the test scenarios in [docs/FIELD_TESTING_AND_DATA_COLLECTION.md](docs/FIELD_TESTING_AND_DATA_COLLECTION.md) (the owner also keeps a private, device-specific walkthrough outside this repo).
  - First real-hardware run: 2026-09-28, on the published APK. It crashed on the first menu item
    opened — `about_dialog_content` had an unescaped `%` (`a7f59c5`). Nothing else has been
    exercised on arm64 yet, and no automated test opens the options menu at all: the overflow
    menu, the diagnostics dialog and the settings screens are untested surface. An instrumented
    smoke test that opens every menu entry would have caught this before the website did.
  - Every other run is an x86_64 emulator. Judge
    inpainting quality at full size, not on a phone screen — the generated patch is a 512 px
    upscale (`ARCHITECTURE.md` §4) and can leave a visible ghost. If that shows up on most shots
    rather than a minority, `core` needs the progressive path before it goes public.
- [ ] **FT-02. Batch Telemetry & CSV Metric Collection**
  - Run multi-photo batch processing across varied lighting conditions and export execution CSVs for performance review.
  - Since 2026-09-21 every flavor (incl. `core`) also has opt-in on-device diagnostics: menu → Diagnostics → switch on, use the app, Export (share sheet) → `autokorrektur-diagnostics-<date>.jsonl` with per-stage timings, AR fps, export durations and crash lines. Off by default; see `PRIVACY_POLICY.md` §5.
- [ ] **FT-03. Social Media Split Export Trials**
  - Generate split cards, 4:5 carousels, and animated sweep MP4s on real street photos to verify Instagram readiness.

---

### 🧭 Milestone 1b: Usability run 001
Five personas walked the live site and the `core` build on 2026-09-28 (`scenarios/`, `reports/`,
ranked in `reports/improvements-001.adoc`). Findings that were artefacts of the test environment
are listed in that report and are not repeated here. The site's stale "no phone has ever run it"
claim was the eleventh finding and is already fixed (`322f984`, deployed).

UX-01 to UX-09 were implemented on 2026-09-28 and verified on an x86_64 emulator (`core`, German
and English): a fresh launch shows both result actions disabled, a processed street photo opens on
the before/after slider with the mask preview below it, and a car-free photo is captioned "Keine
Fahrzeuge erkannt" with the actions still disabled. The site half is committed but only reaches
users at the next `site/deploy.sh` run. UX-10 is owner/infra and stays open.

- [x] **UX-01. The result screen leads with the mask preview.** After processing, the red
      "Erkannte Fahrzeuge (Masken-Vorschau)" overlay fills the first screenful and the before/after
      slider needs a scroll at 720x1280 — `imagesContainer` sits above `beforeAfterSliderView` in
      `fragment_first.xml`. The novice persona took the mask *for* the result and shared it.
      Show the comparison first and demote the diagnostic view below it. (High)
- [x] **UX-02. Zero detections produce no message.** When YOLO finds no vehicles the app shows the
      unchanged photo captioned "Erkannte Fahrzeuge", with no toast and no empty state — the
      caption asserts the opposite of what happened. `PipelineResult` does not carry the detection
      count, so the UI cannot tell this case apart from a successful run. (High)
- [x] **UX-03. Download and export are offered before there is anything to export.** On first
      launch both are filled primary buttons, visually louder than "Foto aufnehmen" and "Bild
      auswählen"; tapping either only produces an error snackbar. Disable until a result exists. (Medium)
- [x] **UX-04. "Für Instagram exportieren" is the only share control.** It opens the ordinary
      Android share sheet, and there is no button labelled "Teilen" anywhere. The persona who
      wanted to send the picture to a WhatsApp group had to guess. (Medium)
- [x] **UX-05. The site's risk warning sits 4.8 screens below the download button** at 375 px
      (page is 6 screens; install instructions start at 3.6). The rushed persona who taps download
      never reaches it. One line next to the button. (High)
- [x] **UX-06. The route to the install help is labelled for experts** — "Installieren & prüfen /
      Sideloading, Prüfsumme, Zertifikat". It is above the fold, so placement is fine; the persona
      who needed it read the subtitle as technical and dismissed it. (Medium)
- [x] **UX-07. The English path ends in a German-only Impressum.** Both English pages send the
      reader there for contact details, and `privacy-en` declares the German text binding. Print
      the responsible person and the contact address on the English page. (Medium)
- [x] **UX-08. Verification instructions assume tools they do not supply** — the page names
      `apksigner verify --print-certs` without saying where `apksigner` comes from, and never says
      how to compute a SHA-256. (Low)
- [x] **UX-09. "Ein Vorher/Nachher-Bild zum Teilen in zwei Tipps" overstates the flow** — measured
      at four taps plus a scroll, on an already-picked photo. (Low)
- [ ] **UX-10. `https://autokorrektur.org/download/` returns 404.** Nothing links to the bare
      directory, so only a URL-trimming visitor meets it — which is exactly the verification-minded
      reader. Owner/infra: Caddy lives in `~/files/work/server` (role `autokorrektur`), not here. (Low)

### 🚀 Milestone 2: Google Play Store Release
- [ ] **REL-01. Google Play Console Listing Setup**
  - Paste prepared German & English metadata from [docs/PLAY_STORE_LISTING.md](docs/PLAY_STORE_LISTING.md).
- [x] **REL-02. Privacy Policy Hosting** — live since 2026-09-21 at https://autokorrektur.org/privacy (`/privacy-en` English); redeploy with `site/deploy.sh`. Still to do by hand: paste the URL into Play Console → App content.
- [ ] **REL-03. Release App Bundle Generation**
  - Build signed `.aab` bundle via `./gradlew bundleCoreRelease` (`core` is the Play Store flavor) and upload to Play Console Internal Testing track.
  - Decide on **Play App Signing** before the first upload. If Play generates its own app signing key,
    the Play build and the APK on autokorrektur.org (REL-04) carry different signatures, and anyone
    who sideloaded has to uninstall before they can take a Play update. Uploading the existing
    release key (`~/.android-signing/autokorrektur`, `keystore.properties`) as the app signing key
    keeps the two interchangeable. The certificate fingerprint is printed on the website, so it is
    a promise to users either way.
- [x] **REL-04. Direct APK download on autokorrektur.org** (2026-09-28) — `/download/` serves the
      signed `core` release APK (arm64-v8a) named with the versionName, plus `SHA256SUMS`;
      `site/build.sh` copies it out of `app/build/outputs/apk/core/release/` and refuses to publish
      one that is stale, x86_64 (`-PscreenshotAbi`) or debug-signed. `DOWNLOAD_VERSION`,
      `APK_BUILD_DATE` and `deploy.sh KEEP_DOWNLOAD=1` allow a pages-only deploy that leaves the
      published bytes alone. Live since 2026-09-28: https://autokorrektur.org/#android serves
      `2.0.0-15-ga7f59c5` (the first published build, `2.0.0-13-g662dacd`, was up for about an
      hour and crashed on About & Licenses; it is deleted from the server). The page calls it an early test build because FT-01 is still open —
      delete that note once a phone has actually run it.

---

### ☁️ Milestone 3: Optional cloud inpainting (deferred)
The SDXL service lives in [konradvoelkel/autokorrektur-backend](https://github.com/konradvoelkel/autokorrektur-backend)
and is not part of any published build. Reviving it means, in this repo: point `BACKEND_URL`
(release build type) at the live host, and update the privacy policy, the Data Safety answers and
the listing copy **first** — all three currently state that the published app has no network access.
