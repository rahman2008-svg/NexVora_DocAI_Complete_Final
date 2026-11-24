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
package com.nexvora.docai.core.common

import java.util.Locale
import kotlin.math.sqrt

data class PageText(val page: Int, val text: String) // page is 1-based

data class SearchHit(val page: Int, val snippet: String, val matches: Int)

/**
 * Plain keyword search over extracted page text. This is NOT AI: it only finds the places in the
 * document where the query words occur and reports the page number.
 */
object KeywordSearch {
    private val STOP_WORDS = setOf(
        "the", "an", "and", "or", "of", "to", "in", "on", "for", "is", "are", "was", "were", "be",
        "what", "which", "who", "how", "does", "do", "this", "that", "it", "with", "as", "at", "by",
        "from", "about",
    )
    private val SPLITTER = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun terms(query: String): List<String> =
        query.lowercase(Locale.ROOT).split(SPLITTER)
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .distinct()

    fun search(pages: List<PageText>, query: String, maxHits: Int = 8, radius: Int = 90): List<SearchHit> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()
        data class Scored(val hit: SearchHit, val score: Int)
        val scored = mutableListOf<Scored>()
        for (p in pages) {
            val lower = p.text.lowercase(Locale.ROOT)
            var distinct = 0
            var total = 0
            var firstIndex = Int.MAX_VALUE
            for (t in terms) {
                var idx = lower.indexOf(t)
                if (idx >= 0) {
                    distinct++
                    firstIndex = minOf(firstIndex, idx)
                    while (idx >= 0) {
                        total++
                        idx = lower.indexOf(t, idx + t.length)
                    }
                }
            }
            if (total == 0) continue
            val start = (firstIndex - radius).coerceAtLeast(0)
            val end = (firstIndex + radius).coerceAtMost(p.text.length)
            val snippet = buildString {
                if (start > 0) append('…')
                append(p.text.substring(start, end).replace(Regex("\\s+"), " ").trim())
                if (end < p.text.length) append('…')
            }
            scored.add(Scored(SearchHit(p.page, snippet, total), distinct * 10 + total))
        }
        return scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.hit.page })
            .take(maxHits).map { it.hit }
    }
}

/** Classic frequency-based extractive summary (selects existing sentences; it does not generate text). */
object ExtractiveSummary {
    private val STOP = setOf(
        "the", "and", "for", "are", "was", "were", "this", "that", "with", "from", "have", "has",
        "had", "not", "but", "you", "your", "can", "will", "all", "any", "its", "their", "there",
    )
    private val WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun sentences(text: String): List<String> =
        text.replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?\u0964])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 20 }

    fun summarize(text: String, maxSentences: Int = 5): List<String> {
        val sentences = sentences(text)
        if (sentences.size <= maxSentences) return sentences
        val freq = HashMap<String, Int>()
        for (s in sentences) for (w in words(s)) freq[w] = (freq[w] ?: 0) + 1
        val scored = sentences.mapIndexed { i, s ->
            val ws = words(s)
            val score = if (ws.isEmpty()) 0.0 else ws.sumOf { (freq[it] ?: 0).toDouble() } / sqrt(ws.size.toDouble())
            Triple(i, s, score)
        }
        return scored.sortedByDescending { it.third }.take(maxSentences).sortedBy { it.first }.map { it.second }
    }

    private fun words(s: String): List<String> =
        s.lowercase(Locale.ROOT).split(WORD).filter { it.length >= 3 && it !in STOP }
}
