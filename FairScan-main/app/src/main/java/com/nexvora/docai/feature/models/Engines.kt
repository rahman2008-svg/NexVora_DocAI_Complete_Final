/*
 * Copyright 2026 NexVora Lab's Ofc
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.nexvora.docai.feature.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device text generation. [isAvailable] is true ONLY when a native engine is present AND a model is loaded.
 * The only implementation shipped is [LlamaCppEngine], which needs the native library described in
 * docs/LLAMA_CPP_INTEGRATION.md; it is NOT part of this build, so the UI reports "unavailable".
 */
interface TextGenerationEngine {
    /** The native inference library is part of this build. */
    val engineInstalled: Boolean
    /** Engine present and a model is loaded, i.e. generate() can be called. */
    val isAvailable: Boolean
    val unavailableReason: String
    val loadedModelName: String?
    suspend fun load(file: File, name: String, contextLength: Int, threads: Int): Result<Unit>
    fun unload()
    /** Streams pieces to [onToken]; returns the full text. Throws on failure; honours [cancel]. */
    suspend fun generate(prompt: String, maxTokens: Int, onToken: (String) -> Unit): String
    fun cancel()
}

object NoTextGenerationEngine : TextGenerationEngine {
    override val engineInstalled = false
    override val isAvailable = false
    override val unavailableReason =
        "Local AI model not installed. No on-device inference engine is included in this build, so AI answers are unavailable. " +
            "Document search and extractive summaries still work."
    override val loadedModelName: String? = null
    override suspend fun load(file: File, name: String, contextLength: Int, threads: Int) =
        Result.failure<Unit>(UnsupportedOperationException(unavailableReason))
    override fun unload() {}
    override suspend fun generate(prompt: String, maxTokens: Int, onToken: (String) -> Unit): String =
        throw UnsupportedOperationException(unavailableReason)
    override fun cancel() {}
}

/**
 * JNI bridge to llama.cpp. The native library `libnexvora_llama.so` is NOT built by this project yet
 * (see docs/LLAMA_CPP_INTEGRATION.md); without it [engineInstalled] is false and nothing here is ever called.
 * The native contract: nativeLoad returns an opaque handle (0 = failure), nativeGenerate streams pieces to the sink
 * (sink returns false to stop) and returns the full text, nativeCancel asks a running generation to stop.
 */
class LlamaCppEngine : TextGenerationEngine {
    fun interface TokenSink { fun onToken(piece: String): Boolean }

    private external fun nativeLoad(path: String, contextLength: Int, threads: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, sink: TokenSink): String
    private external fun nativeCancel(handle: Long)
    private external fun nativeFree(handle: Long)

    companion object {
        val libraryLoaded: Boolean = try { System.loadLibrary("nexvora_llama"); true } catch (_: UnsatisfiedLinkError) { false } catch (_: SecurityException) { false }
    }

    private val mutex = Mutex()
    @Volatile private var handle = 0L
    @Volatile private var cancelled = false
    @Volatile override var loadedModelName: String? = null
        private set

    override val engineInstalled: Boolean get() = libraryLoaded
    override val isAvailable: Boolean get() = libraryLoaded && handle != 0L
    override val unavailableReason: String
        get() = when {
            !libraryLoaded -> NoTextGenerationEngine.unavailableReason
            handle == 0L -> "Local AI model not installed or not loaded. Open Model Manager."
            else -> ""
        }

    override suspend fun load(file: File, name: String, contextLength: Int, threads: Int): Result<Unit> {
        if (!libraryLoaded) return Result.failure(UnsupportedOperationException(NoTextGenerationEngine.unavailableReason))
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                unloadLocked()
                try {
                    val h = nativeLoad(file.absolutePath, contextLength, threads)
                    if (h == 0L) Result.failure(IllegalStateException("The engine could not load this model (unsupported architecture, corrupt file or not enough memory)."))
                    else { handle = h; loadedModelName = name; Result.success(Unit) }
                } catch (e: Throwable) { Result.failure(e) }
            }
        }
    }

    private fun unloadLocked() {
        val h = handle
        if (h != 0L && libraryLoaded) runCatching { nativeFree(h) }
        handle = 0L
        loadedModelName = null
    }

    override fun unload() { cancel(); unloadLocked() }

    override suspend fun generate(prompt: String, maxTokens: Int, onToken: (String) -> Unit): String {
        check(isAvailable) { unloadedMessage() }
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                cancelled = false
                nativeGenerate(handle, prompt, maxTokens) { piece -> onToken(piece); !cancelled }
            }
        }
    }

    private fun unloadedMessage() = unavailableReason.ifBlank { "No model loaded." }

    override fun cancel() { cancelled = true; val h = handle; if (h != 0L && libraryLoaded) runCatching { nativeCancel(h) } }
}

interface TranslationEngine {
    val isAvailable: Boolean
    val unavailableReason: String
    suspend fun translate(text: String, fromName: String, toName: String, onPiece: (String) -> Unit): String
}

/**
 * Translation by prompting the local LLM. Available only when [llm] is. Quality depends entirely on the loaded model
 * and is NOT verified; the UI says so. There is no online fallback.
 */
class LlmTranslationEngine(private val llm: TextGenerationEngine) : TranslationEngine {
    override val isAvailable: Boolean get() = llm.isAvailable
    override val unavailableReason: String
        get() = "No on-device translation model is available. " + llm.unavailableReason +
            " NexVora DocAI never sends your text to an online translator."

    override suspend fun translate(text: String, fromName: String, toName: String, onPiece: (String) -> Unit): String {
        val prompt = "Translate the following text from $fromName to $toName. Reply with the translation only, no comments.\n\n$text\n\nTranslation:"
        return llm.generate(prompt, maxTokens = (text.length / 2).coerceIn(128, 1024), onToken = onPiece).trim()
    }
}

/** Kept for tests/compatibility: the "no engine" translation state. */
object NoTranslationEngine : TranslationEngine {
    override val isAvailable = false
    override val unavailableReason =
        "No on-device translation engine or model is installed. NexVora DocAI never sends your text to an online translator."
    override suspend fun translate(text: String, fromName: String, toName: String, onPiece: (String) -> Unit): String =
        throw UnsupportedOperationException(unavailableReason)
}
