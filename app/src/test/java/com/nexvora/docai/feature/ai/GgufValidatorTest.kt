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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GgufValidatorTest {
    @Test fun readsMetadataAndSkipsArrays() {
        val info = GgufValidator.read(GgufTestData.minimal().inputStream())
        assertThat(info.version).isEqualTo(3)
        assertThat(info.architecture).isEqualTo("llama")
        assertThat(info.name).isEqualTo("Tiny Test")
        assertThat(info.license).isEqualTo("apache-2.0")
        assertThat(info.contextLength).isEqualTo(4096)
    }

    @Test fun rejectsWrongMagicVersionAndTruncation() {
        fun fails(bytes: ByteArray) = try { GgufValidator.read(bytes.inputStream()); false } catch (_: GgufException) { true }
        assertThat(fails("NOPE".toByteArray() + ByteArray(40))).isTrue()
        assertThat(fails(GgufTestData.minimal(version = 1))).isTrue()
        assertThat(fails(GgufTestData.minimal().copyOf(60))).isTrue()
        assertThat(fails(ByteArray(0))).isTrue()
    }

    @Test fun requiresArchitecture() {
        val noArch = GgufTestData.minimal().let { String(it, Charsets.ISO_8859_1).replace("general.architecture", "general.architecturX").toByteArray(Charsets.ISO_8859_1) }
        try { GgufValidator.read(noArch.inputStream()); throw AssertionError("expected failure") } catch (_: GgufException) { }
    }
}
