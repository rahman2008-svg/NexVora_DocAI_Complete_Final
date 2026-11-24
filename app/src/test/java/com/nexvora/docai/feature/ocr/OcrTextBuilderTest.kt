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
package com.nexvora.docai.feature.ocr

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.ImageRect
import org.fairscan.imageprocessing.OcrTextBox
import org.junit.Test

class OcrTextBuilderTest {
    private fun box(t: String, left: Int, lineBottom: Int) =
        OcrTextBox(t, ImageRect(left, lineBottom - 20, left + 50, lineBottom), 20, lineBottom)

    @Test fun groupsWordsIntoLinesLeftToRight() {
        val text = OcrTextBuilder.build(listOf(box("world", 100, 50), box("Hello", 10, 52), box("Second", 10, 120)))
        assertThat(text).isEqualTo("Hello world\nSecond")
    }

    @Test fun emptyInputGivesEmptyText() {
        assertThat(OcrTextBuilder.build(emptyList())).isEmpty()
    }
}
