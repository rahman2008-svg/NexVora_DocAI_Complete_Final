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

class PageRangesTest {
    @Test fun parsesSinglePagesAndRanges() {
        val r = PageRanges.parse("1-3, 5", 10)
        assertThat(r.ok).isTrue()
        assertThat(r.pages).containsExactly(0, 1, 2, 4)
    }

    @Test fun keepsOrderAndDuplicates() {
        assertThat(PageRanges.parse("3,1,3", 5).pages).containsExactly(2, 0, 2)
    }

    @Test fun groupsSplitByComma() {
        val g = PageRanges.parseGroups("1-2, 3, 4-5", 5)
        assertThat(g.groups).containsExactly(listOf(0, 1), listOf(2), listOf(3, 4))
    }

    @Test fun rejectsOutOfRange() {
        assertThat(PageRanges.parse("1-9", 5).error).contains("does not exist")
        assertThat(PageRanges.parse("0", 5).ok).isFalse()
    }

    @Test fun rejectsGarbageAndReversedRanges() {
        assertThat(PageRanges.parse("a", 5).ok).isFalse()
        assertThat(PageRanges.parse("4-2", 5).ok).isFalse()
        assertThat(PageRanges.parse("1-2-3", 5).ok).isFalse()
    }

    @Test fun rejectsEmptyInput() {
        assertThat(PageRanges.parse("  ", 5).ok).isFalse()
        assertThat(PageRanges.parse("1", 0).ok).isFalse()
    }
}
