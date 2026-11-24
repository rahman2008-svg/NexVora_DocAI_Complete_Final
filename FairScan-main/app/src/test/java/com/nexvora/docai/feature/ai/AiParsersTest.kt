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

import com.nexvora.docai.feature.study.AiJsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AiParsersTest {
    @Test fun parsesMcqJsonEvenWithChatterAndInvalidItems() {
        val raw = """Sure! Here you go:
```json
[{"question":"2+2?","options":["3","4","5","6"],"correct":1,"explanation":"basic"},
 {"question":"","options":["a","b"],"correct":0},
 {"question":"bad index","options":["a","b"],"correct":5}]
```
Hope it helps"""
        val qs = AiJsonParser.mcqs(raw)
        assertThat(qs).hasSize(1)
        assertThat(qs.single().correct).isEqualTo(1)
        assertThat(qs.single().explanation).isEqualTo("basic")
    }

    @Test fun parsesFlashcardsAndRejectsGarbage() {
        assertThat(AiJsonParser.flashcards("""[{"front":"Q","back":"A"},{"front":"","back":"x"}]""")).hasSize(1)
        assertThat(AiJsonParser.flashcards("no json here")).isEmpty()
        assertThat(AiJsonParser.mcqs("[{broken")).isEmpty()
    }
}
