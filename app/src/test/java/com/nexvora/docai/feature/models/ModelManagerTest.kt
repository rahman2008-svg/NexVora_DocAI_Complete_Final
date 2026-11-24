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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelManagerTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun manager() = ModelManager(tmp.root.resolve("models"), CoroutineScope(Dispatchers.Unconfined)) { 1L shl 40 }

    @Test fun onlyHttpsLinksAreAccepted() {
        assertThat(ModelManager.validateUrl("http://example.com/m.gguf")).isNotNull()
        assertThat(ModelManager.validateUrl("ftp://example.com/m.gguf")).isNotNull()
        assertThat(ModelManager.validateUrl("https://example.com/m.gguf")).isNull()
    }

    @Test fun importListsPersistsAndDeletes() {
        val m = manager()
        assertThat(m.importStream("Tiny", ModelKind.TRANSLATION, "", "abc".byteInputStream(), "tiny.bin")).isNull()
        val rec = m.installed.value.single()
        assertThat(rec.license).isEqualTo("Unknown")
        assertThat(rec.sizeBytes).isEqualTo(3)
        assertThat(manager().installed.value).hasSize(1)
        assertThat(m.delete(rec.id)).isTrue()
        assertThat(m.file(rec).exists()).isFalse()
        assertThat(manager().installed.value).isEmpty()
    }

    @Test fun emptyFileIsRejected() {
        val m = manager()
        assertThat(m.importStream("Empty", ModelKind.LLM, "MIT", "".byteInputStream(), "e.gguf")).isNotNull()
        assertThat(m.installed.value).isEmpty()
    }

    @Test fun noInferenceEngineIsReportedHonestly() {
        assertThat(NoTextGenerationEngine.isAvailable).isFalse()
        assertThat(NoTranslationEngine.isAvailable).isFalse()
    }

    @Test fun llmImportRequiresValidGguf() {
        val m = manager()
        val err = m.importStream("Fake", ModelKind.LLM, "MIT", "not a gguf file at all, just text".byteInputStream(), "fake.gguf")
        assertThat(err).contains("GGUF")
        assertThat(m.installed.value).isEmpty()
        assertThat(tmp.root.resolve("models").listFiles { f -> f.name.endsWith(".gguf") }?.size ?: 0).isEqualTo(0)
        val ok = m.importStream("Real", ModelKind.LLM, "Apache-2.0", com.nexvora.docai.feature.ai.GgufTestData.minimal().inputStream(), "real.gguf")
        assertThat(ok).isNull()
        val rec = m.installed.value.single()
        assertThat(rec.architecture).isEqualTo("llama")
        assertThat(rec.contextLength).isEqualTo(4096)
    }
}
