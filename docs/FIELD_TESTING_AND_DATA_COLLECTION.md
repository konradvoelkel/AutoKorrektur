# 🏙️ Field Testing, Data Collection & Evaluation Protocol

This guide outlines the protocol for executing real-world field tests with **AutoKorrektur**, collecting empirical datasets across varied urban environments, recording diagnostic performance telemetry, and evaluating inpainting fidelity for public advocacy and scientific analysis.

---

## 1. What to test, per flavor

Which build you carry decides what you can test (`docs/PRODUCT_TIERS.md`):

- **`core`** — the Play Store build and the one that matters most: photo in, cars out, before/after
  slider, split card shared from the share sheet. Test it in real light, on real streets, with real
  parked cars: glare, deep shadows, wet asphalt, snow, dense clusters, delivery vans, bicycles and
  people that must *not* be erased.
- **`full`** — additionally the live AR viewfinder (stability while walking, 30–60 FPS), the 5-second
  AR video snippets (temporal stability), high-res progressive inpainting on complex corners, the
  mask brush, and batch mode with CSV export.

Install one with `./gradlew :app:installCoreDebug` or `installFullDebug`.

---

## 2. Collecting data while testing

### A. On-device diagnostics (the main instrument)
Menu → **Diagnostics** → switch on. From then on the app appends one line per run to a private file:
per-stage timings, image size, number of detected vehicles, outcome or error class, AR frame rate,
and once per session the device model, RAM, cores and Android version — never images, file names or
locations (`PRIVACY_POLICY.md` §5). It is off by default and nothing is transmitted.

After a session, **Export** hands the file to the share sheet (mail it to yourself), or pull it:

```bash
adb shell run-as de.konradvoelkel.android.autokorrektur cat files/telemetry/events.jsonl
```

Each line is JSON, so the whole session aggregates with one command:

```bash
jq -s '[.[] | select(.event=="pipeline_run")] | {runs: length,
        median_ms: (map(.total_ms) | sort | .[length/2 | floor]),
        failures: [.[] | select(.success==false) | .error] | length}' events.jsonl
```

**Delete** clears the file and the installation id when you are done.

### B. Batch CSV (`beta`/`full` only)
Batch mode writes `filename,model,scoreThreshold,maskUpscale,maskDownshift,downscaleMp,inferenceTimeMs,timestamp`
to `Downloads/` via "CSV exportieren" — useful for sweeping parameters over a folder of photos,
where the diagnostics file gives you the per-stage breakdown.

### C. Artifacts and logs

```bash
adb logcat -s AutoKorrektur:* AndroidRuntime:*          # live app log
adb pull /sdcard/Pictures/ ./field_test_data/photos/     # saved results
adb shell dumpsys meminfo de.konradvoelkel.android.autokorrektur   # memory during inpainting
```

AR video clips (`full`) land in `Movies/AutoKorrektur/`.

---

## 3. Systematic 5-Criteria Evaluation Framework

Following the methodology established by Schellscheidt (2024) and Beckers (2025), score each test capture on a 1–5 scale:

| Criterion | Aspect Evaluated | Target Score |
|---|---|:---:|
| **1. Instanzsegmentierung** | Did YOLOv11 detect 100% of vehicles without cutoffs or missing cars? | $\ge 4.5$ |
| **2. Realismus** | Are generated road textures, paving stones, and greenery plausible? | $\ge 3.5$ |
| **3. Konsistenz (Seams)** | Is the boundary transition between untouched background and inpaint seamless? | $\ge 4.0$ |
| **4. Natürlichkeit** | Does the overall scene look like a genuine, believable photo? | $\ge 3.5$ |
| **5. Geschwindigkeit** | Did on-device processing complete within acceptable time? | Fast: $<1\text{s}$<br>High-Res: $<6\text{s}$ |

---

## 4. Field Testing Checklist for Activists & Researchers

- [ ] **Pre-Trip Check**:
  - Phone battery $\ge 70\%$, storage $\ge 2\text{ GB}$.
  - Current build installed, and **Diagnostics switched on** (menu → Diagnostics) so the trip is measured.
  - Know which flavor you carry: `core` has no AR, video or batch mode.
- [ ] **In the Field**:
  - Test 1: Typical residential street with parked cars along sidewalk.
  - Test 2: Multi-vehicle cluster (commercial street or parking lot).
  - Test 3: Mixed active mobility scene (cars parked next to parked bicycles / pedestrians).
  - Test 4: Dynamic lighting (bright sunlight vs deep tree canopy shadows).
  - Test 5 (`full` only): 5-second AR video snippet while walking slowly along the sidewalk.
- [ ] **Post-Trip Evaluation**:
  - Export the diagnostics file (menu → Diagnostics → Export); CSV from batch mode if you used it.
  - Review captures in `VisionGalleryBottomSheet`.
  - Rate samples against the 5-criteria rubric.
