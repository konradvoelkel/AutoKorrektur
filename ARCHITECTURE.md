# AutoKorrektur — Architecture & System Specification

This document defines the core architecture, dataflow pipelines, matrix conventions, coordinate spaces, and resource management protocols across the AutoKorrektur project.

---

## 1. System Overview

- **On-device (what ships)**: zero-network pipeline combining YOLOv11-seg (TFLite) instance segmentation with MI-GAN (ONNX Runtime) local inpainting. The Play Store flavor `core` has no `INTERNET` permission at all.
- **Cloud SDXL (not shipped)**: an optional remote FastAPI service, present in the `beta`/`full` flavors behind `FEATURE_CLOUD_SDXL` and **not used by any published build**. The service itself lives in [autokorrektur-backend](https://github.com/konradvoelkel/autokorrektur-backend); the client code and the mask contract below are kept here so the path still compiles and is tested.

See `docs/PRODUCT_TIERS.md` for which flavor contains what.

```mermaid
graph TD
    A[Image Input: Gallery / Camera] --> B[UriLoader & EXIF Normalizer]
    B --> C[ImageProcessor: RGB 640x640 Resize & Aspect Pad]
    C --> D[YOLOv11-seg: Detection & Prototype Assembly]
    D --> E[Guided Filter Edge Refinement]
    E --> F[Inverted Binary Mask Mat: 0=Car, 255=Background]
    F --> G{Inpainting Mode}
    G -->|Local| H[MI-GAN ONNX Runtime 512x512]
    G -->|Cloud SDXL| I[FastAPI Server via Multipart HTTP]
    H --> J[Alpha & Mask Blending: unpaddedInpainted onto Car Hole]
    I --> J
    J --> K[BeforeAfterSliderView & Gallery / Instagram Export]
```

---

## 2. Mask Polarity & Value Conventions

> [!IMPORTANT]
> **Strict Mask Polarity Rule**:
> - **Value `0` (Black)**: Represents the **vehicle / hole to be inpainted**.
> - **Value `255` (White)**: Represents the **background / context to be preserved**.

### Component Polarity Contract
| Stage / Component | Car Pixel Value | Background Pixel Value | Description |
|---|---|---|---|
| **YOLO Raw Segmentation** | `255` | `0` | Standard instance segmentation proposal mask |
| **Mask Assembler Inversion** | `0` | `255` | Subtractive mask passed to inpainting engines |
| **MI-GAN ONNX Tensor (`mask`)** | `0` | `255` | `1x1x512x512 UINT8` where 0 indicates hole region |
| **Cloud Inpainting Payload** | `255` | `0` | Standard Diffusion mask (white = inpaint target) |
| **Final Composition Blending** | Inverted (`carMask=255`) | `0` | `unpaddedInpainted.copyTo(blendedMat, carMask)` |

---

## 3. Color Space & Channel Ordering Conventions

1. **Android `Bitmap`**: ARGB_8888 (standard Android canvas rendering).
2. **OpenCV Intermediate `Mat`**:
   - Camera input: `8UC4` BGRA or `8UC3` BGR.
   - Inference input: `8UC3` RGB.
   - Masks: `8UC1` Grayscale.
3. **TFLite YOLO Input**: `1x640x640x3` normalized `Float32` in NHWC order ($[0.0, 1.0]$).
4. **MI-GAN ONNX Input**:
   - `image`: `1x3x512x512` `UINT8` in NCHW order ($[0, 255]$).
   - `mask`: `1x1x512x512` `UINT8` in NCHW order ($[0, 255]$).

---

## 4. Coordinate Transformations & Aspect-Fit Ratios

When scaling an arbitrary $W \times H$ photo to model input $640 \times 640$:
1. Determine scale factor $s = \frac{640}{\max(W, H)}$.
2. Scaled dimensions: $W' = W \cdot s$, $H' = H \cdot s$.
3. Compute symmetric square padding:
   $$\text{xPad} = \frac{640 - W'}{2}, \quad \text{yPad} = \frac{640 - H'}{2}$$
4. Ratios for coordinate un-mapping:
   $$xRatio = \frac{\max(W, H)}{W}, \quad yRatio = \frac{\max(W, H)}{H}$$

### Inpainting resolution

Three scale steps stack, and the last one is invisible in the tensor shapes above:

1. `ImageProcessor` loads at most `DEFAULT_MAX_MEGAPIXELS` (8 MP) with power-of-two subsampling,
   so a 50 MP camera file arrives as roughly 3 MP.
2. MI-GAN always generates at $512 \times 512$, whatever the photo measures.
3. `processOutputMat` resizes that generation to $\max(W, H)$ of the processed image and crops it
   back, and `blendResult` copies it into the mask with a hard binary `copyTo` — no feathering,
   no alpha.

So the detail inside a removed vehicle is fixed at 512 px across the image's longest side: on a
2040 px photo the patch is a 4x upscale of generated content. This is why a removed car can leave
a soft or iridescent ghost while the untouched background stays sharp, and it is what high-res
progressive tile inpainting (`beta`/`full`, `InpaintingQualityMode.HIGH_RES_PROGRESSIVE`) exists to
avoid. `core` has no such fallback, so its output quality is image-dependent by construction.

---

## 5. Memory & Native JNI Lifecycle Protocol

1. **OpenCV `Mat` Management**: Every dynamically allocated OpenCV matrix must be protected with `try-finally` blocks or tracked in a `matsToRelease` list and freed via `.release()`.
2. **Bitmap Management**: Large intermediate bitmaps in background loops (`BatchProcessingWorker`) must explicitly invoke `.recycle()` upon completing extraction.
3. **Coroutine Cancellation**: ML pipelines must check `currentCoroutineContext().ensureActive()` between major stages to release native sessions promptly when jobs are aborted.
