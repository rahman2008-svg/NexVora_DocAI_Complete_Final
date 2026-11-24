# Adding the llama.cpp engine (NOT part of this build)

Status: **not integrated.** `LlamaCppEngine` (Kotlin) already exists and expects `libnexvora_llama.so`; without it the app reports
"Local AI model not installed / engine not installed" and never claims inference works. This document is the remaining native work.

## Why it was not done here
The authoring environment had no Android NDK, no CMake, no network (llama.cpp cannot be fetched) and no device. A JNI bridge written
against llama.cpp's fast-moving C API without compiling it would be guesswork, so it was deliberately not shipped.

## Steps
1. Add llama.cpp as a pinned submodule: `git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp` and pin a release tag.
2. Install NDK + CMake via the SDK Manager; in `app/build.gradle.kts` add `externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }`
   and `ndk { abiFilters += listOf("arm64-v8a") }` (keep existing ABI splits in sync). Do not change AGP/Kotlin versions for this.
3. Write `src/main/cpp/nexvora_llama.cpp` implementing the four JNI functions declared in `LlamaCppEngine`:
   `nativeLoad(path, contextLength, threads): Long` (0 on failure), `nativeGenerate(handle, prompt, maxTokens, sink): String` (call
   `sink.onToken(piece)` per token; stop when it returns false), `nativeCancel(handle)`, `nativeFree(handle)`. Use llama.cpp's current
   model-load / context / sampler / tokenize / decode APIs from the pinned tag; build with `-O3`, ARM NEON, no GPU backend at first.
4. CMake target name must be `nexvora_llama` (loaded by `System.loadLibrary("nexvora_llama")`).
5. Verify on a real device: load a small GGUF (e.g. a 0.5–1.5B instruct model whose license you have read), generate, cancel mid-stream,
   unload, repeat to check for leaks; test low-memory behaviour. Only then may the UI claim AI is available.
6. Add the library's license (MIT) to `THIRD_PARTY_NOTICES.md` and the GGUF model licenses to `MODEL_LICENSES.md`.

## What is already in place (JVM-tested where possible)
GGUF header validation, model storage/import/download with HTTPS-only and optional SHA-256, memory pre-check, page-aware chunking,
BM25 retrieval, grounded prompts with `[p.N]` citations, citation filtering, streaming/cancel plumbing in the assistant, JSON parsing of
model-generated flashcards/MCQs, LLM-based translation path. Everything stays disabled until the native engine loads a model.
