# THIRD-PARTY NOTICES

NexVora DocAI is a modified version of **FairScan** (https://github.com/pynicolas/fairscan), © The FairScan authors,
licensed under the **GNU General Public License v3.0 or later**. The full license text is in `LICENSE`
(and `app/src/main/res/raw/gpl3.txt`, shown in-app). Upstream copyright headers have been preserved.

Corresponding source: this repository. If you distribute the app (APK), you must offer the complete corresponding source under the GPL.

## Libraries (versions from `gradle/libs.versions.toml`)
Licenses below are from the publishers' public information and **must be re-verified before release** (`./gradlew` AboutLibraries output is available in-app under Settings › Open-source licenses).

| Component | Version | License |
|---|---|---|
| AndroidX (core-ktx, lifecycle, activity-compose, datastore, documentfile, CameraX) | see toml | Apache-2.0 |
| Jetpack Compose BoM / Material 3 / Material icons extended | 2026.09.00 | Apache-2.0 |
| LiteRT (TensorFlow Lite) + support/metadata | 1.4.1 | Apache-2.0 |
| OpenCV (Android) | 4.12.0 | Apache-2.0 |
| pdfbox-android (Tom Roush port of Apache PDFBox) | 2.0.27.0 | Apache-2.0 |
| Tesseract4Android (Tesseract + Leptonica) | 4.9.0 | Apache-2.0 (Tesseract), BSD-style (Leptonica) |
| Zoomable, Reorderable | 2.16.0 / 3.1.0 | Apache-2.0 |
| AboutLibraries | 15.3.0 | Apache-2.0 |
| kotlinx.serialization, kotlinx.collections.immutable, kotlinx.coroutines | see toml | Apache-2.0 |
| JUnit / AssertJ (tests only) | 4.13.2 / 3.27.7 | EPL-1.0 / Apache-2.0 |

## Data and models
- **Tesseract language data** (`tessdata_fast`, downloaded by the user at runtime from https://github.com/tesseract-ocr/tessdata_fast): Apache-2.0.
- **FairScan segmentation model** (`fairscan-segmentation-model.tflite` v1.2.0, downloaded at build time from https://github.com/pynicolas/fairscan-segmentation-model): license to be confirmed from that repository before distribution.
- **AI / translation models:** none are bundled. Any file you import or download through the Model Manager has its own license, which you must check and enter yourself; the app does not verify it.

## NexVora-authored material
New files under `core/` and `feature/` © 2026 NexVora Lab's Ofc, released under GPL-3.0-or-later as part of this derivative work.
The launcher icon and logo are original vector artwork (not copied from the reference image).
