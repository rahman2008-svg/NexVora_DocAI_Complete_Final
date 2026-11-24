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
package com.nexvora.docai.feature.assistant

import com.nexvora.docai.core.common.ExtractiveSummary
import com.nexvora.docai.core.common.KeywordSearch
import com.nexvora.docai.core.common.PageText
import com.nexvora.docai.feature.ai.AiTask
import com.nexvora.docai.feature.ai.ChunkRetriever
import com.nexvora.docai.feature.ai.CitationParser
import com.nexvora.docai.feature.ai.LlmRuntime
import com.nexvora.docai.feature.ai.PromptBuilder
import com.nexvora.docai.feature.ai.TextChunker
import com.nexvora.docai.feature.models.TextGenerationEngine
import com.nexvora.docai.feature.vault.VaultDoc
import com.nexvora.docai.feature.vault.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ChatMessage(val role: String, val text: String, val pages: List<Int> = emptyList(), val time: Long = System.currentTimeMillis()) {
    val isUser get() = role == "user"
}

data class AssistantState(
    val doc: VaultDoc? = null,
    val pages: List<PageText> = emptyList(),
    val extracting: Boolean = false,
    val status: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val noTextFound: Boolean = false,
    val generating: Boolean = false,
)

/**
 * Chat-style document assistant. Without a local model it answers ONLY with deterministic features
 * (keyword search with page references, extractive summary) and labels them as such.
 */
class AssistantController(
    private val vault: VaultRepository,
    private val extractor: DocumentTextExtractor,
    private val historyDir: File,
    private val scope: CoroutineScope,
    val runtime: LlmRuntime,
) {
    val engine: TextGenerationEngine get() = runtime.engine

    val state = MutableStateFlow(AssistantState())
    private val json = Json { ignoreUnknownKeys = true }
    private var job: Job? = null

    private fun historyFile(doc: VaultDoc) = File(historyDir, "${doc.id}.json")

    private fun loadHistory(doc: VaultDoc): List<ChatMessage> = try {
        historyFile(doc).takeIf { it.exists() }?.let { json.decodeFromString(ListSerializer(ChatMessage.serializer()), it.readText()) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun saveHistory() {
        val s = state.value
        val doc = s.doc ?: return
        historyDir.mkdirs()
        runCatching { historyFile(doc).writeText(json.encodeToString(ListSerializer(ChatMessage.serializer()), s.messages.takeLast(200))) }
    }

    private fun add(msg: ChatMessage) {
        state.update { it.copy(messages = it.messages + msg) }
        saveHistory()
    }

    fun attach(doc: VaultDoc) {
        job?.cancel()
        state.value = AssistantState(doc = doc, extracting = true, status = "Reading document…", messages = loadHistory(doc))
        job = scope.launch(Dispatchers.IO) {
            try {
                val pages = extractor.extractEmbedded(vault.file(doc)) { p, t -> state.update { it.copy(status = "Reading page $p of $t…") } }
                val hasText = pages.any { it.text.isNotBlank() }
                state.update { it.copy(pages = pages, extracting = false, status = null, noTextFound = !hasText) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                state.update { it.copy(extracting = false, status = null, error = "This document could not be read: ${e.message ?: "corrupt or encrypted file"}") }
            }
        }
    }

    fun runOcr() {
        val doc = state.value.doc ?: return
        job?.cancel()
        state.update { it.copy(extracting = true, status = "Running OCR…", error = null) }
        job = scope.launch(Dispatchers.IO) {
            try {
                val pages = extractor.extractWithOcr(vault.file(doc)) { p, t -> state.update { it.copy(status = "OCR page $p of $t…") } }
                val hasText = pages.any { it.text.isNotBlank() }
                state.update {
                    it.copy(pages = pages, extracting = false, status = null, noTextFound = !hasText,
                        error = if (hasText) null else "OCR found no text. Install/enable an OCR language in OCR settings.")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                state.update { it.copy(extracting = false, status = null, error = "OCR failed: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun cancel() { job?.cancel(); state.update { it.copy(extracting = false, status = null) } }

    fun ask(query: String) {
        val s = state.value
        if (query.isBlank()) return
        if (s.doc == null) { add(ChatMessage("assistant", "Attach a PDF first.")); return }
        if (engine.isAvailable) { generate(AiTask.ASK, query.trim(), query.trim()); return }
        add(ChatMessage("user", query.trim()))
        val hits = KeywordSearch.search(s.pages, query)
        if (hits.isEmpty()) {
            add(ChatMessage("assistant", "Keyword search (no AI model active): nothing in this document matches \"${query.trim()}\"."))
        } else {
            val body = hits.joinToString("\n\n") { "Page ${it.page} (${it.matches} match${if (it.matches == 1) "" else "es"}):\n${it.snippet}" }
            add(ChatMessage("assistant", "Keyword search (no AI model active). Passages from the document:\n\n$body", hits.map { it.page }))
        }
    }

    fun summarize() {
        val s = state.value
        if (s.doc == null) { add(ChatMessage("assistant", "Attach a PDF first.")); return }
        if (engine.isAvailable) { generate(AiTask.SUMMARIZE, "", "Summarize this PDF"); return }
        add(ChatMessage("user", "Summarize this PDF"))
        val sentences = ExtractiveSummary.summarize(s.pages.joinToString("\n") { it.text }, 5)
        if (sentences.isEmpty()) add(ChatMessage("assistant", "There is not enough text to summarize."))
        else add(ChatMessage("assistant", "Extractive summary (selects key sentences from the document; it is not AI-written):\n\n" + sentences.joinToString("\n") { "• $it" }))
    }

    /** Chip actions that need a language model. Without a loaded model an honest "not installed" message is shown. */
    fun aiTask(label: String) {
        val task = when (label) {
            "Explain this document" -> AiTask.EXPLAIN
            "Extract important information" -> AiTask.EXTRACT_INFO
            "Create notes" -> AiTask.NOTES
            else -> null
        }
        if (task == null) {
            add(ChatMessage("user", label))
            add(ChatMessage("assistant", "Open the Translation screen to translate text; it uses the same local model when one is loaded."))
            return
        }
        if (state.value.doc == null) { add(ChatMessage("assistant", "Attach a PDF first.")); return }
        if (engine.isAvailable) generate(task, "", label)
        else { add(ChatMessage("user", label)); add(ChatMessage("assistant", engine.unavailableReason)) }
    }

    fun stopGeneration() { engine.cancel(); job?.cancel() }

    private fun generate(task: AiTask, request: String, label: String) {
        val s = state.value
        val budget = (runtime.contextLength * 3 - 1000).coerceAtLeast(1500)
        val all = TextChunker.chunk(s.pages)
        if (all.isEmpty()) { add(ChatMessage("assistant", "This document has no readable text. Run OCR first.")); return }
        val chosen = if (task == AiTask.ASK) ChunkRetriever.top(all, request, 6).ifEmpty { all.take(4) } else spread(all, 8)
        val prompt = PromptBuilder.build(task, request, chosen, budget)
        add(ChatMessage("user", label))
        add(ChatMessage("assistant", ""))
        state.update { it.copy(generating = true, error = null) }
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            try {
                val text = engine.generate(prompt.text, 512) { piece ->
                    state.update { st -> st.copy(messages = st.messages.dropLast(1) + st.messages.last().copy(text = st.messages.last().text + piece)) }
                }
                val cited = CitationParser.cited(text, prompt.pages)
                state.update { st -> st.copy(generating = false, messages = st.messages.dropLast(1) + st.messages.last().copy(text = text.trim().ifEmpty { "(no answer)" }, pages = cited)) }
                saveHistory()
            } catch (e: CancellationException) {
                state.update { it.copy(generating = false) }; saveHistory(); throw e
            } catch (e: Exception) {
                state.update { st -> st.copy(generating = false, error = "Generation failed: ${e.message ?: e.javaClass.simpleName}", messages = st.messages.dropLast(1)) }
            }
        }
    }

    private fun <T> spread(list: List<T>, n: Int): List<T> =
        if (list.size <= n) list else (0 until n).map { list[it * (list.size - 1) / (n - 1)] }

    fun clear() {
        state.value.doc?.let { historyFile(it).delete() }
        state.update { it.copy(messages = emptyList()) }
    }
}
