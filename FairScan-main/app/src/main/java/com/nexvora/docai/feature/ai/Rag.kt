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
package com.nexvora.docai.feature.ai

import com.nexvora.docai.core.common.KeywordSearch
import com.nexvora.docai.core.common.PageText
import kotlin.math.ln

data class Chunk(val id: Int, val page: Int, val text: String)

/** Page-aware chunking: a chunk never spans two pages, so every chunk has an exact page reference. */
object TextChunker {
    fun chunk(pages: List<PageText>, maxChars: Int = 1200, overlap: Int = 150): List<Chunk> {
        require(maxChars > overlap * 2) { "maxChars must be larger than twice the overlap" }
        val out = ArrayList<Chunk>()
        for (p in pages) {
            val text = p.text.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim()
            if (text.isEmpty()) continue
            if (text.length <= maxChars) { out.add(Chunk(out.size, p.page, text)); continue }
            var start = 0
            while (start < text.length) {
                var end = minOf(start + maxChars, text.length)
                if (end < text.length) {
                    // prefer to cut at a paragraph / sentence / space boundary in the last 25% of the window
                    val window = text.substring(start + maxChars * 3 / 4, end)
                    val cut = listOf(window.lastIndexOf("\n\n"), window.lastIndexOf(". "), window.lastIndexOf("\u0964 "), window.lastIndexOf(' ')).maxOrNull() ?: -1
                    if (cut >= 0) end = start + maxChars * 3 / 4 + cut + 1
                }
                out.add(Chunk(out.size, p.page, text.substring(start, end).trim()))
                if (end >= text.length) break
                start = (end - overlap).coerceAtLeast(start + 1)
            }
        }
        return out
    }
}

/** BM25 retrieval over chunks (no embeddings model needed). */
object ChunkRetriever {
    fun top(chunks: List<Chunk>, query: String, k: Int = 6): List<Chunk> {
        val terms = KeywordSearch.terms(query)
        if (terms.isEmpty() || chunks.isEmpty()) return emptyList()
        val tokenized = chunks.map { c -> c.text.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() } }
        val avg = tokenized.map { it.size }.average().coerceAtLeast(1.0)
        val n = chunks.size
        val df = terms.associateWith { t -> tokenized.count { toks -> toks.contains(t) } }
        val scored = chunks.indices.map { i ->
            val toks = tokenized[i]
            val tf = toks.groupingBy { it }.eachCount()
            var score = 0.0
            for (t in terms) {
                val f = tf[t] ?: 0
                if (f == 0) continue
                val idf = ln(1 + (n - df.getValue(t) + 0.5) / (df.getValue(t) + 0.5))
                score += idf * (f * 2.5) / (f + 1.5 * (0.25 + 0.75 * toks.size / avg))
            }
            i to score
        }
        return scored.filter { it.second > 0 }.sortedByDescending { it.second }.take(k).map { chunks[it.first] }
    }
}

enum class AiTask { ASK, SUMMARIZE, EXPLAIN, EXTRACT_INFO, NOTES, QUESTIONS, FLASHCARDS, TRANSLATE }

data class GroundedPrompt(val text: String, val pages: List<Int>, val truncated: Boolean)

/** Builds prompts that restrict the model to the supplied document excerpts and ask for page citations. */
object PromptBuilder {
    private const val RULES = "You are a document assistant. Use ONLY the numbered excerpts below. " +
        "If the answer is not in the excerpts, reply exactly: \"Not found in the document.\" " +
        "Cite the page of every fact as [p.N]. Do not invent information."

    fun build(task: AiTask, userRequest: String, chunks: List<Chunk>, maxContextChars: Int): GroundedPrompt {
        val sb = StringBuilder()
        val used = LinkedHashSet<Int>()
        var truncated = false
        for (c in chunks) {
            val block = "[p.${c.page}] ${c.text}\n\n"
            if (sb.length + block.length > maxContextChars) { truncated = true; break }
            sb.append(block); used.add(c.page)
        }
        val instruction = when (task) {
            AiTask.ASK -> "Question: $userRequest\nAnswer concisely."
            AiTask.SUMMARIZE -> "Write a short summary of the excerpts."
            AiTask.EXPLAIN -> "Explain the excerpts in simple words. ${userRequest}".trim()
            AiTask.EXTRACT_INFO -> "List the important facts (names, dates, amounts, deadlines) as bullet points."
            AiTask.NOTES -> "Write concise study notes as bullet points."
            AiTask.QUESTIONS -> "Write 5 multiple-choice questions. Reply ONLY with a JSON array of objects " +
                "{\"question\":string,\"options\":[4 strings],\"correct\":index 0-3,\"explanation\":string}."
            AiTask.FLASHCARDS -> "Write 8 flashcards. Reply ONLY with a JSON array of objects {\"front\":string,\"back\":string}."
            AiTask.TRANSLATE -> userRequest
        }
        return GroundedPrompt("$RULES\n\nExcerpts:\n$sb\n$instruction\nAnswer:", used.toList(), truncated)
    }
}

object CitationParser {
    private val RX = Regex("\\[p\\.(\\d{1,5})]|\\(page (\\d{1,5})\\)", RegexOption.IGNORE_CASE)

    /** Pages cited by the model, keeping only pages that were actually supplied (hallucinated pages are dropped). */
    fun cited(answer: String, allowedPages: Collection<Int>): List<Int> =
        RX.findAll(answer).mapNotNull { (it.groupValues[1].ifEmpty { it.groupValues[2] }).toIntOrNull() }
            .filter { it in allowedPages }.distinct().toList()
}
