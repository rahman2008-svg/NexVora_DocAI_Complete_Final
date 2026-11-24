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

/** Parser for page selections such as "1-3, 5, 8-9" (1-based in the text, 0-based in the result). */
object PageRanges {
    data class Result(val pages: List<Int>, val error: String? = null) {
        val ok: Boolean get() = error == null
    }

    data class Groups(val groups: List<List<Int>>, val error: String? = null) {
        val ok: Boolean get() = error == null
    }

    /** All pages in the order written (duplicates are kept: useful for "reorder"/"extract"). */
    fun parse(input: String, pageCount: Int): Result {
        val groups = parseGroups(input, pageCount)
        return if (groups.error != null) Result(emptyList(), groups.error) else Result(groups.groups.flatten())
    }

    /** One group per comma-separated token (used by "Split": every token becomes one output file). */
    fun parseGroups(input: String, pageCount: Int): Groups {
        if (pageCount <= 0) return Groups(emptyList(), "The document has no pages.")
        val tokens = input.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Groups(emptyList(), "Enter at least one page or range, for example 1-3, 5.")
        val groups = mutableListOf<List<Int>>()
        for (token in tokens) {
            val parts = token.split('-', '–').map { it.trim() }
            val range = when (parts.size) {
                1 -> parts[0].toIntOrNull()?.let { it..it }
                2 -> {
                    val a = parts[0].toIntOrNull()
                    val b = parts[1].toIntOrNull()
                    if (a != null && b != null) a..b else null
                }
                else -> null
            } ?: return Groups(emptyList(), "\"$token\" is not a valid page or range.")
            if (range.first < 1 || range.last < range.first) {
                return Groups(emptyList(), "\"$token\" is not a valid range.")
            }
            if (range.last > pageCount) {
                return Groups(emptyList(), "Page ${range.last} does not exist (the document has $pageCount pages).")
            }
            groups.add(range.map { it - 1 })
        }
        return Groups(groups)
    }
}
