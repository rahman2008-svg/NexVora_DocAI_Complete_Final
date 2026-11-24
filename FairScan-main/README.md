# NexVora DocAI

**Scan. Understand. Organize. Create.**

Native Android document app (Kotlin, Jetpack Compose, MVVM-style controllers) — offline-first and privacy-focused.

- Application ID: `com.nexvora.docai` · Version: `1.0.0`
- Developer: Prince AR Abdur Rahman · Publisher: NexVora Lab's Ofc
- Based on **FairScan 2.4.0** (© The FairScan authors, GPL-3.0-or-later). See `UPSTREAM_README_FairScan.md`.

> **Status honesty:** this source tree (two development passes) was produced in an environment without an Android SDK, Gradle cache or network access.
> **It has not been compiled, unit-tested or run on a device.** No APK exists. See `docs/IMPLEMENTATION_REPORT.md` and `docs/TESTING.md`.

## Features

| Area | What it does | Notes |
|---|---|---|
| Scanner | Upstream FairScan flow, unchanged: CameraX, TFLite segmentation, OpenCV perspective correction, quad editor, filters, rotate, reorder, multi-page PDF export; export screen now has **Save to Document Vault** (identical files are not duplicated) | Hosted inside the NexVora shell; reskinned by the dark theme only |
| Home / Splash / Nav | Dashboard, 5-slot bottom bar with central camera button, splash that waits (max 6 s) for the scanner model | |
| Files (Vault) | Folders, import (system picker), search, filters, sort, rename, move, favourite, share, export, delete with confirmation, in-app PDF viewer | JSON index + files in app-private storage (not encrypted); SHA-256 duplicate detection, thumbnails, details |
| PDF Studio | Merge, split, compress, rotate, reorder, extract, delete pages, images→PDF, PDF→images, watermark, password, remove password (needs the password), page numbers, PDF→text, PDF info, preview | pdfbox-android + platform PdfRenderer; every output is read back and page-counted before success is reported; saved to Files › PDF Studio |
| OCR | English/Bengali Tesseract via upstream integration; image or PDF input; copy, share, search, export TXT | Language data downloaded on demand by the user |
| AI Assistant | Chat UI over a selected PDF. Always: keyword search with page references, extractive summary. **When a model is loaded:** page-aware chunking → BM25 retrieval → grounded prompt → streamed answer with `[p.N]` citations (cancel supported) | Native llama.cpp engine NOT built in: see `docs/LLAMA_CPP_INTEGRATION.md` |
| Study | Notes, flashcards (reveal/navigate), MCQ practice, scored quiz, extractive summary | You author cards/questions; no auto-generation |
| Translation | Language selector/swap, TTS "Listen", copy/share; Translate runs through the loaded local LLM | Disabled with an explanation until the engine + model exist; no online fallback |
| Model Manager | Import or HTTPS-download models (optional SHA-256), **GGUF header validation**, architecture/context metadata, license, size, memory estimate, load/unload, cancel, delete | No verified catalog bundled; engine missing, so models cannot run yet |
| Settings | App Lock (BiometricPrompt on Android 11+, system credential screen on 8–10), privacy, storage & cache, **encrypted backup (AES-256-GCM, PBKDF2-SHA256)** or plain ZIP, about/licenses | App Lock and documents are NOT encrypted at rest |

### What is deliberately NOT included
No Firebase/Supabase/analytics/ads/cloud AI, no bundled models, no online translation, no handwriting OCR, no fake AI output.
`INTERNET` is declared only for user-initiated downloads (OCR language data, model files).

## Build

Requirements: JDK 21+ (AGP 9.5.1), Android SDK with `compileSdk` platform per `app/build.gradle.kts`, network access for the first build
(Gradle dependencies and the segmentation model `fairscan-segmentation-model.tflite` downloaded by the `downloadTFLiteModel` task).

```bash
./gradlew tasks
./gradlew clean
./gradlew test            # JVM unit tests (upstream + NexVora tests)
./gradlew check
./gradlew assembleDebug   # -> app/build/outputs/apk/debug/NexVoraDocAI-1.0.0-<abi>.apk
./gradlew assembleRelease # needs your own signing config
```

CI: `.github/workflows/android.yml` builds the debug APK, runs unit tests and uploads the real APK as an artifact (it has never been run).

Gradle, AGP, Kotlin, compileSdk and dependency versions are the upstream FairScan 2.4.0 ones; none were changed.

## Project layout (NexVora additions)

```
app/src/main/java/com/nexvora/docai/
  core/design      design tokens + reusable components (NexVora*)
  core/common      helpers (page ranges, keyword search, share, bitmaps)
  core/navigation  NxRoute, ShellStack, NexVoraShell
  core/NxServices  application-scoped services
  feature/{home,vault,pdf,ocr,assistant,study,translation,models,settings}
  (everything else = upstream FairScan code, package renamed org.fairscan.app -> com.nexvora.docai)
```

## License

GPL-3.0-or-later (see `LICENSE`). Upstream files keep the FairScan header; NexVora-authored files carry
"Copyright 2026 NexVora Lab's Ofc" with the same GPL notice. The UI text "© 2026 NexVora Lab's Ofc. All Rights Reserved." refers to
NexVora-authored material and does not limit GPL rights. **Anyone you distribute the app to must receive the complete corresponding source**
(this repository) under the GPL. Third-party licenses: `THIRD_PARTY_NOTICES.md`.
