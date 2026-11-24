# Build & test checklist

Nothing below has been run for this delivery (no SDK/network in the authoring environment). Tick each item on a real machine/device.

## Automated
- [ ] `./gradlew tasks` · `./gradlew clean` · `./gradlew test` · `./gradlew check` · `./gradlew assembleDebug`
- [ ] Fix any compile errors first (code was written without a compiler: expect small API/import fixes, esp. Compose icon names and PDFBox-Android signatures).
- [ ] `./gradlew connectedAndroidTest` (upstream torch lifecycle test) on a device/emulator.

## Manual (device, Android 8–15)
1. Launch → splash ends by itself within ~6 s → Home.
2. Home cards each open their screen; bottom bar: Home / Scan / camera / Files / Settings; camera button opens the scanner.
3. Scanner: deny then grant camera permission; live preview, auto detection, capture, crop, rotate, filters, reorder, delete, multi-page, export PDF, import from gallery. Rotate device.
4. Back from scanner camera returns to Home.
5. Files: import PDF + image + txt; create folder; rename/move/favourite/share/export; delete shows confirmation; search/filter/sort; open PDF in viewer; kill app and reopen (persistence); try an empty file and a corrupt PDF.
6. PDF Studio: run each tool with a 3+ page PDF; check outputs open elsewhere. Compress reports before/after sizes honestly. Password: open result in another viewer. Corrupt/encrypted input shows an error, cancel works on a long job.
7. OCR: install English (and Bengali) in the language manager, run on an image and a PDF; copy/share/search/export TXT; with no language installed a clear message appears.
8. Assistant: attach a text PDF, search words (page chips open the right page), extractive summary; scanned PDF → "Run OCR"; AI chips explain unavailability; clear conversation.
9. Study: create note, deck (reveal/prev/next), question set (practice + scored quiz), summary from a PDF.
10. Translation: Translate disabled with explanation; Listen reads Bangla/English input (needs a TTS voice).
11. Model Manager: import a file, delete it; https download with cancel and with an invalid link; confirm no inference is claimed.
12. App Lock: enable (needs device screen lock), background >30 s → locked, unlock; disable. Verify screenshots blocked after restart while enabled.
13. Backup: create ZIP, wipe app data, restore; confirm documents/notes return.
14. Offline: airplane mode — everything except downloads works.
15. Legacy integration: `adb shell am start -a org.fairscan.app.action.SCAN_TO_PDF -n com.nexvora.docai/.MainActivity` returns a PDF to the caller.

## Added in the completion run
16. Scanner export screen → "Save to Document Vault": file appears in Files › Personal; repeating it reports "already in the vault".
17. PDF Studio: PDF Info shows pages/size/metadata; Preview opens the viewer; Remove Password (wrong password → error; right password → unlocked copy); Password output refuses to open elsewhere without the password.
18. Backup with password: file is unreadable as ZIP; wrong password rejected; correct password restores; tamper with one byte → rejected.
19. Model Manager: importing a non-GGUF file as "Language model" is rejected; a real GGUF shows architecture/context; Load is disabled while the native engine is absent.
20. Study: quiz history appears after a quiz; explanations show for questions that have one.
21. Settings › Storage & cache: sizes plausible; clearing temp files leaves documents intact.
22. (Only after the native engine is added) load a small GGUF, ask a question → streamed answer with page chips; Stop works; unload frees memory.
