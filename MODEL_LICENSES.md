# MODEL LICENSES

NexVora DocAI **bundles no AI, translation or OCR-language model** (except the FairScan document-segmentation model fetched at build time, see below).

| Model / data | How it gets onto the device | License | Notes |
|---|---|---|---|
| Tesseract `tessdata_fast` language files (English, Bengali, …) | Downloaded by the user inside the app | Apache-2.0 (tesseract-ocr/tessdata_fast) | Verify at https://github.com/tesseract-ocr/tessdata_fast before redistributing |
| FairScan segmentation model `fairscan-segmentation-model.tflite` | Downloaded by Gradle at build time from https://github.com/pynicolas/fairscan-segmentation-model | **UNVERIFIED — check that repository's license before distributing an APK** | Part of the upstream FairScan build |
| GGUF language models | Imported or downloaded by the user via Model Manager | Set by each publisher | The user enters the license text; the app does not verify it. Many models have use restrictions (e.g. community/acceptable-use licenses). Do not redistribute third-party models without permission. |

Model Manager shows the license string, size, source URL, checksum status and GGUF architecture for every installed model.
HTTPS is mandatory for downloads; supply a SHA-256 to have the file integrity-checked. A valid GGUF header does not prove the engine supports the model.
