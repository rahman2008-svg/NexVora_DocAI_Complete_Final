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

import com.nexvora.docai.core.common.PageText
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RagTest {
    private val pages = listOf(
        PageText(1, "The lease starts on 1 March. Rent is 500 dollars per month, payable on the first day."),
        PageText(2, "Maintenance of the heating system is the landlord's responsibility. Tenants must report faults."),
        PageText(3, "Termination requires three months written notice by either party."),
    )

    @Test fun chunksNeverSpanPagesAndKeepPageNumbers() {
        val long = PageText(4, ("Sentence number one is here. ").repeat(200))
        val chunks = TextChunker.chunk(pages + long, maxChars = 400, overlap = 50)
        assertThat(chunks.filter { it.page == 1 }).hasSize(1)
        assertThat(chunks.filter { it.page == 4 }.size).isGreaterThan(5)
        assertThat(chunks.all { it.text.length <= 400 }).isTrue()
        assertThat(chunks.map { it.id }).isEqualTo(chunks.indices.toList())
    }

    @Test fun emptyPagesAreSkipped() {
        assertThat(TextChunker.chunk(listOf(PageText(1, "  \n "), PageText(2, "x y z")))).hasSize(1)
    }

    @Test fun retrieverFindsRelevantPage() {
        val chunks = TextChunker.chunk(pages)
        assertThat(ChunkRetriever.top(chunks, "how much is the rent per month").first().page).isEqualTo(1)
        assertThat(ChunkRetriever.top(chunks, "termination notice").first().page).isEqualTo(3)
        assertThat(ChunkRetriever.top(chunks, "zebra")).isEmpty()
    }

    @Test fun promptIsGroundedAndRespectsBudget() {
        val chunks = TextChunker.chunk(pages)
        val p = PromptBuilder.build(AiTask.ASK, "What is the rent?", chunks, maxContextChars = 10_000)
        assertThat(p.text).contains("[p.1]").contains("Not found in the document.").contains("What is the rent?")
        assertThat(p.pages).containsExactly(1, 2, 3)
        val small = PromptBuilder.build(AiTask.ASK, "q", chunks, maxContextChars = 120)
        assertThat(small.truncated).isTrue()
        assertThat(small.pages).containsExactly(1)
    }

    @Test fun citationsOutsideSuppliedPagesAreDropped() {
        assertThat(CitationParser.cited("Rent is 500 [p.1]. Also [p.9] and (page 3).", listOf(1, 2, 3))).containsExactly(1, 3)
    }
}
