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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TextAnalysisTest {
    private val pages = listOf(
        PageText(1, "Introduction to the project. Nothing about money here."),
        PageText(2, "The invoice total is 500 dollars. The invoice must be paid by Friday."),
        PageText(3, "Appendix with unrelated notes."),
    )

    @Test fun searchFindsPagesWithReferences() {
        val hits = KeywordSearch.search(pages, "invoice total")
        assertThat(hits).isNotEmpty()
        assertThat(hits.first().page).isEqualTo(2)
        assertThat(hits.first().snippet).contains("invoice")
    }

    @Test fun searchReturnsNothingWhenAbsentOrOnlyStopWords() {
        assertThat(KeywordSearch.search(pages, "zebra")).isEmpty()
        assertThat(KeywordSearch.search(pages, "the of and")).isEmpty()
    }

    @Test fun searchKeepsBanglaWordsIntact() {
        val bn = listOf(PageText(1, "ডিজিটাল ডকুমেন্ট ব্যবস্থাপনা"))
        assertThat(KeywordSearch.terms("ডিজিটাল")).containsExactly("ডিজিটাল")
        assertThat(KeywordSearch.search(bn, "ডকুমেন্ট")).hasSize(1)
    }

    @Test fun summaryPicksExistingSentencesInOriginalOrder() {
        val text = "Solar energy is a renewable energy source used widely. " +
            "Many homes install solar panels to produce energy. " +
            "Cats are small animals that sleep a lot during the day. " +
            "Solar energy panels convert sunlight into electric energy. " +
            "The weather today is somewhat cloudy in the city."
        val summary = ExtractiveSummary.summarize(text, 2)
        assertThat(summary).hasSize(2)
        assertThat(text).contains(summary[0]).contains(summary[1])
        assertThat(text.indexOf(summary[0])).isLessThan(text.indexOf(summary[1]))
        assertThat(summary.joinToString(" ")).doesNotContain("Cats")
    }

    @Test fun summaryOfShortTextReturnsAllSentences() {
        assertThat(ExtractiveSummary.summarize("Only one sentence is here.", 5)).hasSize(1)
        assertThat(ExtractiveSummary.summarize("", 5)).isEmpty()
    }
}
