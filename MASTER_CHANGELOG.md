# MASTER CHANGELOG

## 1.0.0 (NexVora DocAI) — derived from FairScan 2.4.0

### Rebrand / build
- Package and namespace `org.fairscan.app` → `com.nexvora.docai` (sources, tests, androidTest moved). FileProvider authority is `${applicationId}.fileprovider` → `com.nexvora.docai.fileprovider`.
- Legacy intent action `org.fairscan.app.action.SCAN_TO_PDF` kept (plus new `com.nexvora.docai.action.SCAN_TO_PDF`) so existing integrations keep working.
- `versionName` 1.0.0, `versionCode` 3, APK name `NexVoraDocAI-<version>-<abi>.apk`. Gradle/AGP/Kotlin/SDK/dependencies unchanged.
- App label, theme (`Theme.NexVoraDocAI`), splash colour, original adaptive launcher icon (vector) and monochrome icon.
- `allowBackup=false` (no Android cloud auto-backup); `USE_BIOMETRIC` added; `<queries>` for TTS; FileProvider paths for vault and temp outputs.
- Root `build.gradle.kts`: NexVora-authored directories excluded from the upstream header check/format task.

### Upstream code touched (minimal)
- `ui/theme/Theme.kt`: NexVora dark palette, dark-only.
- `domain/ImageSegmentation.kt`: `isReady` flag (used by splash).
- `FairScanApp.kt`: creates `NxServices`.
- `MainActivity.kt`: scanner flow wrapped in a lambda and hosted by `NexVoraShell`; App Lock gate; system-bar style; OCR language / scanner settings / licenses screens reused from the shell.

### Added (all new code under core/ and feature/)
Design system, splash, home, scan hub, vault, PDF viewer, PDF Studio (+engine), OCR screen/controller, assistant (keyword search, extractive summary), study tools, translation shell, model manager + engine interfaces, settings, privacy, about, backup/restore, App Lock.

### Tests added (JVM)
PageRanges, text helpers, keyword search/extractive summary, ShellStack, VaultRepository (+query), StudyRepository/Scoring, OcrTextBuilder, ModelManager, BackupManager (round-trip, invalid ZIP, path-traversal ids).

### Known gaps
See `docs/IMPLEMENTATION_REPORT.md`.

## 1.0.0 — second pass (completion run)
- Scanner export screen: "Save to Document Vault" (upstream `ExportScreen`/`ExportViewModel` extended additively; legacy callers unaffected).
- Vault: SHA-256 duplicate detection (`ImportOutcome`), thumbnails, details dialog (type, size, pages, dates, hash).
- PDF Studio: PDF Info, Remove Password, Preview; automatic output verification (page counts, password check, image decode) before success.
- Encrypted backups (`BackupCrypto`, format NXBK v1: PBKDF2-HMAC-SHA256 210k iterations, AES-256-GCM per 1 MiB chunk with chunk index + final flag as AAD); restore handles wrong password / tamper / truncation; restore stays additive.
- AI architecture: `TextGenerationEngine` + JNI `LlamaCppEngine` (native lib not built), `LlmRuntime` (memory check, context length), GGUF validator, page-aware chunker, BM25 retriever, grounded `PromptBuilder`, `CitationParser`, streaming/cancel in the assistant, LLM translation path, AI flashcard/MCQ generation with strict JSON validation.
- Study: explanations, quiz attempt history, saved attempts in backups.
- Settings: Storage & cache screen with "clear temporary files".
- Release: GitHub Actions workflow, `MODEL_LICENSES.md`, `OPEN_SOURCE_ATTRIBUTION.md`, `docs/LLAMA_CPP_INTEGRATION.md`, instrumented Home UI test.
- New JVM tests: BackupCrypto, encrypted BackupManager round trip, GGUF validator, chunker/retriever/prompt/citations, AI JSON parsers, vault duplicates, model import validation.
