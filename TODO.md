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
Fahrzeuge erkannt" with the actions still disabled. The site half is deployed too — verified on
2026-09-28 during usability run 002, when the live `index.html` and `en.html` were found
byte-identical to `site/dist/`. UX-10 was owner/infra and is now done in the server repo (below).

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
- [x] **UX-10. `https://autokorrektur.org/download/` returned 404.** Nothing links to the bare
      directory, so only a URL-trimming visitor met it — which is exactly the verification-minded
      reader. Owner/infra, so it was fixed where Caddy lives: `~/files/work/server`, role
      `autokorrektur`, tracked and done there as **B13** (`ef3be16`, 2026-09-29) — `browse` on a
      matcher for exactly `/download` and `/download/`, deliberately not `/download/*`, so the one
      intentionally public directory lists itself and no other path does. Verified live on
      2026-09-29: `/download/` returns 200 and lists the APK, `SHA256SUMS` and `apache-2.0.txt`;
      `/download` 308-redirects to it; `/icons/` still 404s. Nothing left to do in this repo. (Low)

### 🧭 Milestone 1c: Usability run 002
Five fresh personas on 2026-09-28 — two re-walking the paths UX-01 to UX-09 touched, three on ground
run 001 never reached (the camera path, diagnostics end to end, a Turkish-locale device). Ranked in
`reports/improvements-002.adoc`; scenarios `frank`, `grace`, `heidi`, `ivan`, `judy`.

**UX-01 to UX-09 all hold.** Re-confirmed blind: each persona was denied access to this file and to
the scenarios' own regression notes, so nothing told them what had changed. Findings discarded as
test artefacts — including a Critical "diagnostics delete is a no-op" that turned out to be a
mis-aimed tap — are listed in that report and are not repeated here.

The theme is narrower than run 001's: *the app states an outcome but not its consequence*. UX-11,
UX-13, UX-15 and UX-16 are all that shape, and all four are wording or one-line-of-feedback fixes.

**UX-11 to UX-25 were implemented on 2026-09-29** and verified on an x86_64 emulator (`core`, both
languages) — 93 unit tests green, `verifyCoreDebugPermissions` / `verifyFullDebugPermissions` green.
Verified by hand on the device, not just in code: diagnostics deleted leaves `0 events · 0.0 KB`,
no install-ID line, `telemetry_enabled=false` and no `events.jsonl` on disk; the event list reads
"Sep 29, 11:01 — Photo processed: 1 vehicle found, 1.3 s"; a car-free photo records
`detections=0, inpaint_ms=3` (a street photo's was 1090) and shows the unchanged image with no
slider and the line "Nothing was changed, so there is nothing to save."; the camera rationale
appears on the first ask only; and with the device in English but the app in German the overflow
icon is described "Weitere Optionen" and dialogs say "Abbrechen".

Two things found while fixing, both now handled and worth remembering: the diagnostics dialog grew
taller than a 720x1280 screen once the event list was added, which pushed its own **Delete button
off the bottom** — the body is now a height-capped ScrollView and Delete moved out of the button
row into the body. And `site/dist/` is gitignored and was rebuilt from source here, so the pages
carry UX-15/UX-22/UX-25 while the published APK stays byte-identical
(`accc64b0…`, unchanged — never republish different bytes under a checksummed name).

**Not yet deployed:** the site half of this batch reaches users only at the next `site/deploy.sh`
run. The app half reaches users only at the next release build.

- [x] **UX-11. Deleting diagnostics data looks like it silently failed.** Delete works — but the
      switch stays on, so `Telemetry.clear()` immediately mints a new install ID and writes a new
      `session_start` (`telemetry/Telemetry.kt:101-112`, deliberate). Reopening the dialog shows
      "1 event · ID 8d49ed78…" seconds after confirming "Delete all recorded diagnostics and the
      installation ID?". Either turn the switch off as part of deleting, or say what happened. (High)
- [x] **UX-12. Diagnostics data cannot be read without exporting it to another app.** The dialog
      offers only a count, a size and a truncated ID; Export opens a share sheet with no
      "open"/"view" target, and the file is `.jsonl`. Add a plain in-app list of the events in human
      words and local time. (High)
- [x] **UX-13. After "no vehicles detected", Download and Share are dead with no explanation.**
      Tapping the disabled buttons gives no feedback at all. The camera persona, who had just taken
      the photo herself, could not save it and could not learn why; the gallery persona met the same
      state and read it as correct. Explain them — do not re-enable them. (Medium)
- [x] **UX-14. Dialog buttons come from the system, so they change language independently.** On a
      Turkish phone the About dialog is all English but for "Tamam"; Diagnostics shows "İptal" beside
      "Delete" and "Export". Four call sites use `android.R.string.ok`/`cancel` —
      `MainActivity.kt:68`, `ui/diagnostics/DiagnosticsDialog.kt:38` and `:82`,
      `ui/delegate/BatchUiDelegate.kt:38`. `btn_delete` is already app-owned, so add `btn_ok` and
      `btn_cancel` to `values/` and `values-de/`. (Medium)
- [x] **UX-15. "Was das heißt" lands on the install heading, not its own explanation.** Both
      languages: `site/index.html` line 29 links to `#android` (the `<h2>` at line 48) while the
      explanation is the `<p class="note">` at line 57; `site/en.html` the same at 27/46/55. The
      paragraph has no `id`. Add `id="testbuild"` and point the link at it. `#android` is also
      overloaded — the nav download link shares the target. Needs `site/deploy.sh`. (Medium)
- [x] **UX-16. The diagnostics description undersells what is recorded.** It names "compute times,
      image sizes, modes, detection counts, error types and your device model"; the file also carries
      manufacturer, RAM, core count, locale, app version, version code, build flavor and build type.
      Every one is defensible — omitting them from the list is not. (Medium)
- [x] **UX-17. "100% On-Device AI processing without data collection" sits against diagnostics.**
      Nothing is untrue, but the unqualified phrasing in About invites the objection once a reader
      finds the diagnostics screen. "All processing happens on your device. Nothing is uploaded."
      survives contact with it and is the stronger claim anyway. (Medium)
- [x] **UX-18. Nothing explains why the app wants the camera before Android asks.** "Take Photo"
      goes straight to the stock permission dialog; a user wary of a non-Play-Store install has only
      the OS's generic wording. One line of rationale on the first ask. Note the *decline* path is
      already handled well — see below. (Low)
- [x] **UX-19. The before/after captions disappear on a no-detection result.** Each badge is drawn
      only when the divider is >40dp from its side (`ui/BeforeAfterSliderView.kt:270-281`), so both
      vanish when it sits at an edge. Check the divider's initial position on that path, or hide the
      slider when there is nothing to compare. No localization risk — the badges use
      `badge_before`/`badge_after`. (Low)
- [x] **UX-20. Inpainting still runs when the detection mask is empty.** A car-free photo advances
      through an "Inpainting auf dem Gerät" stage doing work on an empty mask. Skip the stage and its
      progress label. Developer-facing; it earns a line only because the label claims work with no
      purpose. (Low)
- [x] **UX-21. The install ID is truncated with no way to see or copy it.** Shown as "570a9891…";
      the full value appears only inside the exported `.jsonl`. It is a random UUID, so there is
      nothing to protect by shortening it — make the line long-pressable or show it in full. (Low)
- [x] **UX-22. The English contact address must be decoded and retyped.** "kontakt [at]
      autokorrektur [punkt] org" is not a `mailto:`, and "punkt" is German. Residue of the UX-07 fix,
      not a regression. Use "[dot]" on the English page, or put a real `mailto:` behind the
      obfuscated text. (Low)
- [x] **UX-23. Switching diagnostics on is itself the first thing it records.** The count reads
      "1 event" before the user has done anything. Arguably correct; no code change needed if UX-12
      is done, since an in-app view would show "Diagnostics switched on" as the first line. (Low)
- [x] **UX-24. The overflow menu is an unlabelled icon whose description follows the device.** On a
      Turkish phone its only name is "Diğer seçenekler" while the menu it opens is English. Standard
      Android, recorded because both features a cautious user hunts for (UX-11, UX-12) live behind
      it. Supply the content description from the app's own strings. (Low)
- [x] **UX-25. "4 GB RAM empfehlenswert" gives the reader no way to check.** Add where to look
      ("Einstellungen → Über das Telefon"), or drop the number and keep the consequence: older
      phones take longer. Ranked last — the soft phrasing kept it from blocking anyone. (Low)

Worth preserving, confirmed by this run and not to be "fixed": the declined-camera-permission path
(plain snackbar, app still usable, one-tap recovery); diagnostics genuinely off by default with a
description that held up under adversarial reading; live percentage progress on every stage; the
thoroughness of About & Licenses; and the site's German/English switch. The novice flow now completes
with no wrong turns — the Turkish-locale persona finished it without reading any English at all.

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
