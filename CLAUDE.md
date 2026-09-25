# Working in this repository

AutoKorrektur 2.0 removes parked cars from photographs, with all machine learning running on the
phone. Kotlin/Android, one module (`:app`). The 1.0 in the name is [Benjamin Beckers' browser
version](https://github.com/BenB2/AutoKorrektur); this is the native rewrite.

Canonical repository: **konradvoelkel/AutoKorrektur**. (`xamde/AutoKorrektur` is the retired fork it
was forked from — don't push there.)

Lessons shared with the sibling projects (Laberampel, bubatzblick, hAInrich) live in
`~/files/work/PLAYBOOK.md`, outside this repo: its §4 row for AutoKorrektur is the audit checklist,
§7 lists what is stale here right now, and a lesson learned here that is not AutoKorrektur-specific
goes there the same day.

## Five things that will bite you

1. **The models and test fixtures are not in git.** Run `scripts/fetch_assets.sh` after cloning; it
   downloads them from the `assets-v1` release and verifies every SHA-256 against
   `scripts/assets.manifest`. Gradle's `verifyAssets` task fails the build with that instruction if
   anything is missing. Never `git add` a model or a fixture — publish it to a new `assets-vN`
   release and add a manifest line in the same commit.
2. **Gradle tasks need a flavor prefix.** There are four product flavors (`core`/`plus`/`beta`/`full`,
   see `docs/PRODUCT_TIERS.md`), so bare `testDebugUnitTest` does not resolve. Use
   `:app:testFullDebugUnitTest`, `:app:connectedFullDebugAndroidTest`, `:app:bundleCoreRelease`.
   `full` is the development baseline (every feature on); `core` is what goes to the Play Store.
3. **`core` has no `INTERNET` permission**, and the privacy policy, the Play listing and the Data
   Safety answers all say so. Anything that adds a network call to `core` — or promotes the cloud
   tier into it — means changing those texts *first*. Permissions also arrive uninvited: a library
   merges its own into every flavor (androidx.work contributed four, for a feature `core` disables),
   Play prints the merged set on the store page, and `app/src/core/AndroidManifest.xml` removes them
   one by one. Read the merged manifest, not the source one, before a release:
   `app/build/intermediates/merged_manifests/coreRelease/processCoreReleaseManifest/AndroidManifest.xml`.
4. **German and English are both complete, and a test enforces it.** `values/strings.xml` is English
   and the fallback for every locale; `values-de/` is a complete override; there is no `values-en/`.
   `StringResourceLocalizationTest` checks key parity, placeholder parity and that no German entry is
   a copy of the English one. User-visible text belongs in resources — including text drawn onto
   bitmaps (that bug shipped once: the exported image said "VORHER/AUTOFREI" on English devices).
5. **Colours are derived, never picked.** One hue for the project (55°, orange); every tone is
   `oklch(L C 55)` with only L and C moved — see the header of `app/src/main/res/values/colors.xml`.
   If a tone doesn't fit, move L/C for the whole set rather than patching one hex.

## Layout

| Where | What |
|---|---|
| `app/src/main/java/.../pipeline/` | `StaticImagePipeline` — load → segment (YOLO) → inpaint (MI-GAN) |
| `app/src/main/java/.../ml/` | engines, mask maths, pre/post-processing, the optional server client |
| `app/src/main/java/.../ar/` | live AR viewfinder (`full` only) |
| `app/src/main/java/.../telemetry/` | opt-in, on-device diagnostics; never transmits |
| `scripts/` | `fetch_assets.sh` + `assets.manifest` |
| `site/` | autokorrektur.org — `build.sh` renders the privacy policy, `deploy.sh` ships it |

Documentation map: `docs/INDEX.md`. Architecture and the mask-polarity contract: `ARCHITECTURE.md`.
Test suites and invariants: `TESTING.md`. Roadmap: `TODO.md`.

## Conventions

- Verify rather than assume: run the tests, read the generated artifact, check the emulator screen.
  Several bugs in this repo's history were invisible in code review and obvious in a screenshot.
- Emulators are x86_64 and translate arm64; TFLite segfaults under translation. Use
  `-PscreenshotAbi=x86_64` for emulator work, and never pass it when building a release bundle.
- Keep documentation short and factual. Timings, test counts and dates rot; invariants don't.
- Some files are deliberately gitignored and local-only: `BRANDING.md`, `TODO-for-human.md`,
  `HUMAN_RELEASE_CHECKLIST.md`, `keystore.properties`, `site/deploy.local.env`. Don't recreate them
  in the repo.
